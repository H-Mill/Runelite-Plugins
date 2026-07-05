package com.customweaponsfx;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class TriggerEvaluatorTest
{
	private static final Set<Triggers> NO_COVER = EnumSet.noneOf(Triggers.class);

	private static TriggerGroup group(Triggers... triggers)
	{
		Set<Triggers> set = triggers.length == 0
			? EnumSet.noneOf(Triggers.class)
			: EnumSet.copyOf(Arrays.asList(triggers));
		return new TriggerGroup(set, new ArrayList<>(), 100);
	}

	private static List<TriggerGroup> fire(
		List<TriggerGroup> groups, boolean wasSpec, boolean anyHit, boolean allZero,
		boolean allMax, boolean suppressMax, boolean suppressZero, boolean isKill,
		Set<Triggers> covered)
	{
		return TriggerEvaluator.selectFiringGroups(groups, wasSpec, anyHit, allZero,
			allMax, suppressMax, suppressZero, isKill, covered);
	}

	// ---- matchesTrigger truth table ------------------------------------------------

	@Test
	public void regularHitMatchesOnlyNonSpecDamageThatIsNotAllMax()
	{
		// anyHit, not allMax, not spec -> match
		assertTrue(TriggerEvaluator.matchesTrigger(Triggers.REGULAR_HIT, false, true, false, false, false, false, false));
		// all max -> REGULAR_HIT must NOT fire (REGULAR_MAX owns that case)
		assertFalse(TriggerEvaluator.matchesTrigger(Triggers.REGULAR_HIT, false, true, false, true, false, false, false));
		// spec attack -> REGULAR_HIT must not fire
		assertFalse(TriggerEvaluator.matchesTrigger(Triggers.REGULAR_HIT, true, true, false, false, false, false, false));
		// no damage -> no match
		assertFalse(TriggerEvaluator.matchesTrigger(Triggers.REGULAR_HIT, false, false, true, false, false, false, false));
	}

	@Test
	public void regularZeroRespectsSuppressZero()
	{
		assertTrue(TriggerEvaluator.matchesTrigger(Triggers.REGULAR_ZERO, false, false, true, false, false, false, false));
		// suppressZero (e.g. thrall active) blocks it
		assertFalse(TriggerEvaluator.matchesTrigger(Triggers.REGULAR_ZERO, false, false, true, false, false, true, false));
		// spec zero is a different trigger
		assertFalse(TriggerEvaluator.matchesTrigger(Triggers.REGULAR_ZERO, true, false, true, false, false, false, false));
	}

	@Test
	public void maxTriggersRespectSuppressMax()
	{
		assertTrue(TriggerEvaluator.matchesTrigger(Triggers.REGULAR_MAX, false, true, false, true, false, false, false));
		assertFalse(TriggerEvaluator.matchesTrigger(Triggers.REGULAR_MAX, false, true, false, true, true, false, false));
		assertTrue(TriggerEvaluator.matchesTrigger(Triggers.SPECIAL_MAX, true, true, false, true, false, false, false));
		assertFalse(TriggerEvaluator.matchesTrigger(Triggers.SPECIAL_MAX, true, true, false, true, true, false, false));
	}

	@Test
	public void specialTriggersRequireWasSpec()
	{
		assertTrue(TriggerEvaluator.matchesTrigger(Triggers.SPECIAL_HIT, true, true, false, false, false, false, false));
		assertFalse(TriggerEvaluator.matchesTrigger(Triggers.SPECIAL_HIT, false, true, false, false, false, false, false));
		assertTrue(TriggerEvaluator.matchesTrigger(Triggers.SPECIAL_ZERO, true, false, true, false, false, false, false));
		// SPECIAL_ZERO ignores suppressZero (only REGULAR_ZERO is gated by it)
		assertTrue(TriggerEvaluator.matchesTrigger(Triggers.SPECIAL_ZERO, true, false, true, false, false, true, false));
	}

	@Test
	public void allTriggerAlwaysMatchesAndKillNeedsKill()
	{
		assertTrue(TriggerEvaluator.matchesTrigger(Triggers.ALL, false, false, true, false, false, true, false));
		assertTrue(TriggerEvaluator.matchesTrigger(Triggers.KILL, false, false, false, false, false, false, true));
		assertFalse(TriggerEvaluator.matchesTrigger(Triggers.KILL, false, false, false, false, false, false, false));
	}

	@Test
	public void playerDeathNeverMatchesViaHitsplatPath()
	{
		assertFalse(TriggerEvaluator.matchesTrigger(Triggers.PLAYER_DEATH, false, true, false, false, false, false, true));
	}

	// ---- selectFiringGroups --------------------------------------------------------

	@Test
	public void emptyTriggerGroupsNeverFire()
	{
		List<TriggerGroup> groups = Collections.singletonList(group());
		assertTrue(fire(groups, false, true, false, false, false, false, false, NO_COVER).isEmpty());
	}

	@Test
	public void playerDeathGroupsAreSkippedOnHitsplatPath()
	{
		List<TriggerGroup> groups = Collections.singletonList(group(Triggers.PLAYER_DEATH));
		assertTrue(fire(groups, false, true, false, false, false, false, false, NO_COVER).isEmpty());
	}

	@Test
	public void regularHitFiresMatchingGroup()
	{
		TriggerGroup hit = group(Triggers.REGULAR_HIT);
		List<TriggerGroup> result = fire(Collections.singletonList(hit), false, true, false, false, false, false, false, NO_COVER);
		assertEquals(Collections.singletonList(hit), result);
	}

	@Test
	public void specAttackRoutesToSpecialGroupsNotRegular()
	{
		TriggerGroup regular = group(Triggers.REGULAR_HIT);
		TriggerGroup special = group(Triggers.SPECIAL_HIT);
		List<TriggerGroup> result = fire(Arrays.asList(regular, special), true, true, false, false, false, false, false, NO_COVER);
		assertEquals(Collections.singletonList(special), result);
	}

	@Test
	public void declarationOrderIsPreserved()
	{
		TriggerGroup a = group(Triggers.ALL);
		TriggerGroup b = group(Triggers.REGULAR_HIT);
		List<TriggerGroup> result = fire(Arrays.asList(a, b), false, true, false, false, false, false, false, NO_COVER);
		assertEquals(Arrays.asList(a, b), result);
	}

	@Test
	public void killGroupTakesExclusivePriorityOverNonKillGroups()
	{
		TriggerGroup kill = group(Triggers.KILL);
		TriggerGroup hit = group(Triggers.REGULAR_HIT);
		TriggerGroup all = group(Triggers.ALL);
		List<TriggerGroup> result = fire(Arrays.asList(hit, kill, all), false, true, false, false, false, false, true, NO_COVER);
		// On a kill with a KILL group present, only the kill group fires.
		assertEquals(Collections.singletonList(kill), result);
	}

	@Test
	public void nonKillGroupsFireWhenKillOccursButNoKillGroupExists()
	{
		TriggerGroup hit = group(Triggers.REGULAR_HIT);
		List<TriggerGroup> result = fire(Collections.singletonList(hit), false, true, false, false, false, false, true, NO_COVER);
		assertEquals(Collections.singletonList(hit), result);
	}

	@Test
	public void killGroupWithExtraTriggerActsAsAndGate()
	{
		// KILL + SPECIAL_MAX: fires only if the kill was also a special max hit.
		TriggerGroup killSpecMax = group(Triggers.KILL, Triggers.SPECIAL_MAX);

		List<TriggerGroup> noSpec = fire(Collections.singletonList(killSpecMax),
			false, true, false, true, false, false, true, NO_COVER);
		assertTrue("kill but not a spec max -> AND gate blocks", noSpec.isEmpty());

		List<TriggerGroup> specMax = fire(Collections.singletonList(killSpecMax),
			true, true, false, true, false, false, true, NO_COVER);
		assertEquals(Collections.singletonList(killSpecMax), specMax);
	}

	@Test
	public void killOnlyGroupFiresOnKillRegardlessOfDamage()
	{
		TriggerGroup kill = group(Triggers.KILL);
		List<TriggerGroup> result = fire(Collections.singletonList(kill), false, false, true, false, false, false, true, NO_COVER);
		assertEquals(Collections.singletonList(kill), result);
	}

	@Test
	public void weaponCoveredTriggersFilterOverlappingGlobalGroups()
	{
		TriggerGroup overlapping = group(Triggers.REGULAR_HIT); // already handled by weapon
		TriggerGroup distinct = group(Triggers.REGULAR_ZERO);
		Set<Triggers> covered = EnumSet.of(Triggers.REGULAR_HIT);

		// regular hit outcome: the overlapping global group is filtered out, distinct doesn't match -> nothing
		List<TriggerGroup> onHit = fire(Arrays.asList(overlapping, distinct), false, true, false, false, false, false, false, covered);
		assertTrue(onHit.isEmpty());

		// zero outcome: distinct (REGULAR_ZERO) survives the filter and matches
		List<TriggerGroup> onZero = fire(Arrays.asList(overlapping, distinct), false, false, true, false, false, false, false, covered);
		assertEquals(Collections.singletonList(distinct), onZero);
	}

	@Test
	public void weaponCoveredDoesNotFilterEmptyTriggerlessGroups()
	{
		// An "ALL" global group has triggers; covered set should not remove it since ALL isn't covered.
		TriggerGroup all = group(Triggers.ALL);
		Set<Triggers> covered = EnumSet.of(Triggers.REGULAR_HIT);
		List<TriggerGroup> result = fire(Collections.singletonList(all), false, true, false, false, false, false, false, covered);
		assertEquals(Collections.singletonList(all), result);
	}

	@Test
	public void suppressMaxBlocksMaxGroupsButLeavesHitGroups()
	{
		TriggerGroup max = group(Triggers.REGULAR_MAX);
		// all-max small hit gets suppressed
		assertTrue(fire(Collections.singletonList(max), false, true, false, true, true, false, false, NO_COVER).isEmpty());
		// without suppression it fires
		assertEquals(Collections.singletonList(max),
			fire(Collections.singletonList(max), false, true, false, true, false, false, false, NO_COVER));
	}
}
