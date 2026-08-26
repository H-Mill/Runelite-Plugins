package com.customweaponsfx;

import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import net.runelite.api.Actor;
import net.runelite.api.coords.WorldPoint;

/**
 * Attributes a landing hitsplat to the weapon that <em>launched the projectile</em> which caused it,
 * rather than to whatever weapon the player most recently swung.
 *
 * <p>A magic/ranged projectile lands several ticks after the attack animation. In that window the
 * player can swap weapons and a new attack animation (a manual melee swing, or an auto-retaliate
 * swing) fires — overwriting the single-slot {@link AttackWeaponTracker}. Without projectile
 * tracking the delayed hit would then be mis-attributed to the newly-equipped weapon. This tracker
 * captures the weapon at projectile-spawn time and hands it back when the matching hitsplat lands.
 *
 * <p>Melee (instant) hits and non-projectile "mine" hitsplats (thralls, poison/venom, recoil) have
 * no tracked projectile; the caller falls back to {@link AttackWeaponTracker} for those.
 */
class ProjectileWeaponTracker
{
	/** A projectile fired by the local player, awaiting its landing hitsplat. */
	static final class TrackedProjectile
	{
		final int weaponId;
		final Actor target;          // null for AoE / ground-targeted projectiles
		final WorldPoint targetPoint;
		final int endCycle;          // game cycle the projectile reaches its target (Projectile#getEndCycle)
		final int spawnTick;
		final boolean wasSpec;

		TrackedProjectile(int weaponId, Actor target, WorldPoint targetPoint,
			int endCycle, int spawnTick, boolean wasSpec)
		{
			this.weaponId = weaponId;
			this.target = target;
			this.targetPoint = targetPoint;
			this.endCycle = endCycle;
			this.spawnTick = spawnTick;
			this.wasSpec = wasSpec;
		}
	}

	/**
	 * How far in the future (in game cycles, 30 per tick) a projectile may still be travelling and yet
	 * be considered "arrived" for a hit landing now. A projectile lands on a game tick boundary while
	 * its {@code endCycle} can fall anywhere inside that tick, so the hit-time game cycle may read a
	 * little before {@code endCycle}; one tick of slack absorbs that without matching shots that are
	 * still clearly in flight.
	 */
	private static final int ARRIVE_SLACK_CYCLES = 30;

	private final List<TrackedProjectile> tracked = new CopyOnWriteArrayList<>();
	private final Set<Integer> seenStartCycles = new HashSet<>();

	/**
	 * @return true the first time a given projectile (identified by its start cycle) is seen, so the
	 * per-frame {@code ProjectileMoved} event only records the spawn once.
	 */
	boolean hasSeen(int startCycle)
	{
		return !seenStartCycles.add(startCycle);
	}

	void onProjectileSpawned(int weaponId, Actor target, WorldPoint targetPoint,
		int endCycle, int spawnTick, boolean wasSpec)
	{
		tracked.add(new TrackedProjectile(weaponId, target, targetPoint, endCycle, spawnTick, wasSpec));
	}

	/**
	 * Find the projectile a landing hitsplat should be attributed to, remove it, and return it.
	 * Returns {@code null} when no tracked projectile matches (caller falls back to the instant tracker).
	 *
	 * <p>Matching is keyed on the target and consumes the oldest projectile that has already reached
	 * (or is within a tick of reaching) that target — independent of how long the projectile travelled,
	 * so a long-range shot can't fall outside a tick window and be mis-attributed to a swapped weapon.
	 * A projectile that is still clearly in flight is left untouched, so an instant melee hit landing on
	 * the same target meanwhile correctly falls back to the melee weapon.
	 *
	 * @param nowCycle the current game cycle ({@code client.getGameCycle()}) at hit time
	 */
	TrackedProjectile matchAndRemove(Actor hitActor, int nowCycle)
	{
		// Prefer actor-targeted projectiles aimed at this actor; fall back to AoE projectiles by tile.
		TrackedProjectile best = oldestArrived(hitActor, null, nowCycle);
		if (best == null && hitActor != null)
			best = oldestArrived(null, hitActor.getWorldLocation(), nowCycle);

		if (best != null) tracked.remove(best);
		return best;
	}

	/**
	 * Oldest (FIFO) arrived projectile matching either an actor target or, for AoE, a world tile.
	 * Exactly one of {@code actor} / {@code point} is non-null.
	 */
	private TrackedProjectile oldestArrived(Actor actor, WorldPoint point, int nowCycle)
	{
		TrackedProjectile best = null;
		for (TrackedProjectile tp : tracked)
		{
			if (actor != null)
			{
				if (tp.target != actor) continue;
			}
			else
			{
				if (tp.target != null || tp.targetPoint == null || !tp.targetPoint.equals(point)) continue;
			}
			if (tp.endCycle - nowCycle > ARRIVE_SLACK_CYCLES) continue; // still clearly in flight
			if (best == null || tp.spawnTick < best.spawnTick) best = tp;
		}
		return best;
	}

	/** Drop projectiles that never produced a hitsplat (dodged, blocked, off-screen) so nothing leaks. */
	void prune(int nowTick)
	{
		for (Iterator<TrackedProjectile> it = tracked.iterator(); it.hasNext(); )
		{
			TrackedProjectile tp = it.next();
			if (nowTick - tp.spawnTick > CustomWeaponSfxPlugin.PROJECTILE_TTL_TICKS)
				tracked.remove(tp);
		}
		if (tracked.isEmpty()) seenStartCycles.clear();
	}

	void reset()
	{
		tracked.clear();
		seenStartCycles.clear();
	}
}
