package com.customweaponsfx;

/** Immutable boolean summary of a {@link PendingAttack}'s hitsplats, consumed by trigger evaluation. */
final class AttackOutcome
{
	final boolean wasSpec;
	final boolean anyHit;
	final boolean allZero;
	final boolean allMax;
	final int     maxAmount;
	final boolean isKill;
	/** Each hitsplat's damage this attack; amount-based triggers sum these to compare the total against a threshold. */
	final int[]   amounts;

	AttackOutcome(boolean wasSpec, boolean anyHit, boolean allZero, boolean allMax, int maxAmount, boolean isKill, int[] amounts)
	{
		this.wasSpec   = wasSpec;
		this.anyHit    = anyHit;
		this.allZero   = allZero;
		this.allMax    = allMax;
		this.maxAmount = maxAmount;
		this.isKill    = isKill;
		this.amounts   = amounts;
	}
}
