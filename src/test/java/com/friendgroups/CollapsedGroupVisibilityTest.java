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

import java.util.Arrays;
import java.util.List;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import org.junit.Before;
import org.junit.Test;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import net.runelite.api.Client;
import net.runelite.api.Friend;
import net.runelite.api.FriendContainer;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetType;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.game.chatbox.ChatboxPanelManager;
import net.runelite.client.ui.components.colorpicker.ColorPickerManager;

/**
 * Drives {@link ListReorderer#reorder()} against mocked friends-list widgets to lock in the
 * collapsed-group visibility contract. The reported bug: a friend who is in a collapsed group
 * <em>and</em> an expanded one went invisible under the expanded group, because the collapsed
 * group (processed first) hid the friend's real row and the expanded group could then only clone
 * an already-hidden source. A friend's real row is now placed at its first <em>expanded</em>
 * appearance and only hidden when no expanded group holds them, so the outcome no longer depends
 * on which section comes first.
 *
 * <p>The row widgets are stateful mocks tracking just the fields the walk mutates - {@code hidden}
 * and {@code originalY} - so a placed row reads back as visible at its new Y and a hidden row reads
 * back as hidden.
 */
public class CollapsedGroupVisibilityTest
{
	/** Row pitch when two rows are present; see {@link ListReorderer#measureRowHeight}. */
	private static final int ROW_HEIGHT = 14;

	private Client client;
	private ClientThread clientThread;
	private FriendGroupsConfig config;
	private GroupStore manager;
	private Widget list;

	@Before
	public void setUp()
	{
		client = mock(Client.class);
		clientThread = mock(ClientThread.class);
		config = mock(FriendGroupsConfig.class);
		manager = mock(GroupStore.class);

		when(client.isClientThread()).thenReturn(true);
		when(config.inGameMarker()).thenReturn(InGameMarker.GROUPED);
		when(config.offlineDisplay()).thenReturn(OfflineDisplay.IN_GROUP);
		when(manager.isUngroupedCollapsed()).thenReturn(false);
		when(manager.ungroupedPosition()).thenReturn(99);
	}

	@Test
	public void sharedFriendStaysVisibleUnderExpandedGroupWhenCollapsedGroupIsAbove()
	{
		final FriendGroup collapsed = group("PvM", true, "Alice");
		final FriendGroup expanded = group("Clan", false, "Alice", "Bob");
		when(manager.getGroups()).thenReturn(Arrays.asList(collapsed, expanded));

		final Widget alice = row("Alice", 0);
		final Widget bob = row("Bob", ROW_HEIGHT);
		reorder(Arrays.asList(alice, bob), "Alice", "Bob");

		// The shared friend's real row is placed (visible) under the expanded group, not hidden by
		// the collapsed group above it. Expanded "Clan" is the 2nd section: its header sits at slot
		// 1, so Alice lands at slot 2 and Bob at slot 3 (the collapsed header took slot 0).
		assertFalse("shared friend's row must stay visible under the expanded group", alice.isHidden());
		assertEquals(2 * ROW_HEIGHT, alice.getOriginalY());
		assertFalse(bob.isHidden());
		assertEquals(3 * ROW_HEIGHT, bob.getOriginalY());
	}

	@Test
	public void sharedFriendStaysVisibleWhenExpandedGroupIsAbove()
	{
		// The order that already worked before the fix, kept as a regression guard: expanded first,
		// then the collapsed group holding the same friend.
		final FriendGroup expanded = group("Clan", false, "Alice", "Bob");
		final FriendGroup collapsed = group("PvM", true, "Alice");
		when(manager.getGroups()).thenReturn(Arrays.asList(expanded, collapsed));

		final Widget alice = row("Alice", 0);
		final Widget bob = row("Bob", ROW_HEIGHT);
		reorder(Arrays.asList(alice, bob), "Alice", "Bob");

		// Expanded "Clan" is the 1st section (header at slot 0), so Alice is placed at slot 1.
		assertFalse(alice.isHidden());
		assertEquals(ROW_HEIGHT, alice.getOriginalY());
		assertFalse(bob.isHidden());
		assertEquals(2 * ROW_HEIGHT, bob.getOriginalY());
	}

	@Test
	public void friendOnlyInACollapsedGroupIsHidden()
	{
		final FriendGroup collapsed = group("PvM", true, "Alice");
		when(manager.getGroups()).thenReturn(Arrays.asList(collapsed));

		final Widget alice = row("Alice", 0);
		reorder(Arrays.asList(alice), "Alice");

		// No expanded group holds Alice, so her real row is hidden.
		assertTrue("a friend only in a collapsed group must be hidden", alice.isHidden());
	}

	/** Builds a {@link ListReorderer} over {@code rows} as the list's children and runs one pass. */
	private void reorder(List<Widget> rows, String... friendNames)
	{
		final Friend[] friends = new Friend[friendNames.length];
		for (int i = 0; i < friendNames.length; i++)
		{
			final Friend friend = mock(Friend.class);
			when(friend.getName()).thenReturn(friendNames[i]);
			friends[i] = friend;
		}
		final FriendContainer container = mock(FriendContainer.class);
		when(container.getMembers()).thenReturn(friends);
		when(client.getFriendContainer()).thenReturn(container);

		list = mock(Widget.class);
		when(list.getDynamicChildren()).thenReturn(rows.toArray(new Widget[0]));
		when(list.getWidth()).thenReturn(100);
		// Each injected header/clone is a throwaway mock; the walk only writes to it.
		when(list.createChild(anyInt(), anyInt())).thenAnswer(i -> mock(Widget.class));
		when(client.getWidget(InterfaceID.Friends.LIST)).thenReturn(list);

		final ListReorderer reorderer = new ListReorderer(client, clientThread, manager, config,
			mock(ChatboxPanelManager.class), mock(ColorPickerManager.class), GroupList.FRIENDS);
		reorderer.reorder();
	}

	private static FriendGroup group(String name, boolean collapsed, String... members)
	{
		final FriendGroup group = new FriendGroup(name, 0xFFFFFF);
		group.setCollapsed(collapsed);
		group.getMembers().addAll(Arrays.asList(members));
		return group;
	}

	/**
	 * A stateful stand-in for one friend row: a single text widget tracking the {@code hidden} and
	 * {@code originalY} the reorder walk reads back after mutating.
	 */
	private static Widget row(String text, int y)
	{
		final Widget w = mock(Widget.class);
		final int[] originalY = {y};
		final boolean[] hidden = {false};

		when(w.getText()).thenReturn(text);
		when(w.getType()).thenReturn(WidgetType.TEXT);
		when(w.getOriginalY()).thenAnswer(i -> originalY[0]);
		when(w.setOriginalY(anyInt())).thenAnswer(i ->
		{
			originalY[0] = i.getArgument(0);
			return w;
		});
		when(w.isHidden()).thenAnswer(i -> hidden[0]);
		when(w.setHidden(anyBoolean())).thenAnswer(i ->
		{
			hidden[0] = i.getArgument(0);
			return w;
		});
		return w;
	}
}
