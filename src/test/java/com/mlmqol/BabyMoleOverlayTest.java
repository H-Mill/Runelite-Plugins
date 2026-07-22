package com.mlmqol;

import java.awt.Color;
import java.awt.Graphics2D;
import java.util.Collections;
import net.runelite.api.NPC;
import net.runelite.api.Point;
import org.junit.Before;
import org.junit.Test;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Covers the name drawn over the baby mole. Which moles are in the set is the plugin's decision, and
 * is what keeps this to the Motherlode Mine — see MlmQolPluginTest.
 */
public class BabyMoleOverlayTest
{
	private static final String NAME = "Digby";
	private static final int TEXT_X = 40;
	private static final int TEXT_Y = 50;

	private MlmQolPlugin plugin;
	private MlmQolConfig config;
	private Graphics2D graphics;
	private BabyMoleOverlay overlay;

	@Before
	public void setUp()
	{
		plugin = mock(MlmQolPlugin.class);
		config = mock(MlmQolConfig.class);
		graphics = mock(Graphics2D.class);
		overlay = new BabyMoleOverlay(plugin, config);

		NPC mole = mock(NPC.class);
		when(mole.getLogicalHeight()).thenReturn(100);
		when(mole.getCanvasTextLocation(any(), any(), anyInt())).thenReturn(new Point(TEXT_X, TEXT_Y));
		when(plugin.getBabyMoles()).thenReturn(Collections.singleton(mole));

		when(config.nameBabyMole()).thenReturn(true);
		when(config.babyMoleName()).thenReturn(NAME);
		when(config.babyMoleNameColor()).thenReturn(Color.WHITE);
	}

	@Test
	public void theNameIsDrawnOverTheMole()
	{
		overlay.render(graphics);

		verify(graphics).drawString(NAME, TEXT_X, TEXT_Y);
	}

	@Test
	public void nothingIsDrawnWhenTurnedOff()
	{
		when(config.nameBabyMole()).thenReturn(false);

		overlay.render(graphics);

		verify(graphics, never()).drawString(anyString(), anyInt(), anyInt());
	}

	@Test
	public void turnedOffDoesNotEvenWalkTheSceneState()
	{
		// This runs every frame, so the cheap check has to come first.
		when(config.nameBabyMole()).thenReturn(false);

		overlay.render(graphics);

		verify(plugin, never()).getBabyMoles();
	}

	@Test
	public void anEmptyNameDrawsNothing()
	{
		when(config.babyMoleName()).thenReturn("");

		overlay.render(graphics);

		verify(graphics, never()).drawString(anyString(), anyInt(), anyInt());
	}

	@Test
	public void aNameOfNothingButSpacesDrawsNothing()
	{
		when(config.babyMoleName()).thenReturn("   ");

		overlay.render(graphics);

		verify(graphics, never()).drawString(anyString(), anyInt(), anyInt());
	}

	@Test
	public void surroundingSpaceIsTrimmedOffTheName()
	{
		when(config.babyMoleName()).thenReturn("  Digby  ");

		overlay.render(graphics);

		verify(graphics).drawString(NAME, TEXT_X, TEXT_Y);
	}

	@Test
	public void colourTagsInTheNameAreStripped()
	{
		// Otherwise the raw tag would be drawn as literal text.
		when(config.babyMoleName()).thenReturn("<col=ff0000>Digby</col>");

		overlay.render(graphics);

		verify(graphics).drawString(NAME, TEXT_X, TEXT_Y);
	}

	@Test
	public void aMoleWithNoCanvasLocationIsSkippedRatherThanCrashing()
	{
		NPC offScreen = mock(NPC.class);
		when(offScreen.getCanvasTextLocation(any(), any(), anyInt())).thenReturn(null);
		when(plugin.getBabyMoles()).thenReturn(Collections.singleton(offScreen));

		overlay.render(graphics);

		verify(graphics, never()).drawString(anyString(), anyInt(), anyInt());
	}

	@Test
	public void nothingIsDrawnWhenNoMoleIsTracked()
	{
		when(plugin.getBabyMoles()).thenReturn(Collections.emptySet());

		overlay.render(graphics);

		verify(graphics, never()).drawString(anyString(), anyInt(), anyInt());
	}
}
