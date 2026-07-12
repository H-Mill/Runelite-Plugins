package com.friendgroups;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

/**
 * Covers the name normalization that group membership, the rename migration and the
 * in-game row guard all key off. Names reach the plugin from the friend container,
 * from colour-tagged widget text and from config, each formatted differently.
 */
public class FriendGroupManagerTest
{
	@Test
	public void keyIgnoresCase()
	{
		assertEquals(FriendGroupManager.key("Zezima"), FriendGroupManager.key("zezima"));
	}

	@Test
	public void keyTreatsUnderscoresAndHyphensAsSpaces()
	{
		assertEquals(FriendGroupManager.key("Some_Name"), FriendGroupManager.key("Some Name"));
		assertEquals(FriendGroupManager.key("Some-Name"), FriendGroupManager.key("some name"));
	}

	@Test
	public void keyNormalizesNonBreakingSpace()
	{
		assertEquals(FriendGroupManager.key("Some\u00A0Name"), FriendGroupManager.key("Some Name"));
	}

	@Test
	public void keyStripsColourTagsFromFriendsListText()
	{
		// This is the shape of the text the friends list hands to the script callback.
		assertEquals(FriendGroupManager.key("Woox"), FriendGroupManager.key("<col=00ff00>Woox</col>"));
	}

	@Test
	public void keyDistinguishesDifferentNames()
	{
		assertFalse(FriendGroupManager.key("Zezima").equals(FriendGroupManager.key("Woox")));
	}

	@Test
	public void sanitizeNameTrimsAndStripsTags()
	{
		assertEquals("PvM", FriendGroupManager.sanitizeName("  PvM  "));
		assertEquals("PvM", FriendGroupManager.sanitizeName("<col=ff0000>PvM</col>"));
	}

	@Test
	public void sanitizeNameRejectsBlankNames()
	{
		assertNull(FriendGroupManager.sanitizeName(null));
		assertNull(FriendGroupManager.sanitizeName(""));
		assertNull(FriendGroupManager.sanitizeName("   "));
		assertNull(FriendGroupManager.sanitizeName("<col=ff0000></col>"));
	}

	@Test
	public void sanitizeNameNeutralizesStrayAngleBrackets()
	{
		// A lone '<' would otherwise open a tag in the in-game row text.
		final String sanitized = FriendGroupManager.sanitizeName("a<b");
		assertFalse(sanitized.contains("<"));
	}

	@Test
	public void sanitizeNameTruncatesToTheLimit()
	{
		final StringBuilder overlong = new StringBuilder();
		for (int i = 0; i < FriendGroupManager.MAX_NAME_LENGTH + 20; i++)
		{
			overlong.append('x');
		}

		assertEquals(FriendGroupManager.MAX_NAME_LENGTH, FriendGroupManager.sanitizeName(overlong.toString()).length());
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
