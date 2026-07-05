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
}
