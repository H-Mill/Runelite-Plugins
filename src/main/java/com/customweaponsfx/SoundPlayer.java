package com.customweaponsfx;

import java.io.File;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.audio.AudioPlayer;

/**
 * Plays the sound effects: resolves which {@link SoundEntry} in a firing {@link TriggerGroup} should
 * play, then plays it off the client thread on a single-threaded executor.
 *
 * <p>Owns the audio executor lifecycle ({@link #start()} / {@link #shutDown()}). Separate from
 * {@link SoundLibrary}, which knows which sounds exist.
 */
@Slf4j
class SoundPlayer
{
	/** Played when a group's sound file is unset/empty. */
	private static final String DEFAULT_BUNDLED_FILE = "squeak.wav";

	private final AudioPlayer audioPlayer;
	private final File soundsDir;
	private ExecutorService executor;

	SoundPlayer(AudioPlayer audioPlayer, File soundsDir)
	{
		this.audioPlayer = audioPlayer;
		this.soundsDir = soundsDir;
	}

	void start()
	{
		executor = Executors.newSingleThreadExecutor();
	}

	void shutDown()
	{
		if (executor != null)
		{
			executor.shutdownNow();
			executor = null;
		}
	}

	/** Fires every group in {@code groups} that the trigger evaluator selects for this attack. */
	void fireMatchingGroups(List<TriggerGroup> groups, boolean wasSpec,
							boolean anyHit, boolean allZero, boolean allMax,
							boolean suppressMax, boolean suppressZero, boolean isKill,
							Set<Triggers> weaponCoveredTriggers, int[] amounts)
	{
		for (TriggerGroup group : TriggerEvaluator.selectFiringGroups(groups, wasSpec, anyHit, allZero,
			allMax, suppressMax, suppressZero, isKill, weaponCoveredTriggers, amounts))
		{
			fireGroup(group);
		}
	}

	/** Rolls the group's chance, picks one of its sounds (weighted), and plays it. */
	void fireGroup(TriggerGroup group)
	{
		int chance = group.getChance();
		if (chance <= 0) return;
		if (chance < 100 && ThreadLocalRandom.current().nextInt(100) >= chance) return;
		List<SoundEntry> sounds = group.getSounds();
		if (sounds.isEmpty()) return;
		// A lone sound always plays; with several, pick one weighted by each sound's chance.
		SoundEntry se = sounds.size() == 1 ? sounds.get(0) : pickWeighted(sounds);
		if (se == null) return;
		playSoundFile(se.getSoundFile(), se.getVolume());
	}

	/**
	 * Picks a sound at random weighted by each entry's {@link SoundEntry#getWeight() weight}.
	 * Returns {@code null} if every weight is 0 (nothing should play).
	 */
	static SoundEntry pickWeighted(List<SoundEntry> sounds)
	{
		double total = 0;
		for (SoundEntry s : sounds) total += Math.max(0, s.getWeight());
		if (total <= 0) return null;

		double roll = ThreadLocalRandom.current().nextDouble(total);
		for (SoundEntry s : sounds)
		{
			roll -= Math.max(0, s.getWeight());
			if (roll < 0) return s;
		}
		return sounds.get(sounds.size() - 1); // unreachable: roll < total guarantees a hit
	}

	void playSoundFile(String soundFile, int volume)
	{
		if (executor == null || executor.isShutdown()) return;
		if (volume == 0) return;
		float gain = TriggerEvaluator.volumeToGain(volume);

		if (soundFile == null || soundFile.isEmpty() || soundFile.startsWith(CustomWeaponSfxPanel.BUNDLED_PREFIX))
		{
			String name = (soundFile == null || soundFile.isEmpty())
				? DEFAULT_BUNDLED_FILE
				: soundFile.substring(CustomWeaponSfxPanel.BUNDLED_PREFIX.length()) + ".wav";
			executor.submit(() ->
			{
				try { audioPlayer.play(CustomWeaponSfxPlugin.class, name, gain); }
				catch (Exception e) { log.debug("Failed to play bundled sound {}", name, e); }
			});
		}
		else
		{
			File f = new File(soundsDir, soundFile + ".wav");
			if (!f.exists())
			{
				log.debug("Sound file missing: {}", f.getAbsolutePath());
				return;
			}
			executor.submit(() ->
			{
				try { audioPlayer.play(f, gain); }
				catch (Exception e) { log.debug("Failed to play {}", soundFile, e); }
			});
		}
	}
}
