package com.customweaponsfx;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class AttackAggregatorTest
{
	private static final List<TriggerGroup> GROUPS = new ArrayList<>();

	// Burning claws: special only, 3 hits over 2 ticks, any max-coloured splat counts as a max.
	private static final AttackProfile BURNING_CLAWS =
		new AttackProfile(3, 2, true, flags -> flags.stream().anyMatch(Boolean::booleanValue));
	// Dragon claws: special only, 4 hits (2 per tick over 2 ticks). Only the first two splats can max, so a
	// max hit requires both of the first two to be max.
	private static final AttackProfile DRAGON_CLAWS =
		new AttackProfile(4, 2, true, flags -> flags.size() >= 2 && flags.get(0) && flags.get(1));
	// Dark bow: any attack, 2 hits; count-based completion with a wide 5-tick fallback span (arrows can land
	// a couple of ticks apart at range). Max only when both splats are max.
	private static final AttackProfile DARK_BOW =
		new AttackProfile(2, 5, false, flags -> flags.stream().allMatch(Boolean::booleanValue));
	// Twinflame staff: any attack, 2 hits a tick apart, only the first hit can be max.
	private static final AttackProfile TWINFLAME =
		new AttackProfile(2, 3, false, flags -> !flags.isEmpty() && flags.get(0));

	private static int sum(int[] a)
	{
		return Arrays.stream(a).sum();
	}

	@Test
	public void clawsAggregateAcrossTwoTicksWithAnySplatMax()
	{
		AttackAggregator agg = new AttackAggregator();
		agg.begin(555, 10, BURNING_CLAWS, GROUPS, false);
		agg.add(Arrays.asList(18, 12), Arrays.asList(false, false), true, false);
		assertFalse("not complete: only 2 of 3 hits", agg.isComplete(10));

		agg.add(Arrays.asList(15), Arrays.asList(true), false, false); // tail splat carries max, lost spec flag
		assertTrue(agg.isComplete(11));

		AttackOutcome o = agg.buildOutcome();
		assertTrue("spec flag OR-ed across the attack", o.wasSpec);
		assertTrue("any max-coloured splat -> max hit", o.allMax);
		assertEquals(18 + 12 + 15, sum(o.amounts));
	}

	@Test
	public void dragonClawsAreMaxOnlyWhenBothOfTheFirstTwoSplatsAreMax()
	{
		// 2 splats on tick 10, 2 on tick 11; completes by count (4) on the second tick.
		// Both of the first two splats max -> a max hit.
		AttackAggregator bothMax = new AttackAggregator();
		bothMax.begin(13652, 10, DRAGON_CLAWS, GROUPS, false);
		bothMax.add(Arrays.asList(40, 20), Arrays.asList(true, true), true, false);
		assertFalse("only 2 of 4 hits", bothMax.isComplete(10));
		bothMax.add(Arrays.asList(10, 11), Arrays.asList(false, false), false, false);
		assertTrue(bothMax.isComplete(11));
		AttackOutcome o = bothMax.buildOutcome();
		assertTrue("both of the first two splats max -> max hit", o.allMax);
		assertTrue("spec flag carried across the ticks", o.wasSpec);
		assertEquals(81, sum(o.amounts));

		// Only the first splat max -> not a max hit.
		AttackAggregator firstOnly = new AttackAggregator();
		firstOnly.begin(13652, 10, DRAGON_CLAWS, GROUPS, false);
		firstOnly.add(Arrays.asList(40, 20), Arrays.asList(true, false), true, false);
		firstOnly.add(Arrays.asList(10, 11), Arrays.asList(false, false), false, false);
		assertFalse("only the first splat max -> not a max hit", firstOnly.buildOutcome().allMax);

		// Only a later (3rd/4th) splat flagged max -> not a max hit; those splats never actually max for claws.
		AttackAggregator laterOnly = new AttackAggregator();
		laterOnly.begin(13652, 10, DRAGON_CLAWS, GROUPS, false);
		laterOnly.add(Arrays.asList(40, 20), Arrays.asList(false, false), true, false);
		laterOnly.add(Arrays.asList(10, 11), Arrays.asList(true, true), false, false);
		assertFalse("first two not max -> not a max hit", laterOnly.buildOutcome().allMax);
	}

	@Test
	public void darkBowSameTickPairCompletesImmediately()
	{
		// Both arrows land on tick 10 -> hit count reached at once, no need to wait a tick.
		AttackAggregator agg = new AttackAggregator();
		agg.begin(11235, 10, DARK_BOW, GROUPS, false);
		agg.add(Arrays.asList(20, 26), Arrays.asList(true, true), false, false);
		assertTrue(agg.isComplete(10));

		AttackOutcome o = agg.buildOutcome();
		assertFalse(o.wasSpec);
		assertTrue("both splats max -> max hit", o.allMax);
		assertEquals(46, sum(o.amounts));
	}

	@Test
	public void darkBowSplitPairCompletesOnSecondTick()
	{
		AttackAggregator agg = new AttackAggregator();
		agg.begin(11235, 10, DARK_BOW, GROUPS, false);
		agg.add(Arrays.asList(20), Arrays.asList(true), false, false);
		assertFalse("only one arrow so far", agg.isComplete(10));
		agg.add(Arrays.asList(6), Arrays.asList(false), false, false);
		assertTrue(agg.isComplete(11));

		AttackOutcome o = agg.buildOutcome();
		assertFalse("only one splat maxed -> not a max hit for the dark bow", o.allMax);
		assertEquals(26, sum(o.amounts));
	}

	@Test
	public void darkBowTwoTickGapWaitsForTheSecondArrowInsteadOfFiringTwice()
	{
		// Regression: at range the arrows can land two ticks apart (10 and 12). The wide fallback span must not
		// complete the attack at tick 11 with only the first arrow — that split it into two firings.
		AttackAggregator agg = new AttackAggregator();
		agg.begin(11235, 10, DARK_BOW, GROUPS, false);
		agg.add(Arrays.asList(20), Arrays.asList(true), false, false);
		assertFalse("tick 11: still waiting for the second arrow", agg.isComplete(11));
		assertFalse("tick 12 before the arrow: still waiting", agg.isComplete(12));

		agg.add(Arrays.asList(26), Arrays.asList(true), false, false); // second arrow lands on tick 12
		assertTrue("both arrows in -> completes by count", agg.isComplete(12));
		assertTrue("both splats max", agg.buildOutcome().allMax);
		assertEquals(46, sum(agg.buildOutcome().amounts));
	}

	@Test
	public void tickSpanFallbackCompletesWhenAHitIsMissing()
	{
		// Dark bow but only one arrow ever lands (e.g. target died): the tick-span fallback completes it after
		// the span (firstTick 10 + span 5 - 1 = 14), well within the dark bow's ≥8-tick attack cadence.
		AttackAggregator agg = new AttackAggregator();
		agg.begin(11235, 10, DARK_BOW, GROUPS, false);
		agg.add(Arrays.asList(30), Arrays.asList(false), false, true);
		assertFalse("not yet: span has not elapsed and only one hit landed", agg.isComplete(13));
		assertTrue("span elapsed -> complete even though only one hit landed", agg.isComplete(14));
		assertTrue(agg.buildOutcome().isKill);
	}

	@Test
	public void twinflameIsMaxOnlyWhenTheFirstHitIsMax()
	{
		// First hit max, second hit not -> a max hit (only the first hit can max).
		AttackAggregator first = new AttackAggregator();
		first.begin(30634, 10, TWINFLAME, GROUPS, false);
		first.add(Arrays.asList(30), Arrays.asList(true), false, false);
		first.add(Arrays.asList(12), Arrays.asList(false), false, false); // second hit lands a tick later
		assertTrue(first.isComplete(11));
		AttackOutcome maxed = first.buildOutcome();
		assertTrue("first hit max -> max hit", maxed.allMax);
		assertEquals(42, sum(maxed.amounts));

		// First hit not max -> not a max hit, regardless of the second.
		AttackAggregator second = new AttackAggregator();
		second.begin(30634, 10, TWINFLAME, GROUPS, false);
		second.add(Arrays.asList(20), Arrays.asList(false), false, false);
		second.add(Arrays.asList(12), Arrays.asList(true), false, false);
		assertFalse("first hit not max -> not a max hit", second.buildOutcome().allMax);
	}

	@Test
	public void isActiveForOnlyMatchesTheOpenWeapon()
	{
		AttackAggregator agg = new AttackAggregator();
		assertFalse(agg.isActiveFor(11235));
		agg.begin(11235, 10, DARK_BOW, GROUPS, false);
		assertTrue(agg.isActiveFor(11235));
		assertFalse(agg.isActiveFor(555));
		agg.reset();
		assertFalse(agg.isActiveFor(11235));
	}
}
