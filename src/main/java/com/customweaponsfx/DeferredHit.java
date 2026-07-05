package com.customweaponsfx;

import java.util.List;
import net.runelite.api.Actor;

/** A single hitsplat buffered for end-of-tick aggregation by {@link HitAggregator}. */
class DeferredHit
{
	final int                key;
	final List<TriggerGroup> groups;
	final boolean            dontOverrideGlobal;
	final boolean            wasSpec;
	final int                amount;
	final boolean            isMax;
	final int                tick;
	final Actor              actor;

	DeferredHit(int key, List<TriggerGroup> groups, boolean dontOverrideGlobal, boolean wasSpec, int amount, boolean isMax, int tick, Actor actor)
	{
		this.key                = key;
		this.groups             = groups;
		this.dontOverrideGlobal = dontOverrideGlobal;
		this.wasSpec            = wasSpec;
		this.amount             = amount;
		this.isMax              = isMax;
		this.tick               = tick;
		this.actor              = actor;
	}
}
