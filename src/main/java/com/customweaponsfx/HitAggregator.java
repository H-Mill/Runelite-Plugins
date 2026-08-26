package com.customweaponsfx;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Buffers hitsplats and groups them into per-tick attacks. Hitsplats are batched per tick so a
 * multi-hit attack is evaluated together. Grouping by the tick a hit landed on means a skipped
 * {@code onGameTick} — which lets more than one tick's hits accumulate here — still evaluates every
 * tick's attack instead of discarding all but the last. Ticks are returned in ascending order.
 */
class HitAggregator
{
	private final List<DeferredHit> deferredHits = new ArrayList<>();

	void add(DeferredHit hit)
	{
		deferredHits.add(hit);
	}

	boolean isEmpty()
	{
		return deferredHits.isEmpty();
	}

	void clear()
	{
		deferredHits.clear();
	}

	/**
	 * Drains every buffered hit, returning the aggregated attacks grouped by the tick they landed
	 * on, in ascending tick order. Within a tick, hits sharing a key belong to the same attack.
	 */
	List<List<PendingAttack>> drainByTick()
	{
		Map<Integer, Map<Integer, PendingAttack>> attacksByTick = new TreeMap<>();
		for (DeferredHit h : deferredHits)
		{
			Map<Integer, PendingAttack> attacks = attacksByTick.computeIfAbsent(h.tick, k -> new HashMap<>());
			PendingAttack attack = attacks.get(h.key);
			if (attack == null)
			{
				attack = new PendingAttack(h.groups, h.dontOverrideGlobal);
				attack.wasSpec = h.wasSpec;
				attack.tick = h.tick;
				attack.key = h.key;
				attacks.put(h.key, attack);
			}
			attack.amounts.add(h.amount);
			attack.isMaxList.add(h.isMax);
			if (h.actor != null) attack.actors.add(h.actor);
		}
		deferredHits.clear();

		List<List<PendingAttack>> byTick = new ArrayList<>();
		for (Map<Integer, PendingAttack> attacks : attacksByTick.values())
			byTick.add(new ArrayList<>(attacks.values()));
		return byTick;
	}
}
