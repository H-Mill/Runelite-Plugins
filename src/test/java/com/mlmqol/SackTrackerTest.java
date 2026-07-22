package com.mlmqol;

import net.runelite.api.Client;
import net.runelite.api.ItemContainer;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.VarbitID;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Covers the sack bookkeeping: capacity, and the "in transit" accounting that credits pay-dirt to the
 * sack the moment it leaves the inventory rather than several seconds later when the conveyor belt
 * finally delivers it.
 */
public class SackTrackerTest
{
	private static final int SACK_SIZE = 108;
	private static final int SACK_LARGE_SIZE = 189;

	private Client client;
	private ItemContainer inventory;
	private SackTracker tracker;

	@Before
	public void setUp()
	{
		client = mock(Client.class);
		inventory = mock(ItemContainer.class);
		when(client.getItemContainer(InventoryID.INV)).thenReturn(inventory);
		tracker = new SackTracker();

		deposited(0);
		upgraded(false);
		carrying(0);
	}

	private void deposited(int count)
	{
		when(client.getVarbitValue(VarbitID.MOTHERLODE_SACK_TRANSMIT)).thenReturn(count);
	}

	private void upgraded(boolean upgraded)
	{
		when(client.getVarbitValue(VarbitID.MOTHERLODE_BIGGERSACK)).thenReturn(upgraded ? 1 : 0);
	}

	private void carrying(int payDirt)
	{
		when(inventory.count(ItemID.PAYDIRT)).thenReturn(payDirt);
	}

	/** Puts the tracker in the state the plugin primes it into on entering the mine. */
	private void prime()
	{
		tracker.reset();
		tracker.refreshSack(client);
		tracker.refreshInventory(client);
		tracker.prime();
	}

	// ---- capacity ------------------------------------------------------------------------------

	@Test
	public void capacityDefaultsToTheSmallSack()
	{
		assertEquals(SACK_SIZE, tracker.getCapacity());
	}

	@Test
	public void capacityFollowsTheUpgradeVarbit()
	{
		upgraded(true);
		tracker.refreshCapacity(client);
		assertEquals(SACK_LARGE_SIZE, tracker.getCapacity());
	}

	@Test
	public void capacityDropsBackWhenTheUpgradeVarbitIsUnset()
	{
		upgraded(true);
		tracker.refreshCapacity(client);
		upgraded(false);
		tracker.refreshCapacity(client);
		assertEquals(SACK_SIZE, tracker.getCapacity());
	}

	@Test
	public void refreshCapacityLeavesTheInTransitBookkeepingAlone()
	{
		prime();
		carrying(27);
		tracker.refreshInventory(client);
		carrying(0);
		tracker.refreshInventory(client);
		assertEquals(27, tracker.getInTransit());

		// Called from outside the mine on login — it must not be mistaken for an arrival.
		upgraded(true);
		tracker.refreshCapacity(client);

		assertEquals(27, tracker.getInTransit());
		assertEquals(SACK_LARGE_SIZE, tracker.getCapacity());
	}

	// ---- in transit -----------------------------------------------------------------------------

	@Test
	public void initialReadsDoNotInventPhantomInTransitPayDirt()
	{
		// Priming reads a full sack and an empty inventory. Neither is a deposit.
		deposited(50);
		carrying(0);
		prime();

		assertEquals(0, tracker.getInTransit());
		assertEquals(50, tracker.getDeposited());
	}

	@Test
	public void payDirtLeavingTheInventoryGoesInTransit()
	{
		carrying(27);
		prime();

		carrying(0);
		tracker.refreshInventory(client);

		assertEquals(27, tracker.getInTransit());
		assertEquals(0, tracker.getCarriedPayDirt());
	}

	@Test
	public void payDirtArrivingAtTheSackClearsItFromTransit()
	{
		carrying(27);
		prime();
		carrying(0);
		tracker.refreshInventory(client);

		deposited(27);
		tracker.refreshSack(client);

		assertEquals(0, tracker.getInTransit());
		assertEquals(27, tracker.getDeposited());
	}

	@Test
	public void partialDeliveryLeavesTheRemainderInTransit()
	{
		// The belt delivers a few at a time rather than the whole load at once.
		carrying(27);
		prime();
		carrying(0);
		tracker.refreshInventory(client);

		deposited(10);
		tracker.refreshSack(client);
		assertEquals(17, tracker.getInTransit());

		deposited(27);
		tracker.refreshSack(client);
		assertEquals(0, tracker.getInTransit());
	}

	@Test
	public void arrivalsNeverDriveInTransitNegative()
	{
		carrying(5);
		prime();
		carrying(0);
		tracker.refreshInventory(client);

		// More arrives than we knew about — another source, or state we joined midway.
		deposited(40);
		tracker.refreshSack(client);

		assertEquals(0, tracker.getInTransit());
	}

	@Test
	public void collectingFromTheSackDoesNotTouchInTransit()
	{
		deposited(80);
		carrying(27);
		prime();
		carrying(0);
		tracker.refreshInventory(client);
		assertEquals(27, tracker.getInTransit());

		// Collecting empties the sack — a decrease, not an arrival.
		deposited(0);
		tracker.refreshSack(client);

		assertEquals(27, tracker.getInTransit());
		assertEquals(0, tracker.getDeposited());
	}

	@Test
	public void gainingPayDirtIsNotADeposit()
	{
		prime();
		carrying(14);
		tracker.refreshInventory(client);

		assertEquals(0, tracker.getInTransit());
		assertEquals(14, tracker.getCarriedPayDirt());
	}

	// ---- the stale write-off -------------------------------------------------------------------

	@Test
	public void inTransitPayDirtThatNeverArrivesIsEventuallyWrittenOff()
	{
		// Guards against pay-dirt dropped on the floor blocking mining forever.
		carrying(27);
		prime();
		carrying(0);
		tracker.refreshInventory(client);

		for (int i = 0; i < 49; i++)
		{
			tracker.onTick();
		}
		assertEquals("not written off before the timeout", 27, tracker.getInTransit());

		tracker.onTick();
		assertEquals(0, tracker.getInTransit());
	}

	@Test
	public void everyArrivalRestartsTheWriteOffTimer()
	{
		carrying(60);
		prime();
		carrying(0);
		tracker.refreshInventory(client);

		for (int i = 0; i < 40; i++)
		{
			tracker.onTick();
		}

		deposited(10);
		tracker.refreshSack(client);

		for (int i = 0; i < 40; i++)
		{
			tracker.onTick();
		}

		assertEquals("the timer restarted, so nothing is written off yet", 50, tracker.getInTransit());
	}

	@Test
	public void tickingWithNothingInTransitIsHarmless()
	{
		prime();
		for (int i = 0; i < 100; i++)
		{
			tracker.onTick();
		}
		assertEquals(0, tracker.getInTransit());
	}

	// ---- reset ---------------------------------------------------------------------------------

	@Test
	public void resetClearsEverythingIncludingThePrimedFlag()
	{
		deposited(50);
		upgraded(true);
		carrying(27);
		prime();
		carrying(0);
		tracker.refreshInventory(client);

		tracker.reset();

		assertEquals(0, tracker.getDeposited());
		assertEquals(0, tracker.getInTransit());
		assertEquals(0, tracker.getCarriedPayDirt());
		assertEquals(SACK_SIZE, tracker.getCapacity());

		// Unprimed again: the next reads are treated as initial, not as a deposit.
		carrying(27);
		tracker.refreshInventory(client);
		carrying(0);
		tracker.refreshSack(client);
		assertEquals(0, tracker.getInTransit());
	}

	// ---- used / isFull -------------------------------------------------------------------------

	@Test
	public void usedSumsDepositedInTransitAndCarried()
	{
		deposited(40);
		carrying(30);
		prime();
		carrying(10);
		tracker.refreshInventory(client);

		// 40 deposited, 20 that left the inventory and is on the belt, 10 still carried.
		assertEquals(20, tracker.getInTransit());
		assertEquals(70, tracker.getUsed());
	}

	// ---- committed (what the sack can no longer take more of) ----------------------------------

	@Test
	public void committedCountsTheSackAndTheBeltButNotTheInventory()
	{
		deposited(40);
		carrying(30);
		prime();
		carrying(10);
		tracker.refreshInventory(client);

		// 40 delivered, 20 on the belt, 10 still carried.
		assertEquals(60, tracker.getCommitted());
		assertEquals(70, tracker.getUsed());
	}

	@Test
	public void carriedPayDirtNeverMakesTheSackCommittedFull()
	{
		// The load in the inventory is the one asking to be deposited, and there is room for it.
		deposited(81);
		carrying(27);
		prime();

		assertTrue("used counts it", tracker.isFull(SACK_SIZE));
		assertFalse("committed does not", tracker.isCommittedFull(SACK_SIZE));
	}

	@Test
	public void depositingThatSameLoadDoesMakeItCommittedFull()
	{
		deposited(81);
		carrying(27);
		prime();

		carrying(0);
		tracker.refreshInventory(client);

		assertTrue(tracker.isCommittedFull(SACK_SIZE));
	}

	@Test
	public void isCommittedFullClampsABlockCountAboveCapacity()
	{
		deposited(108);
		prime();

		assertTrue(tracker.isCommittedFull(SACK_LARGE_SIZE));
	}

	@Test
	public void isFullOnlyAtOrAboveTheBlockCount()
	{
		deposited(107);
		prime();
		assertFalse(tracker.isFull(SACK_SIZE));

		deposited(108);
		tracker.refreshSack(client);
		assertTrue(tracker.isFull(SACK_SIZE));
	}

	@Test
	public void isFullHonoursABlockCountBelowCapacity()
	{
		deposited(50);
		prime();

		assertTrue("stopping early at 50 was asked for", tracker.isFull(50));
		assertFalse(tracker.isFull(51));
	}

	@Test
	public void isFullClampsABlockCountAboveCapacity()
	{
		// An un-upgraded sack cannot hold 189, so asking to block at 189 must still block at 108
		// rather than never blocking at all.
		deposited(108);
		prime();

		assertTrue(tracker.isFull(SACK_LARGE_SIZE));
	}

	@Test
	public void isFullCountsCarriedAndInTransitPayDirt()
	{
		deposited(60);
		carrying(48);
		prime();

		assertTrue("60 in the sack plus 48 carried fills it", tracker.isFull(SACK_SIZE));

		// Depositing moves it to the belt — still full, with no gap while the belt catches up.
		carrying(0);
		tracker.refreshInventory(client);
		assertTrue(tracker.isFull(SACK_SIZE));
	}
}
