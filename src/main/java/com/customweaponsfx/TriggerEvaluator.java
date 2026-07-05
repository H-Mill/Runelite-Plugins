package com.customweaponsfx;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import net.runelite.api.HitsplatID;

/**
 * Pure decision logic for the plugin: given the aggregated outcome of a tick's hitsplats, decide
 * which {@link TriggerGroup}s should fire. Intentionally free of RuneLite {@code Client} state and
 * side effects (no RNG, no audio) so it can be unit tested in isolation.
 */
final class TriggerEvaluator
{
	private TriggerEvaluator()
	{
	}

	/**
	 * Selects, in declaration order, the groups whose triggers match the given attack outcome.
	 *
	 * @param groups                 candidate groups (weapon, global, or received)
	 * @param wasSpec                the attack was a special attack
	 * @param anyHit                 at least one hitsplat dealt damage
	 * @param allZero                every hitsplat was a zero
	 * @param allMax                 every hitsplat was a max hit
	 * @param suppressMax            max-hit triggers should be suppressed (e.g. small max hits)
	 * @param suppressZero           zero triggers should be suppressed (e.g. thrall active)
	 * @param isKill                 the attack killed another player
	 * @param weaponCoveredTriggers  triggers already handled by the weapon; when non-empty, global
	 *                               groups overlapping these are skipped to avoid double-firing
	 * @return the groups that should fire, in order
	 */
	static List<TriggerGroup> selectFiringGroups(
		List<TriggerGroup> groups, boolean wasSpec,
		boolean anyHit, boolean allZero, boolean allMax,
		boolean suppressMax, boolean suppressZero, boolean isKill,
		Set<Triggers> weaponCoveredTriggers)
	{
		// Pre-filter: when a non-empty suppression set is provided, skip groups whose triggers
		// overlap with triggers the weapon already handles.
		List<TriggerGroup> activeGroups = weaponCoveredTriggers.isEmpty() ? groups : groups.stream()
			.filter(g -> g.getTriggers().isEmpty()
				|| g.getTriggers().stream().noneMatch(weaponCoveredTriggers::contains))
			.collect(Collectors.toList());

		// If a kill occurred and the active groups include KILL-trigger groups, those take exclusive priority.
		boolean hasKillGroups = isKill && activeGroups.stream()
			.anyMatch(g -> !g.getTriggers().isEmpty() && g.getTriggers().contains(Triggers.KILL));

		List<TriggerGroup> firing = new ArrayList<>();
		for (TriggerGroup group : activeGroups)
		{
			Set<Triggers> triggers = group.getTriggers();
			if (triggers.isEmpty()) continue;
			// PLAYER_DEATH groups are fired exclusively from onActorDeath, not from the hitsplat path.
			if (triggers.contains(Triggers.PLAYER_DEATH)) continue;

			boolean groupHasKill = triggers.contains(Triggers.KILL);
			// Kill groups only fire on kills; when kill groups exist, non-kill groups are suppressed.
			if (groupHasKill != hasKillGroups) continue;

			boolean matches;
			if (groupHasKill)
			{
				// isKill is guaranteed true here (hasKillGroups requires it).
				// Any non-KILL triggers in the group also need at least one to match (AND gate).
				boolean hasOtherTriggers = false;
				boolean anyOtherMatches = false;
				for (Triggers trigger : triggers)
				{
					if (trigger == Triggers.KILL) continue;
					hasOtherTriggers = true;
					if (matchesTrigger(trigger, wasSpec, anyHit, allZero, allMax, suppressMax, suppressZero, isKill))
					{
						anyOtherMatches = true;
						break;
					}
				}
				matches = !hasOtherTriggers || anyOtherMatches;
			}
			else
			{
				matches = false;
				for (Triggers trigger : triggers)
				{
					if (matchesTrigger(trigger, wasSpec, anyHit, allZero, allMax, suppressMax, suppressZero, isKill))
					{
						matches = true;
						break;
					}
				}
			}
			if (!matches) continue;
			firing.add(group);
		}
		return firing;
	}

	static boolean matchesTrigger(Triggers trigger, boolean wasSpec,
								  boolean anyHit, boolean allZero, boolean allMax,
								  boolean suppressMax, boolean suppressZero, boolean isKill)
	{
		switch (trigger)
		{
			case REGULAR_ZERO: return !wasSpec && allZero && !suppressZero;
			case REGULAR_HIT:  return !wasSpec && anyHit && !allMax;
			case REGULAR_MAX:  return !wasSpec && allMax && !suppressMax;
			case SPECIAL_ZERO: return wasSpec && allZero;
			case SPECIAL_HIT:  return wasSpec && anyHit && !allMax;
			case SPECIAL_MAX:  return wasSpec && allMax && !suppressMax;
			case ALL:          return true;
			case KILL:         return isKill;
			default:           return false;
		}
	}

	static boolean isMaxHit(int hitsplatType)
	{
		return hitsplatType == HitsplatID.DAMAGE_MAX_ME
			|| hitsplatType == HitsplatID.DAMAGE_MAX_ME_CYAN
			|| hitsplatType == HitsplatID.DAMAGE_MAX_ME_ORANGE
			|| hitsplatType == HitsplatID.DAMAGE_MAX_ME_YELLOW
			|| hitsplatType == HitsplatID.DAMAGE_MAX_ME_WHITE
			|| hitsplatType == HitsplatID.DAMAGE_MAX_ME_POISE;
	}

	static float volumeToGain(int volume)
	{
		if (volume <= 0) return -80f;
		return 20.0f * (float) Math.log10(volume / 100.0f);
	}
}
