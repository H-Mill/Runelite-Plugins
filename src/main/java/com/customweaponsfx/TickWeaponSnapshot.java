package com.customweaponsfx;

/**
 * Remembers which weapon a projectile fired on the current game tick was launched with.
 *
 * <p>{@code ProjectileMoved} is dispatched in the render phase, <em>after</em> that tick's
 * {@code GameTick} and after the tick's equipment changes have already been applied — so live
 * equipment (or a snapshot taken during the same {@code GameTick}) already reflects a same-tick
 * weapon swap. But you can't equip a weapon and attack with it on the same tick, so a projectile
 * fired this tick was launched by the weapon held at the <em>start</em> of the tick, i.e. the weapon
 * as of the <em>previous</em> game tick.
 *
 * <p>This holds a one-tick-delayed snapshot: {@link #onGameTick(int)} is called every tick with the
 * currently equipped weapon, and {@link #launchWeapon()} returns the weapon to attribute a projectile
 * fired this tick to.
 */
class TickWeaponSnapshot
{
	static final int NONE = -1;

	private int thisTick = NONE;
	private int prevTick = NONE;

	/** Seed both snapshots when tracking begins (plugin start), so a projectile fired before the first
	 *  game tick is still attributed to the equipped weapon rather than {@link #NONE}. */
	void init(int equippedWeaponId)
	{
		thisTick = prevTick = equippedWeaponId;
	}

	/** Advance the snapshot once per game tick with the currently equipped weapon. */
	void onGameTick(int equippedWeaponId)
	{
		prevTick = thisTick;
		thisTick = equippedWeaponId;
	}

	/**
	 * The weapon a projectile fired on the current tick was launched with — the weapon held at the
	 * start of this tick, unaffected by a swap performed later in the same tick.
	 */
	int launchWeapon()
	{
		return prevTick;
	}

	void reset()
	{
		thisTick = prevTick = NONE;
	}
}
