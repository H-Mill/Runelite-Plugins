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

		final List<Row> rows = new ArrayList<>();
		rows.add(buildRow(config.counterLabel(), plugin.getDeaths(), fm));
		if (config.showAllTimeDeaths())
		{
			rows.add(buildRow(config.allTimeLabel(), plugin.getAllTimeDeaths(), fm));
		}

		int contentWidth = 0;
		int contentHeight = 0;
		for (final Row row : rows)
		{
			contentWidth = Math.max(contentWidth, row.labelWidth + GAP + row.countWidth);
			contentHeight += row.labelLines.size() * lineHeight;
		}

		final int width = BORDER * 2 + contentWidth;
		final int height = BORDER * 2 + contentHeight;

		background.setRectangle(new Rectangle(0, 0, width, height));
		background.render(graphics);

		int rowTop = BORDER;
		for (final Row row : rows)
		{
			final int rowHeight = row.labelLines.size() * lineHeight;

			int baseline = rowTop + fm.getAscent();
			for (final String line : row.labelLines)
			{
				final TextComponent lineText = new TextComponent();
				lineText.setText(line);
				lineText.setPosition(new Point(BORDER, baseline));
				lineText.render(graphics);
				baseline += lineHeight;
			}

			final int countBaseline = rowTop + fm.getAscent() + (rowHeight - lineHeight) / 2;
			final TextComponent countText = new TextComponent();
			countText.setText(row.count);
			countText.setPosition(new Point(width - BORDER - row.countWidth, countBaseline));
			countText.render(graphics);

			rowTop += rowHeight;
		}

		return new Dimension(width, height);
	}

	private Row buildRow(String label, int value, FontMetrics fm)
	{
		final List<String> labelLines = wrap(label, fm, MAX_LABEL_WIDTH);
		int labelWidth = 0;
		for (final String line : labelLines)
		{
			labelWidth = Math.max(labelWidth, fm.stringWidth(line));
		}

		final String count = Integer.toString(value);
		return new Row(labelLines, labelWidth, count, fm.stringWidth(count));
	}

	private static final class Row
	{
		private final List<String> labelLines;
		private final int labelWidth;
		private final String count;
		private final int countWidth;

		Row(List<String> labelLines, int labelWidth, String count, int countWidth)
		{
			this.labelLines = labelLines;
			this.labelWidth = labelWidth;
			this.count = count;
			this.countWidth = countWidth;
		}
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
