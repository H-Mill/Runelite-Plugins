package com.customweaponsfx;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.config.ConfigManager;

/**
 * Single source of truth for all Custom Weapon SFX config-key construction and (de)serialization.
 * No other class should build a config key string — everything goes through this store.
 *
 * <p>The flat key scheme persisted here:
 * <ul>
 *   <li>{@code specWeaponIds} — comma-joined index of configured weapon item ids.</li>
 *   <li>Per weapon {@code specWeapon_<id>}: {@code _name}, {@code _enabled}, {@code _groupCount},
 *       plus its groups.</li>
 *   <li>A "default" section (e.g. {@code defaultReceived}, {@code globalWeapon}): {@code _groupCount},
 *       {@code _enabled}, plus its groups.</li>
 *   <li>A group {@code <section>_group_<i>}: {@code _name}, {@code _triggers}, {@code _chance},
 *       {@code _soundCount}, and per sound {@code _sound_<j>} / {@code _volume_<j>}.</li>
 * </ul>
 *
 * <p>The store reads/writes through a small {@link Backend} seam so its serialize→deserialize
 * round-trip can be unit-tested against an in-memory map instead of a live {@code ConfigManager}.
 */
@Slf4j
class CustomWeaponSfxConfigStore
{
	/** Minimal key-value seam over {@link ConfigManager}, scoped to {@code CONFIG_GROUP}. */
	interface Backend
	{
		String get(String key);
		void set(String key, Object value);
		void unset(String key);
	}

	private static final String WEAPON_IDS_KEY = "specWeaponIds";
	private static final String EXCLUDED_NPC_IDS_KEY = "excludedNpcIds";
	private static final String EXCLUDED_NPC_NAMES_KEY = "excludedNpcNames";
	private static final String MUTED_WEAPON_SOUND_IDS_KEY = "mutedWeaponSoundIds";
	/**
	 * Legacy global "Don't override Global" key, superseded in 2.11 by a per-weapon flag. Read only, for the
	 * one-time migration in {@link #loadWeapons()}. TODO(2.12): delete once that migration is removed.
	 */
	private static final String LEGACY_DONT_OVERRIDE_GLOBAL_KEY = "dontOverrideGlobal";

	private static final int DEFAULT_CHANCE = 100;
	private static final int DEFAULT_VOLUME = 75;
	private static final int DEFAULT_SOUND_COUNT = 1;

	private final Backend backend;

	CustomWeaponSfxConfigStore(ConfigManager cfg)
	{
		this(new ConfigManagerBackend(cfg));
	}

	CustomWeaponSfxConfigStore(Backend backend)
	{
		this.backend = backend;
	}

	// ----- weapons --------------------------------------------------------------------------

	List<WeaponEntry> loadWeapons()
	{
		List<WeaponEntry> result = new ArrayList<>();
		String idsStr = backend.get(WEAPON_IDS_KEY);
		if (idsStr == null || idsStr.isBlank()) return result;

		for (String idStr : idsStr.split(","))
		{
			Integer itemId = tryParse(idStr);
			if (itemId == null)
			{
				log.debug("Skipping invalid weapon ID in config: {}", idStr);
				continue;
			}

			String section = weaponSection(itemId);
			List<TriggerGroup> groups = loadGroupsOrNull(section);
			if (groups == null)
			{
				// missing or invalid groupCount — drop the weapon entirely (parity with old loader)
				continue;
			}

			String name = backend.get(section + "_name");
			WeaponEntry entry = new WeaponEntry(itemId, name != null ? name : "Weapon #" + itemId, groups);
			String enabledStr = backend.get(section + "_enabled");
			if (enabledStr != null) entry.setEnabled(Boolean.parseBoolean(enabledStr));
			// MIGRATION (added 2.11) — "Don't override Global" was once a single global toggle; weapons
			// saved before it became per-weapon have no key of their own, so they inherit the old global
			// value and we write it straight back as their own key. Once every weapon owns a key the legacy
			// global key is unset below, so this only ever runs once per upgraded install.
			String dogStr = backend.get(section + "_dontOverrideGlobal");
			if (dogStr != null)
			{
				entry.setDontOverrideGlobal(Boolean.parseBoolean(dogStr));
			}
			else
			{
				entry.setDontOverrideGlobal(getBool(LEGACY_DONT_OVERRIDE_GLOBAL_KEY, false));
				backend.set(section + "_dontOverrideGlobal", entry.isDontOverrideGlobal());
			}
			result.add(entry);
		}
		// Every weapon now carries its own key, so the legacy global toggle is fully absorbed.
		// TODO(2.12): once 2.11 has shipped long enough that every active install has loaded once,
		// this migration can be deleted — remove the legacy fallback above, this unset, the
		// LEGACY_DONT_OVERRIDE_GLOBAL_KEY constant, and its unset in resetAll().
		backend.unset(LEGACY_DONT_OVERRIDE_GLOBAL_KEY);
		return result;
	}

	/** Writes name + enabled + dontOverrideGlobal + all groups for one weapon. Does not touch the id index. */
	void saveWeapon(WeaponEntry entry)
	{
		String section = weaponSection(entry.getItemId());
		backend.set(section + "_name", entry.getWeaponName());
		backend.set(section + "_enabled", entry.isEnabled());
		backend.set(section + "_dontOverrideGlobal", entry.isDontOverrideGlobal());
		saveGroups(section, entry.getGroups());
	}

	/** Writes only the group data for a weapon (used by panel edits). */
	void saveWeaponGroups(WeaponEntry entry)
	{
		saveGroups(weaponSection(entry.getItemId()), entry.getGroups());
	}

	void saveWeaponEnabled(int itemId, boolean enabled)
	{
		backend.set(weaponSection(itemId) + "_enabled", enabled);
	}

	void saveWeaponDontOverrideGlobal(int itemId, boolean dontOverrideGlobal)
	{
		backend.set(weaponSection(itemId) + "_dontOverrideGlobal", dontOverrideGlobal);
	}

	/** Unsets every key for one weapon. Caller is responsible for updating the id index. */
	void removeWeapon(WeaponEntry entry)
	{
		String section = weaponSection(entry.getItemId());
		clearGroups(section, entry.getGroups());
		backend.unset(section + "_name");
		backend.unset(section + "_enabled");
		backend.unset(section + "_dontOverrideGlobal");
	}

	void saveWeaponIds(List<WeaponEntry> weapons)
	{
		String ids = weapons.stream()
			.map(e -> String.valueOf(e.getItemId()))
			.collect(Collectors.joining(","));
		backend.set(WEAPON_IDS_KEY, ids);
	}

	// ----- excluded NPC ids -----------------------------------------------------------------

	/** The raw, user-entered comma-separated NPC id string (empty string if unset). */
	String getExcludedNpcIdsRaw()
	{
		String v = backend.get(EXCLUDED_NPC_IDS_KEY);
		return v != null ? v : "";
	}

	void setExcludedNpcIds(String raw)
	{
		backend.set(EXCLUDED_NPC_IDS_KEY, raw == null ? "" : raw);
	}

	/** Parses a comma-separated NPC id string into a set, silently skipping blank/invalid tokens. */
	static Set<Integer> parseNpcIds(String raw)
	{
		Set<Integer> ids = new HashSet<>();
		if (raw == null) return ids;
		for (String token : raw.split(","))
		{
			Integer id = tryParse(token);
			if (id != null) ids.add(id);
		}
		return ids;
	}

	// ----- excluded NPC names ---------------------------------------------------------------

	/** The raw, user-entered comma-separated NPC name string (empty string if unset). */
	String getExcludedNpcNamesRaw()
	{
		String v = backend.get(EXCLUDED_NPC_NAMES_KEY);
		return v != null ? v : "";
	}

	void setExcludedNpcNames(String raw)
	{
		backend.set(EXCLUDED_NPC_NAMES_KEY, raw == null ? "" : raw);
	}

	/**
	 * Parses a comma-separated NPC name string into a set of lowercased, trimmed names for
	 * case-insensitive matching, silently skipping blank tokens.
	 */
	static Set<String> parseNpcNames(String raw)
	{
		Set<String> names = new HashSet<>();
		if (raw == null) return names;
		for (String token : raw.split(","))
		{
			String name = token.trim().toLowerCase();
			if (!name.isEmpty()) names.add(name);
		}
		return names;
	}

	// ----- muted default weapon sounds ------------------------------------------------------

	/** The raw, user-entered comma-separated game sound-effect id string (empty string if unset). */
	String getMutedWeaponSoundIdsRaw()
	{
		String v = backend.get(MUTED_WEAPON_SOUND_IDS_KEY);
		return v != null ? v : "";
	}

	void setMutedWeaponSoundIds(String raw)
	{
		backend.set(MUTED_WEAPON_SOUND_IDS_KEY, raw == null ? "" : raw);
	}

	/** Parses a comma-separated sound-effect id string into a set, silently skipping blank/invalid tokens. */
	static Set<Integer> parseSoundIds(String raw)
	{
		return parseNpcIds(raw);
	}

	// ----- default sections (received / global) ---------------------------------------------

	List<TriggerGroup> loadDefaultGroups(String prefix)
	{
		List<TriggerGroup> groups = loadGroupsOrNull(prefix);
		return groups != null ? groups : new ArrayList<>();
	}

	void saveDefaultGroups(String prefix, List<TriggerGroup> groups)
	{
		saveGroups(prefix, groups);
	}

	void clearDefaultGroups(String prefix, List<TriggerGroup> groups)
	{
		clearGroups(prefix, groups);
	}

	// ----- booleans -------------------------------------------------------------------------

	boolean getBool(String key, boolean def)
	{
		String v = backend.get(key);
		return v == null ? def : Boolean.parseBoolean(v);
	}

	boolean getBool(SfxOption option)
	{
		return getBool(option.configKey(), option.defaultValue());
	}

	void setBool(String key, boolean value)
	{
		backend.set(key, value);
	}

	// ----- reset ----------------------------------------------------------------------------

	/** Wipes all weapons (keys + id index) and both default sections. Booleans are left to the caller. */
	void resetAll(List<WeaponEntry> weapons,
		String receivedPrefix, List<TriggerGroup> receivedGroups,
		String globalPrefix, List<TriggerGroup> globalGroups)
	{
		for (WeaponEntry entry : weapons) removeWeapon(entry);
		backend.unset(WEAPON_IDS_KEY);
		backend.unset(EXCLUDED_NPC_IDS_KEY);
		backend.unset(EXCLUDED_NPC_NAMES_KEY);
		backend.unset(MUTED_WEAPON_SOUND_IDS_KEY);
		backend.unset(LEGACY_DONT_OVERRIDE_GLOBAL_KEY); // TODO(2.12): delete with the load-time migration
		clearDefaultGroups(receivedPrefix, receivedGroups);
		clearDefaultGroups(globalPrefix, globalGroups);
	}

	// ----- internals ------------------------------------------------------------------------

	private static String weaponSection(int itemId)
	{
		return "specWeapon_" + itemId;
	}

	private static String groupKey(String section, int i)
	{
		return section + "_group_" + i;
	}

	/** Returns the section's groups, or {@code null} if its groupCount is missing or unparseable. */
	private List<TriggerGroup> loadGroupsOrNull(String section)
	{
		String countStr = backend.get(section + "_groupCount");
		if (countStr == null || countStr.isBlank()) return null;
		Integer count = tryParse(countStr);
		if (count == null)
		{
			log.debug("Skipping invalid groupCount for {}", section);
			return null;
		}

		List<TriggerGroup> groups = new ArrayList<>();
		for (int i = 0; i < count; i++)
			groups.add(loadGroup(groupKey(section, i), i));
		return groups;
	}

	private TriggerGroup loadGroup(String gk, int index)
	{
		Set<Triggers> triggers = TriggerGroup.deserializeTriggers(backend.get(gk + "_triggers"));
		int chance = parseIntOr(backend.get(gk + "_chance"), DEFAULT_CHANCE);
		int soundCount = parseIntOr(backend.get(gk + "_soundCount"), DEFAULT_SOUND_COUNT);

		List<SoundEntry> sounds = new ArrayList<>();
		for (int j = 0; j < soundCount; j++)
		{
			String sf = backend.get(gk + "_sound_" + j);
			int vol = parseIntOr(backend.get(gk + "_volume_" + j), DEFAULT_VOLUME);
			double weight = parseDoubleOr(backend.get(gk + "_weight_" + j), SoundEntry.DEFAULT_WEIGHT);
			sounds.add(new SoundEntry(sf != null ? sf : "", vol, weight));
		}

		TriggerGroup group = new TriggerGroup(triggers, sounds, chance);
		// Pre-name configs (and any with a blank name) fall back to the positional default.
		String name = backend.get(gk + "_name");
		group.setName(name != null && !name.isBlank() ? name : "Sound Group " + (index + 1));
		return group;
	}

	private void saveGroups(String section, List<TriggerGroup> groups)
	{
		backend.set(section + "_groupCount", groups.size());
		for (int i = 0; i < groups.size(); i++)
			saveGroup(groupKey(section, i), groups.get(i));
	}

	private void saveGroup(String gk, TriggerGroup g)
	{
		if (g.getName() != null) backend.set(gk + "_name", g.getName());
		else backend.unset(gk + "_name");
		backend.set(gk + "_triggers", TriggerGroup.serializeTriggers(g.getTriggers()));
		backend.set(gk + "_chance", g.getChance());
		List<SoundEntry> sounds = g.getSounds();
		backend.set(gk + "_soundCount", sounds.size());
		for (int j = 0; j < sounds.size(); j++)
		{
			backend.set(gk + "_sound_" + j, sounds.get(j).getSoundFile());
			backend.set(gk + "_volume_" + j, sounds.get(j).getVolume());
			backend.set(gk + "_weight_" + j, sounds.get(j).getWeight());
		}
	}

	private void clearGroups(String section, List<TriggerGroup> groups)
	{
		backend.unset(section + "_groupCount");
		for (int i = 0; i < groups.size(); i++)
			clearGroup(groupKey(section, i), groups.get(i).getSounds());
	}

	private void clearGroup(String gk, List<SoundEntry> sounds)
	{
		backend.unset(gk + "_name");
		backend.unset(gk + "_triggers");
		backend.unset(gk + "_chance");
		backend.unset(gk + "_soundCount");
		for (int j = 0; j < sounds.size(); j++)
		{
			backend.unset(gk + "_sound_" + j);
			backend.unset(gk + "_volume_" + j);
			backend.unset(gk + "_weight_" + j);
		}
	}

	private static Integer tryParse(String s)
	{
		if (s == null) return null;
		try { return Integer.parseInt(s.trim()); }
		catch (NumberFormatException e) { return null; }
	}

	private static int parseIntOr(String s, int def)
	{
		Integer v = tryParse(s);
		return v != null ? v : def;
	}

	private static double parseDoubleOr(String s, double def)
	{
		if (s == null) return def;
		try { return Double.parseDouble(s.trim()); }
		catch (NumberFormatException e) { return def; }
	}

	private static final class ConfigManagerBackend implements Backend
	{
		private final ConfigManager cfg;

		ConfigManagerBackend(ConfigManager cfg) { this.cfg = cfg; }

		@Override
		public String get(String key)
		{
			return cfg.getConfiguration(CustomWeaponSfxPlugin.CONFIG_GROUP, key);
		}

		@Override
		public void set(String key, Object value)
		{
			cfg.setConfiguration(CustomWeaponSfxPlugin.CONFIG_GROUP, key, value);
		}

		@Override
		public void unset(String key)
		{
			cfg.unsetConfiguration(CustomWeaponSfxPlugin.CONFIG_GROUP, key);
		}
	}
}
