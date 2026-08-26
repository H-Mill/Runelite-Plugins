package com.customweaponsfx;

import org.junit.Test;
import static org.junit.Assert.assertEquals;

public class AttackWeaponTrackerTest
{
	private static final int SCYTHE = 22325;
	private static final int BOW = 12926;
	private static final int UNARMED = AttackWeaponTracker.NONE;

	@Test
	public void freshTrackerFallsBackToCurrentlyEquippedWeapon()
	{
		AttackWeaponTracker tracker = new AttackWeaponTracker();
		assertEquals(SCYTHE, tracker.resolveWeaponId(SCYTHE));
	}

	@Test
	public void capturedWeaponIsUsedForALandingHitsplat()
	{
		AttackWeaponTracker tracker = new AttackWeaponTracker();
		tracker.onAttack(SCYTHE);
		assertEquals(SCYTHE, tracker.resolveWeaponId(SCYTHE));
	}

	@Test
	public void capturedWeaponWinsOverACurrentSwap()
	{
		// Fire a bow, then swap to a scythe while the arrow is mid-flight.
		// The landing hitsplat should still be attributed to the bow.
		AttackWeaponTracker tracker = new AttackWeaponTracker();
		tracker.onAttack(BOW);
		assertEquals(BOW, tracker.resolveWeaponId(SCYTHE));
	}

	// ---- the regression this protects ---------------------------------------------

	@Test
	public void unarmedAttackAfterUnequipDoesNotReuseTheOldWeapon()
	{
		AttackWeaponTracker tracker = new AttackWeaponTracker();
		tracker.onAttack(SCYTHE);            // attacked with a weapon
		tracker.onAttack(UNARMED);           // unequipped, then punched bare-fisted
		// Resolution must report unarmed, not the stale scythe, so no weapon SFX fires.
		assertEquals(UNARMED, tracker.resolveWeaponId(UNARMED));
	}

	@Test
	public void unarmedCaptureClearsAPreviouslyCapturedWeapon()
	{
		AttackWeaponTracker tracker = new AttackWeaponTracker();
		tracker.onAttack(SCYTHE);
		tracker.onAttack(UNARMED);
		// Even if some equipped weapon is reported at hitsplat-time, an unarmed swing
		// cleared the capture so it must not resurrect the scythe.
		assertEquals(BOW, tracker.resolveWeaponId(BOW));
	}

	@Test
	public void resetClearsCapturedWeapon()
	{
		AttackWeaponTracker tracker = new AttackWeaponTracker();
		tracker.onAttack(SCYTHE);
		tracker.reset();
		assertEquals(UNARMED, tracker.resolveWeaponId(UNARMED));
	}
}
