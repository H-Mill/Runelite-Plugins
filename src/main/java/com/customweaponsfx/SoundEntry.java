package com.customweaponsfx;

import lombok.Getter;
import lombok.Setter;

@Getter
public class SoundEntry
{
	/** Default relative weight for a freshly created sound (equal weighting). */
	public static final double DEFAULT_WEIGHT = 1.0;

	@Setter private String soundFile;
	@Setter private int volume;
	/**
	 * Relative weight for picking this sound within its group. A sound's chance of being
	 * chosen is its weight divided by the sum of all weights in the group (0 = never).
	 */
	@Setter private double weight;

	public SoundEntry(String soundFile, int volume)
	{
		this(soundFile, volume, DEFAULT_WEIGHT);
	}

	public SoundEntry(String soundFile, int volume, double weight)
	{
		this.soundFile = soundFile;
		this.volume = volume;
		this.weight = weight;
	}
}
