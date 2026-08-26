package com.customweaponsfx;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * Owns the configured weapons and their lifecycle: load, add, re-point (change), copy, and remove,
 * keeping the in-memory list and {@link CustomWeaponSfxConfigStore} in sync.
 *
 * <p>Stays free of Swing and the game client: it reports a rebuild via the {@code onChanged} callback
 * and an id-collision via {@code onConflict} (with a ready-to-show message), leaving the plugin to do
 * the actual panel rebuild and warning dialog.
 */
class WeaponManager
{
	private final List<WeaponEntry> entries = new CopyOnWriteArrayList<>();
	private final CustomWeaponSfxConfigStore store;
	private final Runnable onChanged;
	private final Consumer<String> onConflict;

	WeaponManager(CustomWeaponSfxConfigStore store, Runnable onChanged, Consumer<String> onConflict)
	{
		this.store = store;
		this.onChanged = onChanged;
		this.onConflict = onConflict;
	}

	/** (Re)loads the configured weapons from storage. */
	void load()
	{
		entries.clear();
		entries.addAll(store.loadWeapons());
	}

	/** A defensive copy of the current weapons, safe to hand to the panel / other threads. */
	List<WeaponEntry> snapshot()
	{
		return new ArrayList<>(entries);
	}

	WeaponEntry find(int itemId)
	{
		for (WeaponEntry entry : entries)
		{
			if (entry.getItemId() == itemId) return entry;
		}
		return null;
	}

	void add(int itemId, String name)
	{
		if (find(itemId) != null) return;

		WeaponEntry entry = new WeaponEntry(itemId, name, new ArrayList<>());
		entries.add(entry);

		store.saveWeapon(entry);
		store.saveWeaponIds(entries);

		onChanged.run();
	}

	/**
	 * Re-points an existing weapon entry from {@code oldItemId} to {@code newItemId}, preserving its
	 * sound groups and enabled state. Because the item id is the config key, this rewrites the entry
	 * under the new id and updates the id index. No-ops if the id is unchanged; warns and aborts if the
	 * target id already has its own separate configuration.
	 */
	void change(int oldItemId, int newItemId, String newName)
	{
		if (oldItemId == newItemId) return;

		WeaponEntry old = find(oldItemId);
		if (old == null) return;

		if (find(newItemId) != null)
		{
			onConflict.accept(newName + " is already configured as a separate weapon.");
			return;
		}

		int idx = entries.indexOf(old);
		store.removeWeapon(old);

		WeaponEntry updated = new WeaponEntry(newItemId, newName, old.getGroups());
		updated.setEnabled(old.isEnabled());
		updated.setDontOverrideGlobal(old.isDontOverrideGlobal());
		entries.set(idx, updated);

		store.saveWeapon(updated);
		store.saveWeaponIds(entries);

		onChanged.run();
	}

	/**
	 * Creates a new weapon entry for {@code newItemId} with a deep copy of {@code sourceItemId}'s
	 * sound groups, enabled state, and "don't override Global" flag. No-ops if the source is gone;
	 * warns and aborts if the target id already has its own configuration.
	 */
	void copy(int sourceItemId, int newItemId, String newName)
	{
		WeaponEntry source = find(sourceItemId);
		if (source == null) return;

		if (find(newItemId) != null)
		{
			onConflict.accept(newName + " is already configured. Remove it first, or pick a different weapon to copy onto.");
			return;
		}

		WeaponEntry copy = new WeaponEntry(newItemId, newName, copyGroups(source.getGroups()));
		copy.setEnabled(source.isEnabled());
		copy.setDontOverrideGlobal(source.isDontOverrideGlobal());
		entries.add(copy);

		store.saveWeapon(copy);
		store.saveWeaponIds(entries);

		onChanged.run();
	}

	/**
	 * Moves the weapon with {@code itemId} to {@code newIndex} in the ordering and persists the new
	 * order (the id index is order-significant, so this is all that's needed). No-ops if the id is
	 * unknown or it is already at that index.
	 */
	void move(int itemId, int newIndex)
	{
		WeaponEntry entry = find(itemId);
		if (entry == null) return;

		int current = entries.indexOf(entry);
		if (current < 0) return;

		int target = Math.max(0, Math.min(newIndex, entries.size() - 1));
		if (current == target) return;

		entries.remove(current);
		entries.add(target, entry);

		store.saveWeaponIds(entries);

		onChanged.run();
	}

	void remove(int itemId)
	{
		WeaponEntry entry = find(itemId);
		if (entry != null) store.removeWeapon(entry);
		entries.removeIf(e -> e.getItemId() == itemId);
		store.saveWeaponIds(entries);

		onChanged.run();
	}

	/** Empties the in-memory list (config wiping is done separately via the store's reset). */
	void clear()
	{
		entries.clear();
	}

	/** Deep-copies a weapon's sound groups so the copy and the original share no mutable state. */
	private static List<TriggerGroup> copyGroups(List<TriggerGroup> groups)
	{
		List<TriggerGroup> out = new ArrayList<>(groups.size());
		for (TriggerGroup g : groups)
		{
			List<SoundEntry> sounds = new ArrayList<>(g.getSounds().size());
			for (SoundEntry s : g.getSounds())
				sounds.add(new SoundEntry(s.getSoundFile(), s.getVolume(), s.getWeight()));

			Set<Triggers> triggers = EnumSet.noneOf(Triggers.class);
			triggers.addAll(g.getTriggers());

			TriggerGroup copy = new TriggerGroup(triggers, sounds, g.getChance());
			copy.setName(g.getName());
			for (BlacklistEntry b : g.getBlacklist())
				copy.addToBlacklist(b.getItemId(), b.getWeaponName());
			out.add(copy);
		}
		return out;
	}
}
