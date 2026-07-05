package com.customweaponsfx;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class SoundSelectionTest
{
	@Test
	public void allZeroWeightsPickNothing()
	{
		List<SoundEntry> sounds = Arrays.asList(
			new SoundEntry("a", 75, 0),
			new SoundEntry("b", 75, 0));
		assertNull(SoundPlayer.pickWeighted(sounds));
	}

	@Test
	public void zeroWeightSoundIsNeverPicked()
	{
		List<SoundEntry> sounds = Arrays.asList(
			new SoundEntry("never", 75, 0),
			new SoundEntry("always", 75, 1.0));
		for (int i = 0; i < 1000; i++)
			assertEquals("always", SoundPlayer.pickWeighted(sounds).getSoundFile());
	}

	@Test
	public void weightsRoughlyMatchObservedDistribution()
	{
		// 3:1 weighting should land near 75% / 25% over many rolls.
		List<SoundEntry> sounds = Arrays.asList(
			new SoundEntry("heavy", 75, 1.5),
			new SoundEntry("light", 75, 0.5));

		Map<String, Integer> counts = new HashMap<>();
		int n = 100_000;
		for (int i = 0; i < n; i++)
		{
			String f = SoundPlayer.pickWeighted(sounds).getSoundFile();
			counts.merge(f, 1, Integer::sum);
		}

		double heavyRatio = counts.getOrDefault("heavy", 0) / (double) n;
		assertTrue("heavy ratio was " + heavyRatio, heavyRatio > 0.72 && heavyRatio < 0.78);
	}

	@Test
	public void everySoundIsReachable()
	{
		List<SoundEntry> sounds = new ArrayList<>(Arrays.asList(
			new SoundEntry("a", 75, 0.1),
			new SoundEntry("b", 75, 0.1),
			new SoundEntry("c", 75, 0.1)));

		Map<String, Integer> seen = new HashMap<>();
		for (int i = 0; i < 1000; i++)
			seen.merge(SoundPlayer.pickWeighted(sounds).getSoundFile(), 1, Integer::sum);

		assertTrue(seen.containsKey("a"));
		assertTrue(seen.containsKey("b"));
		assertTrue(seen.containsKey("c"));
	}
}
