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
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import org.mockito.junit.MockitoJUnitRunner;
import net.runelite.api.GameState;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.VarClientIntChanged;
import net.runelite.api.gameval.VarClientID;

/**
 * End-to-end coverage of the everyday triggers that must (re)apply the in-game layout, driving the
 * real {@code @Subscribe} handlers:
 * <ul>
 *   <li>the Name / Recent / World sort buttons, which redraw the list ungrouped and so need a
 *       forced rebuild in the cases {@link FriendGroupsPlugin#shouldRebuildOnSort} covers;</li>
 *   <li>config changes that alter the in-game view (marker mode, hide-offline, world prefix); and</li>
 *   <li>logging in.</li>
 * </ul>
 * These complement the pure-logic {@link SortRebuildTest} by proving the wiring from event to a
 * real {@code FRIENDS_UPDATE} run, not just the decision.
 */
@RunWith(MockitoJUnitRunner.class)
public class RefreshTriggerEndToEndTest extends PluginEndToEndHarness
{
	// ---- sort buttons (VarClientIntChanged on FRIENDS_SORT) ----

	@Test
	public void sortInGroupedModeRebuildsList()
	{
		when(config.inGameMarker()).thenReturn(InGameMarker.GROUPED);
		when(config.hideOffline()).thenReturn(false);
		when(client.getGameState()).thenReturn(GameState.LOGGED_IN);
		runClientThreadInline();

		plugin.onVarClientIntChanged(new VarClientIntChanged(VarClientID.FRIENDS_SORT));

		verifyFriendsListRebuilt();
	}

	@Test
	public void sortWithDotsAndNoHideOfflineLeavesGameSortAlone()
	{
		when(config.inGameMarker()).thenReturn(InGameMarker.DOT);
		when(config.hideOffline()).thenReturn(false);

		plugin.onVarClientIntChanged(new VarClientIntChanged(VarClientID.FRIENDS_SORT));

		// shouldRebuildOnSort is false here, so the game's own sorted list is left untouched.
		verify(clientThread, never()).invokeLater(any(Runnable.class));
	}

	@Test
	public void nonSortVarChangeIsIgnored()
	{
		plugin.onVarClientIntChanged(new VarClientIntChanged(VarClientID.FRIENDS_SORT + 1));

		verifyNoInteractions(client, clientThread, reorderer, config);
	}

	// ---- config changes ----

	@Test
	public void switchingMarkerOutOfGroupedRemovesHeadersThenRebuilds()
	{
		when(config.inGameMarker()).thenReturn(InGameMarker.OFF);
		when(client.getGameState()).thenReturn(GameState.LOGGED_IN);
		when(manager.getGroups()).thenReturn(Collections.emptyList());
		runClientThreadInline();

		plugin.onConfigChanged(configChanged(FriendGroupsConfig.GROUP, "inGameMarker"));

		// Leaving grouped mode tears down the header rows before the list is drawn normally again.
		verify(reorderer).removeHeaders();
		verifyFriendsListRebuilt();
	}

	@Test
	public void switchingMarkerToGroupedRebuildsWithoutRemovingHeaders()
	{
		when(config.inGameMarker()).thenReturn(InGameMarker.GROUPED);
		when(client.getGameState()).thenReturn(GameState.LOGGED_IN);
		when(manager.getGroups()).thenReturn(Collections.emptyList());
		runClientThreadInline();

		plugin.onConfigChanged(configChanged(FriendGroupsConfig.GROUP, "inGameMarker"));

		verify(reorderer, never()).removeHeaders();
		verifyFriendsListRebuilt();
	}

	@Test
	public void hideOfflineChangeRebuilds()
	{
		when(client.getGameState()).thenReturn(GameState.LOGGED_IN);
		when(manager.getGroups()).thenReturn(Collections.emptyList());
		runClientThreadInline();

		plugin.onConfigChanged(configChanged(FriendGroupsConfig.GROUP, "hideOffline"));

		verifyFriendsListRebuilt();
	}

	@Test
	public void hideWorldPrefixChangeRebuilds()
	{
		when(client.getGameState()).thenReturn(GameState.LOGGED_IN);
		when(manager.getGroups()).thenReturn(Collections.emptyList());
		runClientThreadInline();

		plugin.onConfigChanged(configChanged(FriendGroupsConfig.GROUP, "hideWorldPrefix"));

		verifyFriendsListRebuilt();
	}

	@Test
	public void configChangeForAnotherPluginIsIgnored()
	{
		plugin.onConfigChanged(configChanged("some-other-plugin", "inGameMarker"));

		verifyNoInteractions(client, clientThread, reorderer);
	}

	// ---- login ----

	@Test
	public void loginReappliesTheLayout()
	{
		when(client.getGameState()).thenReturn(GameState.LOGGED_IN);
		when(manager.getGroups()).thenReturn(Collections.emptyList());
		runClientThreadInline();

		final GameStateChanged event = new GameStateChanged();
		event.setGameState(GameState.LOGGED_IN);
		plugin.onGameStateChanged(event);

		verifyFriendsListRebuilt();
	}
}
