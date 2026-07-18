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

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

/**
 * The rule deciding whether a friend's panel row is double-click-to-hop: only an online friend on a
 * world other than ours. This is what gates the hop listener, the hand cursor and the tooltip, so it
 * also pins that offline friends and friends already on our world stay inert.
 */
public class RowHopTest
{
	@Test
	public void onlineOnAnotherWorldIsHoppable()
	{
		assertTrue(GroupListView.isHoppable(302, 301));
	}

	@Test
	public void offlineFriendIsNotHoppable()
	{
		// World 0 is the game's "offline" sentinel.
		assertFalse(GroupListView.isHoppable(0, 301));
	}

	@Test
	public void friendWithoutOnlineStatusIsNotHoppable()
	{
		// Null world - a list that carries no online status, i.e. the ignore list.
		assertFalse(GroupListView.isHoppable(null, 301));
	}

	@Test
	public void friendOnOurOwnWorldIsNotHoppable()
	{
		assertFalse(GroupListView.isHoppable(301, 301));
	}

	@Test
	public void onlineFriendIsHoppableWhenOurWorldIsUnknown()
	{
		// Our world 0 (unknown) still lets an online friend be hopped to.
		assertTrue(GroupListView.isHoppable(302, 0));
	}
}
