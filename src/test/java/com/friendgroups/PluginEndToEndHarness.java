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

import java.util.Collections;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import net.runelite.api.Client;
import net.runelite.api.ScriptID;
import net.runelite.api.events.WidgetLoaded;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.game.ChatIconManager;
import net.runelite.client.game.chatbox.ChatboxPanelManager;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.overlay.OverlayManager;

/**
 * Shared fixture for the plugin's event-handler end-to-end tests: a {@link FriendGroupsPlugin} with
 * every {@code @Inject} collaborator mocked. Subclasses drive the real {@code @Subscribe} methods
 * and assert the resulting calls. The concrete subclass carries the {@code @RunWith} annotation;
 * the Mockito runner processes these inherited {@code @Mock}/{@code @InjectMocks} fields.
 */
abstract class PluginEndToEndHarness
{
	@Mock
	protected Client client;
	@Mock
	protected ClientThread clientThread;
	@Mock
	protected FriendGroupsConfig config;
	@Mock
	protected ConfigManager configManager;
	// Named to match the plugin's fields: @InjectMocks disambiguates two same-type mocks by field name.
	@Mock
	protected GroupStore friendStore;
	@Mock
	protected GroupStore ignoreStore;
	@Mock
	protected FriendGroupsPanel panel;
	@Mock
	protected FriendGroupsOverlay overlay;
	@Mock
	protected ChatIconManager chatIconManager;
	@Mock
	protected ChatboxPanelManager chatboxPanelManager;
	@Mock
	protected ClientToolbar clientToolbar;
	@Mock
	protected OverlayManager overlayManager;
	@Mock
	protected EventBus eventBus;
	@Mock
	protected ListReorderer friendReorderer;
	@Mock
	protected ListReorderer ignoreReorderer;

	@InjectMocks
	protected FriendGroupsPlugin plugin;

	/** Makes {@link ClientThread#invokeLater(Runnable)} run its task inline so chains resolve now. */
	protected void runClientThreadInline()
	{
		doAnswer(inv ->
		{
			((Runnable) inv.getArgument(0)).run();
			return null;
		}).when(clientThread).invokeLater(any(Runnable.class));
	}

	protected static WidgetLoaded widgetLoaded(int groupId)
	{
		final WidgetLoaded event = new WidgetLoaded();
		event.setGroupId(groupId);
		return event;
	}

	protected static ConfigChanged configChanged(String group, String key)
	{
		final ConfigChanged event = new ConfigChanged();
		event.setGroup(group);
		event.setKey(key);
		return event;
	}

	/**
	 * Stubs both stores as having no groups, so the plugin's icon registration is a no-op and the
	 * list is rebuilt exactly once per refresh (registration is driven off both stores' groups).
	 */
	protected void stubStoresEmpty()
	{
		when(friendStore.getGroups()).thenReturn(Collections.emptyList());
		when(ignoreStore.getGroups()).thenReturn(Collections.emptyList());
	}

	/**
	 * Asserts the friends-list build script was run exactly once, with the widget ids the plugin's
	 * rebuild passes - i.e. the list was redrawn so our layout is reapplied.
	 */
	protected void verifyFriendsListRebuilt()
	{
		verify(client).runScript(
			ScriptID.FRIENDS_UPDATE,
			InterfaceID.Friends.LIST_CONTAINER,
			InterfaceID.Friends.SORT_NAME,
			InterfaceID.Friends.SORT_RECENT,
			InterfaceID.Friends.SORT_WORLD,
			InterfaceID.Friends.SORT_LEGACY,
			InterfaceID.Friends.LIST,
			InterfaceID.Friends.SCROLLBAR,
			InterfaceID.Friends.LOADING,
			InterfaceID.Friends.TOOLTIP);
	}

	/** Asserts the ignore-list build script was run exactly once (no world/recent sort widgets). */
	protected void verifyIgnoreListRebuilt()
	{
		verify(client).runScript(
			ScriptID.IGNORE_UPDATE,
			InterfaceID.Ignore.LIST_CONTAINER,
			InterfaceID.Ignore.SORT_NAME,
			InterfaceID.Ignore.SORT_LEGACY,
			InterfaceID.Ignore.LIST,
			InterfaceID.Ignore.SCROLLBAR,
			InterfaceID.Ignore.LOADING,
			InterfaceID.Ignore.TOOLTIP);
	}
}
