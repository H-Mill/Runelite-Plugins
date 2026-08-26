package com.customweaponsfx;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class TriggerGroupTest
{
	@Test
	public void serializeDeserializeRoundTrips()
	{
		Set<Triggers> original = EnumSet.of(Triggers.REGULAR_HIT, Triggers.KILL, Triggers.SPECIAL_MAX);
		String serialized = TriggerGroup.serializeTriggers(original);
		assertEquals(original, TriggerGroup.deserializeTriggers(serialized));
	}

	@Test
	public void emptySetSerializesToEmptyString()
	{
		assertEquals("", TriggerGroup.serializeTriggers(EnumSet.noneOf(Triggers.class)));
		assertEquals("", TriggerGroup.serializeTriggers(null));
	}

	@Test
	public void deserializeHandlesNullBlankAndWhitespace()
	{
		assertTrue(TriggerGroup.deserializeTriggers(null).isEmpty());
		assertTrue(TriggerGroup.deserializeTriggers("").isEmpty());
		assertTrue(TriggerGroup.deserializeTriggers("   ").isEmpty());
		// surrounding whitespace and empty entries are tolerated
		assertEquals(EnumSet.of(Triggers.ALL),
			TriggerGroup.deserializeTriggers(" ALL ,, "));
	}

	@Test
	public void deserializeIgnoresUnknownTokensButKeepsValidOnes()
	{
		// Forward/backward compat: an unknown trigger name (e.g. removed enum value) must not
		// blow away the rest of a user's saved group.
		Set<Triggers> result = TriggerGroup.deserializeTriggers("REGULAR_HIT,SOMETHING_REMOVED,KILL");
		assertEquals(EnumSet.of(Triggers.REGULAR_HIT, Triggers.KILL), result);
	}

	@Test
	public void amountConditionsSerializeDeserializeRoundTrip()
	{
		Map<Triggers, AmountCondition> original = new EnumMap<>(Triggers.class);
		original.put(Triggers.REGULAR_AMOUNT, new AmountCondition(AmountCondition.Op.EQUAL, 73));

		String serialized = TriggerGroup.serializeAmountConditions(original);
		Map<Triggers, AmountCondition> restored = TriggerGroup.deserializeAmountConditions(serialized);

		assertEquals(1, restored.size());
		AmountCondition c = restored.get(Triggers.REGULAR_AMOUNT);
		assertEquals(AmountCondition.Op.EQUAL, c.getOp());
		assertEquals(73, c.getValue());
	}

	@Test
	public void emptyAmountConditionsSerializeToEmptyString()
	{
		assertEquals("", TriggerGroup.serializeAmountConditions(new EnumMap<>(Triggers.class)));
		assertEquals("", TriggerGroup.serializeAmountConditions(null));
		assertTrue(TriggerGroup.deserializeAmountConditions(null).isEmpty());
		assertTrue(TriggerGroup.deserializeAmountConditions("").isEmpty());
		assertTrue(TriggerGroup.deserializeAmountConditions("  ").isEmpty());
	}

	@Test
	public void deserializeAmountConditionsIgnoresMalformedEntries()
	{
		// A valid entry survives alongside an unknown trigger and a malformed condition.
		Map<Triggers, AmountCondition> restored = TriggerGroup.deserializeAmountConditions(
			"REGULAR_AMOUNT=GREATER:50;BOGUS=EQUAL:1;REGULAR_MAX=notacondition");
		assertEquals(1, restored.size());
		assertEquals(AmountCondition.Op.GREATER, restored.get(Triggers.REGULAR_AMOUNT).getOp());
		assertEquals(50, restored.get(Triggers.REGULAR_AMOUNT).getValue());
	}

	@Test
	public void newTriggerGroupHasNoAmountConditions()
	{
		TriggerGroup g = new TriggerGroup(null, null, 100);
		assertTrue(g.getAmountConditions().isEmpty());
		assertNull(g.getAmountCondition(Triggers.REGULAR_AMOUNT));
		g.setAmountCondition(Triggers.REGULAR_AMOUNT, new AmountCondition(AmountCondition.Op.EQUAL, 5));
		assertEquals(5, g.getAmountCondition(Triggers.REGULAR_AMOUNT).getValue());
		// A null condition clears it.
		g.setAmountCondition(Triggers.REGULAR_AMOUNT, null);
		assertNull(g.getAmountCondition(Triggers.REGULAR_AMOUNT));
	}

	@Test
	public void constructorDefaultsNullArgsToEmptyCollections()
	{
		TriggerGroup g = new TriggerGroup(null, null, 50);
		assertTrue(g.getTriggers().isEmpty());
		assertTrue(g.getSounds().isEmpty());
		assertEquals(50, g.getChance());
	}

	@Test
	public void blacklistSerializeDeserializeRoundTrips()
	{
		List<BlacklistEntry> original = new ArrayList<>();
		original.add(new BlacklistEntry(4151, "Abyssal whip"));
		original.add(new BlacklistEntry(1215, "Dragon dagger"));

		String serialized = TriggerGroup.serializeBlacklist(original);
		List<BlacklistEntry> restored = TriggerGroup.deserializeBlacklist(serialized);

		assertEquals(2, restored.size());
		assertEquals(4151, restored.get(0).getItemId());
		assertEquals("Abyssal whip", restored.get(0).getWeaponName());
		assertEquals(1215, restored.get(1).getItemId());
		assertEquals("Dragon dagger", restored.get(1).getWeaponName());
	}

	@Test
	public void emptyBlacklistSerializesToEmptyStringAndBack()
	{
		assertEquals("", TriggerGroup.serializeBlacklist(new ArrayList<>()));
		assertEquals("", TriggerGroup.serializeBlacklist(null));
		assertTrue(TriggerGroup.deserializeBlacklist(null).isEmpty());
		assertTrue(TriggerGroup.deserializeBlacklist("").isEmpty());
		assertTrue(TriggerGroup.deserializeBlacklist("  ").isEmpty());
	}

	@Test
	public void deserializeBlacklistIgnoresMalformedEntries()
	{
		// A valid entry survives alongside a non-numeric id and a colon-less token.
		List<BlacklistEntry> restored = TriggerGroup.deserializeBlacklist("4151:Abyssal whip;bogus:x;noColon");
		assertEquals(1, restored.size());
		assertEquals(4151, restored.get(0).getItemId());
		assertEquals("Abyssal whip", restored.get(0).getWeaponName());
	}

	@Test
	public void addToBlacklistIsIdempotentAndRemoveWorks()
	{
		TriggerGroup g = new TriggerGroup(null, null, 100);
		assertFalse(g.isBlacklisted(4151));

		assertTrue("first add succeeds", g.addToBlacklist(4151, "Abyssal whip"));
		assertFalse("duplicate add is a no-op", g.addToBlacklist(4151, "Abyssal whip"));
		assertTrue(g.isBlacklisted(4151));
		assertEquals(1, g.getBlacklist().size());

		g.removeFromBlacklist(4151);
		assertFalse(g.isBlacklisted(4151));
		assertTrue(g.getBlacklist().isEmpty());
	}

	private static TriggerGroup named(String name)
	{
		TriggerGroup g = new TriggerGroup(null, null, 100);
		g.setName(name);
		return g;
	}

	@Test
	public void isNameTakenIsCaseInsensitiveAndSkipsExcludedGroup()
	{
		TriggerGroup a = named("Crush");
		TriggerGroup b = named("Slash");
		List<TriggerGroup> groups = new ArrayList<>();
		groups.add(a);
		groups.add(b);

		assertTrue(TriggerGroup.isNameTaken(groups, "crush", null));
		assertFalse(TriggerGroup.isNameTaken(groups, "Stab", null));
		// renaming 'a' to its own (differently-cased) name must not count as a clash with itself
		assertFalse(TriggerGroup.isNameTaken(groups, "CRUSH", a));
		// but it still clashes with the *other* group
		assertTrue(TriggerGroup.isNameTaken(groups, "slash", a));
	}

	@Test
	public void defaultNameSkipsUsedNumbers()
	{
		List<TriggerGroup> groups = new ArrayList<>();
		assertEquals("Sound Group 1", TriggerGroup.defaultName(groups));

		groups.add(named("Sound Group 1"));
		groups.add(named("Sound Group 3"));
		// 1 is taken, 2 is free
		assertEquals("Sound Group 2", TriggerGroup.defaultName(groups));

		groups.add(named("Sound Group 2"));
		// 1,2,3 taken -> 4
		assertEquals("Sound Group 4", TriggerGroup.defaultName(groups));
	}
}
