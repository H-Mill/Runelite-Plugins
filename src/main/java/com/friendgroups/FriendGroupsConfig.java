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
import net.runelite.client.config.Range;

@ConfigGroup(FriendGroupsConfig.GROUP)
public interface FriendGroupsConfig extends Config
{
	String GROUP = "friend-groups";

	String SIDE_PANEL_PRIORITY = "sidePanelPriority";

	@ConfigItem(
			position = 1,
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
			position = 2,
			keyName = "inGameMarker",
			name = "In-game marker",
			description = "How your friend groups are shown in the in-game friends list.<br>"
			+ "'Coloured dots' adds a small colour swatch per group next to the name.<br>"
			+ "'Grouped list' clusters the list under a coloured header per group, in your group order "
			+ "(experimental: it overrides the game's Name / Recent / World sorting, and a friend in "
			+ "several groups appears under each of their groups)."
	)
	default InGameMarker inGameMarker()
	{
		return InGameMarker.GROUPED;
	}

	@ConfigItem(
			position = 3,
			keyName = "showTooltip",
			name = "Hover tooltip",
			description = "Show a friend's group names when hovering them in the in-game friends list"
	)
	default boolean showTooltip()
	{
		return true;
	}

	@ConfigItem(
			position = 4,
			keyName = "hideOffline",
			name = "Hide offline friends",
			description = "Hide offline friends from the in-game friends list. A group whose members are "
			+ "all offline is hidden as a result."
	)
	default boolean hideOffline()
	{
		return false;
	}

	@ConfigItem(
			position = 5,
			keyName = "hideWorldPrefix",
			name = "Hide \"World\" prefix",
			description = "In the in-game friends list, show just the world number (e.g. 369) instead of \"World 369\"."
	)
	default boolean hideWorldPrefix()
	{
		return false;
	}
}
