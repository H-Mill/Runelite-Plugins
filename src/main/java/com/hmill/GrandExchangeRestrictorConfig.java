package com.hmill;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

@ConfigGroup("grand-exchange-restrictor")
public interface GrandExchangeRestrictorConfig extends Config
{
	@ConfigItem(
			keyName = "restrictIronmen",
			name = "Restrict Ironmen Access",
			description = "Restrict Ironmen accounts from accessing the Grand Exchange"
	)
	default boolean restrictIronmen()
	{
		return true;
	}

	@ConfigItem(
			keyName = "restrictMains",
			name = "Restrict Main Access",
			description = "Restrict main accounts from accessing the Grand Exchange"
	)
	default boolean restrictMains()
	{
		return true;
	}
}
