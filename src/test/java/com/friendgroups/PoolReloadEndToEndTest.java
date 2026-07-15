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

import org.junit.Test;
import org.junit.runner.RunWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import org.mockito.junit.MockitoJUnitRunner;
import net.runelite.api.GameState;
import net.runelite.api.ScriptID;
import net.runelite.api.events.ScriptPostFired;
import net.runelite.api.gameval.InterfaceID;

/**
 * End-to-end coverage of the redraw-recovery chain behind the reported bug: content that rebuilds a
 * whole social interface (a Pool of Refreshment being the case reported) drops the layout, because
 * the reload does not fire the list's update script. Two links restore it, verified per list:
 * <ol>
 *   <li>a {@link net.runelite.api.events.WidgetLoaded} for the interface must run its update script, and</li>
 *   <li>the resulting update script must drive the matching {@link ListReorderer#reorder()}.</li>
 * </ol>
 * They are verified separately because the script call is a client-side cs2 script with no Java to
 * run under test, so it does not itself re-emit {@code ScriptPostFired}.
 */
@RunWith(MockitoJUnitRunner.class)
public class PoolReloadEndToEndTest extends PluginEndToEndHarness
{
	@Test
	public void friendsInterfaceReloadRunsFriendsUpdate()
	{
		when(client.getGameState()).thenReturn(GameState.LOGGED_IN);
		stubStoresEmpty();
		runClientThreadInline();

		plugin.onWidgetLoaded(widgetLoaded(InterfaceID.FRIENDS));

		verifyFriendsListRebuilt();
	}

	@Test
	public void ignoreInterfaceReloadRunsIgnoreUpdate()
	{
		when(client.getGameState()).thenReturn(GameState.LOGGED_IN);
		stubStoresEmpty();
		runClientThreadInline();

		plugin.onWidgetLoaded(widgetLoaded(InterfaceID.IGNORE));

		verifyIgnoreListRebuilt();
	}

	@Test
	public void friendsUpdateScriptDrivesTheFriendReorder()
	{
		plugin.onScriptPostFired(new ScriptPostFired(ScriptID.FRIENDS_UPDATE));

		verify(friendReorderer).reorder();
		verify(ignoreReorderer, never()).reorder();
	}

	@Test
	public void ignoreUpdateScriptDrivesTheIgnoreReorder()
	{
		plugin.onScriptPostFired(new ScriptPostFired(ScriptID.IGNORE_UPDATE));

		verify(ignoreReorderer).reorder();
		verify(friendReorderer, never()).reorder();
	}

	@Test
	public void reloadWhileLoggedOutDoesNothing()
	{
		when(client.getGameState()).thenReturn(GameState.LOGIN_SCREEN);

		plugin.onWidgetLoaded(widgetLoaded(InterfaceID.FRIENDS));

		// Guarded before the client-thread hop, so nothing is rebuilt.
		verify(clientThread, never()).invokeLater(any(Runnable.class));
	}

	@Test
	public void unrelatedInterfaceReloadIsLeftAlone()
	{
		plugin.onWidgetLoaded(widgetLoaded(InterfaceID.INVENTORY));

		// Only the friends and ignore interfaces trigger a reapply; every other shares the load event.
		verifyNoInteractions(client, clientThread, friendReorderer, ignoreReorderer);
	}

	@Test
	public void unrelatedScriptDoesNotReorder()
	{
		plugin.onScriptPostFired(new ScriptPostFired(ScriptID.FRIENDS_CHAT_CHANNEL_REBUILD));

		verify(friendReorderer, never()).reorder();
		verify(ignoreReorderer, never()).reorder();
	}
}
