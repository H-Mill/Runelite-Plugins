package com.customweaponsfx;

import java.util.EnumMap;

/**
 * Holds the current value of every {@link SfxOption}, backed by {@link CustomWeaponSfxConfigStore}.
 *
 * <p>Keeps the plugin free of generic option plumbing: adding an option is still a one-line change in
 * {@link SfxOption}, and this class loads, reads, writes, and resets all of them uniformly.
 */
class SfxOptions
{
	private final CustomWeaponSfxConfigStore store;
	private final EnumMap<SfxOption, Boolean> values = new EnumMap<>(SfxOption.class);

	SfxOptions(CustomWeaponSfxConfigStore store)
	{
		this.store = store;
	}

	/** Loads every option's value from config (falling back to its default). */
	void load()
	{
		for (SfxOption option : SfxOption.values())
			values.put(option, store.getBool(option.configKey(), option.defaultValue()));
	}

	boolean get(SfxOption option)
	{
		return values.getOrDefault(option, option.defaultValue());
	}

	/** Sets and persists a single option. */
	void set(SfxOption option, boolean value)
	{
		values.put(option, value);
		store.setBool(option.configKey(), value);
	}

	/** Restores every option to its default and persists the defaults. */
	void reset()
	{
		for (SfxOption option : SfxOption.values())
		{
			values.put(option, option.defaultValue());
			store.setBool(option.configKey(), option.defaultValue());
		}
	}

	void clear()
	{
		values.clear();
	}
}
