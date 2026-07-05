package com.customweaponsfx;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
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
	public void constructorDefaultsNullArgsToEmptyCollections()
	{
		TriggerGroup g = new TriggerGroup(null, null, 50);
		assertTrue(g.getTriggers().isEmpty());
		assertTrue(g.getSounds().isEmpty());
		assertEquals(50, g.getChance());
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
