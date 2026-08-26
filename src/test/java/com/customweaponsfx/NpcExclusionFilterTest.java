package com.customweaponsfx;

import java.util.HashMap;
import java.util.Map;
import net.runelite.api.NPC;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Covers which NPC hitsplats the plugin ignores: the built-in id blocklist plus the user's
 * configured id/name exclusions, including case-insensitive name matching and persistence through
 * the config store.
 */
public class NpcExclusionFilterTest
{
	/** A built-in excluded id (see {@code NpcExclusionFilter.EXCLUDED_NPC_IDS}). */
	private static final int BUILT_IN_EXCLUDED = 11706;

	/** In-memory backend mirroring ConfigManager's string-only persistence. */
	private static final class MapBackend implements CustomWeaponSfxConfigStore.Backend
	{
		final Map<String, String> map = new HashMap<>();

		@Override public String get(String key) { return map.get(key); }
		@Override public void set(String key, Object value) { map.put(key, String.valueOf(value)); }
		@Override public void unset(String key) { map.remove(key); }
	}

	private MapBackend backend;
	private CustomWeaponSfxConfigStore store;
	private NpcExclusionFilter filter;

	@Before
	public void setUp()
	{
		backend = new MapBackend();
		store = new CustomWeaponSfxConfigStore(backend);
		filter = new NpcExclusionFilter(store);
	}

	private static NPC npc(int id, String name)
	{
		NPC npc = mock(NPC.class);
		when(npc.getId()).thenReturn(id);
		when(npc.getName()).thenReturn(name);
		return npc;
	}

	@Test
	public void builtInIdIsExcludedWithoutAnyConfig()
	{
		assertTrue(filter.isExcluded(npc(BUILT_IN_EXCLUDED, "Whatever")));
	}

	@Test
	public void unconfiguredNpcIsNotExcluded()
	{
		assertFalse(filter.isExcluded(npc(99, "Goblin")));
	}

	@Test
	public void userConfiguredIdIsExcluded()
	{
		filter.setIds("99, 100");
		assertTrue(filter.isExcluded(npc(99, "Goblin")));
		assertTrue(filter.isExcluded(npc(100, "Goblin")));
		assertFalse(filter.isExcluded(npc(101, "Goblin")));
	}

	@Test
	public void userConfiguredNameIsExcludedCaseInsensitivelyAndTrimmed()
	{
		filter.setNames("Vorkath");
		assertTrue("exact", filter.isExcluded(npc(1, "Vorkath")));
		assertTrue("different case", filter.isExcluded(npc(1, "VORKATH")));
		assertTrue("surrounding whitespace on the target name", filter.isExcluded(npc(1, "  vorkath ")));
		assertFalse(filter.isExcluded(npc(1, "Zulrah")));
	}

	@Test
	public void nullNpcNameDoesNotMatchAndDoesNotThrow()
	{
		filter.setNames("Vorkath");
		assertFalse(filter.isExcluded(npc(1, null)));
	}

	@Test
	public void setIdsPersistsThroughTheStore()
	{
		filter.setIds("12345");
		assertTrue(store.getExcludedNpcIdsRaw().contains("12345"));

		// A fresh filter loading from the same store sees the persisted exclusion.
		NpcExclusionFilter reloaded = new NpcExclusionFilter(store);
		reloaded.load();
		assertTrue(reloaded.isExcluded(npc(12345, "Boss")));
	}

	@Test
	public void setNamesPersistsThroughTheStore()
	{
		filter.setNames("Zulrah");
		NpcExclusionFilter reloaded = new NpcExclusionFilter(store);
		reloaded.load();
		assertTrue(reloaded.isExcluded(npc(1, "Zulrah")));
	}

	@Test
	public void clearDropsUserExclusionsButKeepsBuiltIns()
	{
		filter.setIds("99");
		filter.setNames("Vorkath");

		filter.clear();

		assertFalse("user id exclusion gone", filter.isExcluded(npc(99, "Goblin")));
		assertFalse("user name exclusion gone", filter.isExcluded(npc(1, "Vorkath")));
		assertTrue("built-in still excluded", filter.isExcluded(npc(BUILT_IN_EXCLUDED, "x")));
	}
}
