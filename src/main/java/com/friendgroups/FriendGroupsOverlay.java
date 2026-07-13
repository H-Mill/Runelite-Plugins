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

import java.awt.Dimension;
import java.awt.Graphics2D;
import java.util.List;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.tooltip.Tooltip;
import net.runelite.client.ui.overlay.tooltip.TooltipManager;
import net.runelite.client.util.ColorUtil;

/**
 * Names the groups of the friend currently hovered in the in-game friends list.
 * The row itself can only carry colored dots, so the names live here.
 */
class FriendGroupsOverlay extends Overlay
{
	private final Client client;
	private final FriendGroupsPlugin plugin;
	private final FriendGroupManager manager;
	private final FriendGroupsConfig config;
	private final TooltipManager tooltipManager;

	@Inject
	private FriendGroupsOverlay(
		Client client,
		FriendGroupsPlugin plugin,
		FriendGroupManager manager,
		FriendGroupsConfig config,
		TooltipManager tooltipManager)
	{
		this.client = client;
		this.plugin = plugin;
		this.manager = manager;
		this.config = config;
		this.tooltipManager = tooltipManager;
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		if (!config.showTooltip() || client.isMenuOpen())
		{
			return null;
		}

		final String hovered = plugin.getHoveredFriend();
		if (hovered == null)
		{
			return null;
		}

		final List<FriendGroup> groups = manager.groupsFor(hovered);
		if (groups.isEmpty())
		{
			return null;
		}

		final StringBuilder tooltip = new StringBuilder();
		for (FriendGroup group : groups)
		{
			if (tooltip.length() > 0)
			{
				tooltip.append(", ");
			}
			tooltip.append(ColorUtil.wrapWithColorTag(group.getName(), group.getAwtColor()));
		}

		tooltipManager.add(new Tooltip(tooltip.toString()));
		return null;
	}
}
