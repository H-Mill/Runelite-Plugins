/*
 * Copyright (c) 2026, H-Mill <huntermill8@gmail.com>
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *     list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *     this list of conditions and the following disclaimer in the documentation
 *     and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE
 * FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
 * OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.friendgroups;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Insets;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.Map;
import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import javax.swing.Box;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.ui.components.colorpicker.ColorPickerManager;
import net.runelite.client.ui.components.materialtabs.MaterialTab;
import net.runelite.client.ui.components.materialtabs.MaterialTabGroup;
import net.runelite.client.util.ImageUtil;
import net.runelite.client.util.LinkBrowser;

/**
 * The side panel shell: a title, the plugin's config/support buttons, and a Friends / Ignore tab bar
 * that swaps between two {@link GroupListView}s - one per list, each fully independent.
 */
@Singleton
class FriendGroupsPanel extends PluginPanel
{
	private static final float TITLE_SIZE = 17f;

	private static final String KOFI_URL = "https://ko-fi.com/hmill8";

	private static final ImageIcon CONFIG_ICON;
	private static final ImageIcon CONFIG_HOVER_ICON;
	private static final ImageIcon KOFI_ICON;
	private static final ImageIcon KOFI_HOVER_ICON;

	static
	{
		final BufferedImage cog = ImageUtil.loadImageResource(FriendGroupsPanel.class, "/com/friendgroups/icon_cog.png");
		CONFIG_ICON = new ImageIcon(cog);
		CONFIG_HOVER_ICON = new ImageIcon(ImageUtil.luminanceOffset(cog, -100));

		final BufferedImage kofi = ImageUtil.loadImageResource(FriendGroupsPanel.class, "/com/friendgroups/icon_kofi.png");
		KOFI_ICON = new ImageIcon(kofi);
		KOFI_HOVER_ICON = new ImageIcon(ImageUtil.luminanceOffset(kofi, -100));
	}

	private final GroupListView friendsView;
	private final GroupListView ignoreView;

	/** Opens this plugin's configuration panel; set by the plugin. */
	private Runnable onOpenConfig;

	@Inject
	FriendGroupsPanel(
		@Named("friendGroups") GroupStore friendStore,
		@Named("ignoreGroups") GroupStore ignoreStore,
		ColorPickerManager colorPickerManager,
		FriendGroupsConfig config)
	{
		this.friendsView = new GroupListView(friendStore, colorPickerManager, config, GroupList.FRIENDS);
		this.ignoreView = new GroupListView(ignoreStore, colorPickerManager, config, GroupList.IGNORE);

		setLayout(new BorderLayout(0, 6));
		setBorder(new EmptyBorder(8, 8, 8, 8));

		final JLabel title = new JLabel("Friend Groups");
		setBoldFont(title, TITLE_SIZE);
		title.setForeground(Color.WHITE);

		final JButton openConfig = makeImageButton(CONFIG_ICON, CONFIG_HOVER_ICON, "Open plugin config");
		openConfig.addActionListener(e ->
		{
			if (onOpenConfig != null)
			{
				onOpenConfig.run();
			}
		});

		final JButton kofi = makeImageButton(KOFI_ICON, KOFI_HOVER_ICON, "Buy me a coffee :)");
		kofi.addActionListener(e -> LinkBrowser.browse(KOFI_URL));

		final JPanel titleButtons = new JPanel();
		titleButtons.setLayout(new BoxLayout(titleButtons, BoxLayout.X_AXIS));
		titleButtons.setOpaque(false);
		titleButtons.add(openConfig);
		titleButtons.add(Box.createHorizontalStrut(4));
		titleButtons.add(kofi);

		final JPanel titleRow = new JPanel(new BorderLayout());
		titleRow.setOpaque(false);
		titleRow.add(title, BorderLayout.WEST);
		titleRow.add(titleButtons, BorderLayout.EAST);

		// The tab bar swaps its selected tab's view into this display panel.
		final JPanel display = new JPanel(new BorderLayout());
		display.setOpaque(false);

		final MaterialTabGroup tabGroup = new MaterialTabGroup(display);
		tabGroup.setLayout(new BoxLayout(tabGroup, BoxLayout.X_AXIS));
		tabGroup.setBorder(new EmptyBorder(4, 0, 4, 0));

		final MaterialTab friendsTab = new MaterialTab("Friends", tabGroup, friendsView);
		final MaterialTab ignoreTab = new MaterialTab("Ignore", tabGroup, ignoreView);
		tabGroup.addTab(friendsTab);
		tabGroup.addTab(ignoreTab);
		tabGroup.select(friendsTab);

		final JPanel header = new JPanel();
		header.setLayout(new BoxLayout(header, BoxLayout.Y_AXIS));
		header.setOpaque(false);
		titleRow.setAlignmentX(LEFT_ALIGNMENT);
		tabGroup.setAlignmentX(LEFT_ALIGNMENT);
		header.add(titleRow);
		header.add(tabGroup);

		add(header, BorderLayout.NORTH);
		add(display, BorderLayout.CENTER);
	}

	@Override
	public void addNotify()
	{
		super.addNotify();

		final JScrollPane scrollPane = (JScrollPane) SwingUtilities.getAncestorOfClass(JScrollPane.class, this);
		if (scrollPane == null)
		{
			return;
		}

		// PluginPanel creates this scroll pane before RuneLite's look-and-feel is applied, so its
		// scroll bar keeps the default (Metal) UI. Re-apply the current LAF's UI so it uses
		// RuneLite's thin scroll bar, and clear the FlatLaf outline drawn around the whole panel.
		scrollPane.getVerticalScrollBar().updateUI();
		scrollPane.getHorizontalScrollBar().updateUI();
		scrollPane.setBorder(BorderFactory.createEmptyBorder());
		scrollPane.setViewportBorder(BorderFactory.createEmptyBorder());
	}

	void setOnOpenConfig(Runnable onOpenConfig)
	{
		this.onOpenConfig = onOpenConfig;
	}

	/** Sets the action run with a friend's world number when their row is double-clicked. */
	void setOnHop(java.util.function.IntConsumer onHop)
	{
		friendsView.setOnHop(onHop);
	}

	void setLoggedIn(boolean loggedIn)
	{
		friendsView.setLoggedIn(loggedIn);
		ignoreView.setLoggedIn(loggedIn);
	}

	/**
	 * @param friends     display name -> world, 0 when offline
	 * @param playerWorld our current world, 0 when unknown
	 */
	void setFriends(Map<String, Integer> friends, int playerWorld)
	{
		friendsView.setFriends(friends, playerWorld);
	}

	void setIgnores(List<String> names)
	{
		ignoreView.setNames(names);
	}

	/** Rebuilds the view for the given list (e.g. after a group change on that list). */
	void rebuild(GroupList list)
	{
		(list == GroupList.FRIENDS ? friendsView : ignoreView).rebuild();
	}

	void rebuildAll()
	{
		friendsView.rebuild();
		ignoreView.rebuild();
	}

	/** A borderless, transparent button showing only {@code icon}, swapping to {@code hoverIcon} on rollover. */
	private static JButton makeImageButton(ImageIcon icon, ImageIcon hoverIcon, String tooltip)
	{
		final JButton button = new JButton(icon);
		if (hoverIcon != null)
		{
			button.setRolloverIcon(hoverIcon);
		}
		button.setToolTipText(tooltip);
		button.setMargin(new Insets(0, 0, 0, 0));
		button.setBorderPainted(false);
		button.setContentAreaFilled(false);
		button.setFocusPainted(false);
		button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		return button;
	}

	private static void setBoldFont(Component c, float size)
	{
		c.setFont(c.getFont().deriveFont(Font.BOLD, size));
	}
}
