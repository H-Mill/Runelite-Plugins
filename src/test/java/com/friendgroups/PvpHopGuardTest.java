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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import org.junit.Test;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import net.runelite.http.api.worlds.World;
import net.runelite.http.api.worlds.WorldType;

/**
 * The World Hopper safety guard the double-click hop borrows: a hop into a PVP world is refused when
 * you are on a non-PVP world, so a stray click cannot drop you into PVP. Every other combination is
 * allowed, and an unknown current world never blocks.
 */
public class PvpHopGuardTest
{
	private static World worldOfTypes(WorldType... types)
	{
		final World world = mock(World.class);
		final EnumSet<WorldType> set = types.length == 0
			? EnumSet.noneOf(WorldType.class)
			: EnumSet.copyOf(java.util.Arrays.asList(types));
		when(world.getTypes()).thenReturn(set);
		return world;
	}

	@Test
	public void blocksPvpTargetFromNonPvpWorld()
	{
		assertTrue(FriendGroupsPlugin.isPvpHopBlocked(worldOfTypes(), worldOfTypes(WorldType.PVP)));
	}

	@Test
	public void allowsNonPvpTargetFromNonPvpWorld()
	{
		assertFalse(FriendGroupsPlugin.isPvpHopBlocked(worldOfTypes(), worldOfTypes()));
	}

	@Test
	public void allowsPvpTargetWhenAlreadyOnPvpWorld()
	{
		assertFalse(FriendGroupsPlugin.isPvpHopBlocked(
			worldOfTypes(WorldType.PVP), worldOfTypes(WorldType.PVP)));
	}

	@Test
	public void allowsNonPvpTargetFromPvpWorld()
	{
		assertFalse(FriendGroupsPlugin.isPvpHopBlocked(worldOfTypes(WorldType.PVP), worldOfTypes()));
	}

	@Test
	public void unknownCurrentWorldDoesNotBlock()
	{
		// A null current world (we could not resolve it) must not stop the hop.
		assertFalse(FriendGroupsPlugin.isPvpHopBlocked(null, worldOfTypes(WorldType.PVP)));
	}
}
