package com.deathcounter;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

@ConfigGroup(DeathCounterConfig.GROUP)
public interface DeathCounterConfig extends Config
{
	String GROUP = "death-counter";
	String DEATHS_KEY = "deaths";
	String ALL_TIME_DEATHS_KEY = "allTimeDeaths";

	@ConfigItem(
		keyName = "counterLabel",
		name = "Counter label",
		description = "Text shown in front of the session death count",
		position = 0
	)
	default String counterLabel()
	{
		return "Deaths";
	}

	@ConfigItem(
		keyName = "showAllTimeDeaths",
		name = "Show all-time deaths",
		description = "Show a second line with the all-time death total, which is never reset by the per-session resets",
		position = 1
	)
	default boolean showAllTimeDeaths()
	{
		return true;
	}

	@ConfigItem(
		keyName = "allTimeLabel",
		name = "All-time label",
		description = "Text shown in front of the all-time death count",
		position = 2
	)
	default String allTimeLabel()
	{
		return "All-time deaths";
	}

	@ConfigItem(
		keyName = "resetOnClientOpen",
		name = "Reset on client open",
		description = "Reset the session death counter each time the client starts up",
		position = 3
	)
	default boolean resetOnClientOpen()
	{
		return false;
	}

	@ConfigItem(
		keyName = "resetOnLogin",
		name = "Reset on login",
		description = "Reset the session death counter each time you log in. On client startup this is skipped when 'Reset on client open' has already reset the counter, so it is not reset twice.",
		position = 4
	)
	default boolean resetOnLogin()
	{
		return false;
	}
}
