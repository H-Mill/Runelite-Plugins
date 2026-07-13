package com.friendgroups;

import static org.junit.Assert.assertEquals;
import org.junit.Test;

/**
 * The friends list menu target carries the row's color tags for online status, and
 * 'Colored dots' appends an {@code <img>} icon. Both have to come off before the name is used.
 */
public class MenuTargetTest
{
	@Test
	public void stripsOnlineStatusColorTags()
	{
		assertEquals("Zezima", FriendGroupsPlugin.friendFromTarget("<col=00ff00>Zezima</col>"));
	}

	@Test
	public void stripsAColoredDotIcon()
	{
		assertEquals("Zezima",
			FriendGroupsPlugin.friendFromTarget("<col=00ff00>Zezima</col> <img=42>"));
	}

	@Test
	public void leavesAnUndecoratedNameAlone()
	{
		assertEquals("Some Name", FriendGroupsPlugin.friendFromTarget("Some_Name"));
	}
}
