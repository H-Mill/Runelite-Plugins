package com.customweaponsfx;

import lombok.Getter;

/**
 * A weapon on a sound group's blacklist: just its item id and a display name. The id is used to
 * suppress a Global (All Weapons) sound group when that weapon attacks (see
 * {@link TriggerGroup#isBlacklisted(int)}).
 */
@Getter
public class BlacklistEntry
{
	private final int itemId;
	private final String weaponName;

	public BlacklistEntry(int itemId, String weaponName)
	{
		this.itemId = itemId;
		this.weaponName = weaponName;
	}
}
