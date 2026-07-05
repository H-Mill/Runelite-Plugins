package com.customweaponsfx;

import org.junit.Test;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Covers the special-attack detection state machine: a spec is "fired" when energy drops below the
 * last seen value, the firing weapon is remembered for a short window, and a window that never
 * produces a hitsplat expires so it can't taint a later normal attack.
 */
public class SpecAttackTrackerTest
{
	private static final int DRAGON_DAGGER = 1215;

	// ---- drop detection ------------------------------------------------------------------------

	@Test
	public void dropOnTheVeryFirstTickIsNotDetectedWithoutInit()
	{
		// A fresh tracker has no baseline, so the first tick only seeds it — no false positive.
		SpecAttackTracker tracker = new SpecAttackTracker();
		assertFalse(tracker.onTick(50, 1));
	}

	@Test
	public void dropAfterInitIsDetected()
	{
		SpecAttackTracker tracker = new SpecAttackTracker();
		tracker.init(100);
		assertTrue("spec energy fell from 100 to 50", tracker.onTick(50, 1));
	}

	@Test
	public void steadyEnergyIsNotASpec()
	{
		SpecAttackTracker tracker = new SpecAttackTracker();
		tracker.init(100);
		assertFalse(tracker.onTick(100, 1));
	}

	@Test
	public void regeneratingEnergyIsNotASpec()
	{
		SpecAttackTracker tracker = new SpecAttackTracker();
		tracker.init(50);
		assertFalse("energy climbing back up must not register as a fire", tracker.onTick(60, 1));
	}

	@Test
	public void consecutiveDropsAreEachDetected()
	{
		SpecAttackTracker tracker = new SpecAttackTracker();
		tracker.init(100);
		assertTrue(tracker.onTick(75, 1));
		assertFalse("no further drop", tracker.onTick(75, 2));
		assertTrue("a second spec drains it again", tracker.onTick(50, 3));
	}

	// ---- the spec window -----------------------------------------------------------------------

	@Test
	public void freshTrackerHasNoOpenWindow()
	{
		assertFalse(new SpecAttackTracker().wasSpec());
	}

	@Test
	public void armOpensTheWindow()
	{
		SpecAttackTracker tracker = new SpecAttackTracker();
		tracker.arm(DRAGON_DAGGER, 5);
		assertTrue(tracker.wasSpec());
	}

	@Test
	public void clearWindowClosesIt()
	{
		SpecAttackTracker tracker = new SpecAttackTracker();
		tracker.arm(DRAGON_DAGGER, 5);
		tracker.clearWindow();
		assertFalse(tracker.wasSpec());
	}

	// ---- window expiry -------------------------------------------------------------------------

	@Test
	public void windowSurvivesUpToTheTimeoutBoundary()
	{
		SpecAttackTracker tracker = new SpecAttackTracker();
		tracker.init(100);
		tracker.arm(DRAGON_DAGGER, 5);
		// 15 - 5 == 10 == TIMEOUT_TICKS; not strictly greater, so the window is still open.
		tracker.onTick(100, 15);
		assertTrue(tracker.wasSpec());
	}

	@Test
	public void staleWindowExpiresPastTheTimeout()
	{
		SpecAttackTracker tracker = new SpecAttackTracker();
		tracker.init(100);
		tracker.arm(DRAGON_DAGGER, 5);
		// 16 - 5 == 11 > TIMEOUT_TICKS: a spec that never landed must not taint a later attack.
		tracker.onTick(100, 16);
		assertFalse(tracker.wasSpec());
	}

	// ---- reset ---------------------------------------------------------------------------------

	@Test
	public void resetClosesTheWindowAndDropsTheBaseline()
	{
		SpecAttackTracker tracker = new SpecAttackTracker();
		tracker.init(100);
		tracker.arm(DRAGON_DAGGER, 5);

		tracker.reset();
		assertFalse("window cleared", tracker.wasSpec());
		// Baseline cleared too: the first post-reset tick only re-seeds, it can't fire.
		assertFalse(tracker.onTick(20, 6));
	}
}
