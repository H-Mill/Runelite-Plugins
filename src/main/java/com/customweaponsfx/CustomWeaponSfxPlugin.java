package com.customweaponsfx;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;
import javax.inject.Inject;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import com.google.inject.Provides;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Actor;
import net.runelite.api.Client;
import net.runelite.api.Player;
import net.runelite.api.GameState;
import net.runelite.api.EquipmentInventorySlot;
import net.runelite.api.Prayer;
import net.runelite.api.Skill;
import net.runelite.api.InventoryID;
import net.runelite.api.Item;
import net.runelite.api.NPC;
import net.runelite.api.Projectile;
import net.runelite.api.ItemContainer;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.events.ActorDeath;
import net.runelite.api.events.AnimationChanged;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.HitsplatApplied;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.events.ProjectileMoved;
import net.runelite.api.events.AreaSoundEffectPlayed;
import net.runelite.api.events.SoundEffectPlayed;
import net.runelite.api.events.VarbitChanged;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.client.audio.AudioPlayer;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.game.ItemManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.api.MenuAction;
import net.runelite.client.events.OverlayMenuClicked;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayMenuEntry;
import net.runelite.client.util.ImageUtil;

@Slf4j
@PluginDescriptor(
		name = "Custom Weapon SFX",
		description = "Plays custom sound effects on weapon hits, misses, and max hits — configurable per weapon with triggers, volume, and chance",
		tags = {"custom", "weapon", "sound", "sfx", "squeaky", "toy", "max hit", "on hit", "damage", "miss", "zero"},
		configName = "SqueakyToyPlugin"
)
public class CustomWeaponSfxPlugin extends Plugin
{
	static final String CONFIG_GROUP = "customweaponsfx";

	/** Drop tracked projectiles that never produced a hitsplat after this many ticks. */
	static final int PROJECTILE_TTL_TICKS = 10;
	private static final int RECEIVED_KEY = -2;

	@Inject private Client client;
	@Inject private AudioPlayer audioPlayer;
	@Inject private ClientToolbar clientToolbar;
	@Inject private ClientThread clientThread;
	@Inject private ConfigManager configManager;
	@Inject private EventBus eventBus;
	@Inject private ItemManager itemManager;
	@Inject private WeaponChatboxSearch weaponSearch;
	@Inject private CustomWeaponSfxConfig config;

	private CustomWeaponSfxPanel panel;
	private NavigationButton navButton;
	private CustomWeaponSfxConfigStore store;
	private SoundLibrary library;
	private SoundPlayer soundPlayer;
	private WeaponManager weapons;

	private final List<TriggerGroup> receivedGroups = new CopyOnWriteArrayList<>();
	private final List<TriggerGroup> globalWeaponGroups = new CopyOnWriteArrayList<>();

	private final SpecAttackTracker spec = new SpecAttackTracker();

	/** One-tick-delayed equipped-weapon snapshot, used to attribute a projectile to the weapon that
	 *  launched it even when the player swaps weapons on the same tick the shot was fired. */
	private final TickWeaponSnapshot launchWeapon = new TickWeaponSnapshot();

	private NpcExclusionFilter npcFilter;
	private WeaponSoundMuteFilter soundMute;
	private SfxOptions options;

	private int thrallTicksRemaining = 0;
	private boolean suppressReceivedOnDeath = false;

	// Hitsplats are batched per tick so multi-hit attacks are evaluated together.
	private final HitAggregator hits = new HitAggregator();

	private final AttackWeaponTracker attackWeapon = new AttackWeaponTracker();

	// Attributes delayed magic/ranged hits to the weapon that launched the projectile, so a weapon
	// swap (or auto-retaliate melee swing) while the projectile is in flight can't steal the sound.
	private final ProjectileWeaponTracker projectiles = new ProjectileWeaponTracker();

	@Provides
	CustomWeaponSfxConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(CustomWeaponSfxConfig.class);
	}

	/** Re-adds the toolbar nav button so a changed {@link CustomWeaponSfxConfig#sidePanelPriority()} takes effect live. */
	private void rebuildNavButton()
	{
		if (panel == null) return;
		if (navButton != null) clientToolbar.removeNavigation(navButton);
		navButton = NavigationButton.builder()
			.tooltip("Custom Weapon SFX")
			.icon(loadIcon())
			.panel(panel)
			.priority(config.sidePanelPriority())
			.build();
		clientToolbar.addNavigation(navButton);
	}

	/**
	 * Opens this plugin's configuration panel. We don't have access to the ConfigPlugin directly, so we
	 * emulate the overlay "Configure" click it listens for, carrying a throwaway overlay bound to this
	 * plugin so ConfigPlugin can resolve which config to show.
	 */
	private void openConfiguration()
	{
		Overlay overlay = new Overlay(this)
		{
			@Override
			public java.awt.Dimension render(Graphics2D graphics)
			{
				return null;
			}
		};
		eventBus.post(new OverlayMenuClicked(
			new OverlayMenuEntry(MenuAction.RUNELITE_OVERLAY_CONFIG, null, null), overlay));
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		if (!CONFIG_GROUP.equals(event.getGroup())) return;
		if (CustomWeaponSfxConfig.SIDE_PANEL_PRIORITY.equals(event.getKey()))
		{
			rebuildNavButton();
		}
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		updateLoginButtons();
	}

	@Subscribe
	public void onItemContainerChanged(ItemContainerChanged event)
	{
		// Re-evaluate the "to equipped weapon" buttons when the equipment changes (weapon equipped/removed).
		if (event.getContainerId() == InventoryID.EQUIPMENT.getId())
		{
			updateLoginButtons();
		}
	}

	/**
	 * Enables the search/equipped/copy buttons only while logged in, since they read live game state. The
	 * "to equipped weapon" actions additionally require a weapon to be equipped.
	 */
	private void updateLoginButtons()
	{
		CustomWeaponSfxPanel p = panel;
		if (p == null) return;
		boolean loggedIn = client.getGameState() == GameState.LOGGED_IN;
		boolean weaponEquipped = loggedIn && getEquippedWeaponId() >= 0;
		SwingUtilities.invokeLater(() -> p.setLoginButtonsEnabled(loggedIn, weaponEquipped));
	}

	@Override
	protected void startUp()
	{
		library = new SoundLibrary();
		soundPlayer = new SoundPlayer(audioPlayer, SoundLibrary.SOUNDS_DIR);
		soundPlayer.start();

		store = new CustomWeaponSfxConfigStore(configManager);
		npcFilter = new NpcExclusionFilter(store);
		soundMute = new WeaponSoundMuteFilter(store);
		options = new SfxOptions(store);
		weapons = new WeaponManager(store, this::rebuildPanel, this::showWeaponConflict);

		weapons.load();
		receivedGroups.clear();
		receivedGroups.addAll(store.loadDefaultGroups(CustomWeaponSfxPanel.RECEIVED_GROUPS_PREFIX));
		globalWeaponGroups.clear();
		globalWeaponGroups.addAll(store.loadDefaultGroups(CustomWeaponSfxPanel.GLOBAL_WEAPON_GROUPS_PREFIX));

		options.load();

		npcFilter.load();
		soundMute.load();

		panel = new CustomWeaponSfxPanel(store, itemManager, this::openWeaponSearch, this::addEquippedWeapon, weapons::remove, this::editWeaponViaSearch, this::editWeaponToEquipped, this::copyWeapon, this::copyWeaponToEquipped, weapons::move, soundPlayer::playSoundFile, soundPlayer::fireGroup, this::resetAllData, this::refreshSounds, this::openConfiguration, this::onOptionChanged, npcFilter::setIds, npcFilter::setNames, soundMute::setIds);

		rebuildNavButton();

		panel.rebuild(weapons.snapshot(), library.getAvailable(), library.getBundled(), receivedGroups, globalWeaponGroups);

		clientThread.invoke(() ->
		{
			spec.init(client.getVarpValue(VarPlayerID.SA_ENERGY));
			launchWeapon.init(getEquippedWeaponId());
			updateLoginButtons();
		});

		log.debug("Custom Weapon SFX started!");
	}

	@Override
	protected void shutDown()
	{
		if (soundPlayer != null) soundPlayer.shutDown();
		soundPlayer = null;
		library = null;

		clientToolbar.removeNavigation(navButton);
		navButton = null;
		panel = null;
		store = null;
		if (weapons != null) weapons.clear();
		weapons = null;

		receivedGroups.clear();
		globalWeaponGroups.clear();

		spec.reset();
		launchWeapon.reset();
		thrallTicksRemaining = 0;
		suppressReceivedOnDeath = false;
		if (options != null) options.clear();
		options = null;
		if (npcFilter != null) npcFilter.clear();
		npcFilter = null;
		if (soundMute != null) soundMute.clear();
		soundMute = null;

		hits.clear();

		attackWeapon.reset();
		projectiles.reset();

		log.debug("Custom Weapon SFX stopped!");
	}

	@Subscribe
	public void onGameTick(GameTick event)
	{
		// Advance the one-tick-delayed weapon snapshot so projectiles fired this tick (rendered after this
		// GameTick) are attributed to the weapon held at the start of the tick, even after a same-tick swap.
		launchWeapon.onGameTick(getEquippedWeaponId());

		if (spec.onTick(client.getVarpValue(VarPlayerID.SA_ENERGY), client.getTickCount()))
		{
			onSpecFired();
		}

		if (thrallTicksRemaining > 0) thrallTicksRemaining--;

		projectiles.prune(client.getTickCount());

		if (!hits.isEmpty())
		{
			for (List<PendingAttack> tickAttacks : hits.drainByTick())
				evaluatePendingAttacks(tickAttacks);
		}
		suppressReceivedOnDeath = false;
	}

	@Subscribe
	public void onAnimationChanged(AnimationChanged event)
	{
		if (event.getActor() != client.getLocalPlayer()) return;
		if (client.getLocalPlayer().getAnimation() == -1) return;
		attackWeapon.onAttack(getEquippedWeaponId());
	}

	@Subscribe
	public void onProjectileMoved(ProjectileMoved event)
	{
		Projectile p = event.getProjectile();
		Player me = client.getLocalPlayer();
		if (me == null) return;

		// ProjectileMoved fires every frame the projectile travels; only record it once, on spawn.
		if (p.getStartCycle() > client.getGameCycle()) return;
		if (projectiles.hasSeen(p.getStartCycle())) return;

		// Only track projectiles the local player fired.
		// fall back to matching the start tile to the player when the source actor doesn't resolve.
		boolean mine = p.getSourceActor() == me;
		if (p.getSourceActor() == null)
		{
			LocalPoint loc = me.getLocalLocation();
			mine = loc != null && p.getX() == loc.getX() && p.getY() == loc.getY();
		}
		if (!mine) return;

		// Attribute to the weapon held at the start of this tick, not live equipment: this event fires
		// after the tick's swap is applied, but the shot used the pre-swap (start-of-tick) weapon.
		int weaponId = launchWeapon.launchWeapon();
		if (weaponId < 0) return;

		// getEndCycle() is the game cycle the projectile reaches its target; matched against the game
		// clock at hit time so attribution is distance-independent (no per-tick travel estimate).
		projectiles.onProjectileSpawned(weaponId, p.getTargetActor(), p.getTargetPoint(),
			p.getEndCycle(), client.getTickCount(), spec.wasSpec());
	}

	/**
	 * Mutes the default in-game sound effects the user listed (e.g. a weapon's stock swing/hit sound),
	 * so their custom SFX replaces it instead of layering on top. Covers both the local "self" sound
	 * effect and the area sound effect other nearby players hear.
	 */
	@Subscribe
	public void onSoundEffectPlayed(SoundEffectPlayed event)
	{
		if (soundMute != null && soundMute.isMuted(event.getSoundId()))
		{
			event.consume();
		}
	}

	@Subscribe
	public void onAreaSoundEffectPlayed(AreaSoundEffectPlayed event)
	{
		if (soundMute != null && soundMute.isMuted(event.getSoundId()))
		{
			event.consume();
		}
	}

	@Subscribe
	public void onHitsplatApplied(HitsplatApplied event)
	{
		Actor actor = event.getActor();
		int amount  = event.getHitsplat().getAmount();
		int tick    = client.getTickCount();

		if (actor == client.getLocalPlayer())
		{
			if (!opt(SfxOption.RECEIVED_ENABLED)) return;
			if (amount == 0 && opt(SfxOption.IGNORE_RECEIVED_ZERO_PRAYER) && isProtectionPrayerActive())
				return;
			hits.add(new DeferredHit(RECEIVED_KEY, receivedGroups, false, false, amount, false, tick, null));
			return;
		}

		if (!event.getHitsplat().isMine()) return;

		if (actor instanceof NPC && npcFilter.isExcluded((NPC) actor)) return;

		boolean isMax   = TriggerEvaluator.isMaxHit(event.getHitsplat().getHitsplatType());

		// A delayed magic/ranged hit is attributed to the weapon that launched its projectile, so a
		// weapon swap (or auto-retaliate melee) in flight can't steal the sound. Melee and other
		// non-projectile "mine" hitsplats find no match and fall back to the instant tracker.
		ProjectileWeaponTracker.TrackedProjectile tp = projectiles.matchAndRemove(actor, client.getGameCycle());
		boolean wasSpec;
		int weaponId;
		if (tp != null)
		{
			weaponId = tp.weaponId;
			wasSpec  = tp.wasSpec;
		}
		else
		{
			weaponId = attackWeapon.resolveWeaponId(getEquippedWeaponId());
			wasSpec  = spec.wasSpec();
		}
		if (weaponId < 0) return;

		WeaponEntry entry = weapons.find(weaponId);
		if (entry != null && entry.isEnabled())
			hits.add(new DeferredHit(weaponId, entry.getGroups(), entry.isDontOverrideGlobal(), wasSpec, amount, isMax, tick, actor));
		else
			hits.add(new DeferredHit(weaponId, java.util.Collections.emptyList(), false, wasSpec, amount, isMax, tick, actor));
	}

	@Subscribe
	public void onActorDeath(ActorDeath event)
	{
		if (event.getActor() != client.getLocalPlayer()) return;
		if (!opt(SfxOption.RECEIVED_ENABLED)) return;
		for (TriggerGroup group : receivedGroups)
		{
			if (!group.getTriggers().contains(Triggers.PLAYER_DEATH)) continue;
			suppressReceivedOnDeath = true;
			soundPlayer.fireGroup(group);
		}
	}

	@Subscribe
	public void onVarbitChanged(VarbitChanged event)
	{
		if (event.getVarbitId() != VarbitID.ARCEUUS_RESURRECTION_COOLDOWN) return;
		if (event.getValue() != 1) return;

		boolean masterTier = client.getVarbitValue(VarbitID.CA_TIER_STATUS_MASTER) == 2;
		thrallTicksRemaining = thrallDurationTicks(client.getBoostedSkillLevel(Skill.MAGIC), masterTier);
	}

	/**
	 * Resurrection thrall lifetime in game ticks: the caster's (boosted) Magic level, doubled when the
	 * Master combat-achievement tier is complete, plus the 4-tick summon delay.
	 */
	static int thrallDurationTicks(int magicLevel, boolean masterCombatAchievements)
	{
		int ticks = magicLevel;
		if (masterCombatAchievements) ticks += ticks;
		return ticks + 4;
	}

	private void evaluatePendingAttacks(List<PendingAttack> pendingAttacks)
	{
		boolean specHitsplatSeen = false;
		for (PendingAttack attack : pendingAttacks)
		{
			boolean isKill = attack.actors.stream()
					.filter(a -> a instanceof Player && a != client.getLocalPlayer())
					.anyMatch(a -> ((Player) a).getHealthRatio() == 0);
			AttackOutcome outcome = attack.collapse(isKill);
			boolean suppressMax  = opt(SfxOption.IGNORE_SMALL_MAX) && outcome.allMax && outcome.maxAmount <= 3;
			boolean suppressZero = opt(SfxOption.IGNORE_ZERO_THRALL) && thrallTicksRemaining > 0;
			if (!(attack.groups == receivedGroups && suppressReceivedOnDeath))
			{
				soundPlayer.fireMatchingGroups(attack.groups, outcome.wasSpec, outcome.anyHit, outcome.allZero, outcome.allMax,
						suppressMax, suppressZero, outcome.isKill, EnumSet.noneOf(Triggers.class));
			}

			if (opt(SfxOption.GLOBAL_ENABLED) && attack.groups != receivedGroups)
			{
				Set<Triggers> weaponCoveredTriggers = coveredGlobalTriggers(attack.groups, attack.dontOverrideGlobal);
				soundPlayer.fireMatchingGroups(globalWeaponGroups, outcome.wasSpec, outcome.anyHit, outcome.allZero, outcome.allMax,
						suppressMax, suppressZero, outcome.isKill, weaponCoveredTriggers);
			}

			if (outcome.wasSpec) specHitsplatSeen = true;
		}
		// Clear the spec window as soon as the spec's hitsplat has been processed
		// so the next normal attack isn't silently swallowed by the wasSpec flag.
		if (specHitsplatSeen)
		{
			spec.clearWindow();
		}
	}

	/**
	 * Triggers the weapon's own groups already cover, so the Global (All Weapons) groups can skip them and
	 * avoid double-firing the same event. Empty when {@code dontOverrideGlobal} is set — the user then wants
	 * both the weapon and global sounds to play.
	 */
	static Set<Triggers> coveredGlobalTriggers(List<TriggerGroup> groups, boolean dontOverrideGlobal)
	{
		if (dontOverrideGlobal)
		{
			return EnumSet.noneOf(Triggers.class);
		}
		return groups.stream()
			.filter(g -> !g.getTriggers().isEmpty())
			.flatMap(g -> g.getTriggers().stream())
			.collect(Collectors.toCollection(() -> EnumSet.noneOf(Triggers.class)));
	}

	private void resetAllData()
	{
		store.resetAll(weapons.snapshot(),
			CustomWeaponSfxPanel.RECEIVED_GROUPS_PREFIX, receivedGroups,
			CustomWeaponSfxPanel.GLOBAL_WEAPON_GROUPS_PREFIX, globalWeaponGroups);
		weapons.clear();
		receivedGroups.clear();
		globalWeaponGroups.clear();
		npcFilter.clear();
		soundMute.clear();

		options.reset();
		if (panel != null) panel.resetToggles();

		rebuildPanel();
	}

	private boolean opt(SfxOption option)
	{
		return options.get(option);
	}

	private void onOptionChanged(SfxOption option, boolean value)
	{
		options.set(option, value);
	}

	private boolean isProtectionPrayerActive()
	{
		return client.isPrayerActive(Prayer.PROTECT_FROM_MELEE)
			|| client.isPrayerActive(Prayer.PROTECT_FROM_MISSILES)
			|| client.isPrayerActive(Prayer.PROTECT_FROM_MAGIC);
	}

	private void onSpecFired()
	{
		int weaponId = getEquippedWeaponId();
		if (weaponId < 0) return;

		WeaponEntry entry = weapons.find(weaponId);
		if (entry == null || entry.getGroups().isEmpty()) return;

		spec.arm(weaponId, client.getTickCount());
	}

	private int getEquippedWeaponId()
	{
		return equippedWeaponId(client);
	}

	/** The weapon-slot item id from the equipment container, or -1 when nothing is in the weapon slot. */
	static int equippedWeaponId(Client client)
	{
		ItemContainer equipment = client.getItemContainer(InventoryID.EQUIPMENT);
		if (equipment == null) return -1;
		Item weapon = equipment.getItem(EquipmentInventorySlot.WEAPON.getSlotIdx());
		return weapon == null ? -1 : weapon.getId();
	}

	/**
	 * Shows a "must be logged in" warning (with {@code action} filled into the message) and returns
	 * {@code false} if not logged in; otherwise returns {@code true}. Must be called on the client thread.
	 */
	private boolean requireLoggedIn(String action)
	{
		if (client.getGameState() == GameState.LOGGED_IN) return true;
		SwingUtilities.invokeLater(() ->
			JOptionPane.showMessageDialog(
				SwingUtilities.getWindowAncestor(client.getCanvas()),
				"You must be logged in to " + action + ".",
				"Not Logged In",
				JOptionPane.WARNING_MESSAGE
			)
		);
		return false;
	}

	private void openWeaponSearch()
	{
		clientThread.invoke(() ->
		{
			if (!requireLoggedIn("search for weapons")) return;
			weaponSearch
				.onItemSelected(itemId ->
				{
					String name = client.getItemDefinition(itemId).getName();
					weapons.add(itemId, name);
				})
				.build();
		});
		client.getCanvas().requestFocusInWindow();
	}

	private void addEquippedWeapon()
	{
		clientThread.invoke(() ->
		{
			if (!requireLoggedIn("add an equipped weapon")) return;
			int weaponId = getEquippedWeaponId();
			if (weaponId < 0) return;
			String name = client.getItemDefinition(weaponId).getName();
			weapons.add(weaponId, name);
		});
	}

	/** Opens the weapon search and re-points the existing {@code oldItemId} weapon at the picked item. */
	private void editWeaponViaSearch(int oldItemId)
	{
		clientThread.invoke(() ->
		{
			if (!requireLoggedIn("search for weapons")) return;
			weaponSearch
				.onItemSelected(itemId ->
				{
					String name = client.getItemDefinition(itemId).getName();
					weapons.change(oldItemId, itemId, name);
				})
				.build();
		});
		client.getCanvas().requestFocusInWindow();
	}

	/** Re-points the existing {@code oldItemId} weapon at the currently equipped weapon. */
	private void editWeaponToEquipped(int oldItemId)
	{
		clientThread.invoke(() ->
		{
			if (!requireLoggedIn("update to an equipped weapon")) return;
			int weaponId = getEquippedWeaponId();
			if (weaponId < 0) return;
			String name = client.getItemDefinition(weaponId).getName();
			weapons.change(oldItemId, weaponId, name);
		});
	}

	/**
	 * Opens the weapon search and creates a new weapon entry for the picked item with a deep copy of
	 * {@code sourceItemId}'s sound groups. A copy needs its own item (the item id is the config key), so
	 * the user picks the target weapon rather than duplicating onto the same id.
	 */
	private void copyWeapon(int sourceItemId)
	{
		clientThread.invoke(() ->
		{
			if (!requireLoggedIn("copy a weapon")) return;
			weaponSearch
				.onItemSelected(itemId ->
				{
					String name = client.getItemDefinition(itemId).getName();
					weapons.copy(sourceItemId, itemId, name);
				})
				.build();
		});
		client.getCanvas().requestFocusInWindow();
	}

	/**
	 * Creates a new weapon entry for the currently equipped weapon with a deep copy of
	 * {@code sourceItemId}'s sound groups — the no-search counterpart to {@link #copyWeapon(int)}.
	 */
	private void copyWeaponToEquipped(int sourceItemId)
	{
		clientThread.invoke(() ->
		{
			if (!requireLoggedIn("copy a weapon")) return;
			int weaponId = getEquippedWeaponId();
			if (weaponId < 0) return;
			String name = client.getItemDefinition(weaponId).getName();
			weapons.copy(sourceItemId, weaponId, name);
		});
	}

	/** Shows the "already configured" warning surfaced by {@link WeaponManager} on an id collision. */
	private void showWeaponConflict(String message)
	{
		SwingUtilities.invokeLater(() ->
			JOptionPane.showMessageDialog(
				SwingUtilities.getWindowAncestor(client.getCanvas()),
				message,
				"Weapon Already Configured",
				JOptionPane.WARNING_MESSAGE
			)
		);
	}

	private void refreshSounds()
	{
		library.refresh();
		rebuildPanel();
	}

	private void rebuildPanel()
	{
		CustomWeaponSfxPanel p = panel;
		if (p == null) return;
		List<WeaponEntry> snapshot = weapons.snapshot();
		List<String> sounds = library.getAvailable();
		List<String> bundled = library.getBundled();
		SwingUtilities.invokeLater(() -> p.rebuild(snapshot, sounds, bundled, receivedGroups, globalWeaponGroups));
	}

	private static BufferedImage loadIcon()
	{
		try
		{
			return ImageUtil.loadImageResource(CustomWeaponSfxPlugin.class, "icon.png");
		}
		catch (Exception ignored) {}

		BufferedImage img = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = img.createGraphics();
		g.setColor(new Color(255, 153, 0));
		g.fillOval(0, 0, 15, 15);
		g.dispose();
		return img;
	}

}
