package com.customweaponsfx;

/**
 * Tracks which weapon the local player attacked with, so a hitsplat that lands
 * after the swing (ranged/magic projectile travel) is attributed to the weapon
 * actually used — even if the player has since swapped or unequipped it.
 *
 * <p>The weapon is captured at attack-time (the attack animation) rather than at
 * hitsplat-time. It is captured unconditionally, including {@link #NONE} for an
 * unarmed attack, so a stale weapon is never reused after the weapon is unequipped.
 */
class AttackWeaponTracker
{
	static final int NONE = -1;

	private int lastAttackWeaponId = NONE;

	/**
	 * Record the weapon equipped at the moment of an attack animation. Pass
	 * {@link #NONE} (or any negative value) when unarmed, which clears any
	 * previously captured weapon.
	 */
	void onAttack(int equippedWeaponId)
	{
		lastAttackWeaponId = equippedWeaponId;
	}

	/**
	 * Resolve the weapon a landing hitsplat should be attributed to, falling back
	 * to the currently equipped weapon when no attack weapon was captured.
	 */
	int resolveWeaponId(int currentEquippedWeaponId)
	{
		return lastAttackWeaponId >= 0 ? lastAttackWeaponId : currentEquippedWeaponId;
	}

	void reset()
	{
		lastAttackWeaponId = NONE;
	}
}
