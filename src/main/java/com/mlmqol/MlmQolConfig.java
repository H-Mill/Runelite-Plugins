package com.mlmqol;

import java.awt.Color;
import net.runelite.client.config.Alpha;
import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.config.Range;

@ConfigGroup(MlmQolPlugin.CONFIG_GROUP)
public interface MlmQolConfig extends Config
{
	String BLOCK_MINING = "blockMining";
	String BLOCK_AT_COUNT = "blockAtCount";
	String WARN_IN_CHAT = "warnInChat";
	String BLOCK_DEPOSIT_WHEN_BLOCKED = "blockDepositWhenBlocked";
	String HIGHLIGHT_BROKEN_STRUTS = "highlightBrokenStruts";
	String NAME_BABY_MOLE = "nameBabyMole";
	String BABY_MOLE_NAME = "babyMoleName";
	String BABY_MOLE_NAME_COLOR = "babyMoleNameColor";
	String STOPPED_HOPPER_COLOR = "stoppedHopperColor";
	String ORE_IN_TRANSIT_COLOR = "oreInTransitColor";

	@ConfigSection(
		name = "Sack",
		description = "Stop mining once the sack has no room left",
		position = 0
	)
	String SACK_SECTION = "sack";

	@ConfigSection(
		name = "Hopper",
		description = "Warn when the machinery has stopped",
		position = 1
	)
	String HOPPER_SECTION = "hopper";

	@ConfigSection(
		name = "Baby mole",
		description = "Name the baby mole while you're in the mine",
		position = 2
	)
	String BABY_MOLE_SECTION = "babyMole";

	@ConfigItem(
		keyName = BLOCK_MINING,
		name = "Block mining when full",
		description = "Removes the Mine option from ore veins once the sack reaches the block count",
		position = 1,
		section = SACK_SECTION
	)
	default boolean blockMining()
	{
		return true;
	}

	@ConfigItem(
		keyName = BLOCK_AT_COUNT,
		name = "Block at",
		description = "Pay-dirt count to stop mining at. Defaults to your sack capacity, and follows the "
			+ "capacity upgrade automatically unless you set your own value. Never exceeds the real capacity.",
		position = 2,
		section = SACK_SECTION
	)
	@Range(min = 1, max = SackTracker.SACK_LARGE_SIZE)
	default int blockAtCount()
	{
		return SackTracker.SACK_SIZE;
	}

	@ConfigItem(
		keyName = WARN_IN_CHAT,
		name = "Warn in chat",
		description = "Sends a chat message when the block count is reached and when the sack has room again",
		position = 3,
		section = SACK_SECTION
	)
	default boolean warnInChat()
	{
		return true;
	}

	@ConfigItem(
		keyName = BLOCK_DEPOSIT_WHEN_BLOCKED,
		name = "Block deposit when blocked",
		description = "Removes the Deposit option from the hopper while it is flagged and the sack has no room "
			+ "left, so pay-dirt can't be fed into a belt that won't carry it",
		position = 1,
		section = HOPPER_SECTION
	)
	default boolean blockDepositWhenBlocked()
	{
		return true;
	}

	@ConfigItem(
		keyName = HIGHLIGHT_BROKEN_STRUTS,
		name = "Highlight broken struts",
		description = "Outlines any broken strut, so you can see which one needs repairing",
		position = 2,
		section = HOPPER_SECTION
	)
	default boolean highlightBrokenStruts()
	{
		return true;
	}

	@ConfigItem(
		keyName = STOPPED_HOPPER_COLOR,
		name = "Broken wheel colour",
		description = "Colour of the broken strut outline, and of the highlight while both struts are broken",
		position = 3,
		section = HOPPER_SECTION
	)
	@Alpha
	default Color stoppedHopperColor()
	{
		return new Color(255, 0, 0, 180);
	}

	@ConfigItem(
		keyName = ORE_IN_TRANSIT_COLOR,
		name = "Ore in transit colour",
		description = "Colour of the highlight while your pay-dirt is on the belt on its way to the sack",
		position = 4,
		section = HOPPER_SECTION
	)
	@Alpha
	default Color oreInTransitColor()
	{
		return new Color(255, 152, 31, 180);
	}

	@ConfigItem(
		keyName = NAME_BABY_MOLE,
		name = "Baby mole name",
		description = "Shows the name below over the baby mole. Only ever applies inside the Motherlode Mine.",
		position = 1,
		section = BABY_MOLE_SECTION
	)
	default boolean nameBabyMole()
	{
		return false;
	}

	@ConfigItem(
		keyName = BABY_MOLE_NAME,
		name = "Name",
		description = "The name to show. Leaving it empty shows nothing.",
		position = 2,
		section = BABY_MOLE_SECTION
	)
	default String babyMoleName()
	{
		return "Rufus";
	}

	@ConfigItem(
		keyName = BABY_MOLE_NAME_COLOR,
		name = "Name colour",
		description = "Colour of the name",
		position = 3,
		section = BABY_MOLE_SECTION
	)
	@Alpha
	default Color babyMoleNameColor()
	{
		return Color.WHITE;
	}
}
