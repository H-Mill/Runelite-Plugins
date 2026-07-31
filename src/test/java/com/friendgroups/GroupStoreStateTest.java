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

import com.google.gson.Gson;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import org.junit.Before;
import org.junit.Test;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.stubbing.Answer;
import net.runelite.client.config.ConfigManager;

/**
 * Exercises the group model {@link GroupStore} owns: create/rename/delete with the
 * dedup and cap rules, the membership index the menu and in-game paths read, friend rename and
 * removal migrations, the display ordering (including the movable Ungrouped section), and the
 * config round-trip - including the per-character storage the groups live in. The
 * {@link ConfigManager} is a Mockito stub backed by an in-memory map standing in for one character's
 * config, so writes really persist and a fresh manager loading the same map reconstructs the same
 * state.
 */
public class GroupStoreStateTest
{
	private Map<String, Object> store;
	private ConfigManager configManager;
	private GroupStore manager;

	/**
	 * A Mockito {@link ConfigManager} whose RuneScape-profile config - where the groups live, one set
	 * per character - reads and writes the given map, like the real thing. No profile-less config is
	 * served, so nothing is seeded; see {@link #configSeededFrom} for that.
	 */
	private static ConfigManager configBackedBy(Map<String, Object> store)
	{
		return configBackedBy(store, new HashMap<>());
	}

	/**
	 * As {@link #configBackedBy}, with {@code shared} standing in for the profile-less config the
	 * plugin used to keep its groups in, which a character is seeded from the first time it loads.
	 */
	private static ConfigManager configSeededFrom(Map<String, Object> shared, Map<String, Object> store)
	{
		return configBackedBy(store, shared);
	}

	private static ConfigManager configBackedBy(Map<String, Object> store, Map<String, Object> shared)
	{
		final ConfigManager cm = mock(ConfigManager.class);
		final String group = FriendGroupsConfig.GROUP;

		// A character is active, so the store loads and persists rather than sitting inactive.
		when(cm.getRSProfileKey()).thenReturn("rsprofile.0123456789abcdef");

		final Answer<Void> put = inv ->
		{
			store.put(inv.getArgument(1), inv.getArgument(2));
			return null;
		};
		doAnswer(put).when(cm).setRSProfileConfiguration(eq(group), anyString(), any());

		doAnswer(inv ->
		{
			store.remove(inv.getArgument(1));
			return null;
		}).when(cm).unsetRSProfileConfiguration(eq(group), anyString());

		when(cm.getRSProfileConfiguration(eq(group), anyString())).thenAnswer(inv ->
		{
			final Object v = store.get(inv.getArgument(1));
			return v == null ? null : v.toString();
		});

		// The typed getter's third parameter is java.lang.reflect.Type; Class implements Type, so
		// this binds to it and serves the Integer/Boolean reads.
		when(cm.getRSProfileConfiguration(eq(group), anyString(), any(Class.class))).thenAnswer(inv ->
		{
			final Object value = store.get(inv.getArgument(1));
			if (!(value instanceof String))
			{
				return value;
			}

			// Config holds every value as a string - which is what a seeded value arrives as - and the
			// real manager parses it on the way out.
			final Object type = inv.getArgument(2);
			if (type == Boolean.class)
			{
				return Boolean.valueOf((String) value);
			}
			if (type == Integer.class)
			{
				return Integer.valueOf((String) value);
			}
			return value;
		});

		// The pre-per-character groups, read only while seeding a character that has none.
		when(cm.getConfiguration(eq(group), anyString())).thenAnswer(inv ->
		{
			final Object v = shared.get(inv.getArgument(1));
			return v == null ? null : v.toString();
		});

		return cm;
	}

	private GroupStore loadFrom(Map<String, Object> store)
	{
		final GroupStore m = new GroupStore(configBackedBy(store), new Gson(), GroupList.FRIENDS);
		m.load();
		return m;
	}

	private List<String> groupNames()
	{
		return manager.getGroups().stream().map(FriendGroup::getName).collect(Collectors.toList());
	}

	private List<String> groupNamesFor(String friend)
	{
		return manager.groupsFor(friend).stream().map(FriendGroup::getName).collect(Collectors.toList());
	}

	@Before
	public void setUp()
	{
		store = new HashMap<>();
		configManager = configBackedBy(store);
		manager = new GroupStore(configManager, new Gson(), GroupList.FRIENDS);
		manager.load();
	}

	// ---- create / rename / delete ----

	@Test
	public void addGroupCreatesAGroup()
	{
		assertTrue(manager.addGroup("PvM"));
		assertEquals(Arrays.asList("PvM"), groupNames());
	}

	@Test
	public void addGroupSanitizesTheName()
	{
		assertTrue(manager.addGroup("  <col=ff0000>PvM</col>  "));
		assertEquals(Arrays.asList("PvM"), groupNames());
	}

	@Test
	public void addGroupRejectsBlankNames()
	{
		assertFalse(manager.addGroup("   "));
		assertFalse(manager.addGroup(null));
		assertTrue(manager.getGroups().isEmpty());
	}

	@Test
	public void addGroupRejectsDuplicatesIgnoringCase()
	{
		assertTrue(manager.addGroup("PvM"));
		assertFalse(manager.addGroup("pvm"));
		assertEquals(1, manager.getGroups().size());
	}

	@Test
	public void addGroupStopsAtTheCap()
	{
		for (int i = 0; i < GroupStore.MAX_GROUPS; i++)
		{
			assertTrue(manager.addGroup("group" + i));
		}
		assertFalse(manager.addGroup("one too many"));
		assertEquals(GroupStore.MAX_GROUPS, manager.getGroups().size());
	}

	@Test
	public void addGroupCyclesThroughThePalette()
	{
		manager.addGroup("a");
		manager.addGroup("b");
		// Distinct groups get distinct starting colors rather than all defaulting to one.
		assertFalse(manager.getGroups().get(0).getColor() == manager.getGroups().get(1).getColor());
	}

	@Test
	public void renameGroupChangesTheName()
	{
		manager.addGroup("PvM");
		assertTrue(manager.renameGroup("PvM", "Bossing"));
		assertEquals(Arrays.asList("Bossing"), groupNames());
	}

	@Test
	public void renameGroupToAnExistingNameFails()
	{
		manager.addGroup("PvM");
		manager.addGroup("Skilling");
		assertFalse(manager.renameGroup("PvM", "skilling"));
		assertEquals(Arrays.asList("PvM", "Skilling"), groupNames());
	}

	@Test
	public void renameGroupToItselfIsAllowedForRecasing()
	{
		manager.addGroup("pvm");
		assertTrue(manager.renameGroup("pvm", "PvM"));
		assertEquals(Arrays.asList("PvM"), groupNames());
	}

	@Test
	public void renameMissingGroupFails()
	{
		assertFalse(manager.renameGroup("Nope", "Something"));
	}

	@Test
	public void renameGroupKeepsItsMembers()
	{
		manager.addGroup("PvM");
		manager.addMember("PvM", "Zezima");
		manager.renameGroup("PvM", "Bossing");
		assertEquals(Arrays.asList("Bossing"), groupNamesFor("Zezima"));
	}

	@Test
	public void deleteGroupRemovesIt()
	{
		manager.addGroup("PvM");
		manager.addGroup("Skilling");
		manager.deleteGroup("pvm");
		assertEquals(Arrays.asList("Skilling"), groupNames());
	}

	@Test
	public void deleteMissingGroupIsANoOp()
	{
		manager.addGroup("PvM");
		manager.deleteGroup("Nope");
		assertEquals(Arrays.asList("PvM"), groupNames());
	}

	// ---- color / collapse ----

	@Test
	public void setColorUpdatesTheGroup()
	{
		manager.addGroup("PvM");
		manager.setColor("PvM", 0x123456);
		assertEquals(0x123456, manager.getGroups().get(0).getColor());
	}

	@Test
	public void setCollapsedTogglesTheFlag()
	{
		manager.addGroup("PvM");
		manager.setCollapsed("PvM", true);
		assertTrue(manager.getGroups().get(0).isCollapsed());
		manager.setCollapsed("PvM", false);
		assertFalse(manager.getGroups().get(0).isCollapsed());
	}

	@Test
	public void ungroupedCollapseIsPersisted()
	{
		manager.setUngroupedCollapsed(true);
		assertTrue(manager.isUngroupedCollapsed());
		assertTrue(loadFrom(store).isUngroupedCollapsed());
	}

	// ---- membership index ----

	@Test
	public void groupsForReflectsMembershipAcrossGroups()
	{
		manager.addGroup("PvM");
		manager.addGroup("Skilling");
		manager.addMember("PvM", "Zezima");
		manager.addMember("Skilling", "Zezima");
		assertEquals(Arrays.asList("PvM", "Skilling"), groupNamesFor("Zezima"));
	}

	@Test
	public void groupsForNormalisesTheLookupName()
	{
		manager.addGroup("PvM");
		manager.addMember("PvM", "Some Name");
		assertEquals(Arrays.asList("PvM"), groupNamesFor("some_name"));
		assertEquals(Arrays.asList("PvM"), groupNamesFor("<col=00ff00>Some Name</col>"));
	}

	@Test
	public void groupsForUnknownFriendIsEmpty()
	{
		manager.addGroup("PvM");
		assertTrue(manager.groupsFor("Stranger").isEmpty());
	}

	@Test
	public void getGroupsSnapshotIsImmutable()
	{
		manager.addGroup("PvM");
		try
		{
			manager.getGroups().add(new FriendGroup("Hack", 0));
			fail("expected the snapshot to be unmodifiable");
		}
		catch (UnsupportedOperationException expected)
		{
			// snapshot must not be mutable by callers on the render/menu paths
		}
	}

	// ---- member add / remove ----

	@Test
	public void addMemberIsDedupedIgnoringFormatting()
	{
		manager.addGroup("PvM");
		manager.addMember("PvM", "Zezima");
		manager.addMember("PvM", "zezima");
		assertEquals(1, manager.getGroups().get(0).getMembers().size());
	}

	@Test
	public void addMemberToMissingGroupIsANoOp()
	{
		manager.addMember("Nope", "Zezima");
		assertTrue(manager.groupsFor("Zezima").isEmpty());
	}

	@Test
	public void removeMemberDropsTheFriend()
	{
		manager.addGroup("PvM");
		manager.addMember("PvM", "Zezima");
		manager.removeMember("PvM", "<col=00ff00>zezima</col>");
		assertTrue(manager.groupsFor("Zezima").isEmpty());
	}

	@Test
	public void forgetFriendClearsEveryGroup()
	{
		manager.addGroup("PvM");
		manager.addGroup("Skilling");
		manager.addMember("PvM", "Zezima");
		manager.addMember("Skilling", "Zezima");
		manager.addMember("PvM", "Woox");

		manager.forgetFriend("Zezima");

		assertTrue(manager.groupsFor("Zezima").isEmpty());
		assertEquals(Arrays.asList("PvM"), groupNamesFor("Woox"));
	}

	@Test
	public void renameFriendMigratesMembership()
	{
		manager.addGroup("PvM");
		manager.addGroup("Skilling");
		manager.addMember("PvM", "OldName");
		manager.addMember("Skilling", "OldName");

		manager.renameFriend("OldName", "NewName");

		assertTrue(manager.groupsFor("OldName").isEmpty());
		assertEquals(Arrays.asList("PvM", "Skilling"), groupNamesFor("NewName"));
	}

	@Test
	public void renameFriendToTheSameKeyIsANoOp()
	{
		manager.addGroup("PvM");
		manager.addMember("PvM", "Some Name");
		// Same underlying key, only formatting differs - membership must be untouched.
		manager.renameFriend("Some Name", "some_name");
		assertEquals(Arrays.asList("PvM"), groupNamesFor("Some Name"));
	}

	// ---- ordering & the Ungrouped section ----

	@Test
	public void ungroupedSitsLastByDefault()
	{
		manager.addGroup("A");
		manager.addGroup("B");
		manager.addGroup("C");
		assertEquals(3, manager.ungroupedPosition());
	}

	@Test
	public void applyOrderReordersGroupsAndPlacesUngrouped()
	{
		manager.addGroup("A");
		manager.addGroup("B");
		manager.addGroup("C");

		manager.applyOrder(Arrays.asList("B", GroupStore.UNGROUPED, "A", "C"));

		assertEquals(Arrays.asList("B", "A", "C"), groupNames());
		assertEquals(1, manager.ungroupedPosition());
	}

	@Test
	public void applyOrderKeepsGroupsMissingFromTheTokens()
	{
		manager.addGroup("A");
		manager.addGroup("B");
		manager.addGroup("C");

		// A stale drop that only names one group must never lose the others.
		manager.applyOrder(Arrays.asList("C"));

		assertEquals(Arrays.asList("C", "A", "B"), groupNames());
	}

	@Test
	public void moveGroupDownSwapsWithItsNeighbour()
	{
		manager.addGroup("A");
		manager.addGroup("B");
		manager.addGroup("C");

		manager.moveGroup("A", 1);

		assertEquals(Arrays.asList("B", "A", "C"), groupNames());
	}

	@Test
	public void moveGroupUpAtTheTopIsANoOp()
	{
		manager.addGroup("A");
		manager.addGroup("B");

		manager.moveGroup("A", -1);

		assertEquals(Arrays.asList("A", "B"), groupNames());
	}

	@Test
	public void moveGroupPastUngroupedSwapsWithTheUngroupedSlot()
	{
		manager.addGroup("A");
		manager.addGroup("B");
		manager.addGroup("C");

		// Ungrouped is last, so moving the last group down pushes Ungrouped above it.
		manager.moveGroup("C", 1);

		assertEquals(Arrays.asList("A", "B", "C"), groupNames());
		assertEquals(2, manager.ungroupedPosition());
	}

	@Test
	public void moveUngroupedUpFromLast()
	{
		manager.addGroup("A");
		manager.addGroup("B");
		manager.addGroup("C");

		manager.moveUngrouped(-1);

		assertEquals(2, manager.ungroupedPosition());
	}

	@Test
	public void deleteGroupAboveUngroupedKeepsItsRelativePosition()
	{
		manager.addGroup("A");
		manager.addGroup("B");
		manager.addGroup("C");
		manager.moveUngrouped(-1); // Ungrouped now at position 2 (between B and C)
		assertEquals(2, manager.ungroupedPosition());

		manager.deleteGroup("A"); // a group above it goes

		assertEquals(Arrays.asList("B", "C"), groupNames());
		assertEquals(1, manager.ungroupedPosition());
	}

	// ---- listeners ----

	@Test
	public void listenerFiresOnChangeWithTheInGameFlag()
	{
		final AtomicInteger calls = new AtomicInteger();
		final AtomicReference<Boolean> last = new AtomicReference<>();
		final Consumer<Boolean> listener = dirty ->
		{
			calls.incrementAndGet();
			last.set(dirty);
		};

		manager.addListener(listener);
		manager.addGroup("PvM");

		assertEquals(1, calls.get());
		assertTrue(last.get());

		manager.removeListener(listener);
		manager.addGroup("Skilling");
		assertEquals(1, calls.get());
	}

	// ---- persistence round-trip ----

	@Test
	public void stateSurvivesAReload()
	{
		manager.addGroup("PvM");
		manager.addGroup("Skilling");
		manager.setColor("PvM", 0x00FF00);
		manager.addMember("PvM", "Zezima");
		manager.setCollapsed("Skilling", true);
		manager.moveUngrouped(-1);

		final GroupStore reloaded = loadFrom(store);

		assertEquals(Arrays.asList("PvM", "Skilling"), reloaded.getGroups().stream()
			.map(FriendGroup::getName).collect(Collectors.toList()));
		assertEquals(0x00FF00, reloaded.getGroups().get(0).getColor());
		assertEquals(Arrays.asList("PvM"), reloaded.groupsFor("Zezima").stream()
			.map(FriendGroup::getName).collect(Collectors.toList()));
		assertTrue(reloaded.getGroups().get(1).isCollapsed());
		assertEquals(1, reloaded.ungroupedPosition());
	}

	@Test
	public void deletingTheLastGroupClearsStoredGroups()
	{
		manager.addGroup("PvM");
		manager.deleteGroup("PvM");
		// A fresh load must find no groups, not a stale "[]" or the old entry.
		assertTrue(loadFrom(store).getGroups().isEmpty());
	}

	@Test
	public void loadSkipsGroupsWithNoName()
	{
		store.put("groups", "[{\"name\":\"\",\"color\":1,\"members\":[]},"
			+ "{\"name\":\"PvM\",\"color\":2,\"members\":[\"Zezima\"]}]");

		final GroupStore m = loadFrom(store);

		assertEquals(Arrays.asList("PvM"), m.getGroups().stream()
			.map(FriendGroup::getName).collect(Collectors.toList()));
	}

	@Test
	public void loadDefaultsMissingMembersToEmpty()
	{
		store.put("groups", "[{\"name\":\"PvM\",\"color\":2}]");

		final GroupStore m = loadFrom(store);

		assertEquals(1, m.getGroups().size());
		assertTrue(m.getGroups().get(0).getMembers().isEmpty());
	}

	@Test
	public void loadRecoversFromMalformedJson()
	{
		store.put("groups", "{ this is not valid json");

		final GroupStore m = loadFrom(store);

		assertTrue(m.getGroups().isEmpty());
	}

	@Test
	public void loadWithNoStoredConfigStartsEmpty()
	{
		final GroupStore m = loadFrom(new HashMap<>());
		assertTrue(m.getGroups().isEmpty());
		assertFalse(m.isUngroupedCollapsed());
		assertEquals(0, m.ungroupedPosition());
	}

	@Test
	public void reloadedManagerCanBeMutatedFurther()
	{
		manager.addGroup("PvM");
		final GroupStore reloaded = loadFrom(store);
		// The loaded list is the private working copy, not the immutable snapshot; a mutation
		// through the API must still work (regression guard for load() wiring).
		assertTrue(reloaded.renameGroup("PvM", "Bossing"));
		assertEquals(new ArrayList<>(Arrays.asList("Bossing")), reloaded.getGroups().stream()
			.map(FriendGroup::getName).collect(Collectors.toList()));
	}

	// ---- friends / ignore independence ----

	@Test
	public void friendsAndIgnoreStoresPersistIndependently()
	{
		// Two stores over one shared config map, keyed differently by their GroupList. A write to one
		// must land under its own keys and never clobber the other.
		final GroupStore friends = new GroupStore(configBackedBy(store), new Gson(), GroupList.FRIENDS);
		final GroupStore ignore = new GroupStore(configBackedBy(store), new Gson(), GroupList.IGNORE);
		friends.load();
		ignore.load();

		friends.addGroup("PvM");
		friends.addMember("PvM", "Zezima");
		ignore.addGroup("Spammers");
		ignore.addMember("Spammers", "Bot123");

		assertTrue(store.containsKey("groups"));
		assertTrue(store.containsKey("ignoreGroups"));

		// A fresh pair loaded from the same map reconstructs each list from only its own keys.
		final GroupStore friends2 = new GroupStore(configBackedBy(store), new Gson(), GroupList.FRIENDS);
		final GroupStore ignore2 = new GroupStore(configBackedBy(store), new Gson(), GroupList.IGNORE);
		friends2.load();
		ignore2.load();

		assertEquals(Arrays.asList("PvM"), namesOf(friends2));
		assertEquals(Arrays.asList("Spammers"), namesOf(ignore2));
		// Membership does not leak across lists.
		assertEquals(Arrays.asList("PvM"), friends2.groupsFor("Zezima").stream()
			.map(FriendGroup::getName).collect(Collectors.toList()));
		assertTrue(ignore2.groupsFor("Zezima").isEmpty());
		assertTrue(friends2.groupsFor("Bot123").isEmpty());
	}

	// ---- per-character storage ----

	@Test
	public void firstLoadSeedsTheCharacterFromTheSharedGroups()
	{
		final Map<String, Object> shared = new HashMap<>();
		shared.put("groups", "[{\"name\":\"PvM\",\"color\":2,\"members\":[\"Zezima\"]}]");
		shared.put("ungroupedOrder", "0");
		shared.put("ungroupedCollapsed", "true");

		final Map<String, Object> character = new HashMap<>();
		final GroupStore m = new GroupStore(configSeededFrom(shared, character), new Gson(), GroupList.FRIENDS);
		m.load();

		// The groups a user had before this was per character follow them onto it, ordering and all.
		assertEquals(Arrays.asList("PvM"), namesOf(m));
		assertEquals(Arrays.asList("PvM"), groupNamesOf(m, "Zezima"));
		assertEquals(0, m.ungroupedPosition());
		assertTrue(m.isUngroupedCollapsed());
		// ...and are now the character's own copy.
		assertTrue(character.containsKey("groups"));
	}

	@Test
	public void seededGroupsStayDeletedAcrossReloads()
	{
		final Map<String, Object> shared = new HashMap<>();
		shared.put("groups", "[{\"name\":\"PvM\",\"color\":2,\"members\":[\"Zezima\"]}]");

		final Map<String, Object> character = new HashMap<>();
		final GroupStore m = new GroupStore(configSeededFrom(shared, character), new Gson(), GroupList.FRIENDS);
		m.load();
		m.deleteGroup("PvM");

		// Seeding is a one-off per character: a second load must not resurrect what they deleted.
		final GroupStore reloaded = new GroupStore(configSeededFrom(shared, character), new Gson(), GroupList.FRIENDS);
		reloaded.load();

		assertTrue(reloaded.getGroups().isEmpty());
	}

	@Test
	public void charactersSeededFromTheSameGroupsThenDiverge()
	{
		final Map<String, Object> shared = new HashMap<>();
		shared.put("groups", "[{\"name\":\"PvM\",\"color\":2,\"members\":[\"Zezima\"]}]");

		final Map<String, Object> main = new HashMap<>();
		final Map<String, Object> alt = new HashMap<>();
		final GroupStore mainStore = new GroupStore(configSeededFrom(shared, main), new Gson(), GroupList.FRIENDS);
		final GroupStore altStore = new GroupStore(configSeededFrom(shared, alt), new Gson(), GroupList.FRIENDS);
		mainStore.load();
		altStore.load();

		mainStore.addGroup("Raids");
		altStore.deleteGroup("PvM");
		altStore.addGroup("Ironmen");

		// Each character edits their own copy; neither write is visible to the other.
		assertEquals(Arrays.asList("PvM", "Raids"), namesOf(mainStore));
		assertEquals(Arrays.asList("Ironmen"), namesOf(altStore));

		// And that is what each loads back, rather than the seed.
		final GroupStore mainReloaded = new GroupStore(configSeededFrom(shared, main), new Gson(), GroupList.FRIENDS);
		mainReloaded.load();
		assertEquals(Arrays.asList("PvM", "Raids"), namesOf(mainReloaded));
	}

	@Test
	public void withNoCharacterActiveTheStoreServesNothingAndWritesNothing()
	{
		final ConfigManager cm = mock(ConfigManager.class);
		when(cm.getRSProfileKey()).thenReturn(null);

		final GroupStore m = new GroupStore(cm, new Gson(), GroupList.FRIENDS);
		m.load();

		assertFalse(m.isActive());
		assertFalse(m.addGroup("PvM"));
		m.setUngroupedCollapsed(true);
		assertTrue(m.getGroups().isEmpty());
		assertFalse(m.isUngroupedCollapsed());

		// Writes with no character to attribute them to would be dropped by ConfigManager, or worse,
		// land on whichever character logs in next - so none are attempted.
		verify(cm, never()).setRSProfileConfiguration(anyString(), anyString(), any());
		verify(cm, never()).setConfiguration(anyString(), anyString(), anyString());
	}

	private static List<String> namesOf(GroupStore store)
	{
		return store.getGroups().stream().map(FriendGroup::getName).collect(Collectors.toList());
	}

	private static List<String> groupNamesOf(GroupStore store, String friend)
	{
		return store.groupsFor(friend).stream().map(FriendGroup::getName).collect(Collectors.toList());
	}
}
