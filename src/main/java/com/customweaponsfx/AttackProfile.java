package com.customweaponsfx;

import java.util.List;
import java.util.function.Predicate;
import lombok.Getter;

/**
 * Describes a weapon whose attack lands several hitsplats that may spread across consecutive ticks (e.g.
 * claws' spec, or the dark bow's two arrows), so {@link AttackAggregator} can combine them into a single
 * attack rather than judging each tick's partial. Registered per weapon item id in {@link AttackProfiles}.
 */
@Getter
class AttackProfile
{
	/** Expected number of hitsplats; aggregation completes as soon as this many arrive (fast path). */
	private final int hitCount;

	/** Max consecutive ticks the hitsplats span; a fallback completion if fewer than {@link #hitCount} land. */
	private final int tickSpan;

	/**
	 * When true only special attacks are aggregated (e.g. claws, whose regular attack is a single instant hit);
	 * when false every attack with this weapon is aggregated (e.g. the dark bow, whose regular attack also hits
	 * twice).
	 */
	private final boolean specialOnly;

	/**
	 * Given the per-hitsplat "was max" flags of the whole attack, decides whether it counts as a max hit.
	 * Defined per weapon because these attacks vary: claws paint the max colour on only one splat (so "any"),
	 * whereas the dark bow's two arrows are only a max when both are (so "all").
	 */
	private final Predicate<List<Boolean>> maxRule;

	AttackProfile(int hitCount, int tickSpan, boolean specialOnly, Predicate<List<Boolean>> maxRule)
	{
		this.hitCount = hitCount;
		this.tickSpan = tickSpan;
		this.specialOnly = specialOnly;
		this.maxRule = maxRule;
	}
}
