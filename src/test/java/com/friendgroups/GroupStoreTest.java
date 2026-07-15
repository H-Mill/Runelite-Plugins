package com.friendgroups;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

/**
 * Covers the name normalization that group membership, the rename migration and the
 * in-game row guard all key off. Names reach the plugin from the friend container,
 * from color-tagged widget text and from config, each formatted differently.
 */
public class GroupStoreTest
{
	@Test
	public void keyIgnoresCase()
	{
		assertEquals(GroupStore.key("Zezima"), GroupStore.key("zezima"));
	}

	@Test
	public void keyTreatsUnderscoresAndHyphensAsSpaces()
	{
		assertEquals(GroupStore.key("Some_Name"), GroupStore.key("Some Name"));
		assertEquals(GroupStore.key("Some-Name"), GroupStore.key("some name"));
	}

	@Test
	public void keyNormalizesNonBreakingSpace()
	{
		assertEquals(GroupStore.key("Some\u00A0Name"), GroupStore.key("Some Name"));
	}

	@Test
	public void keyStripsColorTagsFromFriendsListText()
	{
		// This is the shape of the text the friends list hands to the script callback.
		assertEquals(GroupStore.key("Woox"), GroupStore.key("<col=00ff00>Woox</col>"));
	}

	@Test
	public void keyDistinguishesDifferentNames()
	{
		assertFalse(GroupStore.key("Zezima").equals(GroupStore.key("Woox")));
	}

	@Test
	public void sanitizeNameTrimsAndStripsTags()
	{
		assertEquals("PvM", GroupStore.sanitizeName("  PvM  "));
		assertEquals("PvM", GroupStore.sanitizeName("<col=ff0000>PvM</col>"));
	}

	@Test
	public void sanitizeNameRejectsBlankNames()
	{
		assertNull(GroupStore.sanitizeName(null));
		assertNull(GroupStore.sanitizeName(""));
		assertNull(GroupStore.sanitizeName("   "));
		assertNull(GroupStore.sanitizeName("<col=ff0000></col>"));
	}

	@Test
	public void sanitizeNameNeutralizesStrayAngleBrackets()
	{
		// A lone '<' would otherwise open a tag in the in-game row text.
		final String sanitized = GroupStore.sanitizeName("a<b");
		assertFalse(sanitized.contains("<"));
	}

	@Test
	public void sanitizeNameTruncatesToTheLimit()
	{
		final StringBuilder overlong = new StringBuilder();
		for (int i = 0; i < GroupStore.MAX_NAME_LENGTH + 20; i++)
		{
			overlong.append('x');
		}

		assertEquals(GroupStore.MAX_NAME_LENGTH, GroupStore.sanitizeName(overlong.toString()).length());
	}

	@Test
	public void groupMembershipIgnoresNameFormatting()
	{
		final FriendGroup group = new FriendGroup("PvM", 0xFFFFFF);
		group.getMembers().add("Some Name");

		assertTrue(group.contains("some_name"));
		assertTrue(group.contains("<col=00ff00>Some Name</col>"));
		assertFalse(group.contains("Other Name"));
	}

	@Test
	public void copyDoesNotShareMembersWithTheOriginal()
	{
		final FriendGroup group = new FriendGroup("PvM", 0xFFFFFF);
		group.getMembers().add("Zezima");

		final FriendGroup copy = group.copy();
		copy.getMembers().add("Woox");

		assertEquals(1, group.getMembers().size());
		assertEquals(2, copy.getMembers().size());
	}
}
