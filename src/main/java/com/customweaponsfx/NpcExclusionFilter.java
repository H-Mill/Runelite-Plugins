package com.customweaponsfx;

import java.util.Collections;
import java.util.Set;
import net.runelite.api.NPC;

/**
 * Decides which NPC hitsplats are ignored by the plugin.
 *
 * <p>Combines a built-in id blocklist (NPCs whose hitsplats aren't "real" attacks — clones, summoned
 * helpers, multi-bodied bosses, etc.) with user-configured id and name exclusions entered in the panel.
 * The user sets are persisted through {@link CustomWeaponSfxConfigStore} and swapped atomically so the
 * client thread always reads a consistent, immutable set.
 */
class NpcExclusionFilter
{
	/**
	 * NPC IDs whose hitsplats never trigger SFX. Add IDs here to exclude more NPCs — e.g. ones whose
	 * hitsplats aren't "real" attacks (clones, summoned helpers, multi-bodied bosses, etc.).
	 */
	private static final Set<Integer> EXCLUDED_NPC_IDS = Set.of(
		11706,
		11707);

	private final CustomWeaponSfxConfigStore store;

	/** User-configured NPC id exclusions, on top of {@link #EXCLUDED_NPC_IDS}. Swapped atomically. */
	private volatile Set<Integer> userIds = Collections.emptySet();

	/** User-configured NPC name exclusions (lowercased, trimmed) matched against the target's name. */
	private volatile Set<String> userNames = Collections.emptySet();

	NpcExclusionFilter(CustomWeaponSfxConfigStore store)
	{
		this.store = store;
	}

	/** Loads the user id/name exclusions from config. */
	void load()
	{
		userIds = Collections.unmodifiableSet(
			CustomWeaponSfxConfigStore.parseNpcIds(store.getExcludedNpcIdsRaw()));
		userNames = Collections.unmodifiableSet(
			CustomWeaponSfxConfigStore.parseNpcNames(store.getExcludedNpcNamesRaw()));
	}

	boolean isExcluded(NPC npc)
	{
		if (EXCLUDED_NPC_IDS.contains(npc.getId()) || userIds.contains(npc.getId()))
			return true;
		String name = npc.getName();
		return name != null && userNames.contains(name.trim().toLowerCase());
	}

	/** Persists and applies a raw, user-entered comma-separated NPC id string. */
	void setIds(String raw)
	{
		store.setExcludedNpcIds(raw);
		userIds = Collections.unmodifiableSet(CustomWeaponSfxConfigStore.parseNpcIds(raw));
	}

	/** Persists and applies a raw, user-entered comma-separated NPC name string. */
	void setNames(String raw)
	{
		store.setExcludedNpcNames(raw);
		userNames = Collections.unmodifiableSet(CustomWeaponSfxConfigStore.parseNpcNames(raw));
	}

	/** Drops the in-memory user exclusions (config is wiped separately via the store's reset). */
	void clear()
	{
		userIds = Collections.emptySet();
		userNames = Collections.emptySet();
	}
}
