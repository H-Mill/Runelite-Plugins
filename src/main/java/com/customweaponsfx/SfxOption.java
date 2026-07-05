package com.customweaponsfx;

/**
 * A user-toggleable plugin option, each carrying its config key and default value.
 *
 * <p>Adding a new boolean option is a one-line addition here — the panel renders a checkbox for it
 * and the plugin stores its value generically, so no per-option fields or handlers are needed.
 */
enum SfxOption
{
	IGNORE_SMALL_MAX("ignoreSmallMaxHits", true),
	IGNORE_RECEIVED_ZERO_PRAYER("ignoreReceivedZeroWithPrayer", true),
	IGNORE_ZERO_THRALL("ignoreZeroWhileThrallActive", true),
	GLOBAL_ENABLED(CustomWeaponSfxPanel.GLOBAL_WEAPON_GROUPS_PREFIX + "_enabled", true),
	RECEIVED_ENABLED(CustomWeaponSfxPanel.RECEIVED_GROUPS_PREFIX + "_enabled", true);

	private final String configKey;
	private final boolean defaultValue;

	SfxOption(String configKey, boolean defaultValue)
	{
		this.configKey = configKey;
		this.defaultValue = defaultValue;
	}

	String configKey()
	{
		return configKey;
	}

	boolean defaultValue()
	{
		return defaultValue;
	}
}
