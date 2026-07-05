package com.customweaponsfx;

import java.util.ArrayList;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class HitAggregatorTest
{
	private static DeferredHit hit(int key, List<TriggerGroup> groups, boolean wasSpec, int amount, boolean isMax, int tick)
	{
		return new DeferredHit(key, groups, false, wasSpec, amount, isMax, tick, null);
	}

	private static PendingAttack only(List<PendingAttack> attacks)
	{
		assertEquals(1, attacks.size());
		return attacks.get(0);
	}

	private static PendingAttack withGroups(List<PendingAttack> attacks, List<TriggerGroup> groups)
	{
		return attacks.stream().filter(a -> a.groups == groups).findFirst().orElseThrow(AssertionError::new);
	}

	// ---- PendingAttack.collapse ----------------------------------------------------

	@Test
	public void collapseFlagsAllMaxWhenEveryHitIsMax()
	{
		PendingAttack attack = new PendingAttack(new ArrayList<>());
		attack.wasSpec = true;
		attack.amounts.add(5);
		attack.amounts.add(3);
		attack.isMaxList.add(true);
		attack.isMaxList.add(true);

		AttackOutcome o = attack.collapse(false);
		assertTrue(o.wasSpec);
		assertTrue(o.anyHit);
		assertFalse(o.allZero);
		assertTrue(o.allMax);
		assertEquals(5, o.maxAmount);
		assertFalse(o.isKill);
	}

	@Test
	public void collapseFlagsAllZeroOnlyWhenEveryHitIsZero()
	{
		PendingAttack attack = new PendingAttack(new ArrayList<>());
		attack.amounts.add(0);
		attack.amounts.add(0);
		attack.isMaxList.add(false);
		attack.isMaxList.add(false);

		AttackOutcome o = attack.collapse(false);
		assertFalse(o.anyHit);
		assertTrue(o.allZero);
		assertFalse(o.allMax);
		assertEquals(0, o.maxAmount);
	}

	@Test
	public void collapseMixedHitIsNeitherAllMaxNorAllZero()
	{
		PendingAttack attack = new PendingAttack(new ArrayList<>());
		attack.amounts.add(5);
		attack.amounts.add(0);
		attack.isMaxList.add(true);
		attack.isMaxList.add(false);

		AttackOutcome o = attack.collapse(false);
		assertTrue(o.anyHit);
		assertFalse(o.allZero);
		assertFalse(o.allMax);
		assertEquals(5, o.maxAmount);
	}

	@Test
	public void collapsePassesThroughIsKill()
	{
		PendingAttack attack = new PendingAttack(new ArrayList<>());
		attack.amounts.add(7);
		attack.isMaxList.add(false);
		assertTrue(attack.collapse(true).isKill);
	}

	// ---- HitAggregator.drainByTick -------------------------------------------------

	@Test
	public void emptyAggregatorDrainsToNothing()
	{
		HitAggregator agg = new HitAggregator();
		assertTrue(agg.isEmpty());
		assertTrue(agg.drainByTick().isEmpty());
	}

	@Test
	public void hitsSharingTickAndKeyAggregateIntoOneAttack()
	{
		List<TriggerGroup> groups = new ArrayList<>();
		HitAggregator agg = new HitAggregator();
		agg.add(hit(1, groups, false, 5, true, 10));
		agg.add(hit(1, groups, false, 3, false, 10));

		List<List<PendingAttack>> byTick = agg.drainByTick();
		assertEquals(1, byTick.size());
		PendingAttack attack = only(byTick.get(0));
		assertEquals(2, attack.amounts.size());
		assertEquals(5, (int) attack.amounts.get(0));
		assertEquals(3, (int) attack.amounts.get(1));
		assertSame(groups, attack.groups);
	}

	@Test
	public void distinctKeysInSameTickBecomeSeparateAttacks()
	{
		List<TriggerGroup> weapon = new ArrayList<>();
		List<TriggerGroup> received = new ArrayList<>();
		HitAggregator agg = new HitAggregator();
		agg.add(hit(100, weapon, true, 9, true, 10));
		agg.add(hit(-2, received, false, 0, false, 10));

		List<PendingAttack> tick = agg.drainByTick().get(0);
		assertEquals(2, tick.size());
		assertTrue(withGroups(tick, weapon).wasSpec);
		assertFalse(withGroups(tick, received).wasSpec);
		assertEquals(0, (int) withGroups(tick, received).amounts.get(0));
	}

	@Test
	public void separateTicksAreReturnedInAscendingOrder()
	{
		List<TriggerGroup> groups = new ArrayList<>();
		HitAggregator agg = new HitAggregator();
		// add out of order to prove the drain sorts by tick
		agg.add(hit(1, groups, false, 5, false, 12));
		agg.add(hit(1, groups, false, 6, false, 11));

		List<List<PendingAttack>> byTick = agg.drainByTick();
		assertEquals(2, byTick.size());
		assertEquals(6, (int) only(byTick.get(0)).amounts.get(0)); // tick 11 first
		assertEquals(5, (int) only(byTick.get(1)).amounts.get(0)); // tick 12 second
	}

	@Test
	public void drainClearsTheBuffer()
	{
		HitAggregator agg = new HitAggregator();
		agg.add(hit(1, new ArrayList<>(), false, 5, false, 10));
		assertFalse(agg.isEmpty());
		agg.drainByTick();
		assertTrue(agg.isEmpty());
		assertTrue(agg.drainByTick().isEmpty());
	}
}
