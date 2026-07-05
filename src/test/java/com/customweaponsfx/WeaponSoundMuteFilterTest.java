package com.customweaponsfx;

import java.util.HashMap;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Covers which default in-game sound-effect ids the plugin silences: the user's configured id list,
 * including parsing of messy input and persistence through the config store.
 */
public class WeaponSoundMuteFilterTest
{
	/** In-memory backend mirroring ConfigManager's string-only persistence. */
	private static final class MapBackend implements CustomWeaponSfxConfigStore.Backend
	{
		final Map<String, String> map = new HashMap<>();

		@Override public String get(String key) { return map.get(key); }
		@Override public void set(String key, Object value) { map.put(key, String.valueOf(value)); }
		@Override public void unset(String key) { map.remove(key); }
	}

	private CustomWeaponSfxConfigStore store;
	private WeaponSoundMuteFilter filter;

	@Before
	public void setUp()
	{
		store = new CustomWeaponSfxConfigStore(new MapBackend());
		filter = new WeaponSoundMuteFilter(store);
	}

	@Test
	public void nothingIsMutedWithoutAnyConfig()
	{
		assertFalse(filter.isMuted(2554));
	}

	@Test
	public void userConfiguredIdsAreMuted()
	{
		filter.setIds("2554, 2555");
		assertTrue(filter.isMuted(2554));
		assertTrue(filter.isMuted(2555));
		assertFalse(filter.isMuted(2556));
	}

	@Test
	public void blankAndInvalidTokensAreIgnored()
	{
		filter.setIds(" 2554 ,, abc, 2555,");
		assertTrue(filter.isMuted(2554));
		assertTrue(filter.isMuted(2555));
		assertFalse(filter.isMuted(0));
	}

	@Test
	public void setIdsPersistsThroughTheStore()
	{
		filter.setIds("12345");
		assertTrue(store.getMutedWeaponSoundIdsRaw().contains("12345"));

		// A fresh filter loading from the same store sees the persisted mute.
		WeaponSoundMuteFilter reloaded = new WeaponSoundMuteFilter(store);
		reloaded.load();
		assertTrue(reloaded.isMuted(12345));
	}

	@Test
	public void clearDropsUserMutes()
	{
		filter.setIds("2554");
		filter.clear();
		assertFalse(filter.isMuted(2554));
	}
}
