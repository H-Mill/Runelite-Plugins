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
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class CustomWeaponSfxConfigStoreTest
{
	/** In-memory {@link CustomWeaponSfxConfigStore.Backend} mirroring ConfigManager string semantics. */
	private static final class MapBackend implements CustomWeaponSfxConfigStore.Backend
	{
		final Map<String, String> map = new HashMap<>();

		@Override
		public String get(String key)
		{
			return map.get(key);
		}

		@Override
		public void set(String key, Object value)
		{
			// ConfigManager persists everything as strings; mirror that here.
			map.put(key, String.valueOf(value));
		}

		@Override
		public void unset(String key)
		{
			map.remove(key);
		}
	}

	private MapBackend backend;
	private CustomWeaponSfxConfigStore store;

	@Before
	public void setUp()
	{
		backend = new MapBackend();
		store = new CustomWeaponSfxConfigStore(backend);
	}

	private static TriggerGroup group(java.util.Set<Triggers> triggers, int chance, SoundEntry... sounds)
	{
		List<SoundEntry> list = new ArrayList<>();
		for (SoundEntry s : sounds) list.add(s);
		return new TriggerGroup(triggers, list, chance);
	}

	private static void assertGroupEquals(TriggerGroup expected, TriggerGroup actual)
	{
		assertEquals(expected.getTriggers(), actual.getTriggers());
		assertEquals(expected.getAmountConditions().keySet(), actual.getAmountConditions().keySet());
		for (Triggers t : expected.getAmountConditions().keySet())
		{
			assertEquals(expected.getAmountCondition(t).getOp(), actual.getAmountCondition(t).getOp());
			assertEquals(expected.getAmountCondition(t).getValue(), actual.getAmountCondition(t).getValue());
		}
		assertEquals(expected.getChance(), actual.getChance());
		assertEquals(expected.getSounds().size(), actual.getSounds().size());
		for (int i = 0; i < expected.getSounds().size(); i++)
		{
			assertEquals(expected.getSounds().get(i).getSoundFile(), actual.getSounds().get(i).getSoundFile());
			assertEquals(expected.getSounds().get(i).getVolume(), actual.getSounds().get(i).getVolume());
			assertEquals(expected.getSounds().get(i).getWeight(), actual.getSounds().get(i).getWeight(), 0.0001);
		}
	}

	@Test
	public void weaponRoundTripsThroughSaveAndLoad()
	{
		List<TriggerGroup> groups = new ArrayList<>();
		groups.add(group(EnumSet.of(Triggers.REGULAR_HIT, Triggers.KILL), 80,
			new SoundEntry("bundled:bonk", 50, 0.5), new SoundEntry("my-clip", 100, 2.5)));
		groups.add(group(EnumSet.of(Triggers.SPECIAL_MAX), 25, new SoundEntry("", 75)));

		WeaponEntry entry = new WeaponEntry(1234, "Test Weapon", groups);
		entry.setEnabled(false);
		entry.setDontOverrideGlobal(true);

		store.saveWeapon(entry);
		store.saveWeaponIds(java.util.Collections.singletonList(entry));

		List<WeaponEntry> loaded = store.loadWeapons();
		assertEquals(1, loaded.size());

		WeaponEntry back = loaded.get(0);
		assertEquals(1234, back.getItemId());
		assertEquals("Test Weapon", back.getWeaponName());
		assertFalse(back.isEnabled());
		assertTrue(back.isDontOverrideGlobal());
		assertEquals(2, back.getGroups().size());
		assertGroupEquals(groups.get(0), back.getGroups().get(0));
		assertGroupEquals(groups.get(1), back.getGroups().get(1));
	}

	@Test
	public void weaponWithoutPerWeaponFlagInheritsLegacyGlobalDontOverride()
	{
		// A weapon saved before "Don't override Global" became per-weapon: it has groups + id index but
		// no _dontOverrideGlobal key, while the old global toggle was on.
		backend.set("dontOverrideGlobal", true);
		backend.set("specWeapon_555_groupCount", 0);
		backend.set("specWeaponIds", "555");

		WeaponEntry back = store.loadWeapons().get(0);
		assertTrue("legacy global value should carry over to the weapon", back.isDontOverrideGlobal());
	}

	@Test
	public void legacyDontOverrideMigratesForwardAndIsRemoved()
	{
		// Same legacy setup, but assert the one-time migration writes the value onto the weapon's own key
		// and clears the legacy global key, so future loads no longer depend on the fallback.
		backend.set("dontOverrideGlobal", true);
		backend.set("specWeapon_555_groupCount", 0);
		backend.set("specWeaponIds", "555");

		store.loadWeapons();

		assertEquals("per-weapon key should be persisted", "true", backend.map.get("specWeapon_555_dontOverrideGlobal"));
		assertNull("legacy global key should be absorbed", backend.map.get("dontOverrideGlobal"));

		// A second load with the legacy key gone still yields the migrated value.
		assertTrue(store.loadWeapons().get(0).isDontOverrideGlobal());
	}

	@Test
	public void weaponWithoutAnyDontOverrideConfigDefaultsToFalse()
	{
		backend.set("specWeapon_555_groupCount", 0);
		backend.set("specWeaponIds", "555");

		assertFalse(store.loadWeapons().get(0).isDontOverrideGlobal());
	}

	@Test
	public void amountConditionRoundTripsThroughSaveAndLoad()
	{
		String prefix = CustomWeaponSfxPanel.GLOBAL_WEAPON_GROUPS_PREFIX;
		List<TriggerGroup> groups = new ArrayList<>();
		TriggerGroup g = group(EnumSet.of(Triggers.REGULAR_AMOUNT), 100, new SoundEntry("boom", 80));
		g.setAmountCondition(Triggers.REGULAR_AMOUNT, new AmountCondition(AmountCondition.Op.EQUAL, 73));
		groups.add(g);

		store.saveDefaultGroups(prefix, groups);

		TriggerGroup loaded = store.loadDefaultGroups(prefix).get(0);
		AmountCondition c = loaded.getAmountCondition(Triggers.REGULAR_AMOUNT);
		assertEquals(AmountCondition.Op.EQUAL, c.getOp());
		assertEquals(73, c.getValue());
	}

	@Test
	public void defaultGroupsRoundTrip()
	{
		String prefix = CustomWeaponSfxPanel.RECEIVED_GROUPS_PREFIX;
		List<TriggerGroup> groups = new ArrayList<>();
		groups.add(group(EnumSet.of(Triggers.REGULAR_ZERO), 100, new SoundEntry("ouch", 60)));

		store.saveDefaultGroups(prefix, groups);

		List<TriggerGroup> loaded = store.loadDefaultGroups(prefix);
		assertEquals(1, loaded.size());
		assertGroupEquals(groups.get(0), loaded.get(0));
	}

	@Test
	public void groupNameRoundTrips()
	{
		String prefix = CustomWeaponSfxPanel.GLOBAL_WEAPON_GROUPS_PREFIX;
		List<TriggerGroup> groups = new ArrayList<>();
		TriggerGroup g = group(EnumSet.of(Triggers.ALL), 100, new SoundEntry("g", 50));
		g.setName("My Custom Name");
		groups.add(g);

		store.saveDefaultGroups(prefix, groups);
		assertEquals("My Custom Name", store.loadDefaultGroups(prefix).get(0).getName());
	}

	@Test
	public void groupWithoutStoredNameLoadsPositionalDefault()
	{
		// A pre-name config: groupCount present, no _name key on either group.
		backend.set("globalWeapon_groupCount", 2);
		backend.set("globalWeapon_group_0_triggers", "ALL");
		backend.set("globalWeapon_group_1_triggers", "KILL");

		List<TriggerGroup> loaded = store.loadDefaultGroups("globalWeapon");
		assertEquals("Sound Group 1", loaded.get(0).getName());
		assertEquals("Sound Group 2", loaded.get(1).getName());
	}

	@Test
	public void missingDefaultGroupsLoadEmpty()
	{
		assertTrue(store.loadDefaultGroups(CustomWeaponSfxPanel.GLOBAL_WEAPON_GROUPS_PREFIX).isEmpty());
	}

	@Test
	public void loadGroupAppliesDefaultsForMissingFields()
	{
		// Only a groupCount + triggers persisted; chance/volume/soundCount should fall back to defaults.
		backend.set("globalWeapon_groupCount", 1);
		backend.set("globalWeapon_group_0_triggers", "ALL");

		List<TriggerGroup> loaded = store.loadDefaultGroups("globalWeapon");
		assertEquals(1, loaded.size());
		TriggerGroup g = loaded.get(0);
		assertEquals(EnumSet.of(Triggers.ALL), g.getTriggers());
		assertEquals(100, g.getChance());          // DEFAULT_CHANCE
		assertEquals(1, g.getSounds().size());      // DEFAULT_SOUND_COUNT
		assertEquals("", g.getSounds().get(0).getSoundFile());
		assertEquals(75, g.getSounds().get(0).getVolume()); // DEFAULT_VOLUME
		assertEquals(1.0, g.getSounds().get(0).getWeight(), 0.0001); // SoundEntry.DEFAULT_WEIGHT
	}

	@Test
	public void weaponWithMissingGroupCountIsSkipped()
	{
		backend.set("specWeaponIds", "55");
		backend.set("specWeapon_55_name", "Orphan");
		// no groupCount key — parity with old loader: weapon is dropped entirely
		assertTrue(store.loadWeapons().isEmpty());
	}

	@Test
	public void invalidWeaponIdsAreIgnoredButValidOnesKept()
	{
		WeaponEntry valid = new WeaponEntry(7, "Valid", new ArrayList<>());
		store.saveWeapon(valid);
		backend.set("specWeaponIds", "notanumber,7");

		List<WeaponEntry> loaded = store.loadWeapons();
		assertEquals(1, loaded.size());
		assertEquals(7, loaded.get(0).getItemId());
	}

	@Test
	public void removeWeaponUnsetsAllItsKeys()
	{
		List<TriggerGroup> groups = new ArrayList<>();
		groups.add(group(EnumSet.of(Triggers.REGULAR_HIT), 100, new SoundEntry("s", 10)));
		WeaponEntry entry = new WeaponEntry(99, "Doomed", groups);
		store.saveWeapon(entry);

		store.removeWeapon(entry);

		assertNull(backend.get("specWeapon_99_name"));
		assertNull(backend.get("specWeapon_99_enabled"));
		assertNull(backend.get("specWeapon_99_groupCount"));
		assertNull(backend.get("specWeapon_99_group_0_triggers"));
		assertNull(backend.get("specWeapon_99_group_0_chance"));
		assertNull(backend.get("specWeapon_99_group_0_soundCount"));
		assertNull(backend.get("specWeapon_99_group_0_sound_0"));
		assertNull(backend.get("specWeapon_99_group_0_volume_0"));
		assertNull(backend.get("specWeapon_99_group_0_weight_0"));
	}

	@Test
	public void resetAllWipesWeaponsAndDefaults()
	{
		List<TriggerGroup> wGroups = new ArrayList<>();
		wGroups.add(group(EnumSet.of(Triggers.KILL), 100, new SoundEntry("a", 50)));
		WeaponEntry weapon = new WeaponEntry(3, "W", wGroups);
		store.saveWeapon(weapon);
		store.saveWeaponIds(java.util.Collections.singletonList(weapon));

		List<TriggerGroup> received = new ArrayList<>();
		received.add(group(EnumSet.of(Triggers.REGULAR_ZERO), 100, new SoundEntry("r", 50)));
		store.saveDefaultGroups(CustomWeaponSfxPanel.RECEIVED_GROUPS_PREFIX, received);

		List<TriggerGroup> global = new ArrayList<>();
		global.add(group(EnumSet.of(Triggers.ALL), 100, new SoundEntry("g", 50)));
		store.saveDefaultGroups(CustomWeaponSfxPanel.GLOBAL_WEAPON_GROUPS_PREFIX, global);

		store.resetAll(java.util.Collections.singletonList(weapon),
			CustomWeaponSfxPanel.RECEIVED_GROUPS_PREFIX, received,
			CustomWeaponSfxPanel.GLOBAL_WEAPON_GROUPS_PREFIX, global);

		assertNull(backend.get("specWeaponIds"));
		assertTrue(store.loadWeapons().isEmpty());
		assertTrue(store.loadDefaultGroups(CustomWeaponSfxPanel.RECEIVED_GROUPS_PREFIX).isEmpty());
		assertTrue(store.loadDefaultGroups(CustomWeaponSfxPanel.GLOBAL_WEAPON_GROUPS_PREFIX).isEmpty());
	}

	@Test
	public void groupBlacklistRoundTripsThroughSaveAndLoad()
	{
		String prefix = CustomWeaponSfxPanel.GLOBAL_WEAPON_GROUPS_PREFIX;
		List<TriggerGroup> groups = new ArrayList<>();
		TriggerGroup g = group(EnumSet.of(Triggers.ALL), 100, new SoundEntry("g", 50));
		g.addToBlacklist(4151, "Abyssal whip");
		g.addToBlacklist(1215, "Dragon dagger");
		groups.add(g);

		store.saveDefaultGroups(prefix, groups);

		TriggerGroup loaded = store.loadDefaultGroups(prefix).get(0);
		assertEquals(2, loaded.getBlacklist().size());
		assertTrue(loaded.isBlacklisted(4151));
		assertTrue(loaded.isBlacklisted(1215));
		assertEquals("Abyssal whip", loaded.getBlacklist().get(0).getWeaponName());
		assertEquals("Dragon dagger", loaded.getBlacklist().get(1).getWeaponName());
	}

	@Test
	public void groupWithoutBlacklistLoadsEmpty()
	{
		backend.set("globalWeapon_groupCount", 1);
		backend.set("globalWeapon_group_0_triggers", "ALL");
		assertTrue(store.loadDefaultGroups("globalWeapon").get(0).getBlacklist().isEmpty());
	}

	@Test
	public void getBoolReturnsDefaultWhenAbsentAndParsesWhenPresent()
	{
		assertTrue(store.getBool("missingKey", true));
		assertFalse(store.getBool("missingKey", false));

		store.setBool("someFlag", false);
		assertFalse(store.getBool("someFlag", true));

		store.setBool("someFlag", true);
		assertTrue(store.getBool("someFlag", false));
	}

	@Test
	public void saveWeaponEnabledOnlyTouchesEnabledKey()
	{
		store.saveWeaponEnabled(42, false);
		assertEquals("false", backend.get("specWeapon_42_enabled"));
		assertNull(backend.get("specWeapon_42_name"));
		assertNull(backend.get("specWeapon_42_groupCount"));
	}

	@Test
	public void excludedNpcIdsRoundTripAndDefaultEmpty()
	{
		assertEquals("", store.getExcludedNpcIdsRaw());

		store.setExcludedNpcIds("11706, 11707");
		assertEquals("11706, 11707", store.getExcludedNpcIdsRaw());
	}

	@Test
	public void parseNpcIdsSkipsBlankAndInvalidTokens()
	{
		java.util.Set<Integer> ids = CustomWeaponSfxConfigStore.parseNpcIds(" 11706 ,, abc, 11707,");
		assertEquals(new java.util.HashSet<>(java.util.Arrays.asList(11706, 11707)), ids);

		assertTrue(CustomWeaponSfxConfigStore.parseNpcIds(null).isEmpty());
		assertTrue(CustomWeaponSfxConfigStore.parseNpcIds("").isEmpty());
	}

	@Test
	public void resetAllUnsetsExcludedNpcIds()
	{
		store.setExcludedNpcIds("11706");
		store.resetAll(java.util.Collections.emptyList(),
			CustomWeaponSfxPanel.RECEIVED_GROUPS_PREFIX, new ArrayList<>(),
			CustomWeaponSfxPanel.GLOBAL_WEAPON_GROUPS_PREFIX, new ArrayList<>());
		assertEquals("", store.getExcludedNpcIdsRaw());
	}

	@Test
	public void excludedNpcNamesRoundTripAndDefaultEmpty()
	{
		assertEquals("", store.getExcludedNpcNamesRaw());

		store.setExcludedNpcNames("Vorkath, Zulrah");
		assertEquals("Vorkath, Zulrah", store.getExcludedNpcNamesRaw());
	}

	@Test
	public void parseNpcNamesLowercasesTrimsAndSkipsBlanks()
	{
		java.util.Set<String> names = CustomWeaponSfxConfigStore.parseNpcNames(" Vorkath ,, ZULRAH,");
		assertEquals(new java.util.HashSet<>(java.util.Arrays.asList("vorkath", "zulrah")), names);

		assertTrue(CustomWeaponSfxConfigStore.parseNpcNames(null).isEmpty());
		assertTrue(CustomWeaponSfxConfigStore.parseNpcNames("").isEmpty());
	}

	@Test
	public void resetAllUnsetsExcludedNpcNames()
	{
		store.setExcludedNpcNames("Vorkath");
		store.resetAll(java.util.Collections.emptyList(),
			CustomWeaponSfxPanel.RECEIVED_GROUPS_PREFIX, new ArrayList<>(),
			CustomWeaponSfxPanel.GLOBAL_WEAPON_GROUPS_PREFIX, new ArrayList<>());
		assertEquals("", store.getExcludedNpcNamesRaw());
	}

	@Test
	public void mutedWeaponSoundIdsRoundTripAndDefaultEmpty()
	{
		assertEquals("", store.getMutedWeaponSoundIdsRaw());

		store.setMutedWeaponSoundIds("2554, 2555");
		assertEquals("2554, 2555", store.getMutedWeaponSoundIdsRaw());
	}

	@Test
	public void parseSoundIdsSkipsBlankAndInvalidTokens()
	{
		java.util.Set<Integer> ids = CustomWeaponSfxConfigStore.parseSoundIds(" 2554 ,, abc, 2555,");
		assertEquals(new java.util.HashSet<>(java.util.Arrays.asList(2554, 2555)), ids);

		assertTrue(CustomWeaponSfxConfigStore.parseSoundIds(null).isEmpty());
		assertTrue(CustomWeaponSfxConfigStore.parseSoundIds("").isEmpty());
	}

	@Test
	public void resetAllUnsetsMutedWeaponSoundIds()
	{
		store.setMutedWeaponSoundIds("2554");
		store.resetAll(java.util.Collections.emptyList(),
			CustomWeaponSfxPanel.RECEIVED_GROUPS_PREFIX, new ArrayList<>(),
			CustomWeaponSfxPanel.GLOBAL_WEAPON_GROUPS_PREFIX, new ArrayList<>());
		assertEquals("", store.getMutedWeaponSoundIdsRaw());
	}
}
