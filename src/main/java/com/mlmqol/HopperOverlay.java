package com.mlmqol;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Shape;
import java.util.ArrayList;
import java.util.List;
import javax.inject.Inject;
import net.runelite.api.Point;
import net.runelite.api.TileObject;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.OverlayUtil;

/**
 * Marks any broken strut, and marks the hopper while something is worth knowing about it, saying
 * which thing: a strut needs repairing, your pay-dirt is still on the belt, or both at once.
 */
class HopperOverlay extends Overlay
{
	private static final String STOPPED_TEXT = "Strut broken";
	private static final String IN_TRANSIT_TEXT = "Ore in transit";

	private final MlmQolPlugin plugin;
	private final MlmQolConfig config;

	@Inject
	HopperOverlay(MlmQolPlugin plugin, MlmQolConfig config)
	{
		this.plugin = plugin;
		this.config = config;
		setPosition(OverlayPosition.DYNAMIC);
		setLayer(OverlayLayer.ABOVE_SCENE);
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		// Independent of the hopper: a single broken strut is worth pointing out even though the belt
		// keeps running on the other wheel.
		if (config.highlightBrokenStruts())
		{
			for (TileObject strut : plugin.getBrokenStruts())
			{
				Shape clickbox = strut.getClickbox();
				if (clickbox != null)
				{
					OverlayUtil.renderPolygon(graphics, clickbox, config.stoppedHopperColor());
				}
			}
		}

		if (!plugin.shouldFlagHopper())
		{
			return null;
		}

		List<String> texts = new ArrayList<>(2);
		List<Color> colors = new ArrayList<>(2);

		if (plugin.isMachineryStopped())
		{
			texts.add(STOPPED_TEXT);
			colors.add(config.stoppedHopperColor());
		}

		if (plugin.hasPayDirtInTransit())
		{
			texts.add(IN_TRANSIT_TEXT);
			colors.add(config.oreInTransitColor());
		}

		if (texts.isEmpty())
		{
			return null;
		}

		// One outline for the hopper, in the colour of the first thing that is wrong with it.
		Color outlineColor = colors.get(0);
		int lineHeight = texts.size() > 1 ? graphics.getFontMetrics().getHeight() : 0;

		for (TileObject hopper : plugin.getHoppers())
		{
			Shape clickbox = hopper.getClickbox();
			if (clickbox == null)
			{
				// Off screen or not rendered this frame.
				continue;
			}

			OverlayUtil.renderPolygon(graphics, clickbox, outlineColor);

			for (int i = 0; i < texts.size(); i++)
			{
				String text = texts.get(i);
				Point location = hopper.getCanvasTextLocation(graphics, text, 0);
				if (location == null)
				{
					continue;
				}

				// Each line is centred on its own width, then stacked downwards.
				Point stacked = new Point(location.getX(), location.getY() + i * lineHeight);
				OverlayUtil.renderTextLocation(graphics, stacked, text, colors.get(i));
			}
		}

		return null;
	}
}
