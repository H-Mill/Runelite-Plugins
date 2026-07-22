package com.mlmqol;

import com.google.common.collect.ImmutableSet;
import com.google.inject.Provides;
import java.util.HashSet;
import java.util.Set;
import javax.inject.Inject;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.GameObject;
import net.runelite.api.GameState;
import net.runelite.api.MenuAction;
import net.runelite.api.MenuEntry;
import net.runelite.api.NPC;
import net.runelite.api.Tile;
import net.runelite.api.TileObject;
import net.runelite.api.WorldView;
import net.runelite.api.events.DecorativeObjectDespawned;
import net.runelite.api.events.DecorativeObjectSpawned;
import net.runelite.api.events.GameObjectDespawned;
import net.runelite.api.events.GameObjectSpawned;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GroundObjectDespawned;
import net.runelite.api.events.GroundObjectSpawned;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.events.MenuEntryAdded;
import net.runelite.api.events.NpcDespawned;
import net.runelite.api.events.NpcSpawned;
import net.runelite.api.events.VarbitChanged;
import net.runelite.api.events.WallObjectDespawned;
import net.runelite.api.events.WallObjectSpawned;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.gameval.NpcID;
import net.runelite.api.gameval.ObjectID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.chat.ChatColorType;
import net.runelite.client.chat.ChatMessageBuilder;
import net.runelite.client.chat.ChatMessageManager;
import net.runelite.client.chat.QueuedMessage;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.util.Text;

@Slf4j
@PluginDescriptor(
		name = "MLM QOL",
		description = "Quality of life for the Motherlode Mine: stop mining when the sack is full, and flag a stopped hopper",
		tags = {"motherlode", "mlm", "mining", "sack", "pay", "dirt", "skilling"},
		configName = "MlmQolPlugin"
)
public class MlmQolPlugin extends Plugin
{
	static final String CONFIG_GROUP = "mlm-qol";

	private static final Set<Integer> MOTHERLODE_MAP_REGIONS = ImmutableSet.of(14679, 14680, 14681, 14935, 14936, 14937, 15191, 15192, 15193);
	private static final Set<Integer> ORE_VEINS = ImmutableSet.of(ObjectID.MOTHERLODE_ORE_SINGLE, ObjectID.MOTHERLODE_ORE_LEFT,
		ObjectID.MOTHERLODE_ORE_MIDDLE, ObjectID.MOTHERLODE_ORE_RIGHT);
	private static final Set<MenuAction> OBJECT_OPTIONS = ImmutableSet.of(MenuAction.GAME_OBJECT_FIRST_OPTION,
		MenuAction.GAME_OBJECT_SECOND_OPTION, MenuAction.GAME_OBJECT_THIRD_OPTION, MenuAction.GAME_OBJECT_FOURTH_OPTION,
		MenuAction.GAME_OBJECT_FIFTH_OPTION);
	/**
	 * The baby mole pet, plus the baby mole NPCs that share its look. Only ever matched while in the
	 * mine, so a pet being renamed anywhere else is not possible.
	 */
	private static final Set<Integer> BABY_MOLES = ImmutableSet.of(NpcID.MOLE_PET, NpcID.MOLE_PET_NAKED,
		NpcID.MOLE_BABY_01, NpcID.MOLE_BABY_02, NpcID.MOLE_BABY_03);

	private static final String MINE_OPTION = "Mine";
	private static final String DEPOSIT_OPTION = "Deposit";
	/** One strut per water wheel. The belt only stops when every one of them is broken. */
	private static final int STRUT_COUNT = 2;

	@Inject
	private Client client;

	@Inject
	private ClientThread clientThread;

	@Inject
	private ChatMessageManager chatMessageManager;

	@Inject
	private ConfigManager configManager;

	@Inject
	private OverlayManager overlayManager;

	@Inject
	private HopperOverlay hopperOverlay;

	@Inject
	private BabyMoleOverlay babyMoleOverlay;

	@Inject
	private MlmQolConfig config;

	private final SackTracker sackTracker = new SackTracker();

	@Getter(AccessLevel.PACKAGE)
	private final Set<TileObject> hoppers = new HashSet<>();
	@Getter(AccessLevel.PACKAGE)
	private final Set<TileObject> brokenStruts = new HashSet<>();
	private final Set<TileObject> workingStruts = new HashSet<>();
	@Getter(AccessLevel.PACKAGE)
	private final Set<NPC> babyMoles = new HashSet<>();

	private boolean inMlm;
	private boolean sackFull;
	private boolean machineryStopped;

	@Provides
	MlmQolConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(MlmQolConfig.class);
	}

	@Override
	protected void startUp()
	{
		overlayManager.add(hopperOverlay);
		overlayManager.add(babyMoleOverlay);

		clientThread.invokeLater(() ->
		{
			inMlm = checkInMlm();
			if (inMlm)
			{
				primeTracker();
				// Spawn events for the scene we're already standing in have long since fired.
				scanScene();
			}
			// Adopt the current state silently so enabling the plugin doesn't spam a warning. This has
			// to settle before following the upgrade, since writing config re-evaluates the state.
			sackFull = inMlm && sackTracker.isFull(config.blockAtCount());

			// Not gated on being in the mine: the upgrade is readable from anywhere, and the plugin
			// usually starts up at the login screen.
			sackTracker.refreshCapacity(client);
			followCapacityUpgrade();
		});
	}

	@Override
	protected void shutDown()
	{
		overlayManager.remove(hopperOverlay);
		overlayManager.remove(babyMoleOverlay);
		inMlm = false;
		sackFull = false;
		sackTracker.reset();
		clearSceneObjects();
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		GameState gameState = event.getGameState();
		if (gameState == GameState.LOADING)
		{
			// On region changes the tracked objects get set to null.
			clearSceneObjects();

			boolean wasInMlm = inMlm;
			inMlm = checkInMlm();
			if (inMlm && !wasInMlm)
			{
				// Anything tracked from a previous visit is stale — start over.
				primeTracker();
				followCapacityUpgrade();
				updateSackFull();
			}
		}
		else if (gameState == GameState.LOGGED_IN)
		{
			// Backstop for the varbit handler: re-logging in the same client session doesn't change
			// the varp, so no VarbitChanged fires and the upgrade would otherwise go unnoticed.
			sackTracker.refreshCapacity(client);
			followCapacityUpgrade();
		}
		else if (gameState == GameState.LOGIN_SCREEN)
		{
			inMlm = false;
			sackFull = false;
			sackTracker.reset();
			clearSceneObjects();
		}
	}

	// The mine mixes object types — the ore veins are wall objects, for instance — so the hopper and
	// struts are tracked from every spawn event rather than assuming which kind they are.

	@Subscribe
	public void onGameObjectSpawned(GameObjectSpawned event)
	{
		trackObject(event.getGameObject());
	}

	@Subscribe
	public void onGameObjectDespawned(GameObjectDespawned event)
	{
		untrackObject(event.getGameObject());
	}

	@Subscribe
	public void onWallObjectSpawned(WallObjectSpawned event)
	{
		trackObject(event.getWallObject());
	}

	@Subscribe
	public void onWallObjectDespawned(WallObjectDespawned event)
	{
		untrackObject(event.getWallObject());
	}

	@Subscribe
	public void onDecorativeObjectSpawned(DecorativeObjectSpawned event)
	{
		trackObject(event.getDecorativeObject());
	}

	@Subscribe
	public void onDecorativeObjectDespawned(DecorativeObjectDespawned event)
	{
		untrackObject(event.getDecorativeObject());
	}

	@Subscribe
	public void onGroundObjectSpawned(GroundObjectSpawned event)
	{
		trackObject(event.getGroundObject());
	}

	@Subscribe
	public void onGroundObjectDespawned(GroundObjectDespawned event)
	{
		untrackObject(event.getGroundObject());
	}

	@Subscribe
	public void onNpcSpawned(NpcSpawned event)
	{
		if (inMlm && BABY_MOLES.contains(event.getNpc().getId()))
		{
			babyMoles.add(event.getNpc());
		}
	}

	@Subscribe
	public void onNpcDespawned(NpcDespawned event)
	{
		babyMoles.remove(event.getNpc());
	}

	private void trackObject(TileObject object)
	{
		if (!inMlm || object == null)
		{
			return;
		}

		switch (object.getId())
		{
			case ObjectID.MOTHERLODE_HOPPER:
				hoppers.add(object);
				break;
			case ObjectID.MOTHERLODE_WHEEL_STRUT_BROKEN:
				brokenStruts.add(object);
				updateMachineryState();
				break;
			case ObjectID.MOTHERLODE_WHEEL_STRUT_FIXED:
				workingStruts.add(object);
				updateMachineryState();
				break;
		}
	}

	private void untrackObject(TileObject object)
	{
		if (!inMlm || object == null)
		{
			return;
		}

		hoppers.remove(object);

		boolean wasStrut = brokenStruts.remove(object);
		wasStrut |= workingStruts.remove(object);
		if (wasStrut)
		{
			updateMachineryState();
		}
	}

	/**
	 * Picks up the hopper and struts from the loaded scene. Only run when the plugin is enabled
	 * mid-session, since after that the spawn and despawn events keep the sets current.
	 */
	private void scanScene()
	{
		WorldView worldView = client.getTopLevelWorldView();
		if (worldView == null)
		{
			return;
		}

		for (Tile[][] plane : worldView.getScene().getTiles())
		{
			for (Tile[] column : plane)
			{
				for (Tile tile : column)
				{
					if (tile == null)
					{
						continue;
					}

					for (GameObject gameObject : tile.getGameObjects())
					{
						trackObject(gameObject);
					}

					trackObject(tile.getWallObject());
					trackObject(tile.getDecorativeObject());
					trackObject(tile.getGroundObject());
				}
			}
		}

		for (NPC npc : worldView.npcs())
		{
			if (BABY_MOLES.contains(npc.getId()))
			{
				babyMoles.add(npc);
			}
		}
	}

	@Subscribe
	public void onVarbitChanged(VarbitChanged event)
	{
		// The upgrade varbit is only transmitted during the login varp sync, long before the player is
		// anywhere near the mine, so this must not be gated on inMlm.
		if (event.getVarbitId() == VarbitID.MOTHERLODE_BIGGERSACK)
		{
			sackTracker.refreshCapacity(client);
			followCapacityUpgrade();
		}

		if (!inMlm)
		{
			return;
		}

		if (event.getVarbitId() == VarbitID.MOTHERLODE_SACK_TRANSMIT || event.getVarbitId() == VarbitID.MOTHERLODE_BIGGERSACK)
		{
			sackTracker.refreshSack(client);
			updateSackFull();
		}
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		if (CONFIG_GROUP.equals(event.getGroup()))
		{
			updateSackFull();
		}
	}

	@Subscribe
	public void onItemContainerChanged(ItemContainerChanged event)
	{
		if (!inMlm || event.getContainerId() != InventoryID.INV)
		{
			return;
		}

		sackTracker.refreshInventory(client);
		updateSackFull();
	}

	@Subscribe
	public void onGameTick(GameTick event)
	{
		if (!inMlm)
		{
			return;
		}

		// The write-off assumes pay-dirt that never arrives was never deposited. A stopped belt is a
		// legitimate long gap, so don't let it discard pay-dirt that is genuinely stranded out there.
		if (!machineryStopped)
		{
			sackTracker.onTick();
		}

		updateSackFull();
	}

	@Subscribe
	public void onMenuEntryAdded(MenuEntryAdded event)
	{
		if (!inMlm)
		{
			return;
		}

		MenuEntry entry = event.getMenuEntry();
		if (!OBJECT_OPTIONS.contains(entry.getType()))
		{
			return;
		}

		int objectId = event.getIdentifier();
		String option = Text.removeTags(entry.getOption());

		// Nothing to mine into once the sack is at its block count.
		if (ORE_VEINS.contains(objectId) && MINE_OPTION.equals(option) && sackFull && config.blockMining())
		{
			client.getMenu().removeMenuEntry(entry);
			return;
		}

		// Nowhere for it to go: the belt is stopped and the sack has no room for another load.
		if (objectId == ObjectID.MOTHERLODE_HOPPER && DEPOSIT_OPTION.equals(option) && isDepositBlocked())
		{
			client.getMenu().removeMenuEntry(entry);
		}
	}

	/**
	 * Two things stop another load going in, and either is enough: pay-dirt already stranded on the
	 * stopped belt, or a sack with no room left for it. Pay-dirt in the inventory counts towards
	 * neither — that is the load asking to be deposited, not something in its way.
	 */
	private boolean isDepositBlocked()
	{
		// Only a stopped belt makes a deposit pointless. While it runs, pay-dirt on it is on its way
		// out and the sack will have room again shortly.
		if (!config.blockDepositWhenBlocked() || !machineryStopped)
		{
			return false;
		}

		return hasPayDirtInTransit() || sackTracker.isCommittedFull(config.blockAtCount());
	}

	/**
	 * Whether there is anything worth saying about the hopper at all. The overlay runs every frame,
	 * so it checks this before working out which of the two problems apply.
	 */
	boolean shouldFlagHopper()
	{
		return machineryStopped || hasPayDirtInTransit();
	}

	/**
	 * @return true when pay-dirt of yours is on the belt, whether or not the belt is moving
	 */
	boolean hasPayDirtInTransit()
	{
		return sackTracker.getInTransit() > 0;
	}

	/**
	 * @return true when the belt has stopped, so pay-dirt put in the hopper would not reach the sack
	 */
	boolean isMachineryStopped()
	{
		return machineryStopped;
	}

	/**
	 * @return true when pay-dirt is stranded on a stopped belt, going nowhere until a strut is fixed
	 */
	boolean hasStuckPayDirt()
	{
		return machineryStopped && sackTracker.getInTransit() > 0;
	}

	/**
	 * The two water wheels drive the conveyor belt, and each is held up by a strut. Water only stops
	 * once both struts are broken — repairing either one starts the belt again.
	 */
	private void updateMachineryState()
	{
		boolean stopped = brokenStruts.size() >= STRUT_COUNT;
		if (stopped == machineryStopped)
		{
			return;
		}

		machineryStopped = stopped;
		log.debug("Machinery stopped: {} ({} broken struts, {} working, {} hoppers tracked)", stopped,
			brokenStruts.size(), workingStruts.size(), hoppers.size());
	}

	private void clearSceneObjects()
	{
		hoppers.clear();
		brokenStruts.clear();
		workingStruts.clear();
		babyMoles.clear();
		machineryStopped = false;
	}

	private void primeTracker()
	{
		sackTracker.reset();
		sackTracker.refreshSack(client);
		sackTracker.refreshInventory(client);
		sackTracker.prime();
	}

	/**
	 * Moves the block count up to the larger sack size once the upgrade is unlocked. A block count of
	 * 108 always follows the upgrade, whether it was left at the default or set there deliberately —
	 * only some other value is treated as a deliberate choice and left alone.
	 */
	private void followCapacityUpgrade()
	{
		if (sackTracker.getCapacity() != SackTracker.SACK_LARGE_SIZE)
		{
			return;
		}

		String stored = configManager.getConfiguration(CONFIG_GROUP, MlmQolConfig.BLOCK_AT_COUNT);
		if (stored != null && !stored.equals(Integer.toString(SackTracker.SACK_SIZE)))
		{
			log.debug("Sack upgrade unlocked but block count is set to {}, leaving it alone", stored);
			return;
		}

		log.debug("Sack upgrade unlocked, raising block count to {}", SackTracker.SACK_LARGE_SIZE);
		configManager.setConfiguration(CONFIG_GROUP, MlmQolConfig.BLOCK_AT_COUNT, SackTracker.SACK_LARGE_SIZE);
	}

	private void updateSackFull()
	{
		if (!inMlm)
		{
			return;
		}

		int blockAt = Math.min(config.blockAtCount(), sackTracker.getCapacity());
		boolean full = sackTracker.isFull(blockAt);
		if (full == sackFull)
		{
			return;
		}

		sackFull = full;
		log.debug("Sack full: {} ({}/{} blocking at {}, {} deposited, {} on the belt, {} carried)", full,
			sackTracker.getUsed(), sackTracker.getCapacity(), blockAt,
			sackTracker.getDeposited(), sackTracker.getInTransit(), sackTracker.getCarriedPayDirt());

		if (config.warnInChat())
		{
			sendChatMessage(full
				? "Sack limit reached (" + sackTracker.getUsed() + "/" + blockAt
					+ ") - mining is blocked until you collect from the sack."
				: "Your sack has room again - mining is re-enabled.");
		}
	}

	private void sendChatMessage(String message)
	{
		String formatted = new ChatMessageBuilder()
			.append(ChatColorType.HIGHLIGHT)
			.append(message)
			.build();

		chatMessageManager.queue(QueuedMessage.builder()
			.type(ChatMessageType.GAMEMESSAGE)
			.runeLiteFormattedMessage(formatted)
			.build());
	}

	private boolean checkInMlm()
	{
		GameState gameState = client.getGameState();
		if (gameState != GameState.LOGGED_IN && gameState != GameState.LOADING)
		{
			return false;
		}

		WorldView worldView = client.getTopLevelWorldView();
		if (worldView == null)
		{
			return false;
		}

		for (int region : worldView.getMapRegions())
		{
			if (!MOTHERLODE_MAP_REGIONS.contains(region))
			{
				return false;
			}
		}

		return true;
	}
}
