package com.mlmqol;

import lombok.Getter;
import net.runelite.api.Client;
import net.runelite.api.ItemContainer;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.VarbitID;

/**
 * Tracks how much room is left in the Motherlode Mine sack.
 *
 * <p>The sack holds 108 pay-dirt, or 189 once the capacity upgrade has been bought from Prospector
 * Percy. Deposited pay-dirt is transmitted in {@link VarbitID#MOTHERLODE_SACK_TRANSMIT}; the upgrade
 * flag lives in {@link VarbitID#MOTHERLODE_BIGGERSACK}.
 *
 * <p>Pay-dirt put into the hopper takes several seconds to travel the conveyor belt before the sack
 * varbit catches up, so pay-dirt that has left the inventory but not yet arrived is tracked
 * separately as "in transit" and counted against the sack immediately. Pay-dirt still in the
 * inventory counts too, since it is already destined for the sack.
 */
class SackTracker
{
	static final int SACK_SIZE = 108;
	static final int SACK_LARGE_SIZE = 189;

	/**
	 * Ticks without the sack growing before in-transit pay-dirt is assumed lost (e.g. dropped rather
	 * than deposited) and written off. The belt delivers continuously, so real gaps are far shorter.
	 */
	private static final int IN_TRANSIT_STALE_TICKS = 50;

	@Getter
	private int deposited;
	@Getter
	private int capacity = SACK_SIZE;
	@Getter
	private int carriedPayDirt;
	@Getter
	private int inTransit;

	private boolean primed;
	private int ticksSinceArrival;

	/**
	 * Reads the capacity upgrade. Safe to call anywhere — the varbit is not tied to being in the
	 * mine, and unlike {@link #refreshSack(Client)} this does not disturb the in-transit bookkeeping.
	 */
	void refreshCapacity(Client client)
	{
		capacity = client.getVarbitValue(VarbitID.MOTHERLODE_BIGGERSACK) == 1 ? SACK_LARGE_SIZE : SACK_SIZE;
	}

	void refreshSack(Client client)
	{
		int previous = deposited;
		deposited = client.getVarbitValue(VarbitID.MOTHERLODE_SACK_TRANSMIT);
		refreshCapacity(client);

		if (primed && deposited > previous)
		{
			// Pay-dirt reached the sack — it is no longer in transit.
			inTransit = Math.max(0, inTransit - (deposited - previous));
			ticksSinceArrival = 0;
		}
	}

	void refreshInventory(Client client)
	{
		ItemContainer inventory = client.getItemContainer(InventoryID.INV);
		int previous = carriedPayDirt;
		carriedPayDirt = inventory == null ? 0 : inventory.count(ItemID.PAYDIRT);

		if (primed && carriedPayDirt < previous)
		{
			// Pay-dirt left the inventory, so it went into the hopper and is now on the belt.
			inTransit += previous - carriedPayDirt;
			ticksSinceArrival = 0;
		}
	}

	/**
	 * Writes off in-transit pay-dirt that never showed up in the sack, so a dropped stack cannot
	 * block mining forever.
	 */
	void onTick()
	{
		if (inTransit == 0)
		{
			return;
		}

		if (++ticksSinceArrival >= IN_TRANSIT_STALE_TICKS)
		{
			inTransit = 0;
			ticksSinceArrival = 0;
		}
	}

	/**
	 * Marks the tracker as holding real values, so subsequent changes are treated as deposits and
	 * arrivals rather than as the initial read.
	 */
	void prime()
	{
		primed = true;
	}

	void reset()
	{
		deposited = 0;
		capacity = SACK_SIZE;
		carriedPayDirt = 0;
		inTransit = 0;
		ticksSinceArrival = 0;
		primed = false;
	}

	/**
	 * @return pay-dirt already committed to the sack: deposited, on the belt, and carried
	 */
	int getUsed()
	{
		return deposited + inTransit + carriedPayDirt;
	}

	/**
	 * Pay-dirt the sack has already taken on: delivered, plus still travelling the belt. Excludes
	 * what is in the inventory, which has somewhere to go and simply hasn't gone yet.
	 */
	int getCommitted()
	{
		return deposited + inTransit;
	}

	/**
	 * @param blockAt the configured pay-dirt count to stop at
	 * @return true when the sack has no room left for another deposit, whatever you are carrying
	 */
	boolean isCommittedFull(int blockAt)
	{
		return getCommitted() >= Math.min(blockAt, capacity);
	}

	/**
	 * @param blockAt the configured pay-dirt count to stop at
	 * @return true when the sack has reached the block count
	 */
	boolean isFull(int blockAt)
	{
		// The real capacity is always a hard ceiling — a block count above it would never trigger.
		return getUsed() >= Math.min(blockAt, capacity);
	}
}
