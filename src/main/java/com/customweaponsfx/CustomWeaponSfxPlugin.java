package com.customweaponsfx;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;
import javax.inject.Inject;
import javax.inject.Named;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import com.google.inject.Provides;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Actor;
import net.runelite.api.ChatMessageType;
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
	@Inject @Named("developerMode") private boolean developerMode;

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

	// Aggregates the hitsplats of a multi-hit attack (e.g. claws' spec over 2 ticks, or the dark bow's two
	// arrows) into one attack, per the weapon's AttackProfile, so triggers see the combined max/hit/total
	// instead of each tick's partial. Fired once the attack's hits complete (see onGameTick).
	private final AttackAggregator attackSpread = new AttackAggregator();

	// Debug: ticks queued by the ::cwsdamage command, one injected per game tick so the normal
	// aggregation/trigger/sound pipeline is exercised.
	private final Deque<DebugTick> debugDamageQueue = new ArrayDeque<>();

	/** One tick's simultaneous simulated hitsplats for the {@code ::cwsdamage} debug command. */
	private static final class DebugTick
	{
		/** Weapon item id to attribute the hits to, or -1 to use whatever's currently equipped. */
		final int weaponId;
		final List<DebugHit> hits;

		DebugTick(int weaponId, List<DebugHit> hits)
		{
			this.weaponId = weaponId;
			this.hits     = hits;
		}
	}

	/** One queued simulated hitsplat for the {@code ::cwsdamage} debug command. */
	private static final class DebugHit
	{
		final int amount;
		final boolean isMax;
		final boolean wasSpec;

		DebugHit(int amount, boolean isMax, boolean wasSpec)
		{
			this.amount  = amount;
			this.isMax   = isMax;
			this.wasSpec = wasSpec;
		}
	}

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
		// Drop any in-progress multi-hit attack on leaving the world so it can't fire a stale sound on relogin.
		if (event.getGameState() != GameState.LOGGED_IN)
		{
			attackSpread.reset();
			debugDamageQueue.clear();
		}
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

		panel = new CustomWeaponSfxPanel(store, itemManager, this::openWeaponSearch, this::addEquippedWeapon, weapons::remove, this::editWeaponViaSearch, this::editWeaponToEquipped, this::copyWeapon, this::copyWeaponToEquipped, weapons::move, this::pickWeaponForBlacklist, this::pickEquippedForBlacklist, soundPlayer::playSoundFile, soundPlayer::fireGroup, this::resetAllData, this::refreshSounds, this::openConfiguration, this::onOptionChanged, npcFilter::setIds, npcFilter::setNames, soundMute::setIds, developerMode ? this::runDebugDamage : null);

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
		attackSpread.reset();
		debugDamageQueue.clear();

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

		if (!debugDamageQueue.isEmpty())
		{
			injectDebugHits(debugDamageQueue.poll());
		}

		if (!hits.isEmpty())
		{
			for (List<PendingAttack> tickAttacks : hits.drainByTick())
				evaluatePendingAttacks(tickAttacks);
		}

		// Fire an aggregated multi-hit attack once all its hitsplats have landed (see AttackAggregator).
		if (attackSpread.isComplete(client.getTickCount()))
		{
			fireAttack(attackSpread.getWeaponId(), attackSpread.getGroups(), attackSpread.isDontOverrideGlobal(), attackSpread.buildOutcome());
			attackSpread.reset();
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

	/**
	 * Parses {@code ::cwsdamage}-style tick tokens ({@code args[startIndex..]}) and queues them against
	 * {@code weaponId} (-1 = the debug weapon, else equipped, resolved at inject time). Drives the panel's
	 * dev-only "Simulate damage" menu.
	 *
	 * <p>Each token is one tick. Join hitsplats with {@code +} to land several on the same tick ({@code 8+7}),
	 * or use {@code -} for an empty tick to space an attack out ({@code 30 - 30}). Append {@code m} for a max
	 * hit and/or {@code s} for a special attack; a standalone {@code m}/{@code s} token applies to every hit.
	 */
	private void enqueueDebugDamage(int weaponId, String[] args, int startIndex)
	{
		// A standalone flag-only argument (e.g. a trailing "s") applies to every hit in the command,
		// so `9+19 4+5 s` flags the whole attack as a spec rather than treating "s" as a tick.
		boolean forceMax = false;
		boolean forceSpec = false;
		for (int i = startIndex; i < args.length; i++)
		{
			if (isFlagsOnly(args[i]))
			{
				String f = args[i].toLowerCase();
				forceMax  |= f.indexOf('m') >= 0;
				forceSpec |= f.indexOf('s') >= 0;
			}
		}

		Deque<DebugTick> parsed = new ArrayDeque<>();
		int hitCount = 0;
		for (int i = startIndex; i < args.length; i++)
		{
			if (args[i].isEmpty() || isFlagsOnly(args[i])) continue; // command-wide flags (or empty tokens) aren't ticks

			// A lone "-" is an empty tick: it advances a game tick without landing any hitsplat, so an
			// attack whose hits are spread apart (e.g. a dark bow firing on tick 1 and tick 3) can be modelled.
			if ("-".equals(args[i]))
			{
				parsed.add(new DebugTick(weaponId, java.util.Collections.emptyList()));
				continue;
			}

			List<DebugHit> tickHits = new ArrayList<>();
			for (String token : args[i].split("\\+"))
			{
				DebugHit hit = parseDebugHit(token, forceMax, forceSpec);
				if (hit == null)
				{
					debugMessage("Invalid value: '" + token + "'. Use whole numbers or - to skip a tick, e.g. 30 - 30");
					return;
				}
				tickHits.add(hit);
			}
			parsed.add(new DebugTick(weaponId, tickHits));
			hitCount += tickHits.size();
		}

		if (hitCount == 0)
		{
			debugMessage("No damage given. e.g. 15 15");
			return;
		}

		debugDamageQueue.addAll(parsed);
		String on = weaponId >= 0 ? ("item " + weaponId) : "the equipped weapon";
		debugMessage("Simulating " + hitCount + " hitsplat(s) over " + parsed.size() + " tick(s) on " + on + ".");
	}

	/**
	 * Dev-only: runs a {@code ::cwsdamage}-style args string (e.g. {@code 15m+15m 7+5 s}) against {@code weaponId}
	 * (the right-clicked weapon), from the panel's "Simulate damage" dialog. Hops to the client thread since the
	 * menu fires on the Swing EDT.
	 */
	private void runDebugDamage(int weaponId, String input)
	{
		clientThread.invoke(() -> enqueueDebugDamage(weaponId, input.trim().split("\\s+"), 0));
	}

	/** True if an argument is only {@code m}/{@code s} flags (no digits), meaning it modifies every hit. */
	private static boolean isFlagsOnly(String arg)
	{
		return arg.matches("(?i)[ms]+");
	}

	/** Parses one {@code ::cwsdamage} token (digits plus optional {@code m}/{@code s} flags), or null if malformed. */
	private static DebugHit parseDebugHit(String token, boolean forceMax, boolean forceSpec)
	{
		String s = token.toLowerCase();
		boolean isMax  = forceMax  || s.indexOf('m') >= 0;
		boolean wasSpec = forceSpec || s.indexOf('s') >= 0;
		String digits = s.replace("m", "").replace("s", "");
		try
		{
			return new DebugHit(Integer.parseInt(digits), isMax, wasSpec);
		}
		catch (NumberFormatException e)
		{
			return null;
		}
	}

	/** Injects one tick's simulated hitsplats, all landing this tick, attributed to the given (or equipped) weapon. */
	private void injectDebugHits(DebugTick debugTick)
	{
		if (debugTick.hits.isEmpty()) return; // a "-" skip tick: consume the tick, land nothing

		int weaponId = debugTick.weaponId >= 0 ? debugTick.weaponId : getEquippedWeaponId();
		if (weaponId < 0)
		{
			debugMessage("No weapon equipped — set a debug weapon from its right-click menu, or equip a weapon.");
			debugDamageQueue.clear();
			return;
		}

		int tick = client.getTickCount();
		WeaponEntry entry = weapons.find(weaponId);
		boolean enabled = entry != null && entry.isEnabled();
		List<TriggerGroup> groups = enabled ? entry.getGroups() : java.util.Collections.emptyList();
		boolean dontOverrideGlobal = enabled && entry.isDontOverrideGlobal();

		for (DebugHit hit : debugTick.hits)
			hits.add(new DeferredHit(weaponId, groups, dontOverrideGlobal, hit.wasSpec, hit.amount, hit.isMax, tick, null));
	}

	private void debugMessage(String message)
	{
		client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", "[Custom Weapon SFX] " + message, null);
	}

	private void evaluatePendingAttacks(List<PendingAttack> pendingAttacks)
	{
		boolean specHitsplatSeen = false;
		for (PendingAttack attack : pendingAttacks)
		{
			boolean isKill = attack.actors.stream()
					.filter(a -> a instanceof Player && a != client.getLocalPlayer())
					.anyMatch(a -> ((Player) a).getHealthRatio() == 0);

			// Some attacks land several hitsplats spread across two ticks — claws' spec, or the dark bow's two
			// arrows — and paint the max-hit colour on only some of them. For those weapons an AttackProfile
			// tells us to aggregate the whole attack into one (fired once its hits complete — see onGameTick)
			// so triggers evaluate on the combined max/hit/total rather than each tick's partial.
			if (attack.groups != receivedGroups)
			{
				if (attackSpread.isActiveFor(attack.key))
				{
					attackSpread.add(attack.amounts, attack.isMaxList, attack.wasSpec, isKill);
					continue;
				}
				AttackProfile profile = AttackProfiles.forWeapon(attack.key);
				// specialOnly profiles (claws) aggregate only the spec; others (dark bow, twinflame) aggregate every attack.
				if (profile != null && (!profile.isSpecialOnly() || attack.wasSpec))
				{
					attackSpread.begin(attack.key, attack.tick, profile, attack.groups, attack.dontOverrideGlobal);
					attackSpread.add(attack.amounts, attack.isMaxList, attack.wasSpec, isKill);
					// A spec is now owned by the aggregator; close the spec window so later hits (a tail splat, or
					// a following normal attack) aren't independently tagged as a spec.
					if (attack.wasSpec) spec.clearWindow();
					continue;
				}
			}

			fireAttack(attack.key, attack.groups, attack.dontOverrideGlobal, attack.collapse(isKill));
			if (attack.wasSpec) specHitsplatSeen = true;
		}
		// Clear the spec window as soon as the spec's hitsplat has been processed
		// so the next normal attack isn't silently swallowed by the wasSpec flag.
		if (specHitsplatSeen)
		{
			spec.clearWindow();
		}
	}

	/** Fires the weapon's groups and (unless overridden) the Global groups that match {@code outcome}. */
	private void fireAttack(int weaponId, List<TriggerGroup> groups, boolean dontOverrideGlobal, AttackOutcome outcome)
	{
		if (soundPlayer == null) return;

		boolean suppressMax  = opt(SfxOption.IGNORE_SMALL_MAX) && outcome.allMax && outcome.maxAmount <= 3;
		boolean suppressZero = opt(SfxOption.IGNORE_ZERO_THRALL) && thrallTicksRemaining > 0;
		if (!(groups == receivedGroups && suppressReceivedOnDeath))
		{
			soundPlayer.fireMatchingGroups(groups, outcome.wasSpec, outcome.anyHit, outcome.allZero, outcome.allMax,
					suppressMax, suppressZero, outcome.isKill, EnumSet.noneOf(Triggers.class), outcome.amounts);
		}

		if (opt(SfxOption.GLOBAL_ENABLED) && groups != receivedGroups)
		{
			Set<Triggers> weaponCoveredTriggers = coveredGlobalTriggers(groups, dontOverrideGlobal);
			// Each Global group can blacklist specific weapons; skip the groups that exclude this one.
			List<TriggerGroup> applicable = globalWeaponGroups.stream()
					.filter(g -> !g.isBlacklisted(weaponId))
					.collect(Collectors.toList());
			soundPlayer.fireMatchingGroups(applicable, outcome.wasSpec, outcome.anyHit, outcome.allZero, outcome.allMax,
					suppressMax, suppressZero, outcome.isKill, weaponCoveredTriggers, outcome.amounts);
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

	/**
	 * Opens the weapon search and hands the picked item's id + name back to {@code sink} (a group's
	 * blacklist add). Resolving the item needs the client thread; the panel's sink hops back to the EDT.
	 */
	private void pickWeaponForBlacklist(java.util.function.BiConsumer<Integer, String> sink)
	{
		clientThread.invoke(() ->
		{
			if (!requireLoggedIn("search for weapons")) return;
			weaponSearch
				.onItemSelected(itemId ->
					sink.accept(itemId, client.getItemDefinition(itemId).getName()))
				.build();
		});
		client.getCanvas().requestFocusInWindow();
	}

	/** Hands the currently equipped weapon's id + name back to {@code sink} — the no-search counterpart. */
	private void pickEquippedForBlacklist(java.util.function.BiConsumer<Integer, String> sink)
	{
		clientThread.invoke(() ->
		{
			if (!requireLoggedIn("add an equipped weapon")) return;
			int weaponId = getEquippedWeaponId();
			if (weaponId < 0) return;
			sink.accept(weaponId, client.getItemDefinition(weaponId).getName());
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
