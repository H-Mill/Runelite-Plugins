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
 * A sort-button click redraws the friends list ungrouped and without our layout, so we force a
 * rebuild to re-apply it. It is needed whenever grouped mode is active (to re-cluster) or offline
 * friends are hidden or separated (to re-apply that to the rows the sort redrew), but not when the
 * game's plain sorted list is exactly what should be shown.
 */
public class SortRebuildTest
{
	@Test
	public void rebuildsInGroupedMode()
	{
		assertTrue(FriendGroupsPlugin.shouldRebuildOnSort(InGameMarker.GROUPED, OfflineDisplay.IN_GROUP));
	}

	@Test
	public void rebuildsWhenHidingOfflineWithColoredDots()
	{
		// The regression: dots marker (not grouped) with offline hidden must still re-hide.
		assertTrue(FriendGroupsPlugin.shouldRebuildOnSort(InGameMarker.DOT, OfflineDisplay.HIDDEN));
	}

	@Test
	public void rebuildsWhenHidingOfflineWithMarkerOff()
	{
		assertTrue(FriendGroupsPlugin.shouldRebuildOnSort(InGameMarker.OFF, OfflineDisplay.HIDDEN));
	}

	@Test
	public void rebuildsWhenSeparatingOfflineWithColoredDots()
	{
		// Without a rebuild the sort's own order puts the offline rows back among the online ones.
		assertTrue(FriendGroupsPlugin.shouldRebuildOnSort(InGameMarker.DOT, OfflineDisplay.SEPARATE_GROUP));
	}

	@Test
	public void groupedStillRebuildsRegardlessOfOfflineDisplay()
	{
		assertTrue(FriendGroupsPlugin.shouldRebuildOnSort(InGameMarker.GROUPED, OfflineDisplay.HIDDEN));
		assertTrue(FriendGroupsPlugin.shouldRebuildOnSort(InGameMarker.GROUPED, OfflineDisplay.SEPARATE_GROUP));
	}

	@Test
	public void leavesPlainSortedListAloneWhenDotsWithOfflineInGroup()
	{
		assertFalse(FriendGroupsPlugin.shouldRebuildOnSort(InGameMarker.DOT, OfflineDisplay.IN_GROUP));
	}

	@Test
	public void leavesPlainSortedListAloneWhenMarkerOffWithOfflineInGroup()
	{
		assertFalse(FriendGroupsPlugin.shouldRebuildOnSort(InGameMarker.OFF, OfflineDisplay.IN_GROUP));
	}
}
