package com.customweaponsfx;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import net.runelite.api.gameval.ItemID;

/**
 * Registry of {@link AttackProfile}s keyed by weapon item id. This is the one place to add a weapon whose
 * attack spreads its hitsplats across ticks; everything else keys off {@link #forWeapon(int)}.
 */
final class AttackProfiles
{
	/** A max hit if any hitsplat shows the max colour — for attacks that only paint it on some splats (claws). */
	private static final Predicate<List<Boolean>> ANY_SPLAT_MAX = flags -> flags.stream().anyMatch(Boolean::booleanValue);
	/** A max hit only if every hitsplat is max — for attacks where all splats max out together (dark bow). */
	private static final Predicate<List<Boolean>> ALL_SPLATS_MAX = flags -> flags.stream().allMatch(Boolean::booleanValue);
	/** A max hit only if the first hitsplat is max — for attacks where only the primary hit can max (twinflame staff). */
	private static final Predicate<List<Boolean>> FIRST_SPLAT_MAX = flags -> !flags.isEmpty() && flags.get(0);
	/** A max hit only if both of the first two hitsplats are max — for dragon claws, whose 3rd/4th splats never max. */
	private static final Predicate<List<Boolean>> FIRST_TWO_SPLATS_MAX = flags -> flags.size() >= 2 && flags.get(0) && flags.get(1);

	private static final Map<Integer, AttackProfile> BY_WEAPON = new HashMap<>();

	static
	{
		// Claws: special only, hitsplats spread over 2 ticks.
		// Dragon claws: 4 hitsplats, 2 per tick over 2 ticks. Only the first two splats can show the max colour
		// (the 3rd/4th never max), so it counts as a max hit only when both of the first two are max.
		register(new AttackProfile(4, 2, true, FIRST_TWO_SPLATS_MAX),
			ItemID.DRAGON_CLAWS, ItemID.BR_DRAGON_CLAWS, ItemID.DRAGON_CLAWS_ORNAMENT,
			ItemID.BH_DRAGON_CLAWS_CORRUPTED, ItemID.DEADMAN_BLIGHTED_DRAGON_CLAWS);
		// Burning claws: 3 hitsplats, 2 on the first tick and 1 on the second, which alone shows the max colour —
		// so any max-coloured splat counts. gameval names item 29577 (in-game "Burning claws") BONE_CLAWS.
		register(new AttackProfile(3, 2, true, ANY_SPLAT_MAX), ItemID.BONE_CLAWS);

		// Dark bow: both the regular and special attack fire two arrows. The pair can share a tick or, at range,
		// land up to a couple of ticks apart, and both can be max — so aggregate every attack (not just the
		// spec) and count it a max hit only when both splats are max. Completion is by hit count (2), so it
		// still fires the moment both arrows land; the generous 5-tick span is only the fallback for when the
		// target dies after one arrow. That stays well inside the dark bow's ≥8-tick attack speed, so a late
		// second arrow is never mistaken for the next attack.
		register(new AttackProfile(2, 5, false, ALL_SPLATS_MAX),
			ItemID.DARKBOW, ItemID.DARKBOW_GREEN, ItemID.DARKBOW_BLUE, ItemID.DARKBOW_YELLOW, ItemID.DARKBOW_WHITE,
			ItemID.BR_DARKBOW, ItemID.BH_DARKBOW_IMBUE, ItemID.DEADMAN_BLIGHTED_DARK_BOW, ItemID.DEADMAN_DARKBOW);

		// Twinflame staff: a Bolt/Blast/Wave cast hits twice, the second hit a tick after the first and only the
		// first able to show the max colour; Strike/Surge cast a single hit. Handled like the dark bow — aggregate
		// every attack up to 2 hits over a 2-tick span with a first-splat-max rule. A double cast completes as soon
		// as its second hit lands; a single cast completes via the tick-span fallback (so it fires ~1 tick later).
		register(new AttackProfile(2, 2, false, FIRST_SPLAT_MAX), ItemID.TWINFLAME_STAFF);
	}

	private AttackProfiles()
	{
	}

	private static void register(AttackProfile profile, int... itemIds)
	{
		for (int itemId : itemIds) BY_WEAPON.put(itemId, profile);
	}

	/** The profile for {@code weaponId}, or {@code null} if the weapon has no multi-hit attack to aggregate. */
	static AttackProfile forWeapon(int weaponId)
	{
		return BY_WEAPON.get(weaponId);
	}
}
