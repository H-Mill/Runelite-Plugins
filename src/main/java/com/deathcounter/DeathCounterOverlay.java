package com.deathcounter;

import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.List;
import javax.inject.Inject;
import net.runelite.api.MenuAction;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayMenuEntry;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.BackgroundComponent;
import net.runelite.client.ui.overlay.components.ComponentConstants;
import net.runelite.client.ui.overlay.components.TextComponent;

class DeathCounterOverlay extends Overlay
{
	static final String RESET_OPTION = "Reset";

	private static final int BORDER = ComponentConstants.STANDARD_BORDER;
	private static final int GAP = 6;
	private static final int MAX_LABEL_WIDTH = 120;

	private final DeathCounterPlugin plugin;
	private final DeathCounterConfig config;

	private final BackgroundComponent background = new BackgroundComponent();

	@Inject
	private DeathCounterOverlay(DeathCounterPlugin plugin, DeathCounterConfig config)
	{
		this.plugin = plugin;
		this.config = config;
		setMovable(true);
		setPosition(OverlayPosition.TOP_LEFT);
		getMenuEntries().add(new OverlayMenuEntry(MenuAction.RUNELITE_OVERLAY, RESET_OPTION, "Death Counter"));
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		final FontMetrics fm = graphics.getFontMetrics();
		final int lineHeight = fm.getHeight();

		final String count = Integer.toString(plugin.getDeaths());
		final int countWidth = fm.stringWidth(count);

		final List<String> lines = wrap(config.counterLabel(), fm, MAX_LABEL_WIDTH);
		int labelWidth = 0;
		for (String line : lines)
		{
			labelWidth = Math.max(labelWidth, fm.stringWidth(line));
		}

		final int textHeight = lines.size() * lineHeight;
		final int width = BORDER * 2 + labelWidth + GAP + countWidth;
		final int height = BORDER * 2 + textHeight;

		background.setRectangle(new Rectangle(0, 0, width, height));
		background.render(graphics);

		int baseline = BORDER + fm.getAscent();
		for (final String line : lines)
		{
			final TextComponent lineText = new TextComponent();
			lineText.setText(line);
			lineText.setPosition(new Point(BORDER, baseline));
			lineText.render(graphics);
			baseline += lineHeight;
		}

		final int countBaseline = BORDER + fm.getAscent() + (textHeight - lineHeight) / 2;
		final TextComponent countText = new TextComponent();
		countText.setText(count);
		countText.setPosition(new Point(width - BORDER - countWidth, countBaseline));
		countText.render(graphics);

		return new Dimension(width, height);
	}

	private static List<String> wrap(String text, FontMetrics fm, int maxWidth)
	{
		final List<String> lines = new ArrayList<>();
		if (text == null || text.isEmpty())
		{
			lines.add("");
			return lines;
		}

		if (fm.stringWidth(text) <= maxWidth)
		{
			lines.add(text);
			return lines;
		}

		final StringBuilder current = new StringBuilder();
		for (final String word : text.split(" "))
		{
			if (current.length() == 0)
			{
				current.append(word);
			}
			else if (fm.stringWidth(current + " " + word) <= maxWidth)
			{
				current.append(' ').append(word);
			}
			else
			{
				lines.add(current.toString());
				current.setLength(0);
				current.append(word);
			}
		}

		if (current.length() > 0)
		{
			lines.add(current.toString());
		}

		return lines;
	}
}
