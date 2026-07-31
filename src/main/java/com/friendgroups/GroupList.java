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

import net.runelite.api.Client;
import net.runelite.api.Nameable;
import net.runelite.api.NameableContainer;
import net.runelite.api.ScriptID;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.VarClientID;

/**
 * The two social lists this plugin groups. Everything that differs between them - the config keys a
 * {@link GroupStore} persists under, the widgets and cs2 script that build the in-game list, the
 * container the names come from, and which config setting controls the in-game marker - is captured
 * here so the store, the {@link ListReorderer} and the plugin's handlers can stay list-agnostic and
 * simply run one instance per value.
 *
 * <p>The ignore list is the friends list minus its online/world concept: it has no
 * Recent/World sort buttons, {@link #offlineDisplay} is always {@link OfflineDisplay#IN_GROUP}, and
 * its rows carry no world.
 */
enum GroupList
{
	FRIENDS(
		"Friends",
		InterfaceID.FRIENDS,
		InterfaceID.Friends.LIST,
		InterfaceID.Friends.SCROLLBAR,
		ScriptID.FRIENDS_UPDATE,
		new int[]{
			InterfaceID.Friends.LIST_CONTAINER,
			InterfaceID.Friends.SORT_NAME,
			InterfaceID.Friends.SORT_RECENT,
			InterfaceID.Friends.SORT_WORLD,
			InterfaceID.Friends.SORT_LEGACY,
			InterfaceID.Friends.LIST,
			InterfaceID.Friends.SCROLLBAR,
			InterfaceID.Friends.LOADING,
			InterfaceID.Friends.TOOLTIP,
		},
		VarClientID.FRIENDS_SORT,
		"Message",
		"groups",
		"ungroupedOrder",
		"ungroupedCollapsed",
		"offlineCollapsed",
		"groupsSeeded",
		true)
	{
		@Override
		InGameMarker marker(FriendGroupsConfig config)
		{
			return config.inGameMarker();
		}

		@Override
		OfflineDisplay offlineDisplay(FriendGroupsConfig config)
		{
			return config.offlineDisplay();
		}

		@Override
		NameableContainer<? extends Nameable> container(Client client)
		{
			return client.getFriendContainer();
		}
	},

	IGNORE(
		"Ignore",
		InterfaceID.IGNORE,
		InterfaceID.Ignore.LIST,
		InterfaceID.Ignore.SCROLLBAR,
		ScriptID.IGNORE_UPDATE,
		new int[]{
			InterfaceID.Ignore.LIST_CONTAINER,
			InterfaceID.Ignore.SORT_NAME,
			InterfaceID.Ignore.SORT_LEGACY,
			InterfaceID.Ignore.LIST,
			InterfaceID.Ignore.SCROLLBAR,
			InterfaceID.Ignore.LOADING,
			InterfaceID.Ignore.TOOLTIP,
		},
		VarClientID.IGNORE_SORT,
		"Delete",
		"ignoreGroups",
		"ignoreUngroupedOrder",
		"ignoreUngroupedCollapsed",
		"ignoreOfflineCollapsed",
		"ignoreGroupsSeeded",
		false)
	{
		@Override
		InGameMarker marker(FriendGroupsConfig config)
		{
			return config.ignoreInGameMarker();
		}

		@Override
		OfflineDisplay offlineDisplay(FriendGroupsConfig config)
		{
			return OfflineDisplay.IN_GROUP;
		}

		@Override
		NameableContainer<? extends Nameable> container(Client client)
		{
			return client.getIgnoreContainer();
		}
	};

	/** Human label, used for the side-panel tab. */
	final String label;
	/** The interface group id (e.g. {@link InterfaceID#FRIENDS}), for widget-load and menu routing. */
	final int interfaceGroup;
	/** The row-holding list widget, whose dynamic children are the rows we regroup. */
	final int listWidget;
	/** The scrollbar widget, updated after the content height changes. */
	final int scrollbar;
	/** The cs2 script that (re)builds the list; {@link ScriptID#FRIENDS_UPDATE} / IGNORE_UPDATE. */
	final int updateScript;
	/** Arguments (after the script id) passed to {@link #updateScript} to force a clean rebuild. */
	final int[] rebuildArgs;
	/** The VarClient the Name/Recent/World/Legacy sort buttons write, watched to re-apply after a sort. */
	final int sortVar;
	/** The right-click option present on a real row, used to attach the "Assign group" submenu. */
	final String anchorOption;
	/**
	 * Config keys this list's {@link GroupStore} persists under (all within the plugin's config group,
	 * and all under the active RuneScape profile, so groups are per character).
	 */
	final String groupsKey;
	final String ungroupedOrderKey;
	final String ungroupedCollapsedKey;
	/** Collapse state of the "Offline" section; unused by the ignore list, which never shows one. */
	final String offlineCollapsedKey;
	/** Marks a character as already seeded from the pre-per-character groups; see {@link GroupStore}. */
	final String seededKey;
	/** Whether rows carry an online world; false for the ignore list, which has no online status. */
	final boolean tracksOnline;

	GroupList(String label, int interfaceGroup, int listWidget, int scrollbar, int updateScript,
		int[] rebuildArgs, int sortVar, String anchorOption, String groupsKey, String ungroupedOrderKey,
		String ungroupedCollapsedKey, String offlineCollapsedKey, String seededKey, boolean tracksOnline)
	{
		this.label = label;
		this.interfaceGroup = interfaceGroup;
		this.listWidget = listWidget;
		this.scrollbar = scrollbar;
		this.updateScript = updateScript;
		this.rebuildArgs = rebuildArgs;
		this.sortVar = sortVar;
		this.anchorOption = anchorOption;
		this.groupsKey = groupsKey;
		this.ungroupedOrderKey = ungroupedOrderKey;
		this.ungroupedCollapsedKey = ungroupedCollapsedKey;
		this.offlineCollapsedKey = offlineCollapsedKey;
		this.seededKey = seededKey;
		this.tracksOnline = tracksOnline;
	}

	/** The in-game marker mode configured for this list (friends and ignore have separate controls). */
	abstract InGameMarker marker(FriendGroupsConfig config);

	/**
	 * Where offline members go in-game; always {@link OfflineDisplay#IN_GROUP} for the ignore list,
	 * whose members have no online status.
	 */
	abstract OfflineDisplay offlineDisplay(FriendGroupsConfig config);

	/** This list's name container. */
	abstract NameableContainer<? extends Nameable> container(Client client);

	/** The full argument array for {@link Client#runScript(Object...)}: the script id then {@link #rebuildArgs}. */
	Object[] rebuildScript()
	{
		final Object[] args = new Object[rebuildArgs.length + 1];
		args[0] = updateScript;
		for (int i = 0; i < rebuildArgs.length; i++)
		{
			args[i + 1] = rebuildArgs[i];
		}
		return args;
	}
}
