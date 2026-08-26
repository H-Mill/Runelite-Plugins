package com.customweaponsfx;

import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class AmountConditionTest
{
	@Test
	public void greaterLessEqualMatchAsExpected()
	{
		AmountCondition gt = new AmountCondition(AmountCondition.Op.GREATER, 10);
		assertTrue(gt.matches(11));
		assertFalse(gt.matches(10));
		assertFalse(gt.matches(9));

		AmountCondition lt = new AmountCondition(AmountCondition.Op.LESS, 10);
		assertTrue(lt.matches(9));
		assertFalse(lt.matches(10));
		assertFalse(lt.matches(11));

		AmountCondition eq = new AmountCondition(AmountCondition.Op.EQUAL, 73);
		assertTrue(eq.matches(73));
		assertFalse(eq.matches(72));
		assertFalse(eq.matches(74));
	}

	@Test
	public void serializeDeserializeRoundTrips()
	{
		AmountCondition original = new AmountCondition(AmountCondition.Op.EQUAL, 73);
		AmountCondition restored = AmountCondition.deserialize(original.serialize());
		assertEquals(original.getOp(), restored.getOp());
		assertEquals(original.getValue(), restored.getValue());
	}

	@Test
	public void deserializeRejectsNullBlankAndMalformed()
	{
		assertNull(AmountCondition.deserialize(null));
		assertNull(AmountCondition.deserialize(""));
		assertNull(AmountCondition.deserialize("EQUAL"));
		assertNull(AmountCondition.deserialize("NOPE:5"));
		assertNull(AmountCondition.deserialize("EQUAL:notanumber"));
	}
}
