/*
 * Copyright (c) 2026, H-Mill <huntermill8@gmail.com>
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *     list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *     this list of conditions and the following disclaimer in the documentation
 *     and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE
 * FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
 * OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.friendgroups;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.config.Range;

@ConfigGroup(FriendGroupsConfig.GROUP)
public interface FriendGroupsConfig extends Config
{
	String GROUP = "friend-groups";

	String SIDE_PANEL_PRIORITY = "sidePanelPriority";

	String HIDE_SIDE_PANEL = "hideSidePanel";

	@ConfigSection(
			name = "Friends list",
			description = "How your friend groups are shown in the in-game friends list.",
			position = 10
	)
	String FRIENDS_SECTION = "friendsList";

	@ConfigSection(
			name = "Ignore list",
			description = "How your ignore groups are shown in the in-game ignore list.",
			position = 20
	)
	String IGNORE_SECTION = "ignoreList";

	@ConfigItem(
			position = 1,
			keyName = HIDE_SIDE_PANEL,
			name = "Hide side panel icon",
			description = "Remove the Friend Groups icon from the side panel. Groups can still be managed "
			+ "from the in-game friends and ignore list right-click menus."
	)
	default boolean hideSidePanel()
	{
		return false;
	}

	@ConfigItem(
			position = 2,
			keyName = SIDE_PANEL_PRIORITY,
			name = "Side Panel Priority",
			description = "Panel icon priority, Lower # = higher pos, Higher # = lower pos"
	)
	@Range(min = Integer.MIN_VALUE)
	default int sidePanelPriority()
	{
		return 0;
	}

	@ConfigItem(
			position = 3,
			keyName = "showTooltip",
			name = "Hover tooltip",
			description = "Show a friend's or ignored player's group names when hovering them in-game"
	)
	default boolean showTooltip()
	{
		return true;
	}

	@ConfigItem(
			position = 1,
			keyName = "inGameMarker",
			name = "In-game marker",
			description = "How your friend groups are shown in the in-game friends list.<br>"
			+ "'Colored dots' adds a small color swatch per group next to the name.<br>"
			+ "'Grouped list' clusters the list under a colored header per group, in your group order "
			+ "(experimental: it overrides the game's Name / Recent / World sorting, and a friend in "
			+ "several groups appears under each of their groups).",
			section = FRIENDS_SECTION
	)
	default InGameMarker inGameMarker()
	{
		return InGameMarker.GROUPED;
	}

	@ConfigItem(
			position = 2,
			keyName = "offlineDisplay",
			name = "Offline friends",
			description = "Where offline friends go in the in-game friends list.<br>"
			+ "'In their group' leaves them where the game puts them.<br>"
			+ "'Offline group' pulls them out of their groups into one section at the bottom "
			+ "(collapsible, in grouped list mode).<br>"
			+ "'Hidden' removes them, so a group whose members are all offline disappears too.",
			section = FRIENDS_SECTION
	)
	default OfflineDisplay offlineDisplay()
	{
		return OfflineDisplay.IN_GROUP;
	}

	@ConfigItem(
			position = 3,
			keyName = "hideWorldPrefix",
			name = "Hide \"World\" prefix",
			description = "In the in-game friends list, show just the world number (e.g. 369) instead of \"World 369\".",
			section = FRIENDS_SECTION
	)
	default boolean hideWorldPrefix()
	{
		return false;
	}

	@ConfigItem(
			position = 1,
			keyName = "ignoreInGameMarker",
			name = "In-game marker",
			description = "How your ignore groups are shown in the in-game ignore list.<br>"
			+ "'Colored dots' adds a small color swatch per group next to the name.<br>"
			+ "'Grouped list' clusters the list under a colored header per group, in your group order "
			+ "(experimental: it overrides the game's Name sorting, and an ignored player in several "
			+ "groups appears under each of their groups).",
			section = IGNORE_SECTION
	)
	default InGameMarker ignoreInGameMarker()
	{
		return InGameMarker.GROUPED;
	}
}
