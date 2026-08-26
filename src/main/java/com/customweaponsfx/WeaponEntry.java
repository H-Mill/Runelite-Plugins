package com.customweaponsfx;

import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

@Getter
public class WeaponEntry
{
	private final int itemId;
	private String weaponName;
	private final List<TriggerGroup> groups;
	@Setter private boolean enabled;
	/** When true, this weapon's triggers do not override the matching Global (All Weapons) triggers — both play. */
	@Setter private boolean dontOverrideGlobal;

	public WeaponEntry(int itemId, String weaponName, List<TriggerGroup> groups)
	{
		this.itemId = itemId;
		this.weaponName = weaponName;
		this.groups = groups != null ? groups : new ArrayList<>();
		this.enabled = true;
		this.dontOverrideGlobal = false;
	}
}
