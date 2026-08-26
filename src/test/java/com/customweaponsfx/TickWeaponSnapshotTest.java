package com.customweaponsfx;

import org.junit.Test;
import static org.junit.Assert.assertEquals;

/**
 * Covers the launch-side fix: a projectile fired on a tick must be attributed to the weapon held at
 * the <em>start</em> of that tick, even if the player swaps weapons later in the same tick (the
 * cast-and-switch-right-away case). {@code ProjectileMoved} fires after the tick's GameTick, so the
 * snapshot must be one game tick behind live equipment.
 */
public class TickWeaponSnapshotTest
{
	private static final int STAFF = 29796 - 1; // a magic/ranged weapon
	private static final int MELEE = 29796;      // the weapon swapped to
	private static final int NONE  = TickWeaponSnapshot.NONE;

	@Test
	public void launchWeaponIsNoneBeforeAnyTick()
	{
		assertEquals(NONE, new TickWeaponSnapshot().launchWeapon());
	}

	@Test
	public void initSeedsLaunchWeaponSoEarlyShotsAreAttributed()
	{
		TickWeaponSnapshot snap = new TickWeaponSnapshot();
		snap.init(STAFF);
		assertEquals(STAFF, snap.launchWeapon());
	}

	@Test
	public void steadyStateReturnsTheEquippedWeapon()
	{
		TickWeaponSnapshot snap = new TickWeaponSnapshot();
		snap.init(STAFF);
		snap.onGameTick(STAFF);
		snap.onGameTick(STAFF);
		assertEquals(STAFF, snap.launchWeapon());
	}

	/** The regression: cast with the staff and swap to a melee weapon on the same tick. By the time
	 *  this tick's GameTick runs, equipment already reads the melee weapon — but the shot used the staff. */
	@Test
	public void sameTickCastThenSwapAttributesTheShotToTheStaff()
	{
		TickWeaponSnapshot snap = new TickWeaponSnapshot();
		snap.init(STAFF);                 // start of the tick: staff equipped
		snap.onGameTick(MELEE);           // GameTick observes post-swap equipment (melee)
		// The projectile renders after this GameTick and must still resolve to the staff.
		assertEquals(STAFF, snap.launchWeapon());
	}

	@Test
	public void weaponSettlesToTheNewWeaponOnTheFollowingTick()
	{
		TickWeaponSnapshot snap = new TickWeaponSnapshot();
		snap.init(STAFF);
		snap.onGameTick(MELEE);           // swap tick
		snap.onGameTick(MELEE);           // next tick, swap has settled
		assertEquals(MELEE, snap.launchWeapon());
	}

	/** Equipping a weapon one tick, then attacking with it the next, attributes to the new weapon. */
	@Test
	public void swapThenAttackNextTickUsesTheNewWeapon()
	{
		TickWeaponSnapshot snap = new TickWeaponSnapshot();
		snap.init(STAFF);
		snap.onGameTick(MELEE);           // tick N: equipped melee (no shot fired)
		snap.onGameTick(MELEE);           // tick N+1: fire with melee
		assertEquals(MELEE, snap.launchWeapon());
	}

	@Test
	public void resetClearsBothSnapshots()
	{
		TickWeaponSnapshot snap = new TickWeaponSnapshot();
		snap.init(STAFF);
		snap.onGameTick(STAFF);
		snap.reset();
		assertEquals(NONE, snap.launchWeapon());
	}
}
