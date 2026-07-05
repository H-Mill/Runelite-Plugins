package com.customweaponsfx;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import lombok.Getter;
import lombok.Setter;

@Getter
public class TriggerGroup
{
	private final Set<Triggers> triggers;
	private final List<SoundEntry> sounds;
	@Setter private int chance;
	@Setter private String name;

	public TriggerGroup(Set<Triggers> triggers, List<SoundEntry> sounds, int chance)
	{
		this.triggers = triggers != null ? triggers : EnumSet.noneOf(Triggers.class);
		this.sounds = sounds != null ? sounds : new ArrayList<>();
		this.chance = chance;
	}

	/**
	 * True if {@code name} (case-insensitively) already belongs to a group in {@code groups},
	 * skipping {@code exclude} (pass the group being renamed so it doesn't clash with itself).
	 */
	public static boolean isNameTaken(List<TriggerGroup> groups, String name, TriggerGroup exclude)
	{
		if (name == null) return false;
		for (TriggerGroup g : groups)
		{
			if (g == exclude) continue;
			if (name.equalsIgnoreCase(g.getName())) return true;
		}
		return false;
	}

	/** Lowest unused {@code "Sound Group N"} name for a fresh group in {@code groups}. */
	public static String defaultName(List<TriggerGroup> groups)
	{
		int n = 1;
		while (isNameTaken(groups, "Sound Group " + n, null)) n++;
		return "Sound Group " + n;
	}

	public static String serializeTriggers(Set<Triggers> triggers)
	{
		if (triggers == null || triggers.isEmpty()) return "";
		StringBuilder sb = new StringBuilder();
		for (Triggers t : triggers)
		{
			if (sb.length() > 0) sb.append(',');
			sb.append(t.name());
		}
		return sb.toString();
	}

	public static Set<Triggers> deserializeTriggers(String s)
	{
		Set<Triggers> set = EnumSet.noneOf(Triggers.class);
		if (s == null || s.isBlank()) return set;
		for (String part : s.split(","))
		{
			part = part.trim();
			if (part.isEmpty()) continue;
			try { set.add(Triggers.valueOf(part)); }
			catch (IllegalArgumentException ignored) {}
		}
		return set;
	}
}
