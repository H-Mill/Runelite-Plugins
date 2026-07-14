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
import net.runelite.api.gameval.InterfaceID;
import org.junit.Test;

/**
 * Some content rebuilds the whole friends interface rather than redrawing its rows - drinking from
 * a Pool of Refreshment tears the interface down and reloads it, and that reload repaints the list
 * ungrouped without firing {@code FRIENDS_UPDATE}, so our reapply path must run off the interface
 * load instead. This is the same family of bug as {@link SortRebuildTest}: a redraw that bypasses
 * our {@code ScriptPostFired} hook. The reapply must fire only for the friends interface and not
 * for the ignore list or anything else that shares the load event.
 */
public class WidgetReloadTest
{
	@Test
	public void reappliesWhenFriendsInterfaceReloads()
	{
		assertTrue(FriendGroupsPlugin.isFriendsInterfaceReload(InterfaceID.FRIENDS));
	}

	@Test
	public void leavesTheIgnoreListAlone()
	{
		// The friends and ignore lists share plumbing; broadening the guard to catch the ignore
		// list would pointlessly rebuild the friends list on an unrelated interface load.
		assertFalse(FriendGroupsPlugin.isFriendsInterfaceReload(InterfaceID.IGNORE));
	}

	@Test
	public void leavesOtherInterfacesAlone()
	{
		assertFalse(FriendGroupsPlugin.isFriendsInterfaceReload(InterfaceID.INVENTORY));
		assertFalse(FriendGroupsPlugin.isFriendsInterfaceReload(InterfaceID.CHATBOX));
	}
}
