package com.customweaponsfx;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import lombok.Getter;

/**
 * Collects the hitsplats of one multi-hit attack (a weapon with an {@link AttackProfile}) across the
 * consecutive ticks they land on, then presents them as a single {@link AttackOutcome} so triggers judge the
 * whole attack at once. Aggregation completes as soon as the profile's expected hit count arrives (so a
 * same-tick pair fires immediately), falling back to the profile's tick span when fewer hits land (e.g. the
 * target died mid-attack).
 *
 * <p>Pure state, mirroring the other trackers: the caller opens it with {@link #begin} on the attack's first
 * hitsplat, feeds each tick's hits with {@link #add}, and once {@link #isComplete} reports it done, reads the
 * combined {@link #buildOutcome} and {@link #reset}s.
 */
class AttackAggregator
{
	private boolean open;
	@Getter private int weaponId;
	private int firstTick;
	private int hitCount;
	private int tickSpan;
	private Predicate<List<Boolean>> maxRule;

	@Getter private List<TriggerGroup> groups;
	@Getter private boolean dontOverrideGlobal;

	private final List<Integer> amounts = new ArrayList<>();
	private final List<Boolean> isMaxList = new ArrayList<>();
	private boolean wasSpec;
	private boolean isKill;

	/** True while an attack from {@code weaponId} is being aggregated — its later hits should feed {@link #add}. */
	boolean isActiveFor(int weaponId)
	{
		return open && this.weaponId == weaponId;
	}

	/** Opens aggregation for an attack by {@code weaponId} on {@code tick}, using {@code profile}'s rules. */
	void begin(int weaponId, int tick, AttackProfile profile, List<TriggerGroup> groups, boolean dontOverrideGlobal)
	{
		open = true;
		this.weaponId = weaponId;
		this.firstTick = tick;
		this.hitCount = profile.getHitCount();
		this.tickSpan = profile.getTickSpan();
		this.maxRule = profile.getMaxRule();
		this.groups = groups;
		this.dontOverrideGlobal = dontOverrideGlobal;
		amounts.clear();
		isMaxList.clear();
		wasSpec = false;
		isKill = false;
	}

	/**
	 * Folds one tick's hitsplats into the open attack. {@code wasSpec}/{@code isKill} are OR-ed across the
	 * attack so a special is recognised even if a tail hitsplat lost its spec flag.
	 */
	void add(List<Integer> amounts, List<Boolean> isMaxList, boolean wasSpec, boolean isKill)
	{
		this.amounts.addAll(amounts);
		this.isMaxList.addAll(isMaxList);
		this.wasSpec |= wasSpec;
		this.isKill |= isKill;
	}

	/**
	 * True once every expected hitsplat has arrived, or the attack's whole tick span has elapsed (fallback for
	 * when fewer hits land than expected), so its combined outcome can be fired.
	 */
	boolean isComplete(int currentTick)
	{
		return open && (amounts.size() >= hitCount || currentTick >= firstTick + tickSpan - 1);
	}

	/** The combined outcome of every hitsplat gathered, with max decided by the profile's {@code maxRule}. */
	AttackOutcome buildOutcome()
	{
		boolean allMax  = !isMaxList.isEmpty() && maxRule.test(isMaxList);
		boolean anyHit  = amounts.stream().anyMatch(a -> a > 0);
		boolean allZero = amounts.stream().allMatch(a -> a == 0);
		int[] amountsArr = amounts.stream().mapToInt(Integer::intValue).toArray();
		int maxAmount   = amounts.stream().mapToInt(Integer::intValue).max().orElse(0);
		return new AttackOutcome(wasSpec, anyHit, allZero, allMax, maxAmount, isKill, amountsArr);
	}

	void reset()
	{
		open = false;
		weaponId = 0;
		firstTick = 0;
		hitCount = 0;
		tickSpan = 0;
		maxRule = null;
		groups = null;
		dontOverrideGlobal = false;
		amounts.clear();
		isMaxList.clear();
		wasSpec = false;
		isKill = false;
	}
}
