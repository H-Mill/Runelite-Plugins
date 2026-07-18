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

import java.util.EnumSet;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.MockitoJUnitRunner;
import net.runelite.api.ChatMessageType;
import net.runelite.api.GameState;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.GameTick;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.http.api.worlds.World;
import net.runelite.http.api.worlds.WorldResult;
import net.runelite.http.api.worlds.WorldType;

/**
 * End-to-end coverage of the panel double-click quick-hop, driving {@link FriendGroupsPlugin#hopTo}
 * (the action the panel wires) and the real {@code onGameTick} pump that mirrors the World Hopper
 * plugin: it opens the world switcher, hops once it is up, gives up after a few attempts, and drops a
 * pending hop when the game refuses it. Complements the pure-logic {@link PvpHopGuardTest}.
 */
@RunWith(MockitoJUnitRunner.class)
public class QuickHopEndToEndTest extends PluginEndToEndHarness
{
	private static final int CURRENT_WORLD = 301;
	private static final int TARGET_WORLD = 302;

	private static final String SWITCHER_BUSY = "Please finish what you're doing before using the World Switcher.";

	/** Stubs a resolvable, non-PVP hop from {@link #CURRENT_WORLD} to {@link #TARGET_WORLD}. */
	private net.runelite.api.World armHopStubs()
	{
		final World targetWorld = mock(World.class);
		when(targetWorld.getTypes()).thenReturn(EnumSet.noneOf(WorldType.class));
		when(targetWorld.getId()).thenReturn(TARGET_WORLD);

		final World currentWorld = mock(World.class);
		when(currentWorld.getTypes()).thenReturn(EnumSet.noneOf(WorldType.class));

		final WorldResult worldResult = mock(WorldResult.class);
		when(worldResult.findWorld(TARGET_WORLD)).thenReturn(targetWorld);
		when(worldResult.findWorld(CURRENT_WORLD)).thenReturn(currentWorld);
		when(worldService.getWorlds()).thenReturn(worldResult);
		when(client.getWorld()).thenReturn(CURRENT_WORLD);

		final net.runelite.api.World rsWorld = mock(net.runelite.api.World.class);
		when(client.createWorld()).thenReturn(rsWorld);

		when(client.isClientThread()).thenReturn(true);
		runClientThreadInvokeInline();
		return rsWorld;
	}

	private static ChatMessage gameMessage(String message)
	{
		final ChatMessage event = new ChatMessage();
		event.setType(ChatMessageType.GAMEMESSAGE);
		event.setMessage(message);
		return event;
	}

	@Test
	public void armedHopOpensSwitcherThenHopsToTargetWorld()
	{
		final net.runelite.api.World rsWorld = armHopStubs();
		when(client.getGameState()).thenReturn(GameState.LOGGED_IN);
		// Switcher is closed on the first tick, then open on the second.
		when(client.getWidget(InterfaceID.Worldswitcher.BUTTONS)).thenReturn(null, mock(Widget.class));

		plugin.hopTo(TARGET_WORLD);

		// First tick: switcher not up yet, so it is opened and no hop happens.
		plugin.onGameTick(new GameTick());
		verify(client).openWorldHopper();
		verify(client, never()).hopToWorld(any());

		// Second tick: switcher is up, so we hop to the world we built.
		plugin.onGameTick(new GameTick());
		verify(client).hopToWorld(rsWorld);
	}

	@Test
	public void hopOnLoginScreenChangesWorldDirectly()
	{
		final net.runelite.api.World rsWorld = armHopStubs();
		when(client.getGameState()).thenReturn(GameState.LOGIN_SCREEN);

		plugin.hopTo(TARGET_WORLD);

		// On the login screen there is no switcher to drive - the world is changed directly...
		verify(client).changeWorld(rsWorld);

		// ...and nothing is left armed, so a following tick does not try to open the switcher.
		plugin.onGameTick(new GameTick());
		verify(client, never()).openWorldHopper();
		verify(client, never()).hopToWorld(any());
	}

	@Test
	public void pvpTargetFromNonPvpWorldDoesNotHop()
	{
		final World targetWorld = mock(World.class);
		when(targetWorld.getTypes()).thenReturn(EnumSet.of(WorldType.PVP));

		final World currentWorld = mock(World.class);
		when(currentWorld.getTypes()).thenReturn(EnumSet.noneOf(WorldType.class));

		final WorldResult worldResult = mock(WorldResult.class);
		when(worldResult.findWorld(TARGET_WORLD)).thenReturn(targetWorld);
		when(worldResult.findWorld(CURRENT_WORLD)).thenReturn(currentWorld);
		when(worldService.getWorlds()).thenReturn(worldResult);
		when(client.getWorld()).thenReturn(CURRENT_WORLD);
		when(client.isClientThread()).thenReturn(true);
		runClientThreadInvokeInline();

		plugin.hopTo(TARGET_WORLD);

		// The guard bails before a world is ever built or armed.
		verify(client, never()).createWorld();
		plugin.onGameTick(new GameTick());
		verify(client, never()).openWorldHopper();
	}

	@Test
	public void hopWithoutWorldListDoesNothing()
	{
		when(worldService.getWorlds()).thenReturn(null);
		when(client.isClientThread()).thenReturn(true);
		runClientThreadInvokeInline();

		plugin.hopTo(TARGET_WORLD);

		verify(client, never()).createWorld();
		plugin.onGameTick(new GameTick());
		verify(client, never()).openWorldHopper();
	}

	@Test
	public void switcherBusyMessageDropsThePendingHop()
	{
		armHopStubs();
		when(client.getGameState()).thenReturn(GameState.LOGGED_IN);

		plugin.hopTo(TARGET_WORLD);

		// The game says it can't switch right now, so the pending hop is abandoned...
		plugin.onChatMessage(gameMessage(SWITCHER_BUSY));

		// ...and no later tick keeps trying.
		plugin.onGameTick(new GameTick());
		verify(client, never()).openWorldHopper();
		verify(client, never()).hopToWorld(any());
	}

	@Test
	public void givesUpAfterThreeFailedSwitcherOpenAttempts()
	{
		armHopStubs();
		when(client.getGameState()).thenReturn(GameState.LOGGED_IN);
		// The switcher never comes up.
		when(client.getWidget(InterfaceID.Worldswitcher.BUTTONS)).thenReturn(null);

		plugin.hopTo(TARGET_WORLD);

		// Three ticks each try to open it; the third gives up and resets.
		plugin.onGameTick(new GameTick());
		plugin.onGameTick(new GameTick());
		plugin.onGameTick(new GameTick());
		// A fourth tick after the reset no longer tries.
		plugin.onGameTick(new GameTick());

		verify(client, times(3)).openWorldHopper();
		verify(client, never()).hopToWorld(any());
	}
}
