package com.customweaponsfx;

import net.runelite.api.Actor;
import net.runelite.api.coords.WorldPoint;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Covers the landing-side matching: a hitsplat is attributed to the weapon that launched the
 * projectile which caused it, keyed on the projectile's target and arrival cycle. Matching must be
 * independent of projectile travel distance, must not be stolen by an instant melee hit while a shot
 * is still clearly in flight, and must consume projectiles oldest-first per target.
 */
public class ProjectileWeaponTrackerTest
{
	private static final int STAFF = 11791;
	private static final int BOW   = 12926;
	private static final int START_CYCLE = 5000;

	private final ProjectileWeaponTracker tracker = new ProjectileWeaponTracker();

	// ---- recording dedup -----------------------------------------------------------------------

	@Test
	public void perFrameSpawnIsRecordedOnlyOnce()
	{
		assertFalse("first sighting is the spawn", tracker.hasSeen(START_CYCLE));
		assertTrue("subsequent frames are ignored", tracker.hasSeen(START_CYCLE));
		assertTrue(tracker.hasSeen(START_CYCLE));
	}

	// ---- target matching -----------------------------------------------------------------------

	@Test
	public void landingHitWithNoTrackedProjectileFallsBack()
	{
		assertNull(tracker.matchAndRemove(mock(Actor.class), 100));
	}

	@Test
	public void hitOnTargetIsAttributedToTheProjectileWeapon()
	{
		Actor target = mock(Actor.class);
		tracker.onProjectileSpawned(STAFF, target, null, 100, 0, false);

		ProjectileWeaponTracker.TrackedProjectile tp = tracker.matchAndRemove(target, 100);
		assertNotNull(tp);
		assertEquals(STAFF, tp.weaponId);
	}

	@Test
	public void matchedProjectileIsConsumed()
	{
		Actor target = mock(Actor.class);
		tracker.onProjectileSpawned(STAFF, target, null, 100, 0, false);

		assertNotNull(tracker.matchAndRemove(target, 100));
		assertNull("a projectile matches a single hit only", tracker.matchAndRemove(target, 100));
	}

	@Test
	public void hitOnADifferentTargetDoesNotMatch()
	{
		Actor target = mock(Actor.class);
		Actor other  = mock(Actor.class);
		tracker.onProjectileSpawned(STAFF, target, null, 100, 0, false);

		assertNull(tracker.matchAndRemove(other, 100));
	}

	// ---- arrival timing (distance independence) ------------------------------------------------

	@Test
	public void projectileStillClearlyInFlightIsNotMatched()
	{
		// Lands at cycle 200; a hit now (cycle 100) is 100 cycles (~3 ticks) early. An instant melee
		// hit landing meanwhile must NOT steal the in-flight shot, so this returns null (caller falls
		// back to the melee weapon).
		Actor target = mock(Actor.class);
		tracker.onProjectileSpawned(STAFF, target, null, 200, 0, false);

		assertNull(tracker.matchAndRemove(target, 100));
	}

	@Test
	public void projectileAboutToLandWithinSlackIsMatched()
	{
		// Lands at 120, hit at 100 -> 20 cycles (< one tick) early; treated as arrived.
		Actor target = mock(Actor.class);
		tracker.onProjectileSpawned(STAFF, target, null, 120, 0, false);

		assertNotNull(tracker.matchAndRemove(target, 100));
	}

	@Test
	public void longRangeShotLandingLateStillMatches()
	{
		// Lands at 100, hit not processed until 200 (a hitsplat a few ticks after a long-range shot
		// reaches the target). Distance/lateness must not cause a miss.
		Actor target = mock(Actor.class);
		tracker.onProjectileSpawned(STAFF, target, null, 100, 0, false);

		assertNotNull(tracker.matchAndRemove(target, 200));
	}

	// ---- FIFO per target -----------------------------------------------------------------------

	@Test
	public void multipleShotsAtSameTargetAreConsumedOldestFirst()
	{
		Actor target = mock(Actor.class);
		tracker.onProjectileSpawned(BOW,   target, null, 100, 1, false); // fired first
		tracker.onProjectileSpawned(STAFF, target, null, 100, 2, false); // fired second

		assertEquals(BOW,   tracker.matchAndRemove(target, 100).weaponId);
		assertEquals(STAFF, tracker.matchAndRemove(target, 100).weaponId);
		assertNull(tracker.matchAndRemove(target, 100));
	}

	// ---- spec flag carried through -------------------------------------------------------------

	@Test
	public void specFlagIsCarriedFromSpawnToLanding()
	{
		Actor target = mock(Actor.class);
		tracker.onProjectileSpawned(BOW, target, null, 100, 0, true);

		ProjectileWeaponTracker.TrackedProjectile tp = tracker.matchAndRemove(target, 100);
		assertNotNull(tp);
		assertTrue(tp.wasSpec);
	}

	// ---- AoE (no target actor) matched by world tile -------------------------------------------

	@Test
	public void aoeProjectileIsMatchedByWorldTile()
	{
		WorldPoint tile = new WorldPoint(3200, 3200, 0);
		Actor hit = mock(Actor.class);
		when(hit.getWorldLocation()).thenReturn(tile);

		tracker.onProjectileSpawned(STAFF, null, tile, 100, 0, false);

		ProjectileWeaponTracker.TrackedProjectile tp = tracker.matchAndRemove(hit, 100);
		assertNotNull(tp);
		assertEquals(STAFF, tp.weaponId);
	}

	@Test
	public void aoeProjectileOnAnotherTileDoesNotMatch()
	{
		Actor hit = mock(Actor.class);
		when(hit.getWorldLocation()).thenReturn(new WorldPoint(3200, 3200, 0));

		tracker.onProjectileSpawned(STAFF, null, new WorldPoint(3205, 3205, 0), 100, 0, false);

		assertNull(tracker.matchAndRemove(hit, 100));
	}

	// ---- pruning / leak-safety -----------------------------------------------------------------

	@Test
	public void projectileIsKeptUntilItsTtlElapses()
	{
		Actor target = mock(Actor.class);
		tracker.hasSeen(START_CYCLE);
		tracker.onProjectileSpawned(STAFF, target, null, 100, 0, false);

		tracker.prune(CustomWeaponSfxPlugin.PROJECTILE_TTL_TICKS);     // within TTL
		assertNotNull("entry within TTL is retained", tracker.matchAndRemove(target, 100));
	}

	@Test
	public void undeliveredProjectileIsPrunedSoNothingLeaks()
	{
		Actor target = mock(Actor.class);
		tracker.hasSeen(START_CYCLE);
		tracker.onProjectileSpawned(STAFF, target, null, 100, 0, false);

		tracker.prune(CustomWeaponSfxPlugin.PROJECTILE_TTL_TICKS + 1); // past TTL
		assertNull("pruned entry no longer matches", tracker.matchAndRemove(target, 100));
		assertFalse("dedup set cleared when nothing is tracked", tracker.hasSeen(START_CYCLE));
	}

	// ---- reset ---------------------------------------------------------------------------------

	@Test
	public void resetClearsTrackedProjectilesAndDedup()
	{
		Actor target = mock(Actor.class);
		tracker.hasSeen(START_CYCLE);
		tracker.onProjectileSpawned(STAFF, target, null, 100, 0, false);

		tracker.reset();
		assertFalse(tracker.hasSeen(START_CYCLE));
		assertNull(tracker.matchAndRemove(target, 100));
	}
}
