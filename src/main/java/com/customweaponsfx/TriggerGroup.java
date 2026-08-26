package com.customweaponsfx;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.Getter;
import lombok.Setter;

@Getter
public class TriggerGroup
{
	private final Set<Triggers> triggers;
	private final List<SoundEntry> sounds;
	/** Per-trigger numeric comparisons for amount-based triggers (see {@link Triggers#isAmount()}). */
	private final Map<Triggers, AmountCondition> amountConditions = new EnumMap<>(Triggers.class);
	/**
	 * Weapons for which this group is suppressed. Only meaningful for Global (All Weapons) groups: when
	 * the attacking weapon is listed here, this group does not play (its weapon-specific and other Global
	 * groups are unaffected).
	 */
	private final List<BlacklistEntry> blacklist = new ArrayList<>();
	@Setter private int chance;
	@Setter private String name;

	public TriggerGroup(Set<Triggers> triggers, List<SoundEntry> sounds, int chance)
	{
		this.triggers = triggers != null ? triggers : EnumSet.noneOf(Triggers.class);
		this.sounds = sounds != null ? sounds : new ArrayList<>();
		this.chance = chance;
	}

	/** The {@link AmountCondition} configured for {@code trigger}, or {@code null} if none. */
	public AmountCondition getAmountCondition(Triggers trigger)
	{
		return amountConditions.get(trigger);
	}

	/** Sets (or, when {@code condition} is null, clears) the comparison for an amount-based trigger. */
	public void setAmountCondition(Triggers trigger, AmountCondition condition)
	{
		if (condition == null) amountConditions.remove(trigger);
		else amountConditions.put(trigger, condition);
	}

	/** True if {@code itemId} is on this group's blacklist, so the group should not play for that weapon. */
	public boolean isBlacklisted(int itemId)
	{
		for (BlacklistEntry e : blacklist)
		{
			if (e.getItemId() == itemId) return true;
		}
		return false;
	}

	/** Adds {@code itemId} to this group's blacklist; returns {@code false} (no-op) if already present. */
	public boolean addToBlacklist(int itemId, String name)
	{
		if (isBlacklisted(itemId)) return false;
		blacklist.add(new BlacklistEntry(itemId, name));
		return true;
	}

	/** Removes {@code itemId} from this group's blacklist. */
	public void removeFromBlacklist(int itemId)
	{
		blacklist.removeIf(e -> e.getItemId() == itemId);
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

	/**
	 * Serializes amount-trigger conditions to {@code "TRIGGER=OP:value;TRIGGER=OP:value"}, e.g.
	 * {@code "REGULAR_AMOUNT=EQUAL:73"}. Empty when no conditions are set.
	 */
	public static String serializeAmountConditions(Map<Triggers, AmountCondition> conditions)
	{
		if (conditions == null || conditions.isEmpty()) return "";
		StringBuilder sb = new StringBuilder();
		for (Map.Entry<Triggers, AmountCondition> e : conditions.entrySet())
		{
			if (e.getValue() == null) continue;
			if (sb.length() > 0) sb.append(';');
			sb.append(e.getKey().name()).append('=').append(e.getValue().serialize());
		}
		return sb.toString();
	}

	public static Map<Triggers, AmountCondition> deserializeAmountConditions(String s)
	{
		Map<Triggers, AmountCondition> map = new EnumMap<>(Triggers.class);
		if (s == null || s.isBlank()) return map;
		for (String part : s.split(";"))
		{
			part = part.trim();
			if (part.isEmpty()) continue;
			int eq = part.indexOf('=');
			if (eq <= 0) continue;
			try
			{
				Triggers trigger = Triggers.valueOf(part.substring(0, eq).trim());
				AmountCondition condition = AmountCondition.deserialize(part.substring(eq + 1));
				if (condition != null) map.put(trigger, condition);
			}
			catch (IllegalArgumentException ignored) {}
		}
		return map;
	}

	/**
	 * Serializes a blacklist to {@code "id:name;id:name"}, e.g. {@code "1215:Dragon dagger;4151:Abyssal whip"}.
	 * Empty when the blacklist is empty. (OSRS item names contain neither {@code :} nor {@code ;}.)
	 */
	public static String serializeBlacklist(List<BlacklistEntry> blacklist)
	{
		if (blacklist == null || blacklist.isEmpty()) return "";
		StringBuilder sb = new StringBuilder();
		for (BlacklistEntry e : blacklist)
		{
			if (sb.length() > 0) sb.append(';');
			sb.append(e.getItemId()).append(':').append(e.getWeaponName());
		}
		return sb.toString();
	}

	public static List<BlacklistEntry> deserializeBlacklist(String s)
	{
		List<BlacklistEntry> list = new ArrayList<>();
		if (s == null || s.isBlank()) return list;
		for (String part : s.split(";"))
		{
			if (part.trim().isEmpty()) continue;
			int colon = part.indexOf(':');
			if (colon <= 0) continue;
			try
			{
				int itemId = Integer.parseInt(part.substring(0, colon).trim());
				String name = part.substring(colon + 1);
				list.add(new BlacklistEntry(itemId, name));
			}
			catch (NumberFormatException ignored) {}
		}
		return list;
	}
}
