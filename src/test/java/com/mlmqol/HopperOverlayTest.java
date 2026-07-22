package com.mlmqol;

import java.awt.Color;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.Shape;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import net.runelite.api.Point;
import net.runelite.api.TileObject;
import org.junit.Before;
import org.junit.Test;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Covers what the hopper highlight says and in which colour. The overlay runs every frame, so the
 * cheap gate has to come before anything that walks the scene.
 */
public class HopperOverlayTest
{
	private static final String STOPPED_TEXT = "Strut broken";
	private static final String IN_TRANSIT_TEXT = "Ore in transit";
	// Part-transparent, so the outline colour is distinguishable from the text colour: OverlayUtil
	// draws the polygon in the configured colour but forces text to full alpha.
	private static final Color STOPPED_COLOR = new Color(255, 0, 0, 180);
	private static final Color IN_TRANSIT_COLOR = new Color(255, 152, 31, 180);
	private static final int LINE_HEIGHT = 12;
	private static final int TEXT_X = 40;
	private static final int TEXT_Y = 50;

	private MlmQolPlugin plugin;
	private MlmQolConfig config;
	private Graphics2D graphics;
	private HopperOverlay overlay;
	private Shape clickbox;

	@Before
	public void setUp()
	{
		plugin = mock(MlmQolPlugin.class);
		config = mock(MlmQolConfig.class);
		graphics = mock(Graphics2D.class);
		overlay = new HopperOverlay(plugin, config);

		FontMetrics fontMetrics = mock(FontMetrics.class);
		when(fontMetrics.getHeight()).thenReturn(LINE_HEIGHT);
		when(graphics.getFontMetrics()).thenReturn(fontMetrics);

		clickbox = new Rectangle(10, 10, 20, 20);
		TileObject hopper = mock(TileObject.class);
		when(hopper.getClickbox()).thenReturn(clickbox);
		when(hopper.getCanvasTextLocation(any(), any(), anyInt())).thenReturn(new Point(TEXT_X, TEXT_Y));

		Set<TileObject> hoppers = new HashSet<>();
		hoppers.add(hopper);
		when(plugin.getHoppers()).thenReturn(hoppers);
		when(plugin.getBrokenStruts()).thenReturn(Collections.emptySet());

		when(config.stoppedHopperColor()).thenReturn(STOPPED_COLOR);
		when(config.oreInTransitColor()).thenReturn(IN_TRANSIT_COLOR);
		when(plugin.shouldFlagHopper()).thenReturn(true);
	}

	private void stopped(boolean stopped)
	{
		when(plugin.isMachineryStopped()).thenReturn(stopped);
	}

	private void inTransit(boolean inTransit)
	{
		when(plugin.hasPayDirtInTransit()).thenReturn(inTransit);
	}

	// ---- what it says --------------------------------------------------------------------------

	@Test
	public void aStoppedBeltReportsTheBrokenStrut()
	{
		stopped(true);
		inTransit(false);

		overlay.render(graphics);

		verify(graphics).drawString(STOPPED_TEXT, TEXT_X, TEXT_Y);
		verify(graphics, never()).drawString(eq(IN_TRANSIT_TEXT), anyInt(), anyInt());
	}

	@Test
	public void bothProblemsAreListedTogether()
	{
		stopped(true);
		inTransit(true);

		overlay.render(graphics);

		verify(graphics).drawString(STOPPED_TEXT, TEXT_X, TEXT_Y);
		verify(graphics).drawString(IN_TRANSIT_TEXT, TEXT_X, TEXT_Y + LINE_HEIGHT);
	}

	@Test
	public void aSingleLineIsNotOffsetAtAll()
	{
		stopped(true);
		inTransit(false);

		overlay.render(graphics);

		verify(graphics, never()).drawString(eq(STOPPED_TEXT), anyInt(), eq(TEXT_Y + LINE_HEIGHT));
	}

	// ---- colours -------------------------------------------------------------------------------

	private static Color opaque(Color color)
	{
		return new Color(color.getRed(), color.getGreen(), color.getBlue());
	}

	@Test
	public void eachLineIsDrawnInItsOwnColour()
	{
		stopped(true);
		inTransit(true);

		overlay.render(graphics);

		verify(graphics).setColor(opaque(STOPPED_COLOR));
		verify(graphics).setColor(opaque(IN_TRANSIT_COLOR));
	}

	@Test
	public void theOutlineTakesTheColourOfTheFirstProblem()
	{
		stopped(true);
		inTransit(true);

		overlay.render(graphics);

		verify(graphics).draw(clickbox);
		verify(graphics).setColor(STOPPED_COLOR);
		verify(graphics, never()).setColor(IN_TRANSIT_COLOR);
	}

	@Test
	public void theOutlineFallsBackToTheInTransitColourWhenThatIsTheOnlyProblem()
	{
		stopped(false);
		inTransit(true);

		overlay.render(graphics);

		verify(graphics).setColor(IN_TRANSIT_COLOR);
		verify(graphics, never()).setColor(STOPPED_COLOR);
	}

	// ---- gating --------------------------------------------------------------------------------
	// What counts as a problem is the plugin's decision, covered in MlmQolPluginTest. The overlay
	// only has to honour the answer.

	@Test
	public void theHopperIsDrawnWhenFlagged()
	{
		stopped(true);
		inTransit(false);

		overlay.render(graphics);

		verify(graphics).draw(clickbox);
	}

	@Test
	public void nothingIsDrawnWhenNotFlagged()
	{
		when(plugin.shouldFlagHopper()).thenReturn(false);
		stopped(true);
		inTransit(true);

		overlay.render(graphics);

		verify(graphics, never()).draw(any());
	}

	@Test
	public void anUnflaggedHopperDoesNotEvenWalkTheSceneState()
	{
		when(plugin.shouldFlagHopper()).thenReturn(false);

		overlay.render(graphics);

		verify(plugin, never()).getHoppers();
	}

	@Test
	public void nothingIsDrawnWhenNeitherProblemApplies()
	{
		// Defensive: shouldFlagHopper() and the two problems are read separately, so a state where
		// the hopper is flagged but nothing is wrong must not draw an empty highlight.
		stopped(false);
		inTransit(false);

		overlay.render(graphics);

		verify(graphics, never()).draw(any());
	}

	// ---- broken struts -------------------------------------------------------------------------

	private Shape brokenStrut()
	{
		Shape strutClickbox = new Rectangle(80, 80, 15, 15);
		TileObject strut = mock(TileObject.class);
		when(strut.getClickbox()).thenReturn(strutClickbox);
		when(plugin.getBrokenStruts()).thenReturn(Collections.singleton(strut));
		return strutClickbox;
	}

	@Test
	public void aBrokenStrutIsOutlined()
	{
		when(config.highlightBrokenStruts()).thenReturn(true);
		Shape strutClickbox = brokenStrut();

		overlay.render(graphics);

		verify(graphics).draw(strutClickbox);
	}

	@Test
	public void aSingleBrokenStrutIsOutlinedEvenThoughTheBeltKeepsRunning()
	{
		// One broken strut does not stop the belt, so nothing flags the hopper — but the strut still
		// wants repairing and must be drawn on its own account.
		when(config.highlightBrokenStruts()).thenReturn(true);
		when(plugin.shouldFlagHopper()).thenReturn(false);
		Shape strutClickbox = brokenStrut();

		overlay.render(graphics);

		verify(graphics).draw(strutClickbox);
		verify(graphics, never()).draw(clickbox);
	}

	@Test
	public void brokenStrutsAreNotOutlinedWhenTurnedOff()
	{
		when(config.highlightBrokenStruts()).thenReturn(false);
		Shape strutClickbox = brokenStrut();
		stopped(true);

		overlay.render(graphics);

		verify(graphics, never()).draw(strutClickbox);
		verify(graphics).draw(clickbox);
	}

	@Test
	public void aStrutWithNoClickboxIsSkippedRatherThanCrashing()
	{
		when(config.highlightBrokenStruts()).thenReturn(true);
		TileObject offScreen = mock(TileObject.class);
		when(offScreen.getClickbox()).thenReturn(null);
		when(plugin.getBrokenStruts()).thenReturn(Collections.singleton(offScreen));

		overlay.render(graphics);

		verify(graphics, never()).draw(any());
	}

	// ---- misc ----------------------------------------------------------------------------------

	@Test
	public void hopperWithNoClickboxIsSkippedRatherThanCrashing()
	{
		stopped(true);
		TileObject offScreen = mock(TileObject.class);
		when(offScreen.getClickbox()).thenReturn(null);
		when(plugin.getHoppers()).thenReturn(Collections.singleton(offScreen));

		overlay.render(graphics);

		verify(graphics, never()).draw(any());
	}

	@Test
	public void nothingIsDrawnWhenNoHopperIsTracked()
	{
		stopped(true);
		when(plugin.getHoppers()).thenReturn(Collections.emptySet());

		overlay.render(graphics);

		verify(graphics, never()).draw(any());
	}
}
