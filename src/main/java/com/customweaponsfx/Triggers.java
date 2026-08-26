package com.customweaponsfx;

import lombok.Getter;

@Getter
public enum Triggers
{
	REGULAR_ZERO("Regular attack zero"),
	REGULAR_HIT("Regular attack hit"),
	REGULAR_MAX("Regular attack max"),
	REGULAR_AMOUNT("Regular attack amount", false, true),
	SPECIAL_ZERO("Special attack zero"),
	SPECIAL_HIT("Special attack hit"),
	SPECIAL_MAX("Special attack max"),
	SPECIAL_AMOUNT("Special attack amount", true, true),
	ALL("All attacks"),
	KILL("Player kill"),
	PLAYER_DEATH("Player death");

	private final String name;
	/** True for triggers that only fire on a special attack (used to gate amount triggers by attack type). */
	private final boolean special;
	/** True for triggers that carry a per-group {@link AmountCondition} comparing against the hit damage. */
	private final boolean amount;

	Triggers(String name)
	{
		this(name, false, false);
	}

	Triggers(String name, boolean special, boolean amount)
	{
		this.name = name;
		this.special = special;
		this.amount = amount;
	}

	@Override
	public String toString()
	{
		return name;
	}
}
