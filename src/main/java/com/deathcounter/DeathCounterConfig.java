package com.deathcounter;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

@ConfigGroup(DeathCounterConfig.GROUP)
public interface DeathCounterConfig extends Config
{
	String GROUP = "death-counter";
	String DEATHS_KEY = "deaths";

	@ConfigItem(
		keyName = "counterLabel",
		name = "Counter label",
		description = "Text shown in front of the death count",
		position = 0
	)
	default String counterLabel()
	{
		return "Deaths";
	}

	@ConfigItem(
		keyName = "resetOnClientOpen",
		name = "Reset on client open",
		description = "Reset the death counter each time the client starts up",
		position = 1
	)
	default boolean resetOnClientOpen()
	{
		return false;
	}

	@ConfigItem(
		keyName = "resetOnLogin",
		name = "Reset on login",
		description = "Reset the death counter each time you log in. On client startup this is skipped when 'Reset on client open' has already reset the counter, so it is not reset twice.",
		position = 2
	)
	default boolean resetOnLogin()
	{
		return false;
	}
}
