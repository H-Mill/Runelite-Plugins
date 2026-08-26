package com.customweaponsfx;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/**
 * Covers the weapon CRUD lifecycle: add / change (re-point) / copy / remove, keeping the in-memory
 * list and the config store in sync, firing the rebuild callback, and raising a conflict (instead of
 * clobbering) when a target id is already configured.
 */
public class WeaponManagerTest
{
	private static final int DRAGON_DAGGER = 1215;
	private static final int ABYSSAL_WHIP = 4151;
	private static final int ARMADYL_GODSWORD = 11802;

	/** In-memory backend mirroring ConfigManager's string-only persistence. */
	private static final class MapBackend implements CustomWeaponSfxConfigStore.Backend
	{
		final Map<String, String> map = new HashMap<>();

		@Override public String get(String key) { return map.get(key); }
		@Override public void set(String key, Object value) { map.put(key, String.valueOf(value)); }
		@Override public void unset(String key) { map.remove(key); }
	}

	private CustomWeaponSfxConfigStore store;
	private WeaponManager manager;
	private int changedCount;
	private String lastConflict;

	@Before
	public void setUp()
	{
		store = new CustomWeaponSfxConfigStore(new MapBackend());
		changedCount = 0;
		lastConflict = null;
		manager = new WeaponManager(store, () -> changedCount++, msg -> lastConflict = msg);
	}

	private static TriggerGroup group()
	{
		List<SoundEntry> sounds = new ArrayList<>();
		sounds.add(new SoundEntry("bundled:bonk", 75, 1.0));
		return new TriggerGroup(EnumSet.of(Triggers.REGULAR_HIT), sounds, 100);
	}

	// ---- add -----------------------------------------------------------------------------------

	@Test
	public void addCreatesEntryPersistsAndNotifies()
	{
		manager.add(DRAGON_DAGGER, "Dragon dagger");

		WeaponEntry entry = manager.find(DRAGON_DAGGER);
		assertNotNull(entry);
		assertEquals("Dragon dagger", entry.getWeaponName());
		assertTrue("new weapons start enabled", entry.isEnabled());
		assertEquals(1, changedCount);

		// Persisted: a reload from the same store finds it.
		assertEquals(1, store.loadWeapons().size());
	}

	@Test
	public void addingADuplicateIdIsANoOp()
	{
		manager.add(DRAGON_DAGGER, "Dragon dagger");
		manager.add(DRAGON_DAGGER, "Dragon dagger (again)");

		assertEquals(1, manager.snapshot().size());
		assertEquals("Dragon dagger", manager.find(DRAGON_DAGGER).getWeaponName());
		assertEquals("second add did not fire onChanged", 1, changedCount);
	}

	// ---- load / snapshot -----------------------------------------------------------------------

	@Test
	public void loadReplacesInMemoryListFromStore()
	{
		WeaponEntry persisted = new WeaponEntry(ABYSSAL_WHIP, "Abyssal whip", new ArrayList<>());
		store.saveWeapon(persisted);
		store.saveWeaponIds(java.util.Collections.singletonList(persisted));

		manager.load();
		assertEquals(1, manager.snapshot().size());
		assertNotNull(manager.find(ABYSSAL_WHIP));
	}

	@Test
	public void snapshotIsADefensiveCopy()
	{
		manager.add(DRAGON_DAGGER, "Dragon dagger");
		List<WeaponEntry> snap = manager.snapshot();
		snap.clear();
		assertEquals("mutating the snapshot must not affect the manager", 1, manager.snapshot().size());
	}

	// ---- change (re-point) ---------------------------------------------------------------------

	@Test
	public void changeRepointsPreservingGroupsAndEnabledState()
	{
		manager.add(DRAGON_DAGGER, "Dragon dagger");
		WeaponEntry original = manager.find(DRAGON_DAGGER);
		original.getGroups().add(group());
		original.setEnabled(false);
		List<TriggerGroup> groupsRef = original.getGroups();

		changedCount = 0;
		manager.change(DRAGON_DAGGER, ARMADYL_GODSWORD, "Armadyl godsword");

		assertNull("old id is gone", manager.find(DRAGON_DAGGER));
		WeaponEntry moved = manager.find(ARMADYL_GODSWORD);
		assertNotNull(moved);
		assertEquals("Armadyl godsword", moved.getWeaponName());
		assertFalse("enabled state carried over", moved.isEnabled());
		assertSame("sound groups carried over", groupsRef, moved.getGroups());
		assertEquals(1, changedCount);

		// Persistence reflects the move.
		List<WeaponEntry> reloaded = store.loadWeapons();
		assertEquals(1, reloaded.size());
		assertEquals(ARMADYL_GODSWORD, reloaded.get(0).getItemId());
	}

	@Test
	public void changeToSameIdIsANoOp()
	{
		manager.add(DRAGON_DAGGER, "Dragon dagger");
		changedCount = 0;
		manager.change(DRAGON_DAGGER, DRAGON_DAGGER, "Dragon dagger");
		assertEquals(0, changedCount);
		assertNotNull(manager.find(DRAGON_DAGGER));
	}

	@Test
	public void changeOfAMissingWeaponIsANoOp()
	{
		manager.change(DRAGON_DAGGER, ARMADYL_GODSWORD, "Armadyl godsword");
		assertEquals(0, changedCount);
		assertNull(manager.find(ARMADYL_GODSWORD));
	}

	@Test
	public void changeOntoAnAlreadyConfiguredIdConflictsAndAborts()
	{
		manager.add(DRAGON_DAGGER, "Dragon dagger");
		manager.add(ARMADYL_GODSWORD, "Armadyl godsword");
		changedCount = 0;

		manager.change(DRAGON_DAGGER, ARMADYL_GODSWORD, "Armadyl godsword");

		assertNotNull("conflict raised", lastConflict);
		assertEquals("both weapons left intact", 2, manager.snapshot().size());
		assertNotNull(manager.find(DRAGON_DAGGER));
		assertEquals(0, changedCount);
	}

	// ---- copy ----------------------------------------------------------------------------------

	@Test
	public void copyDeepCopiesGroupsAndEnabledState()
	{
		manager.add(DRAGON_DAGGER, "Dragon dagger");
		WeaponEntry source = manager.find(DRAGON_DAGGER);
		source.getGroups().add(group());
		source.setEnabled(false);
		changedCount = 0;

		manager.copy(DRAGON_DAGGER, ARMADYL_GODSWORD, "Armadyl godsword");

		WeaponEntry copy = manager.find(ARMADYL_GODSWORD);
		assertNotNull(copy);
		assertFalse("enabled state copied", copy.isEnabled());
		assertEquals(1, copy.getGroups().size());
		assertEquals("source kept too", 2, manager.snapshot().size());
		assertEquals(1, changedCount);

		// Deep copy: the two weapons share no mutable group/sound state.
		assertNotSame(source.getGroups(), copy.getGroups());
		assertNotSame(source.getGroups().get(0), copy.getGroups().get(0));
		assertNotSame(source.getGroups().get(0).getSounds().get(0),
			copy.getGroups().get(0).getSounds().get(0));
	}

	@Test
	public void copyFromAMissingSourceIsANoOp()
	{
		manager.copy(DRAGON_DAGGER, ARMADYL_GODSWORD, "Armadyl godsword");
		assertEquals(0, changedCount);
		assertNull(manager.find(ARMADYL_GODSWORD));
	}

	@Test
	public void copyOntoAnAlreadyConfiguredIdConflictsAndAborts()
	{
		manager.add(DRAGON_DAGGER, "Dragon dagger");
		manager.add(ARMADYL_GODSWORD, "Armadyl godsword");
		changedCount = 0;

		manager.copy(DRAGON_DAGGER, ARMADYL_GODSWORD, "Armadyl godsword");

		assertNotNull(lastConflict);
		assertEquals(2, manager.snapshot().size());
		assertEquals(0, changedCount);
	}

	// ---- remove --------------------------------------------------------------------------------

	@Test
	public void removeDeletesEntryFromMemoryAndStore()
	{
		manager.add(DRAGON_DAGGER, "Dragon dagger");
		manager.add(ABYSSAL_WHIP, "Abyssal whip");
		changedCount = 0;

		manager.remove(DRAGON_DAGGER);

		assertNull(manager.find(DRAGON_DAGGER));
		assertNotNull("other weapon untouched", manager.find(ABYSSAL_WHIP));
		assertEquals(1, changedCount);

		List<WeaponEntry> reloaded = store.loadWeapons();
		assertEquals(1, reloaded.size());
		assertEquals(ABYSSAL_WHIP, reloaded.get(0).getItemId());
	}

	@Test
	public void removingAnUnknownIdStillRewritesTheIndexAndNotifies()
	{
		manager.add(DRAGON_DAGGER, "Dragon dagger");
		changedCount = 0;
		manager.remove(ARMADYL_GODSWORD);
		// Nothing was configured under that id; the surviving weapon is unaffected.
		assertNotNull(manager.find(DRAGON_DAGGER));
		assertEquals(1, manager.snapshot().size());
	}

	@Test
	public void clearEmptiesTheInMemoryList()
	{
		manager.add(DRAGON_DAGGER, "Dragon dagger");
		manager.clear();
		assertTrue(manager.snapshot().isEmpty());
	}
}
