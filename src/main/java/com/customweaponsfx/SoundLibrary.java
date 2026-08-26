package com.customweaponsfx;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.runelite.client.RuneLite;

/**
 * Knows which sounds exist: the .wav resources bundled with the plugin and the user's own .wav files
 * dropped into {@link #SOUNDS_DIR}. Separate from {@link SoundPlayer}, which knows how to play them.
 */
class SoundLibrary
{
	/** Directory under {@code .runelite} where users drop their own .wav files. */
	static final File SOUNDS_DIR = new File(RuneLite.RUNELITE_DIR, "customweaponsfx");

	/** Names of the .wav resources bundled with the plugin (without the .wav extension). */
	static final List<String> BUNDLED_SOUNDS = List.of(
		"bonk", "punch", "shot", "squeak", "oh-baby-a-triple",
		"emotional-damage", "thats-a-lot-of-damage", "okay");

	private final List<String> bundled;
	private List<String> available = new ArrayList<>();

	SoundLibrary()
	{
		SOUNDS_DIR.mkdirs();
		List<String> b = new ArrayList<>(BUNDLED_SOUNDS);
		b.sort(String.CASE_INSENSITIVE_ORDER);
		bundled = Collections.unmodifiableList(b);
		refresh();
	}

	/** Rescans {@link #SOUNDS_DIR} for user .wav files (call after the user adds/removes files). */
	void refresh()
	{
		List<String> sounds = new ArrayList<>();
		File[] files = SOUNDS_DIR.listFiles(f -> f.isFile() && f.getName().toLowerCase().endsWith(".wav"));
		if (files != null)
		{
			for (File f : files)
			{
				String name = f.getName();
				sounds.add(name.substring(0, name.length() - 4));
			}
			sounds.sort(String.CASE_INSENSITIVE_ORDER);
		}
		available = sounds;
	}

	/** User .wav files (without extension), sorted case-insensitively. */
	List<String> getAvailable()
	{
		return available;
	}

	/** Bundled sound names (without extension), sorted case-insensitively. */
	List<String> getBundled()
	{
		return bundled;
	}
}
