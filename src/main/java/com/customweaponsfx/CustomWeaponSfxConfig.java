package com.customweaponsfx;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.Range;

@ConfigGroup(CustomWeaponSfxPlugin.CONFIG_GROUP)
public interface CustomWeaponSfxConfig extends Config
{
	String SIDE_PANEL_PRIORITY = "sidePanelPriority";

	@ConfigItem(
		keyName = SIDE_PANEL_PRIORITY,
		name = "Side Panel Priority",
		description = "Panel icon priority, Lower # = higher pos, Higher # = lower pos"
	)
	@Range(min = Integer.MIN_VALUE)
	default int sidePanelPriority() { return 1; }
}
