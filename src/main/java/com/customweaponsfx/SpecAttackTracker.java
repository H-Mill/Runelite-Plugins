package com.customweaponsfx;

/**
 * Detects special-attack fires (a drop in spec energy) and remembers, for a short window, which weapon
 * fired the spec so a landing hitsplat can be flagged as a spec hit.
 *
 * <p>The window is keyed on the firing weapon's item id and expires once more than {@link #TIMEOUT_TICKS}
 * ticks elapse so a spec that never produces a hitsplat (missed, out of range) can't taint a later
 * normal attack.
 * Mirrors the other trackers ({@link AttackWeaponTracker}, {@link ProjectileWeaponTracker}): pure
 * state, no game-API or weapon-config knowledge — the caller supplies the spec energy, tick, and the
 * resolved weapon id.
 */
class SpecAttackTracker
{
	/** Drop a pending spec window that never produced a hitsplat once more than this many ticks elapse. */
	private static final int TIMEOUT_TICKS = 10;

	private int lastSpecPct = -1;
	private int pendingItemId = -1;
	private int pendingTick = -1;

	/** Seeds the baseline spec energy so the first drop is detected correctly (call on login). */
	void init(int currentSpec)
	{
		lastSpecPct = currentSpec;
	}

	/**
	 * Advances one game tick: reports whether the spec energy just dropped (a spec was fired) and
	 * expires a stale pending window.
	 *
	 * @return {@code true} if a special attack fired this tick; the caller should then resolve the
	 *         firing weapon and call {@link #arm(int, int)}.
	 */
	boolean onTick(int nowSpec, int nowTick)
	{
		boolean fired = lastSpecPct >= 0 && nowSpec < lastSpecPct;
		lastSpecPct = nowSpec;
		if (pendingItemId >= 0 && nowTick - pendingTick > TIMEOUT_TICKS)
			clearWindow();
		return fired;
	}

	/** Opens the spec window for {@code weaponId} at {@code tick}. */
	void arm(int weaponId, int tick)
	{
		pendingItemId = weaponId;
		pendingTick = tick;
	}

	/** Whether a spec window is currently open (i.e. a landing hit should count as a spec hit). */
	boolean wasSpec()
	{
		return pendingItemId >= 0;
	}

	/** Closes the spec window once its hitsplat has been processed. */
	void clearWindow()
	{
		pendingItemId = -1;
		pendingTick = -1;
	}

	void reset()
	{
		lastSpecPct = -1;
		clearWindow();
	}
}
