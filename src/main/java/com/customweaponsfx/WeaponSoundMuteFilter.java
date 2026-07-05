package com.customweaponsfx;

import java.util.Collections;
import java.util.Set;

/**
 * Decides which default in-game sound effects the plugin silences.
 *
 * <p>Holds a user-configured set of sound-effect ids entered in the panel (e.g. the default weapon
 * swing/hit sounds a player wants replaced by their own SFX). The set is persisted through
 * {@link CustomWeaponSfxConfigStore} and swapped atomically so the client thread always reads a
 * consistent, immutable set. Mirrors {@link NpcExclusionFilter}.
 */
class WeaponSoundMuteFilter
{
	private final CustomWeaponSfxConfigStore store;

	/** User-configured sound-effect ids to mute. Swapped atomically. */
	private volatile Set<Integer> mutedIds = Collections.emptySet();

	WeaponSoundMuteFilter(CustomWeaponSfxConfigStore store)
	{
		this.store = store;
	}

	/** Loads the user sound-id mutes from config. */
	void load()
	{
		mutedIds = Collections.unmodifiableSet(
			CustomWeaponSfxConfigStore.parseSoundIds(store.getMutedWeaponSoundIdsRaw()));
	}

	boolean isMuted(int soundId)
	{
		return mutedIds.contains(soundId);
	}

	/** Persists and applies a raw, user-entered comma-separated sound-id string. */
	void setIds(String raw)
	{
		store.setMutedWeaponSoundIds(raw);
		mutedIds = Collections.unmodifiableSet(CustomWeaponSfxConfigStore.parseSoundIds(raw));
	}

	/** Drops the in-memory mutes (config is wiped separately via the store's reset). */
	void clear()
	{
		mutedIds = Collections.emptySet();
	}
}
