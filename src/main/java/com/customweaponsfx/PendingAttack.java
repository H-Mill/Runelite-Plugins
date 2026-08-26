package com.customweaponsfx;

import java.util.ArrayList;
import java.util.List;
import net.runelite.api.Actor;

/**
 * The hitsplats from a single attack (same landing tick + key), aggregated so a multi-hit attack is
 * evaluated as one outcome.
 */
class PendingAttack
{
	final List<TriggerGroup> groups;
	final boolean            dontOverrideGlobal;
	final List<Integer>      amounts   = new ArrayList<>();
	final List<Boolean>      isMaxList = new ArrayList<>();
	final List<Actor>        actors    = new ArrayList<>();
	boolean                  wasSpec   = false;
	/** The game tick these hitsplats landed on; used by {@link AttackAggregator} to span a multi-hit attack. */
	int                      tick      = 0;
	/** The aggregation key (weapon item id for outgoing hits); used to look up a weapon's {@link AttackProfile}. */
	int                      key       = 0;

	PendingAttack(List<TriggerGroup> groups)
	{
		this(groups, false);
	}

	PendingAttack(List<TriggerGroup> groups, boolean dontOverrideGlobal)
	{
		this.groups = groups;
		this.dontOverrideGlobal = dontOverrideGlobal;
	}

	/**
	 * Collapses the buffered hitsplats into the boolean outcome used by trigger evaluation. Pure
	 * given {@code isKill}, which the caller derives from {@link #actors} and live client state
	 * (health ratios) so this method stays free of RuneLite {@code Client} dependencies.
	 */
	AttackOutcome collapse(boolean isKill)
	{
		boolean allMax  = isMaxList.stream().allMatch(b -> b);
		boolean anyHit  = amounts.stream().anyMatch(a -> a > 0);
		boolean allZero = amounts.stream().allMatch(a -> a == 0);
		int[] amountsArr = amounts.stream().mapToInt(Integer::intValue).toArray();
		int maxAmount   = amounts.stream().mapToInt(Integer::intValue).max().orElse(0);
		return new AttackOutcome(wasSpec, anyHit, allZero, allMax, maxAmount, isKill, amountsArr);
	}
}
