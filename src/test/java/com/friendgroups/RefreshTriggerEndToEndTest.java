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
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.VarClientIntChanged;
import net.runelite.api.gameval.VarClientID;
import net.runelite.client.events.RuneScapeProfileChanged;

/**
 * End-to-end coverage of the everyday triggers that must (re)apply the in-game layout, driving the
 * real {@code @Subscribe} handlers for both lists:
 * <ul>
 *   <li>the sort buttons, which redraw a list ungrouped and so need a forced rebuild in the cases
 *       {@link FriendGroupsPlugin#shouldRebuildOnSort} covers;</li>
 *   <li>config changes that alter an in-game view (each list's marker, plus the friends-only offline
 *       display and world prefix); and</li>
 *   <li>logging in; and</li>
 *   <li>the active character changing, which swaps in that character's groups.</li>
 * </ul>
 * These complement the pure-logic {@link SortRebuildTest} by proving the wiring from event to a real
 * update-script run, not just the decision.
 */
@RunWith(MockitoJUnitRunner.class)
public class RefreshTriggerEndToEndTest extends PluginEndToEndHarness
{
	// ---- sort buttons (VarClientIntChanged) ----

	@Test
	public void friendsSortInGroupedModeRebuildsFriends()
	{
		when(config.inGameMarker()).thenReturn(InGameMarker.GROUPED);
		when(config.offlineDisplay()).thenReturn(OfflineDisplay.IN_GROUP);
		when(client.getGameState()).thenReturn(GameState.LOGGED_IN);
		runClientThreadInline();

		plugin.onVarClientIntChanged(new VarClientIntChanged(VarClientID.FRIENDS_SORT));

		verifyFriendsListRebuilt();
	}

	@Test
	public void ignoreSortInGroupedModeRebuildsIgnore()
	{
		when(config.ignoreInGameMarker()).thenReturn(InGameMarker.GROUPED);
		when(client.getGameState()).thenReturn(GameState.LOGGED_IN);
		runClientThreadInline();

		plugin.onVarClientIntChanged(new VarClientIntChanged(VarClientID.IGNORE_SORT));

		verifyIgnoreListRebuilt();
	}

	@Test
	public void friendsSortWithDotsAndNoHideOfflineLeavesGameSortAlone()
	{
		when(config.inGameMarker()).thenReturn(InGameMarker.DOT);
		when(config.offlineDisplay()).thenReturn(OfflineDisplay.IN_GROUP);
		when(client.getGameState()).thenReturn(GameState.LOGGED_IN);

		plugin.onVarClientIntChanged(new VarClientIntChanged(VarClientID.FRIENDS_SORT));

		// shouldRebuildOnSort is false here, so the game's own sorted list is left untouched.
		verify(clientThread, never()).invokeLater(any(Runnable.class));
	}

	@Test
	public void nonSortVarChangeIsIgnored()
	{
		when(client.getGameState()).thenReturn(GameState.LOGGED_IN);

		// A var that is neither list's sort var.
		plugin.onVarClientIntChanged(new VarClientIntChanged(VarClientID.CHATCHANNEL_CURRENT_SORT));

		verify(clientThread, never()).invokeLater(any(Runnable.class));
	}

	// ---- config changes ----

	@Test
	public void switchingFriendMarkerOutOfGroupedRemovesHeadersThenRebuilds()
	{
		when(config.inGameMarker()).thenReturn(InGameMarker.OFF);
		when(client.getGameState()).thenReturn(GameState.LOGGED_IN);
		stubStoresEmpty();
		runClientThreadInline();

		plugin.onConfigChanged(configChanged(FriendGroupsConfig.GROUP, "inGameMarker"));

		// Leaving grouped mode tears down the header rows before the list is drawn normally again.
		verify(friendReorderer).removeHeaders();
		verifyFriendsListRebuilt();
	}

	@Test
	public void switchingFriendMarkerToGroupedRebuildsWithoutRemovingHeaders()
	{
		when(config.inGameMarker()).thenReturn(InGameMarker.GROUPED);
		when(client.getGameState()).thenReturn(GameState.LOGGED_IN);
		stubStoresEmpty();
		runClientThreadInline();

		plugin.onConfigChanged(configChanged(FriendGroupsConfig.GROUP, "inGameMarker"));

		verify(friendReorderer, never()).removeHeaders();
		verifyFriendsListRebuilt();
	}

	@Test
	public void switchingIgnoreMarkerOutOfGroupedRemovesHeadersThenRebuildsIgnore()
	{
		when(config.ignoreInGameMarker()).thenReturn(InGameMarker.OFF);
		when(client.getGameState()).thenReturn(GameState.LOGGED_IN);
		stubStoresEmpty();
		runClientThreadInline();

		plugin.onConfigChanged(configChanged(FriendGroupsConfig.GROUP, "ignoreInGameMarker"));

		verify(ignoreReorderer).removeHeaders();
		verifyIgnoreListRebuilt();
	}

	@Test
	public void offlineDisplayChangeRebuildsFriends()
	{
		when(client.getGameState()).thenReturn(GameState.LOGGED_IN);
		stubStoresEmpty();
		runClientThreadInline();

		plugin.onConfigChanged(configChanged(FriendGroupsConfig.GROUP, "offlineDisplay"));

		verifyFriendsListRebuilt();
	}

	@Test
	public void hideWorldPrefixChangeRebuildsFriends()
	{
		when(client.getGameState()).thenReturn(GameState.LOGGED_IN);
		stubStoresEmpty();
		runClientThreadInline();

		plugin.onConfigChanged(configChanged(FriendGroupsConfig.GROUP, "hideWorldPrefix"));

		verifyFriendsListRebuilt();
	}

	@Test
	public void configChangeForAnotherPluginIsIgnored()
	{
		plugin.onConfigChanged(configChanged("some-other-plugin", "inGameMarker"));

		verifyNoInteractions(client, clientThread, friendReorderer, ignoreReorderer);
	}

	// ---- login ----

	@Test
	public void loginReappliesBothLayouts()
	{
		when(client.getGameState()).thenReturn(GameState.LOGGED_IN);
		stubStoresEmpty();
		runClientThreadInline();

		final GameStateChanged event = new GameStateChanged();
		event.setGameState(GameState.LOGGED_IN);
		plugin.onGameStateChanged(event);

		verifyFriendsListRebuilt();
		verifyIgnoreListRebuilt();
	}

	// ---- character change ----

	@Test
	public void characterChangeReloadsBothStoresAndReappliesBothLayouts()
	{
		when(client.getGameState()).thenReturn(GameState.LOGGED_IN);
		stubStoresEmpty();
		runClientThreadInline();

		plugin.onRuneScapeProfileChanged(new RuneScapeProfileChanged(null, "rsprofile.0123456789abcdef"));

		// Groups are stored per character, so the new character's are loaded and drawn in their lists.
		verify(friendStore).load();
		verify(ignoreStore).load();
		verifyFriendsListRebuilt();
		verifyIgnoreListRebuilt();
	}

	@Test
	public void logoutReloadsBothStoresOntoNoCharacter()
	{
		when(client.getGameState()).thenReturn(GameState.LOGIN_SCREEN);

		plugin.onRuneScapeProfileChanged(new RuneScapeProfileChanged("rsprofile.0123456789abcdef", null));

		// The previous character's groups are dropped rather than left showing for the next one.
		verify(friendStore).load();
		verify(ignoreStore).load();
		// Logged out there is no list to redraw.
		verify(clientThread, never()).invokeLater(any(Runnable.class));
	}
}
