package com.customweaponsfx;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import net.runelite.api.Client;
import net.runelite.api.EquipmentInventorySlot;
import net.runelite.api.InventoryID;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Covers the pieces of game logic that live directly in {@link CustomWeaponSfxPlugin} rather than in one
 * of its extracted helpers: resolving the equipped weapon, the resurrection-thrall lifetime formula, and
 * which triggers the weapon's groups cover so the Global (All Weapons) groups don't double-fire.
 */
public class CustomWeaponSfxPluginTest
{
	private static final int ABYSSAL_WHIP = 4151;
	private static final int WEAPON_SLOT = EquipmentInventorySlot.WEAPON.getSlotIdx();

	// ---- equippedWeaponId -----------------------------------------------------------

	@Test
	public void equippedWeaponIdIsMinusOneWhenNoEquipmentContainer()
	{
		Client client = mock(Client.class);
		when(client.getItemContainer(InventoryID.EQUIPMENT)).thenReturn(null);

		assertEquals(-1, CustomWeaponSfxPlugin.equippedWeaponId(client));
	}

	@Test
	public void equippedWeaponIdIsMinusOneWhenWeaponSlotEmpty()
	{
		Client client = mock(Client.class);
		ItemContainer equipment = mock(ItemContainer.class);
		when(client.getItemContainer(InventoryID.EQUIPMENT)).thenReturn(equipment);
		when(equipment.getItem(WEAPON_SLOT)).thenReturn(null);

		assertEquals(-1, CustomWeaponSfxPlugin.equippedWeaponId(client));
	}

	@Test
	public void equippedWeaponIdReturnsWeaponSlotItemId()
	{
		Client client = mock(Client.class);
		ItemContainer equipment = mock(ItemContainer.class);
		Item weapon = mock(Item.class);
		when(client.getItemContainer(InventoryID.EQUIPMENT)).thenReturn(equipment);
		when(equipment.getItem(WEAPON_SLOT)).thenReturn(weapon);
		when(weapon.getId()).thenReturn(ABYSSAL_WHIP);

		assertEquals(ABYSSAL_WHIP, CustomWeaponSfxPlugin.equippedWeaponId(client));
	}

	// ---- thrallDurationTicks --------------------------------------------------------

	@Test
	public void thrallDurationIsMagicLevelPlusSummonDelay()
	{
		// 99 Magic, no Master CAs: 99 + 4-tick summon delay.
		assertEquals(103, CustomWeaponSfxPlugin.thrallDurationTicks(99, false));
	}

	@Test
	public void thrallDurationDoublesWithMasterCombatAchievements()
	{
		// Master tier doubles the level component: (99 * 2) + 4.
		assertEquals(202, CustomWeaponSfxPlugin.thrallDurationTicks(99, true));
	}

	@Test
	public void thrallDurationDoublingAppliesBeforeTheSummonDelay()
	{
		// Guards against doubling the +4: 50 -> (50*2)+4 = 104, not (50+4)*2 = 108.
		assertEquals(104, CustomWeaponSfxPlugin.thrallDurationTicks(50, true));
		assertEquals(54, CustomWeaponSfxPlugin.thrallDurationTicks(50, false));
	}

	// ---- coveredGlobalTriggers ------------------------------------------------------

	@Test
	public void coveredTriggersIsEmptyWhenDontOverrideGlobalSet()
	{
		List<TriggerGroup> groups = Collections.singletonList(
			group(Triggers.REGULAR_HIT, Triggers.REGULAR_MAX));

		// The user opted to let both weapon and global sounds play, so global covers nothing.
		assertTrue(CustomWeaponSfxPlugin.coveredGlobalTriggers(groups, true).isEmpty());
	}

	@Test
	public void coveredTriggersUnionsAllGroupTriggers()
	{
		List<TriggerGroup> groups = Arrays.asList(
			group(Triggers.REGULAR_HIT, Triggers.REGULAR_MAX),
			group(Triggers.SPECIAL_HIT),
			group(Triggers.REGULAR_HIT)); // duplicate across groups collapses into the set

		Set<Triggers> covered = CustomWeaponSfxPlugin.coveredGlobalTriggers(groups, false);

		assertEquals(
			EnumSet.of(Triggers.REGULAR_HIT, Triggers.REGULAR_MAX, Triggers.SPECIAL_HIT),
			covered);
	}

	@Test
	public void coveredTriggersIgnoresGroupsWithNoTriggers()
	{
		List<TriggerGroup> groups = Arrays.asList(
			group(), // a group with no triggers contributes nothing
			group(Triggers.KILL));

		assertEquals(EnumSet.of(Triggers.KILL),
			CustomWeaponSfxPlugin.coveredGlobalTriggers(groups, false));
	}

	@Test
	public void coveredTriggersIsEmptyForNoGroups()
	{
		assertTrue(CustomWeaponSfxPlugin.coveredGlobalTriggers(
			Collections.emptyList(), false).isEmpty());
	}

	private static TriggerGroup group(Triggers... triggers)
	{
		Set<Triggers> set = EnumSet.noneOf(Triggers.class);
		set.addAll(Arrays.asList(triggers));
		return new TriggerGroup(set, new ArrayList<>(), 100);
	}
}
