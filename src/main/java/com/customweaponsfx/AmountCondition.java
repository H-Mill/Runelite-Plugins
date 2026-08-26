package com.customweaponsfx;

import lombok.Getter;

/**
 * A numeric comparison ({@code >}, {@code <}, or {@code =} against a threshold) used by amount-based
 * triggers such as {@link Triggers#REGULAR_AMOUNT}. Immutable; matched against each hitsplat's damage.
 */
@Getter
class AmountCondition
{
	/** The comparison operator; its {@link #toString()} is the symbol shown in the UI combo box. */
	enum Op
	{
		GREATER(">"),
		LESS("<"),
		EQUAL("=");

		private final String symbol;

		Op(String symbol)
		{
			this.symbol = symbol;
		}

		@Override
		public String toString()
		{
			return symbol;
		}
	}

	private final Op op;
	private final int value;

	AmountCondition(Op op, int value)
	{
		this.op = op;
		this.value = value;
	}

	/** True if {@code amount} satisfies this comparison. */
	boolean matches(int amount)
	{
		switch (op)
		{
			case GREATER: return amount > value;
			case LESS:    return amount < value;
			case EQUAL:   return amount == value;
			default:      return false;
		}
	}

	/** Serializes to {@code "<OP>:<value>"}, e.g. {@code "EQUAL:73"}. */
	String serialize()
	{
		return op.name() + ":" + value;
	}

	/** Inverse of {@link #serialize()}; returns {@code null} for null/blank/malformed input. */
	static AmountCondition deserialize(String s)
	{
		if (s == null) return null;
		int colon = s.indexOf(':');
		if (colon <= 0) return null;
		try
		{
			Op op = Op.valueOf(s.substring(0, colon).trim());
			int value = Integer.parseInt(s.substring(colon + 1).trim());
			return new AmountCondition(op, value);
		}
		catch (IllegalArgumentException e)
		{
			return null;
		}
	}
}
