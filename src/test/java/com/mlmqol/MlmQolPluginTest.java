package com.mlmqol;

import com.google.inject.Guice;
import com.google.inject.Injector;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import net.runelite.api.Client;
import net.runelite.api.GameObject;
import net.runelite.api.GameState;
import net.runelite.api.IndexedObjectSet;
import net.runelite.api.ItemContainer;
import net.runelite.api.Menu;
import net.runelite.api.MenuAction;
import net.runelite.api.MenuEntry;
import net.runelite.api.NPC;
import net.runelite.api.Scene;
import net.runelite.api.Tile;
import net.runelite.api.WallObject;
import net.runelite.api.WorldView;
import net.runelite.api.events.GameObjectDespawned;
import net.runelite.api.events.GameObjectSpawned;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.events.MenuEntryAdded;
import net.runelite.api.events.NpcDespawned;
import net.runelite.api.events.NpcSpawned;
import net.runelite.api.events.VarbitChanged;
import net.runelite.api.events.WallObjectSpawned;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.NpcID;
import net.runelite.api.gameval.ObjectID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.chat.ChatMessageManager;
import net.runelite.client.chat.QueuedMessage;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.ui.overlay.OverlayManager;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * End-to-end coverage of the plugin: real {@link MlmQolPlugin} and {@link SackTracker} wired to a
 * mocked client, driven by the same game events RuneLite would post. Everything here goes in through
 * an event handler and comes out as a menu removal, a chat message, a config write, or scene state.
 */
public class MlmQolPluginTest
{
	private static final int MLM_REGION = 14679;
	private static final int ELSEWHERE_REGION = 12850;
	private static final int SACK_SIZE = 108;
	private static final int SACK_LARGE_SIZE = 189;

	private Client client;
	private ClientThread clientThread;
	private ConfigManager configManager;
	private ChatMessageManager chatMessageManager;
	private OverlayManager overlayManager;
	private HopperOverlay hopperOverlay;
	private BabyMoleOverlay babyMoleOverlay;
	private MlmQolConfig config;
	private Menu menu;
	private ItemContainer inventory;
	private WorldView worldView;
	private Scene scene;

	private MlmQolPlugin plugin;

	@Before
	public void setUp() throws Exception
	{
		client = mock(Client.class);
		clientThread = mock(ClientThread.class);
		configManager = mock(ConfigManager.class);
		chatMessageManager = mock(ChatMessageManager.class);
		overlayManager = mock(OverlayManager.class);
		hopperOverlay = mock(HopperOverlay.class);
		babyMoleOverlay = mock(BabyMoleOverlay.class);
		config = mock(MlmQolConfig.class);
		menu = mock(Menu.class);
		inventory = mock(ItemContainer.class);
		worldView = mock(WorldView.class);
		scene = mock(Scene.class);

		when(config.blockMining()).thenReturn(true);
		when(config.blockAtCount()).thenReturn(SACK_SIZE);
		when(config.warnInChat()).thenReturn(true);
		when(config.blockDepositWhenBlocked()).thenReturn(true);

		when(client.getMenu()).thenReturn(menu);
		when(client.getItemContainer(InventoryID.INV)).thenReturn(inventory);
		when(client.getTopLevelWorldView()).thenReturn(worldView);
		when(worldView.getScene()).thenReturn(scene);
		when(scene.getTiles()).thenReturn(new Tile[0][0][0]);
		doReturn(npcSet()).when(worldView).npcs();

		deposited(0);
		upgraded(false);
		carrying(0);
		inRegion(MLM_REGION);

		// Run anything deferred to the client thread inline.
		doAnswer(invocation ->
		{
			((Runnable) invocation.getArgument(0)).run();
			return null;
		}).when(clientThread).invokeLater(any(Runnable.class));

		Injector injector = Guice.createInjector(binder ->
		{
			binder.bind(Client.class).toInstance(client);
			binder.bind(ClientThread.class).toInstance(clientThread);
			binder.bind(ConfigManager.class).toInstance(configManager);
			binder.bind(ChatMessageManager.class).toInstance(chatMessageManager);
			binder.bind(OverlayManager.class).toInstance(overlayManager);
			binder.bind(HopperOverlay.class).toInstance(hopperOverlay);
			binder.bind(BabyMoleOverlay.class).toInstance(babyMoleOverlay);
			binder.bind(MlmQolConfig.class).toInstance(config);
		});
		plugin = injector.getInstance(MlmQolPlugin.class);
	}

	// ---- harness -------------------------------------------------------------------------------

	private void deposited(int count)
	{
		when(client.getVarbitValue(VarbitID.MOTHERLODE_SACK_TRANSMIT)).thenReturn(count);
	}

	private void upgraded(boolean upgraded)
	{
		when(client.getVarbitValue(VarbitID.MOTHERLODE_BIGGERSACK)).thenReturn(upgraded ? 1 : 0);
	}

	private void carrying(int payDirt)
	{
		when(inventory.count(ItemID.PAYDIRT)).thenReturn(payDirt);
	}

	private void inRegion(int region)
	{
		when(worldView.getMapRegions()).thenReturn(new int[]{region});
	}

	private void gameState(GameState gameState)
	{
		when(client.getGameState()).thenReturn(gameState);
		GameStateChanged event = new GameStateChanged();
		event.setGameState(gameState);
		plugin.onGameStateChanged(event);
	}

	/** Walks into the mine, which is what makes the plugin start tracking anything. */
	private void enterMlm()
	{
		inRegion(MLM_REGION);
		gameState(GameState.LOADING);
	}

	private void leaveMlm()
	{
		inRegion(ELSEWHERE_REGION);
		gameState(GameState.LOADING);
	}

	/** Already logged in and standing in the mine, as when the plugin is enabled mid-session. */
	private void standingInTheMine()
	{
		when(client.getGameState()).thenReturn(GameState.LOGGED_IN);
		inRegion(MLM_REGION);
	}

	/** Builds a one-tile scene holding the given objects, for the enable-mid-session scan. */
	private void sceneContaining(GameObject... objects)
	{
		Tile tile = mock(Tile.class);
		when(tile.getGameObjects()).thenReturn(objects);
		when(scene.getTiles()).thenReturn(new Tile[][][]{{{tile}}});
	}

	private void sackVarbitChanged()
	{
		VarbitChanged event = new VarbitChanged();
		event.setVarbitId(VarbitID.MOTHERLODE_SACK_TRANSMIT);
		plugin.onVarbitChanged(event);
	}

	private void upgradeVarbitChanged()
	{
		VarbitChanged event = new VarbitChanged();
		event.setVarbitId(VarbitID.MOTHERLODE_BIGGERSACK);
		plugin.onVarbitChanged(event);
	}

	private void inventoryChanged()
	{
		plugin.onItemContainerChanged(new ItemContainerChanged(InventoryID.INV, inventory));
	}

	private void tick(int count)
	{
		for (int i = 0; i < count; i++)
		{
			plugin.onGameTick(new GameTick());
		}
	}

	private GameObject spawnGameObject(int id)
	{
		GameObject object = mock(GameObject.class);
		when(object.getId()).thenReturn(id);
		GameObjectSpawned event = new GameObjectSpawned();
		event.setGameObject(object);
		plugin.onGameObjectSpawned(event);
		return object;
	}

	private void despawnGameObject(GameObject object)
	{
		GameObjectDespawned event = new GameObjectDespawned();
		event.setGameObject(object);
		plugin.onGameObjectDespawned(event);
	}

	private NPC spawnNpc(int id)
	{
		NPC npc = mock(NPC.class);
		when(npc.getId()).thenReturn(id);
		plugin.onNpcSpawned(new NpcSpawned(npc));
		return npc;
	}

	private static IndexedObjectSet<NPC> npcSet(NPC... npcs)
	{
		List<NPC> list = Arrays.asList(npcs);
		return new IndexedObjectSet<NPC>()
		{
			@Override
			public NPC byIndex(int index)
			{
				return list.get(index);
			}

			@Override
			public Iterator<NPC> iterator()
			{
				return list.iterator();
			}
		};
	}

	private void breakBothStruts()
	{
		spawnGameObject(ObjectID.MOTHERLODE_WHEEL_STRUT_BROKEN);
		spawnGameObject(ObjectID.MOTHERLODE_WHEEL_STRUT_BROKEN);
	}

	private MenuEntry menuEntry(String option, int objectId, MenuAction type)
	{
		MenuEntry entry = mock(MenuEntry.class);
		when(entry.getOption()).thenReturn(option);
		when(entry.getIdentifier()).thenReturn(objectId);
		when(entry.getType()).thenReturn(type);
		return entry;
	}

	private MenuEntry addMenuEntry(String option, int objectId)
	{
		MenuEntry entry = menuEntry(option, objectId, MenuAction.GAME_OBJECT_FIRST_OPTION);
		plugin.onMenuEntryAdded(new MenuEntryAdded(entry));
		return entry;
	}

	/** Deposits pay-dirt so it is out of the inventory but not yet counted by the sack varbit. */
	private void strandPayDirt(int payDirt)
	{
		carrying(payDirt);
		inventoryChanged();
		carrying(0);
		inventoryChanged();
	}

	/** Fills the sack to its block count, the state that blocks mining. */
	private void fillSack()
	{
		deposited(SACK_SIZE);
		sackVarbitChanged();
	}

	// ---- menu blocking -------------------------------------------------------------------------

	@Test
	public void mineIsRemovedFromAVeinOnceTheSackIsFull()
	{
		enterMlm();
		fillSack();

		MenuEntry entry = addMenuEntry("Mine", ObjectID.MOTHERLODE_ORE_SINGLE);

		verify(menu).removeMenuEntry(entry);
	}

	@Test
	public void mineIsKeptWhileTheSackHasRoom()
	{
		enterMlm();
		deposited(107);
		sackVarbitChanged();

		addMenuEntry("Mine", ObjectID.MOTHERLODE_ORE_SINGLE);

		verify(menu, never()).removeMenuEntry(any());
	}

	@Test
	public void allFourVeinVariantsAreBlocked()
	{
		enterMlm();
		fillSack();

		addMenuEntry("Mine", ObjectID.MOTHERLODE_ORE_SINGLE);
		addMenuEntry("Mine", ObjectID.MOTHERLODE_ORE_LEFT);
		addMenuEntry("Mine", ObjectID.MOTHERLODE_ORE_MIDDLE);
		addMenuEntry("Mine", ObjectID.MOTHERLODE_ORE_RIGHT);

		verify(menu, times(4)).removeMenuEntry(any());
	}

	@Test
	public void otherOptionsOnTheVeinAreLeftAlone()
	{
		enterMlm();
		fillSack();

		addMenuEntry("Prospect", ObjectID.MOTHERLODE_ORE_SINGLE);
		addMenuEntry("Walk here", ObjectID.MOTHERLODE_ORE_SINGLE);

		verify(menu, never()).removeMenuEntry(any());
	}

	@Test
	public void mineOnSomethingThatIsNotAVeinIsLeftAlone()
	{
		enterMlm();
		fillSack();

		// An ordinary rock elsewhere in the scene.
		addMenuEntry("Mine", ObjectID.MOTHERLODE_ROCKFALL_1);

		verify(menu, never()).removeMenuEntry(any());
	}

	@Test
	public void nonObjectMenuActionsAreLeftAlone()
	{
		enterMlm();
		fillSack();

		// Same option and identifier, but a widget action rather than an object one.
		MenuEntry entry = menuEntry("Mine", ObjectID.MOTHERLODE_ORE_SINGLE, MenuAction.WIDGET_TARGET_ON_GAME_OBJECT);
		plugin.onMenuEntryAdded(new MenuEntryAdded(entry));

		verify(menu, never()).removeMenuEntry(any());
	}

	@Test
	public void blockingCanBeTurnedOff()
	{
		when(config.blockMining()).thenReturn(false);
		enterMlm();
		fillSack();

		addMenuEntry("Mine", ObjectID.MOTHERLODE_ORE_SINGLE);

		verify(menu, never()).removeMenuEntry(any());
	}

	@Test
	public void nothingIsBlockedOutsideTheMine()
	{
		enterMlm();
		fillSack();
		leaveMlm();

		addMenuEntry("Mine", ObjectID.MOTHERLODE_ORE_SINGLE);

		verify(menu, never()).removeMenuEntry(any());
	}

	@Test
	public void collectingFromTheSackUnblocksMining()
	{
		enterMlm();
		fillSack();

		deposited(0);
		sackVarbitChanged();
		addMenuEntry("Mine", ObjectID.MOTHERLODE_ORE_SINGLE);

		verify(menu, never()).removeMenuEntry(any());
	}

	@Test
	public void depositingBlocksImmediatelyRatherThanWhenTheBeltCatchesUp()
	{
		// The conveyor takes several seconds, during which the sack varbit still reads low. Without
		// in-transit tracking there would be a window here where mining was allowed again.
		enterMlm();
		deposited(81);
		sackVarbitChanged();
		carrying(27);
		inventoryChanged();

		carrying(0);
		inventoryChanged();

		addMenuEntry("Mine", ObjectID.MOTHERLODE_ORE_SINGLE);

		verify(menu, atLeastOnce()).removeMenuEntry(any());
	}

	@Test
	public void carriedPayDirtCountsTowardsTheBlockCount()
	{
		enterMlm();
		deposited(81);
		sackVarbitChanged();
		carrying(27);
		inventoryChanged();

		addMenuEntry("Mine", ObjectID.MOTHERLODE_ORE_SINGLE);

		verify(menu).removeMenuEntry(any());
	}

	@Test
	public void aLowerBlockCountStopsMiningEarly()
	{
		when(config.blockAtCount()).thenReturn(50);
		enterMlm();
		deposited(50);
		sackVarbitChanged();

		addMenuEntry("Mine", ObjectID.MOTHERLODE_ORE_SINGLE);

		verify(menu).removeMenuEntry(any());
	}

	// ---- the two hopper conditions, each standing on its own ------------------------------------

	@Test
	public void aStoppedBeltAloneFlagsTheHopper()
	{
		enterMlm();
		breakBothStruts();

		assertTrue("nothing is on the belt, but the wheel still needs repairing", plugin.shouldFlagHopper());
		assertFalse(plugin.hasPayDirtInTransit());
	}

	@Test
	public void payDirtOnARunningBeltAloneFlagsTheHopper()
	{
		// Nothing is wrong, but your pay-dirt is out there and worth reporting.
		enterMlm();
		strandPayDirt(27);

		assertTrue(plugin.hasPayDirtInTransit());
		assertTrue(plugin.shouldFlagHopper());
		assertFalse(plugin.isMachineryStopped());
	}

	@Test
	public void payDirtInTransitIsReportedWhetherOrNotTheBeltIsMoving()
	{
		enterMlm();
		strandPayDirt(27);
		assertTrue(plugin.hasPayDirtInTransit());

		breakBothStruts();

		assertTrue("still in transit, now going nowhere", plugin.hasPayDirtInTransit());
		assertTrue(plugin.hasStuckPayDirt());
	}

	@Test
	public void payDirtInTransitStopsBeingReportedOnceItArrives()
	{
		enterMlm();
		strandPayDirt(27);

		deposited(27);
		sackVarbitChanged();

		assertFalse(plugin.hasPayDirtInTransit());
		assertFalse(plugin.shouldFlagHopper());
	}

	@Test
	public void aQuietRunningBeltFlagsNothing()
	{
		enterMlm();
		spawnGameObject(ObjectID.MOTHERLODE_WHEEL_STRUT_BROKEN);

		assertFalse("one broken strut is not a stopped belt", plugin.shouldFlagHopper());
	}

	@Test
	public void stuckIsTheStricterOfTheTwo()
	{
		// hasStuckPayDirt drives the Deposit block and needs both; hasPayDirtInTransit drives the
		// overlay line and needs only the pay-dirt.
		enterMlm();
		strandPayDirt(27);

		assertTrue(plugin.hasPayDirtInTransit());
		assertFalse(plugin.hasStuckPayDirt());
	}

	// ---- hiding the Deposit option -------------------------------------------------------------

	@Test
	public void depositIsRemovedWhenTheBeltIsStoppedAndTheSackIsFull()
	{
		enterMlm();
		breakBothStruts();
		fillSack();

		MenuEntry entry = addMenuEntry("Deposit", ObjectID.MOTHERLODE_HOPPER);

		verify(menu).removeMenuEntry(entry);
	}

	@Test
	public void depositIsRemovedWhenAStrutBreaksBeforeYourLoadArrives()
	{
		// The reported sequence: deposit, then the belt stops while your load is still on it. The sack
		// is nowhere near full, but that stranded load is in the way of the next one.
		enterMlm();
		deposited(20);
		sackVarbitChanged();
		strandPayDirt(27);

		breakBothStruts();

		MenuEntry entry = addMenuEntry("Deposit", ObjectID.MOTHERLODE_HOPPER);

		verify(menu).removeMenuEntry(entry);
	}

	@Test
	public void depositIsRemovedWhenYouDepositIntoAnAlreadyStoppedBelt()
	{
		// Same end state, reached the other way round.
		enterMlm();
		deposited(20);
		sackVarbitChanged();
		breakBothStruts();

		strandPayDirt(27);

		MenuEntry entry = addMenuEntry("Deposit", ObjectID.MOTHERLODE_HOPPER);

		verify(menu).removeMenuEntry(entry);
	}

	@Test
	public void depositIsKeptOnAStoppedBeltWithNothingOnItAndRoomToSpare()
	{
		// Nothing is in the way and the sack can take it, so the load may as well go in and wait.
		enterMlm();
		breakBothStruts();
		deposited(50);
		sackVarbitChanged();

		addMenuEntry("Deposit", ObjectID.MOTHERLODE_HOPPER);

		verify(menu, never()).removeMenuEntry(any());
	}

	@Test
	public void depositIsRemovedOnAStoppedBeltWithAFullSackAndNothingStranded()
	{
		enterMlm();
		breakBothStruts();
		fillSack();

		addMenuEntry("Deposit", ObjectID.MOTHERLODE_HOPPER);

		verify(menu).removeMenuEntry(any());
	}

	@Test
	public void carriedPayDirtDoesNotCountTowardsHidingDeposit()
	{
		// 81 committed plus 27 carried reaches the block count, but the carried load is precisely what
		// wants depositing, and the sack has room for it.
		enterMlm();
		breakBothStruts();
		deposited(81);
		sackVarbitChanged();
		carrying(27);
		inventoryChanged();

		addMenuEntry("Deposit", ObjectID.MOTHERLODE_HOPPER);

		verify(menu, never()).removeMenuEntry(any());
	}

	@Test
	public void strandedPayDirtCountsTowardsHidingDeposit()
	{
		// Once that same load is on the stopped belt it is committed to the sack, and there is no
		// longer room for another one.
		enterMlm();
		breakBothStruts();
		deposited(81);
		sackVarbitChanged();
		strandPayDirt(27);

		addMenuEntry("Deposit", ObjectID.MOTHERLODE_HOPPER);

		verify(menu).removeMenuEntry(any());
	}

	@Test
	public void depositIsKeptWhileTheBeltRuns()
	{
		enterMlm();
		fillSack();

		addMenuEntry("Deposit", ObjectID.MOTHERLODE_HOPPER);

		verify(menu, never()).removeMenuEntry(any());
	}

	@Test
	public void depositFollowsTheOnlyWhenBlockedCondition()
	{
		enterMlm();
		breakBothStruts();
		deposited(50);
		sackVarbitChanged();

		// Stopped, room to spare, and nothing of yours stranded.
		addMenuEntry("Deposit", ObjectID.MOTHERLODE_HOPPER);
		verify(menu, never()).removeMenuEntry(any());

		strandPayDirt(27);

		addMenuEntry("Deposit", ObjectID.MOTHERLODE_HOPPER);
		verify(menu).removeMenuEntry(any());
	}

	@Test
	public void payDirtOnARunningBeltDoesNotHideDeposit()
	{
		// The overlay reports it, but there is nothing stopping another load going in behind it.
		enterMlm();
		strandPayDirt(27);
		assertTrue(plugin.hasPayDirtInTransit());

		addMenuEntry("Deposit", ObjectID.MOTHERLODE_HOPPER);

		verify(menu, never()).removeMenuEntry(any());
	}

	@Test
	public void blockingDepositCanBeTurnedOffWithoutLosingTheHighlight()
	{
		when(config.blockDepositWhenBlocked()).thenReturn(false);
		enterMlm();
		breakBothStruts();
		fillSack();

		addMenuEntry("Deposit", ObjectID.MOTHERLODE_HOPPER);

		verify(menu, never()).removeMenuEntry(any());
		assertTrue("the hopper is still flagged, just not blocked", plugin.shouldFlagHopper());
	}

	@Test
	public void otherHopperOptionsAreLeftAlone()
	{
		enterMlm();
		breakBothStruts();
		fillSack();

		addMenuEntry("Examine", ObjectID.MOTHERLODE_HOPPER);
		addMenuEntry("Walk here", ObjectID.MOTHERLODE_HOPPER);

		verify(menu, never()).removeMenuEntry(any());
	}

	@Test
	public void depositElsewhereIsLeftAlone()
	{
		enterMlm();
		breakBothStruts();
		fillSack();

		// Some other object that happens to offer a Deposit option.
		addMenuEntry("Deposit", ObjectID.MOTHERLODE_SACK);

		verify(menu, never()).removeMenuEntry(any());
	}

	@Test
	public void aStoppedBeltDoesNotBlockMiningByItself()
	{
		// The two rules are independent: the belt being down says nothing about room in the sack.
		enterMlm();
		breakBothStruts();

		addMenuEntry("Mine", ObjectID.MOTHERLODE_ORE_SINGLE);

		verify(menu, never()).removeMenuEntry(any());
	}

	@Test
	public void aFullSackDoesNotHideDepositByItself()
	{
		// Depositing is exactly what you should do with a full sack and a running belt.
		enterMlm();
		fillSack();

		addMenuEntry("Deposit", ObjectID.MOTHERLODE_HOPPER);

		verify(menu, never()).removeMenuEntry(any());
	}

	// ---- capacity upgrade ----------------------------------------------------------------------

	@Test
	public void blockCountIsRaisedToTheLargeSackWhenTheUpgradeIsUnlocked()
	{
		when(configManager.getConfiguration(MlmQolPlugin.CONFIG_GROUP, MlmQolConfig.BLOCK_AT_COUNT))
			.thenReturn(Integer.toString(SACK_SIZE));
		upgraded(true);

		plugin.startUp();

		verify(configManager).setConfiguration(MlmQolPlugin.CONFIG_GROUP, MlmQolConfig.BLOCK_AT_COUNT, SACK_LARGE_SIZE);
	}

	@Test
	public void blockCountIsRaisedWhenItHasNeverBeenSet()
	{
		when(configManager.getConfiguration(MlmQolPlugin.CONFIG_GROUP, MlmQolConfig.BLOCK_AT_COUNT)).thenReturn(null);
		upgraded(true);

		plugin.startUp();

		verify(configManager).setConfiguration(MlmQolPlugin.CONFIG_GROUP, MlmQolConfig.BLOCK_AT_COUNT, SACK_LARGE_SIZE);
	}

	@Test
	public void aCustomBlockCountIsNeverOverwritten()
	{
		when(configManager.getConfiguration(MlmQolPlugin.CONFIG_GROUP, MlmQolConfig.BLOCK_AT_COUNT)).thenReturn("150");
		upgraded(true);

		plugin.startUp();

		verify(configManager, never()).setConfiguration(anyString(), anyString(), any());
	}

	@Test
	public void blockCountIsLeftAtTheSmallSackWithoutTheUpgrade()
	{
		when(configManager.getConfiguration(MlmQolPlugin.CONFIG_GROUP, MlmQolConfig.BLOCK_AT_COUNT))
			.thenReturn(Integer.toString(SACK_SIZE));
		upgraded(false);

		plugin.startUp();

		verify(configManager, never()).setConfiguration(anyString(), anyString(), any());
	}

	@Test
	public void theUpgradeIsPickedUpOutsideTheMine()
	{
		// The varbit is only transmitted during the login varp sync, nowhere near the mine.
		when(configManager.getConfiguration(MlmQolPlugin.CONFIG_GROUP, MlmQolConfig.BLOCK_AT_COUNT))
			.thenReturn(Integer.toString(SACK_SIZE));
		leaveMlm();
		upgraded(true);

		upgradeVarbitChanged();

		verify(configManager).setConfiguration(MlmQolPlugin.CONFIG_GROUP, MlmQolConfig.BLOCK_AT_COUNT, SACK_LARGE_SIZE);
	}

	@Test
	public void theUpgradeIsPickedUpOnLoginWithoutAVarbitChange()
	{
		// Re-logging in the same client session doesn't change the varp, so no VarbitChanged fires.
		when(configManager.getConfiguration(MlmQolPlugin.CONFIG_GROUP, MlmQolConfig.BLOCK_AT_COUNT))
			.thenReturn(Integer.toString(SACK_SIZE));
		upgraded(true);

		gameState(GameState.LOGGED_IN);

		verify(configManager).setConfiguration(MlmQolPlugin.CONFIG_GROUP, MlmQolConfig.BLOCK_AT_COUNT, SACK_LARGE_SIZE);
	}

	@Test
	public void theUpgradedSackDoesNotBlockAtTheSmallCapacity()
	{
		when(config.blockAtCount()).thenReturn(SACK_LARGE_SIZE);
		upgraded(true);
		enterMlm();
		deposited(SACK_SIZE);
		sackVarbitChanged();

		addMenuEntry("Mine", ObjectID.MOTHERLODE_ORE_SINGLE);

		verify(menu, never()).removeMenuEntry(any());
	}

	// ---- struts and the hopper -----------------------------------------------------------------

	@Test
	public void oneBrokenStrutDoesNotStopTheBelt()
	{
		enterMlm();
		spawnGameObject(ObjectID.MOTHERLODE_WHEEL_STRUT_BROKEN);
		spawnGameObject(ObjectID.MOTHERLODE_WHEEL_STRUT_FIXED);

		assertFalse(plugin.isMachineryStopped());
	}

	@Test
	public void bothBrokenStrutsStopTheBelt()
	{
		enterMlm();
		breakBothStruts();

		assertTrue(plugin.isMachineryStopped());
	}

	@Test
	public void repairingEitherStrutRestartsTheBelt()
	{
		enterMlm();
		GameObject broken = spawnGameObject(ObjectID.MOTHERLODE_WHEEL_STRUT_BROKEN);
		spawnGameObject(ObjectID.MOTHERLODE_WHEEL_STRUT_BROKEN);
		assertTrue(plugin.isMachineryStopped());

		// Repairing swaps the broken object out for a fixed one.
		despawnGameObject(broken);
		spawnGameObject(ObjectID.MOTHERLODE_WHEEL_STRUT_FIXED);

		assertFalse(plugin.isMachineryStopped());
	}

	@Test
	public void brokenStrutsAreExposedForHighlighting()
	{
		enterMlm();
		GameObject broken = spawnGameObject(ObjectID.MOTHERLODE_WHEEL_STRUT_BROKEN);
		GameObject working = spawnGameObject(ObjectID.MOTHERLODE_WHEEL_STRUT_FIXED);

		assertTrue(plugin.getBrokenStruts().contains(broken));
		assertFalse("a working strut is not something to point at", plugin.getBrokenStruts().contains(working));
	}

	@Test
	public void aRepairedStrutStopsBeingHighlighted()
	{
		enterMlm();
		GameObject broken = spawnGameObject(ObjectID.MOTHERLODE_WHEEL_STRUT_BROKEN);

		despawnGameObject(broken);

		assertTrue(plugin.getBrokenStruts().isEmpty());
	}

	@Test
	public void theHopperIsTrackedWhateverKindOfObjectItIs()
	{
		enterMlm();
		WallObject hopper = mock(WallObject.class);
		when(hopper.getId()).thenReturn(ObjectID.MOTHERLODE_HOPPER);
		WallObjectSpawned event = new WallObjectSpawned();
		event.setWallObject(hopper);

		plugin.onWallObjectSpawned(event);

		assertTrue(plugin.getHoppers().contains(hopper));
	}

	// ---- the baby mole -------------------------------------------------------------------------

	@Test
	public void aBabyMoleInTheMineIsTracked()
	{
		enterMlm();

		NPC mole = spawnNpc(NpcID.MOLE_PET);

		assertTrue(plugin.getBabyMoles().contains(mole));
	}

	@Test
	public void aBabyMoleOutsideTheMineIsNeverTracked()
	{
		// The whole point of the location gate: a pet must not be renamed anywhere else in the game.
		leaveMlm();

		spawnNpc(NpcID.MOLE_PET);

		assertTrue(plugin.getBabyMoles().isEmpty());
	}

	@Test
	public void otherNpcsAreIgnored()
	{
		enterMlm();

		spawnNpc(NpcID.MOLE_GIANT);

		assertTrue(plugin.getBabyMoles().isEmpty());
	}

	@Test
	public void aDespawningBabyMoleIsForgotten()
	{
		enterMlm();
		NPC mole = spawnNpc(NpcID.MOLE_PET);

		plugin.onNpcDespawned(new NpcDespawned(mole));

		assertTrue(plugin.getBabyMoles().isEmpty());
	}

	@Test
	public void leavingTheMineForgetsTheBabyMole()
	{
		enterMlm();
		spawnNpc(NpcID.MOLE_PET);

		leaveMlm();

		assertTrue(plugin.getBabyMoles().isEmpty());
	}

	@Test
	public void loggingOutForgetsTheBabyMole()
	{
		enterMlm();
		spawnNpc(NpcID.MOLE_PET);

		gameState(GameState.LOGIN_SCREEN);

		assertTrue(plugin.getBabyMoles().isEmpty());
	}

	@Test
	public void enablingThePluginInTheMinePicksUpABabyMoleAlreadyThere()
	{
		standingInTheMine();
		NPC mole = mock(NPC.class);
		when(mole.getId()).thenReturn(NpcID.MOLE_PET);
		doReturn(npcSet(mole)).when(worldView).npcs();

		plugin.startUp();

		assertTrue(plugin.getBabyMoles().contains(mole));
	}

	@Test
	public void sceneObjectsAreIgnoredOutsideTheMine()
	{
		leaveMlm();
		breakBothStruts();
		spawnGameObject(ObjectID.MOTHERLODE_HOPPER);

		assertFalse(plugin.isMachineryStopped());
		assertTrue(plugin.getHoppers().isEmpty());
	}

	@Test
	public void leavingTheMineForgetsTheScene()
	{
		enterMlm();
		breakBothStruts();
		spawnGameObject(ObjectID.MOTHERLODE_HOPPER);
		assertTrue(plugin.isMachineryStopped());

		leaveMlm();

		assertFalse(plugin.isMachineryStopped());
		assertTrue(plugin.getHoppers().isEmpty());
	}

	@Test
	public void loggingOutForgetsTheScene()
	{
		enterMlm();
		breakBothStruts();
		assertTrue(plugin.isMachineryStopped());

		gameState(GameState.LOGIN_SCREEN);

		assertFalse(plugin.isMachineryStopped());
	}

	// ---- stuck pay-dirt ------------------------------------------------------------------------

	@Test
	public void payDirtIsOnlyStuckWhenTheBeltIsAlsoStopped()
	{
		enterMlm();
		carrying(27);
		inventoryChanged();
		carrying(0);
		inventoryChanged();

		assertFalse("the belt is running, so it is on its way, not stuck", plugin.hasStuckPayDirt());

		breakBothStruts();
		assertTrue(plugin.hasStuckPayDirt());
	}

	@Test
	public void aStoppedBeltWithNothingOnItIsNotStuck()
	{
		enterMlm();
		breakBothStruts();

		assertFalse(plugin.hasStuckPayDirt());
	}

	@Test
	public void strandedPayDirtIsNotWrittenOffWhileTheBeltIsStopped()
	{
		// The write-off exists so dropped pay-dirt can't block mining forever, but a stopped belt is
		// a legitimate long gap — writing it off here would clear the warning while it is still true.
		enterMlm();
		breakBothStruts();
		carrying(27);
		inventoryChanged();
		carrying(0);
		inventoryChanged();

		tick(60);

		assertTrue(plugin.hasStuckPayDirt());
	}

	@Test
	public void strandedPayDirtIsWrittenOffOnceTheBeltRunsAgain()
	{
		enterMlm();
		GameObject broken = spawnGameObject(ObjectID.MOTHERLODE_WHEEL_STRUT_BROKEN);
		spawnGameObject(ObjectID.MOTHERLODE_WHEEL_STRUT_BROKEN);
		carrying(27);
		inventoryChanged();
		carrying(0);
		inventoryChanged();
		tick(60);

		despawnGameObject(broken);
		tick(60);

		assertFalse(plugin.hasStuckPayDirt());
	}

	// ---- chat warnings -------------------------------------------------------------------------

	@Test
	public void reachingTheLimitIsAnnouncedOnce()
	{
		enterMlm();
		fillSack();

		// Further events at the same state must not repeat it.
		sackVarbitChanged();
		tick(5);

		verify(chatMessageManager).queue(any(QueuedMessage.class));
	}

	@Test
	public void havingRoomAgainIsAnnounced()
	{
		enterMlm();
		fillSack();
		deposited(0);
		sackVarbitChanged();

		verify(chatMessageManager, times(2)).queue(any(QueuedMessage.class));
	}

	@Test
	public void chatWarningsCanBeTurnedOff()
	{
		when(config.warnInChat()).thenReturn(false);
		enterMlm();
		fillSack();

		verify(chatMessageManager, never()).queue(any(QueuedMessage.class));
	}

	@Test
	public void enablingThePluginNextToAFullSackDoesNotWarn()
	{
		// Adopting the current state silently, rather than announcing something that was already true.
		standingInTheMine();
		deposited(SACK_SIZE);

		plugin.startUp();

		verify(chatMessageManager, never()).queue(any(QueuedMessage.class));
	}

	@Test
	public void enablingThePluginNextToAFullSackStillBlocksMining()
	{
		// Silent, but not inert — the state was adopted, not ignored.
		standingInTheMine();
		deposited(SACK_SIZE);

		plugin.startUp();
		addMenuEntry("Mine", ObjectID.MOTHERLODE_ORE_SINGLE);

		verify(menu).removeMenuEntry(any());
	}

	@Test
	public void enablingThePluginInTheMinePicksUpTheSceneStraightAway()
	{
		// The spawn events for the scene we are already standing in fired long ago, so without the
		// one-off scan the hopper would go unnoticed until the next region load.
		standingInTheMine();
		GameObject hopper = mock(GameObject.class);
		when(hopper.getId()).thenReturn(ObjectID.MOTHERLODE_HOPPER);
		sceneContaining(hopper);

		plugin.startUp();

		assertTrue(plugin.getHoppers().contains(hopper));
	}

	@Test
	public void enablingThePluginInTheMinePicksUpBrokenStruts()
	{
		standingInTheMine();
		GameObject strutA = mock(GameObject.class);
		when(strutA.getId()).thenReturn(ObjectID.MOTHERLODE_WHEEL_STRUT_BROKEN);
		GameObject strutB = mock(GameObject.class);
		when(strutB.getId()).thenReturn(ObjectID.MOTHERLODE_WHEEL_STRUT_BROKEN);
		sceneContaining(strutA, strutB);

		plugin.startUp();

		assertTrue(plugin.isMachineryStopped());
	}

	// ---- lifecycle -----------------------------------------------------------------------------

	@Test
	public void theOverlayIsRegisteredAndCleanedUp() throws Exception
	{
		plugin.startUp();
		verify(overlayManager).add(hopperOverlay);

		plugin.shutDown();
		verify(overlayManager).remove(hopperOverlay);
	}

	@Test
	public void shutDownForgetsEverything() throws Exception
	{
		enterMlm();
		breakBothStruts();
		spawnGameObject(ObjectID.MOTHERLODE_HOPPER);
		fillSack();

		plugin.shutDown();

		assertFalse(plugin.isMachineryStopped());
		assertTrue(plugin.getHoppers().isEmpty());

		// No longer in the mine as far as the plugin is concerned, so nothing is blocked.
		addMenuEntry("Mine", ObjectID.MOTHERLODE_ORE_SINGLE);
		verify(menu, never()).removeMenuEntry(any());
	}

	@Test
	public void reEnteringTheMineStartsFromAKnownState()
	{
		enterMlm();
		carrying(27);
		inventoryChanged();
		carrying(0);
		inventoryChanged();
		breakBothStruts();
		assertTrue(plugin.hasStuckPayDirt());

		leaveMlm();
		enterMlm();

		assertFalse("stale in-transit pay-dirt from the last visit must not carry over",
			plugin.hasStuckPayDirt());
	}

	@Test
	public void aConfigChangeReEvaluatesBlockingImmediately()
	{
		enterMlm();
		deposited(60);
		sackVarbitChanged();
		addMenuEntry("Mine", ObjectID.MOTHERLODE_ORE_SINGLE);
		verify(menu, never()).removeMenuEntry(any());

		when(config.blockAtCount()).thenReturn(50);
		ConfigChanged configChanged = new ConfigChanged();
		configChanged.setGroup(MlmQolPlugin.CONFIG_GROUP);
		configChanged.setKey(MlmQolConfig.BLOCK_AT_COUNT);
		configChanged.setNewValue("50");
		plugin.onConfigChanged(configChanged);

		addMenuEntry("Mine", ObjectID.MOTHERLODE_ORE_SINGLE);
		verify(menu).removeMenuEntry(any());
	}

	@Test
	public void anUnrelatedInventoryContainerIsIgnored()
	{
		enterMlm();
		deposited(81);
		sackVarbitChanged();
		carrying(27);

		// 81 in the sack plus 27 carried would fill it, but a bank change is not the player's
		// inventory and must not be read as one.
		plugin.onItemContainerChanged(new ItemContainerChanged(InventoryID.BANK, inventory));
		addMenuEntry("Mine", ObjectID.MOTHERLODE_ORE_SINGLE);
		verify(menu, never()).removeMenuEntry(any());

		// The real inventory does count.
		inventoryChanged();
		addMenuEntry("Mine", ObjectID.MOTHERLODE_ORE_SINGLE);
		verify(menu).removeMenuEntry(any());
	}
}
