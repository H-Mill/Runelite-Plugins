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
import java.awt.Container;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Insets;
import java.awt.Point;
import java.awt.Window;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.IntConsumer;
import javax.swing.Box;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.components.DragAndDropReorderPane;
import net.runelite.client.ui.components.FlatTextField;
import net.runelite.client.ui.components.IconTextField;
import net.runelite.client.ui.components.colorpicker.ColorPickerManager;
import net.runelite.client.ui.components.colorpicker.RuneliteColorPicker;
import net.runelite.client.util.ImageUtil;

/**
 * One list's group editor: the "New Group" button and the scrollable column of group sections that
 * lets you create, rename, recolor, reorder, collapse and populate groups. Bound to a single
 * {@link GroupStore}; the friends and ignore tabs each own one. Group order, names and colors are
 * edited here since the in-game list is built and sorted by the game and cannot be reordered there.
 *
 * <p>The world column and online/offline coloring are shown only when {@link GroupList#tracksOnline}
 * (the friends list); the ignore list has no online status.
 */
class GroupListView extends JPanel
{
	private static final Color ONLINE_COLOR = ColorScheme.PROGRESS_COMPLETE_COLOR;
	private static final Color OFFLINE_COLOR = ColorScheme.LIGHT_GRAY_COLOR.darker();
	/** Online, but on a world other than ours. */
	private static final Color OTHER_WORLD_COLOR = Color.YELLOW;

	/** Leading glyph on the New Group button; the rest of the icons are PNGs. */
	private static final String ADD = "+";

	private static final float SECTION_TITLE_SIZE = 16f;

	/** Width of the world-number column, wide enough for a 3-digit world. */
	private static final int WORLD_COLUMN_WIDTH = 26;

	private static final int SEARCH_HEIGHT = 24;

	/** Width of the search box's icon and clear-button gutters. */
	private static final int SEARCH_GUTTER_WIDTH = 24;

	/** Client property on each group section holding its group name, so a drop resolves which moved. */
	private static final String GROUP_NAME_KEY = "friendGroupName";

	private static final ImageIcon DRAG_ICON;
	private static final ImageIcon DRAG_HOVER_ICON;
	private static final ImageIcon EDIT_ICON;
	private static final ImageIcon EDIT_HOVER_ICON;
	private static final ImageIcon DELETE_ICON;
	private static final ImageIcon DELETE_HOVER_ICON;
	private static final ImageIcon ADD_ICON;
	private static final ImageIcon ADD_HOVER_ICON;
	/** Shown on a group heading when the group is collapsed (points right, "expand"). */
	private static final ImageIcon EXPAND_ICON;
	/** Shown on a group heading when the group is expanded (points down, "collapse"). */
	private static final ImageIcon COLLAPSE_ICON;
	private static final ImageIcon SEARCH_ICON;

	static
	{
		final BufferedImage drag = ImageUtil.loadImageResource(GroupListView.class, "/com/friendgroups/drag_handle.png");
		DRAG_ICON = new ImageIcon(drag);
		DRAG_HOVER_ICON = new ImageIcon(ImageUtil.luminanceOffset(drag, 40));

		final BufferedImage edit = ImageUtil.loadImageResource(GroupListView.class, "/com/friendgroups/icon_edit.png");
		EDIT_ICON = new ImageIcon(edit);
		EDIT_HOVER_ICON = new ImageIcon(ImageUtil.luminanceOffset(edit, -100));

		final BufferedImage delete = ImageUtil.loadImageResource(GroupListView.class, "/com/friendgroups/icon_delete.png");
		DELETE_ICON = new ImageIcon(delete);
		DELETE_HOVER_ICON = new ImageIcon(ImageUtil.luminanceOffset(delete, -100));

		final BufferedImage add = ImageUtil.loadImageResource(GroupListView.class, "/com/friendgroups/icon_add.png");
		ADD_ICON = new ImageIcon(add);
		ADD_HOVER_ICON = new ImageIcon(ImageUtil.luminanceOffset(add, -100));

		EXPAND_ICON = new ImageIcon(ImageUtil.loadImageResource(GroupListView.class, "/com/friendgroups/icon_right_arrow.png"));
		COLLAPSE_ICON = new ImageIcon(ImageUtil.loadImageResource(GroupListView.class, "/com/friendgroups/icon_down_arrow.png"));

		// The client's own search glyph, so the box matches the search fields in the rest of RuneLite.
		SEARCH_ICON = new ImageIcon(ImageUtil.loadImageResource(IconTextField.class, "search.png"));
	}

	private final GroupStore manager;
	private final ColorPickerManager colorPickerManager;
	private final FriendGroupsConfig config;
	private final GroupList list;
	private final boolean showWorlds;

	private final JPanel content = new JPanel();
	private final JButton newGroup = smallButton(ADD + " New Group", "Create a new group", this::createGroup);
	private final FlatTextField search = new FlatTextField();
	private final JButton clearSearch = makeClearButton();

	/** Normalized search text ({@link GroupStore#key}); empty when not filtering. */
	private String filter = "";

	/** Member display names in list order. */
	private List<String> memberNames = Collections.emptyList();
	/** Normalized member name -> world, 0 when offline. Empty for lists without online status. */
	private Map<String, Integer> worldByKey = Collections.emptyMap();
	/** Our current world, 0 when unknown. Members on a different world are colored differently. */
	private int playerWorld;

	/** Groups and members are only shown while logged in. */
	private boolean loggedIn;

	/** Hops to the given world when a friend row is double-clicked; null when hopping is unavailable. */
	private IntConsumer onHop;

	GroupListView(GroupStore manager, ColorPickerManager colorPickerManager, FriendGroupsConfig config,
		GroupList list)
	{
		this.manager = manager;
		this.colorPickerManager = colorPickerManager;
		this.config = config;
		this.list = list;
		this.showWorlds = list.tracksOnline;

		setLayout(new BorderLayout(0, 6));
		setOpaque(false);

		newGroup.setEnabled(false);

		final JPanel actions = new JPanel();
		actions.setLayout(new BoxLayout(actions, BoxLayout.X_AXIS));
		actions.setOpaque(false);
		actions.setAlignmentX(LEFT_ALIGNMENT);
		actions.add(newGroup);
		actions.add(Box.createHorizontalGlue());

		final JPanel header = new JPanel();
		header.setLayout(new BoxLayout(header, BoxLayout.Y_AXIS));
		header.setOpaque(false);
		header.add(actions);
		header.add(Box.createRigidArea(new Dimension(0, 4)));
		header.add(searchField());

		content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
		content.setOpaque(false);

		add(header, BorderLayout.NORTH);
		add(content, BorderLayout.CENTER);
	}

	/** Sets the action run with a friend's world number when their row is double-clicked. */
	void setOnHop(IntConsumer onHop)
	{
		this.onHop = onHop;
	}

	void setLoggedIn(boolean loggedIn)
	{
		this.loggedIn = loggedIn;
		newGroup.setEnabled(loggedIn);
		search.setEditable(loggedIn);
		rebuild();
	}

	/**
	 * The search box: RuneLite's flat field between a search glyph and a clear button. Assembled here
	 * rather than using {@link IconTextField} because that hardcodes a red-on-pink clear button.
	 */
	private JPanel searchField()
	{
		// The icon gutter stands in for the field's own left padding.
		search.setBorder(null);
		search.setEditable(false);

		// The look-and-feel's default input text is near-black against the dark field. The caret
		// takes its color from the LAF separately, so it needs setting too.
		final JTextField input = search.getTextField();
		input.setForeground(Color.WHITE);
		input.setCaretColor(Color.WHITE);
		// A document listener also covers the clear button and pasted text, which key events miss.
		search.getDocument().addDocumentListener(new DocumentListener()
		{
			@Override
			public void insertUpdate(DocumentEvent e)
			{
				onSearchChanged();
			}

			@Override
			public void removeUpdate(DocumentEvent e)
			{
				onSearchChanged();
			}

			@Override
			public void changedUpdate(DocumentEvent e)
			{
				onSearchChanged();
			}
		});

		final JLabel icon = new JLabel(SEARCH_ICON, SwingConstants.CENTER);
		icon.setPreferredSize(new Dimension(SEARCH_GUTTER_WIDTH, 0));

		final JPanel field = new JPanel(new BorderLayout());
		field.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		field.setToolTipText("Filter " + memberNoun() + "s by name");
		field.setAlignmentX(LEFT_ALIGNMENT);
		field.setPreferredSize(new Dimension(0, SEARCH_HEIGHT));
		field.setMaximumSize(new Dimension(Integer.MAX_VALUE, SEARCH_HEIGHT));
		field.add(icon, BorderLayout.WEST);
		field.add(search, BorderLayout.CENTER);
		field.add(clearSearch, BorderLayout.EAST);
		return field;
	}

	private void onSearchChanged()
	{
		// The raw text drives the button, so a whitespace-only search still offers a way out of it.
		clearSearch.setVisible(!search.getText().isEmpty());

		final String text = GroupStore.key(search.getText());
		if (text.equals(filter))
		{
			return;
		}

		filter = text;
		rebuild();
	}

	/** The × that empties the search box, shown only while there is something to clear. */
	private JButton makeClearButton()
	{
		final JButton button = new JButton("×");
		button.setFont(FontManager.getRunescapeBoldFont());
		button.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		button.setToolTipText("Clear search");
		button.setPreferredSize(new Dimension(SEARCH_GUTTER_WIDTH, 0));
		button.setVisible(false);
		button.setMargin(new Insets(0, 0, 0, 0));
		button.setBorderPainted(false);
		button.setContentAreaFilled(false);
		button.setFocusPainted(false);
		button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		button.addActionListener(e -> search.setText(""));
		button.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mouseEntered(MouseEvent e)
			{
				button.setForeground(Color.WHITE);
			}

			@Override
			public void mouseExited(MouseEvent e)
			{
				button.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
			}
		});
		return button;
	}

	/**
	 * @param friends     display name -> world, 0 when offline
	 * @param playerWorld our current world, 0 when unknown
	 */
	void setFriends(Map<String, Integer> friends, int playerWorld)
	{
		final Map<String, Integer> worlds = new HashMap<>();
		friends.forEach((name, world) -> worlds.put(GroupStore.key(name), world));

		memberNames = new ArrayList<>(friends.keySet());
		worldByKey = worlds;
		this.playerWorld = playerWorld;
		rebuild();
	}

	/** Sets the members for a list with no online status (the ignore list). */
	void setNames(List<String> names)
	{
		memberNames = new ArrayList<>(names);
		worldByKey = Collections.emptyMap();
		playerWorld = 0;
		rebuild();
	}

	void rebuild()
	{
		content.removeAll();

		if (!loggedIn)
		{
			content.add(hint("Log in to view your " + list.label.toLowerCase() + " groups."));
			content.revalidate();
			content.repaint();
			return;
		}

		final List<FriendGroup> groups = manager.getGroups();
		final OfflineDisplay offlineDisplay = list.offlineDisplay(config);

		final Set<String> grouped = new HashSet<>();
		for (FriendGroup group : groups)
		{
			for (String member : group.getMembers())
			{
				grouped.add(GroupStore.key(member));
			}
		}

		final List<String> ungrouped = new ArrayList<>();
		for (String name : memberNames)
		{
			if (!grouped.contains(GroupStore.key(name)) && matches(name) && isShownInPlace(name, offlineDisplay))
			{
				ungrouped.add(name);
			}
		}

		// Collected from the whole list rather than per section, so a friend in two groups is
		// listed here once.
		final List<String> offline = new ArrayList<>();
		if (offlineDisplay == OfflineDisplay.SEPARATE_GROUP)
		{
			for (String name : memberNames)
			{
				if (!isOnline(name) && matches(name))
				{
					offline.add(name);
				}
			}
			offline.sort(String::compareToIgnoreCase);
		}

		final boolean filtering = !filter.isEmpty();

		if (groups.isEmpty())
		{
			if (!filtering)
			{
				content.add(hint("No groups yet. Press + above, or right-click a name in the in-game "
					+ list.label.toLowerCase() + " list and choose \"Assign group\"."));
			}

			if (!ungrouped.isEmpty())
			{
				content.add(ungroupedSection(ungrouped, null));
			}
			else if (filtering && offline.isEmpty())
			{
				content.add(hint(noMatchesText()));
			}
		}
		else
		{
			// Reordering is disabled while filtering: the hidden sections are absent from the pane,
			// so persisting a drag would push every one of them to the end of the saved order.
			final DragAndDropReorderPane reorderPane = filtering ? null : new DragAndDropReorderPane();
			final JComponent host;
			if (reorderPane != null)
			{
				reorderPane.addDragListener(comp -> persistOrder(reorderPane));
				host = reorderPane;
			}
			else
			{
				final JPanel column = new JPanel();
				column.setLayout(new BoxLayout(column, BoxLayout.Y_AXIS));
				column.setOpaque(false);
				host = column;
			}
			host.setAlignmentX(LEFT_ALIGNMENT);

			int shown = 0;
			final int ungroupedPos = manager.ungroupedPosition();
			for (int i = 0; i <= groups.size(); i++)
			{
				if (i == ungroupedPos && !ungrouped.isEmpty())
				{
					final JPanel section = ungroupedSection(ungrouped, reorderPane);
					section.putClientProperty(GROUP_NAME_KEY, GroupStore.UNGROUPED);
					host.add(section);
					shown++;
				}
				if (i < groups.size())
				{
					final FriendGroup group = groups.get(i);
					final List<String> members = visibleMembers(group.getMembers());
					// A group with nothing matching is dropped, rather than left as an empty heading.
					if (filtering && members.isEmpty())
					{
						continue;
					}

					// Offline members may be listed elsewhere, but the section still stands so the
					// group stays editable - its count reports the full membership.
					final List<String> rows = new ArrayList<>(members);
					rows.removeIf(member -> !isShownInPlace(member, offlineDisplay));

					final JPanel section = groupSection(group, members, rows, reorderPane);
					section.putClientProperty(GROUP_NAME_KEY, group.getName());
					host.add(section);
					shown++;
				}
			}

			if (shown == 0)
			{
				if (offline.isEmpty())
				{
					content.add(hint(noMatchesText()));
				}
			}
			else
			{
				content.add(host);
				content.add(Box.createRigidArea(new Dimension(0, 4)));
			}
		}

		if (!offline.isEmpty())
		{
			content.add(offlineSection(offline));
			content.add(Box.createRigidArea(new Dimension(0, 4)));
		}

		content.revalidate();
		content.repaint();
	}

	private String noMatchesText()
	{
		return "No " + memberNoun() + "s match your search.";
	}

	/** Reads the pane's current section order and saves it (group names plus the Ungrouped marker). */
	private void persistOrder(DragAndDropReorderPane reorderPane)
	{
		final Component[] comps = reorderPane.getComponents();
		final String[] byPosition = new String[comps.length];
		for (Component c : comps)
		{
			if (c instanceof JComponent)
			{
				final Object token = ((JComponent) c).getClientProperty(GROUP_NAME_KEY);
				if (token instanceof String)
				{
					final int p = reorderPane.getPosition(c);
					if (p >= 0 && p < byPosition.length)
					{
						byPosition[p] = (String) token;
					}
				}
			}
		}

		final List<String> order = new ArrayList<>();
		for (String token : byPosition)
		{
			if (token != null)
			{
				order.add(token);
			}
		}
		manager.applyOrder(order);
	}

	/**
	 * @param members     the group's members, already sorted and search-filtered; drives the count
	 * @param rows        the subset to actually list here, which omits offline members when they are
	 *                    hidden or shown in their own section
	 * @param reorderPane the pane to drag within, or null when reordering is off (a filter is active)
	 */
	private JPanel groupSection(FriendGroup group, List<String> members, List<String> rows,
		DragAndDropReorderPane reorderPane)
	{
		final String name = group.getName();

		final String count;
		if (showWorlds)
		{
			int online = 0;
			for (String member : members)
			{
				if (isOnline(member))
				{
					online++;
				}
			}
			count = online + "/" + members.size();
		}
		else
		{
			count = String.valueOf(members.size());
		}

		final JLabel heading = new JLabel(name + "  " + count);
		heading.setIcon(group.isCollapsed() ? EXPAND_ICON : COLLAPSE_ICON);
		heading.setIconTextGap(4);
		setBoldFont(heading, SECTION_TITLE_SIZE);
		heading.setForeground(group.getAwtColor());
		heading.setToolTipText(group.isCollapsed() ? "Expand" : "Collapse");
		heading.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mouseClicked(MouseEvent e)
			{
				manager.setCollapsed(name, !group.isCollapsed());
			}
		});

		final JPanel swatch = new JPanel();
		swatch.setPreferredSize(new Dimension(10, 10));
		swatch.setBackground(group.getAwtColor());
		swatch.setToolTipText("Change color");
		swatch.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		swatch.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mouseClicked(MouseEvent e)
			{
				pickColor(group);
			}
		});

		final JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
		actions.setOpaque(false);
		if (reorderPane != null)
		{
			final JLabel dragHandle = makeDragHandle(reorderPane);
			dragHandle.setBorder(new EmptyBorder(0, 0, 0, 6));
			actions.add(dragHandle);
		}
		actions.add(swatch);
		actions.add(iconButton(EDIT_ICON, EDIT_HOVER_ICON, "Rename", () -> renameGroup(group)));
		actions.add(iconButton(DELETE_ICON, DELETE_HOVER_ICON, "Delete", () -> deleteGroup(group)));

		final JPanel head = new JPanel(new BorderLayout(0, 2));
		head.setOpaque(false);
		head.setBorder(new EmptyBorder(2, 4, 2, 2));
		head.add(actions, BorderLayout.NORTH);
		head.add(heading, BorderLayout.CENTER);

		final JPanel section = boundedPanel();
		section.setOpaque(true);
		section.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		// A press on the section's dead areas is absorbed so only the grip handle can start a
		// drag; the 4px matte paints the inter-row gap in the panel background.
		section.addMouseListener(new MouseAdapter()
		{
		});
		section.setBorder(BorderFactory.createMatteBorder(0, 0, 4, 0, ColorScheme.DARK_GRAY_COLOR));
		section.add(head, BorderLayout.NORTH);

		if (!group.isCollapsed())
		{
			final JPanel memberList = new JPanel();
			memberList.setLayout(new BoxLayout(memberList, BoxLayout.Y_AXIS));
			memberList.setOpaque(false);

			for (String member : rows)
			{
				memberList.add(memberRow(member, name));
			}

			if (memberList.getComponentCount() == 0)
			{
				memberList.add(hint(members.isEmpty()
					? "No " + memberNoun() + "s in this group."
					: "No online " + memberNoun() + "s in this group."));
			}
			section.add(memberList, BorderLayout.CENTER);
		}

		return section;
	}

	/**
	 * @param reorderPane the pane to drag within, or null when there are no groups to reorder
	 *                    against, in which case the section has no grip handle
	 */
	private JPanel ungroupedSection(List<String> ungrouped, DragAndDropReorderPane reorderPane)
	{
		final JLabel heading = new JLabel("Ungrouped  " + ungrouped.size());
		setBoldFont(heading, SECTION_TITLE_SIZE);
		heading.setForeground(ColorScheme.LIGHT_GRAY_COLOR);

		final JPanel memberList = new JPanel();
		memberList.setLayout(new BoxLayout(memberList, BoxLayout.Y_AXIS));
		memberList.setOpaque(false);
		for (String name : ungrouped)
		{
			memberList.add(memberRow(name, null));
		}

		final JPanel section = boundedPanel();
		section.setOpaque(true);
		section.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		// A press on the section's dead areas is absorbed so only the grip handle can start a
		// drag; the matte paints the inter-row gap, matching the group sections.
		section.addMouseListener(new MouseAdapter()
		{
		});
		section.setBorder(BorderFactory.createMatteBorder(0, 0, 4, 0, ColorScheme.DARK_GRAY_COLOR));

		if (reorderPane != null)
		{
			final JLabel dragHandle = makeDragHandle(reorderPane);
			dragHandle.setBorder(new EmptyBorder(0, 0, 0, 6));

			final JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
			actions.setOpaque(false);
			actions.add(dragHandle);

			final JPanel head = new JPanel(new BorderLayout(0, 2));
			head.setOpaque(false);
			head.setBorder(new EmptyBorder(2, 4, 2, 2));
			head.add(actions, BorderLayout.NORTH);
			head.add(heading, BorderLayout.CENTER);
			section.add(head, BorderLayout.NORTH);
		}
		else
		{
			heading.setBorder(new EmptyBorder(2, 4, 2, 2));
			section.add(heading, BorderLayout.NORTH);
		}

		section.add(memberList, BorderLayout.CENTER);
		return section;
	}

	/**
	 * The "Offline" bucket: every offline member, gathered out of their groups by
	 * {@link OfflineDisplay#SEPARATE_GROUP}. Pinned below the other sections, so it has no grip
	 * handle; its collapse state is the one the in-game header toggles.
	 */
	private JPanel offlineSection(List<String> offline)
	{
		final boolean collapsed = manager.isOfflineCollapsed();

		final JLabel heading = new JLabel("Offline  " + offline.size());
		heading.setIcon(collapsed ? EXPAND_ICON : COLLAPSE_ICON);
		heading.setIconTextGap(4);
		setBoldFont(heading, SECTION_TITLE_SIZE);
		heading.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		heading.setToolTipText(collapsed ? "Expand" : "Collapse");
		heading.setBorder(new EmptyBorder(2, 4, 2, 2));
		heading.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mouseClicked(MouseEvent e)
			{
				manager.setOfflineCollapsed(!collapsed);
			}
		});

		final JPanel section = boundedPanel();
		section.setOpaque(true);
		section.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		section.setBorder(BorderFactory.createMatteBorder(0, 0, 4, 0, ColorScheme.DARK_GRAY_COLOR));
		section.add(heading, BorderLayout.NORTH);

		if (!collapsed)
		{
			final JPanel memberList = new JPanel();
			memberList.setLayout(new BoxLayout(memberList, BoxLayout.Y_AXIS));
			memberList.setOpaque(false);
			for (String name : offline)
			{
				// No group name: this bucket cuts across every group, so there is no single one
				// for the row's remove button to act on.
				memberList.add(memberRow(name, null));
			}
			section.add(memberList, BorderLayout.CENTER);
		}

		return section;
	}

	/**
	 * Whether a member is listed under their own group (or Ungrouped), rather than pulled out of it
	 * by the offline display mode.
	 */
	private boolean isShownInPlace(String member, OfflineDisplay offlineDisplay)
	{
		return offlineDisplay == OfflineDisplay.IN_GROUP || isOnline(member);
	}

	/**
	 * @param groupName the group this row is listed under, or null when ungrouped
	 */
	private JPanel memberRow(String member, String groupName)
	{
		final JLabel name = new JLabel(member);
		name.setForeground(nameColor(member));

		final JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 1, 0));
		buttons.setOpaque(false);

		final JButton add = makeImageButton(ADD_ICON, ADD_HOVER_ICON, "Add to a group");
		add.addActionListener(e -> showAddPopup(add, member));
		buttons.add(add);

		if (groupName != null)
		{
			buttons.add(iconButton(DELETE_ICON, DELETE_HOVER_ICON, "Remove from " + groupName,
				() -> manager.removeMember(groupName, member)));
		}

		final Integer world = showWorlds ? worldByKey.get(GroupStore.key(member)) : null;

		final JPanel right = new JPanel(new BorderLayout(4, 0));
		right.setOpaque(false);
		if (showWorlds)
		{
			final JLabel worldLabel = new JLabel(world != null && world > 0 ? String.valueOf(world) : "",
				SwingConstants.CENTER);
			worldLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
			// Fixed-width so world numbers line up as a column regardless of the row's other content.
			worldLabel.setPreferredSize(new Dimension(WORLD_COLUMN_WIDTH, worldLabel.getPreferredSize().height));
			right.add(worldLabel, BorderLayout.WEST);
		}
		right.add(buttons, BorderLayout.EAST);

		final JPanel row = boundedPanel();
		row.setBorder(new EmptyBorder(0, 14, 0, 2));
		row.add(name, BorderLayout.WEST);
		row.add(right, BorderLayout.EAST);

		addRowHover(row);

		// Double-click an online friend on another world to quick-hop to their world. Swing does not
		// bubble mouse events to a parent, so the listener is added to both the name and the row so a
		// click anywhere along the row (not just the dead space) is caught.
		if (isHoppable(world, playerWorld))
		{
			final String tooltip = "Double-click to hop to World " + world;
			name.setToolTipText(tooltip);
			row.setToolTipText(tooltip);
			name.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

			final MouseAdapter hop = new MouseAdapter()
			{
				@Override
				public void mouseClicked(MouseEvent e)
				{
					if (e.getClickCount() == 2 && SwingUtilities.isLeftMouseButton(e) && onHop != null)
					{
						onHop.accept(world);
					}
				}
			};
			name.addMouseListener(hop);
			row.addMouseListener(hop);
		}

		return row;
	}

	/**
	 * Paints the row's background while the pointer is anywhere over it. The row is transparent by
	 * default (showing the section behind it) and only made opaque while hovered. The listener is
	 * added to the row and all its children, since Swing routes a mouse event to the deepest component
	 * and does not bubble it up; the exit is ignored unless the pointer has truly left the row, so
	 * moving between a row's own children does not flicker the highlight.
	 */
	private static void addRowHover(JPanel row)
	{
		final MouseAdapter hover = new MouseAdapter()
		{
			@Override
			public void mouseEntered(MouseEvent e)
			{
				row.setBackground(ColorScheme.DARKER_GRAY_HOVER_COLOR);
				row.setOpaque(true);
				row.repaint();
			}

			@Override
			public void mouseExited(MouseEvent e)
			{
				final Point p = SwingUtilities.convertPoint((Component) e.getSource(), e.getPoint(), row);
				if (!row.contains(p))
				{
					row.setOpaque(false);
					row.repaint();
				}
			}
		};
		addRecursiveMouseListener(row, hover);
	}

	/** Adds {@code listener} to {@code component} and every descendant, so hovering a child still fires it. */
	private static void addRecursiveMouseListener(Component component, MouseAdapter listener)
	{
		component.addMouseListener(listener);
		if (component instanceof Container)
		{
			for (Component child : ((Container) component).getComponents())
			{
				addRecursiveMouseListener(child, listener);
			}
		}
	}

	private void showAddPopup(Component invoker, String member)
	{
		final Set<String> memberOf = new HashSet<>();
		for (FriendGroup group : manager.groupsFor(member))
		{
			memberOf.add(group.getName());
		}

		final JPopupMenu popup = new JPopupMenu();
		boolean any = false;

		for (FriendGroup group : manager.getGroups())
		{
			final String name = group.getName();
			if (memberOf.contains(name))
			{
				continue;
			}

			final JMenuItem item = new JMenuItem(name);
			item.setForeground(group.getAwtColor());
			item.addActionListener(e -> manager.addMember(name, member));
			popup.add(item);
			any = true;
		}

		if (any)
		{
			popup.addSeparator();
		}

		final JMenuItem create = new JMenuItem("New group...");
		create.addActionListener(e ->
		{
			final String name = promptName("Name the new group:", "");
			if (name != null && requireAdded(name))
			{
				manager.addMember(name, member);
			}
		});
		popup.add(create);

		popup.show(invoker, 0, invoker.getHeight());
	}

	private void createGroup()
	{
		final String name = promptName("Name the new group:", "");
		if (name != null)
		{
			requireAdded(name);
		}
	}

	private boolean requireAdded(String name)
	{
		if (manager.addGroup(name))
		{
			return true;
		}

		JOptionPane.showMessageDialog(this,
			"That group already exists, or you have reached the limit of "
				+ GroupStore.MAX_GROUPS + " groups.",
			"Friend Groups", JOptionPane.WARNING_MESSAGE);
		return false;
	}

	private void renameGroup(FriendGroup group)
	{
		final String name = promptName("Rename \"" + group.getName() + "\" to:", group.getName());
		if (name == null || name.equalsIgnoreCase(group.getName()))
		{
			return;
		}

		if (!manager.renameGroup(group.getName(), name))
		{
			JOptionPane.showMessageDialog(this, "A group called \"" + name + "\" already exists.",
				"Friend Groups", JOptionPane.WARNING_MESSAGE);
		}
	}

	private void deleteGroup(FriendGroup group)
	{
		final int choice = JOptionPane.showConfirmDialog(this,
			"Delete the group \"" + group.getName() + "\"?\nIts " + memberNoun() + "s are not removed from your "
				+ list.label.toLowerCase() + " list.",
			"Friend Groups", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);

		if (choice == JOptionPane.YES_OPTION)
		{
			manager.deleteGroup(group.getName());
		}
	}

	private void pickColor(FriendGroup group)
	{
		final Window owner = SwingUtilities.getWindowAncestor(this);
		if (owner == null)
		{
			return;
		}

		final String name = group.getName();
		final RuneliteColorPicker picker = colorPickerManager.create(owner, group.getAwtColor(), "Group color", true);
		picker.setOnClose(color -> manager.setColor(name, color.getRGB() & 0xFFFFFF));
		picker.setVisible(true);
	}

	private String promptName(String message, String initial)
	{
		final Object value = JOptionPane.showInputDialog(this, message, initial);
		return value == null ? null : GroupStore.sanitizeName(value.toString());
	}

	/** The word for a single list member: "friend" or "ignored player". */
	private String memberNoun()
	{
		return showWorlds ? "friend" : "ignored player";
	}

	/** {@link #sortedMembers} with the search filter applied. */
	private List<String> visibleMembers(List<String> members)
	{
		final List<String> sorted = sortedMembers(members);
		sorted.removeIf(member -> !matchesSearch(member, filter));
		return sorted;
	}

	private boolean matches(String name)
	{
		return matchesSearch(name, filter);
	}

	/**
	 * Whether {@code name} contains the search text. Both sides go through {@link GroupStore#key},
	 * so the match ignores case, the non-breaking spaces the game uses in display names, and the
	 * underscores and hyphens players type in place of those spaces.
	 *
	 * @param filter search text already normalized by {@link GroupStore#key}; empty matches everything
	 */
	static boolean matchesSearch(String name, String filter)
	{
		return filter.isEmpty() || GroupStore.key(name).contains(filter);
	}

	/**
	 * Whether double-clicking a friend's row should hop to their world: they must be online (a world
	 * number greater than zero) and on a world other than the one we are on. Offline friends and
	 * friends already on our world are not hoppable.
	 *
	 * @param world       the friend's world, 0 when offline, null on lists without online status
	 * @param playerWorld our current world, 0 when unknown
	 */
	static boolean isHoppable(Integer world, int playerWorld)
	{
		return world != null && world > 0 && world != playerWorld;
	}

	private List<String> sortedMembers(List<String> members)
	{
		final List<String> sorted = new ArrayList<>(members);
		sorted.sort((a, b) ->
		{
			// Online-first only matters where there is online status; otherwise plain alphabetical.
			final int status = showWorlds ? Boolean.compare(!isOnline(a), !isOnline(b)) : 0;
			return status != 0 ? status : a.compareToIgnoreCase(b);
		});
		return sorted;
	}

	private boolean isOnline(String member)
	{
		final Integer world = worldByKey.get(GroupStore.key(member));
		return world != null && world > 0;
	}

	/**
	 * Green when on our world, yellow when online elsewhere, dimmed when offline. Lists without
	 * online status (the ignore list) show every name in the normal light color.
	 */
	private Color nameColor(String member)
	{
		if (!showWorlds)
		{
			return ColorScheme.LIGHT_GRAY_COLOR;
		}

		final Integer world = worldByKey.get(GroupStore.key(member));
		if (world == null || world <= 0)
		{
			return OFFLINE_COLOR;
		}
		return playerWorld > 0 && world != playerWorld ? OTHER_WORLD_COLOR : ONLINE_COLOR;
	}

	/**
	 * A grip icon that drags its group section. {@link DragAndDropReorderPane} begins a drag
	 * from a press anywhere on a child, so to confine dragging to this handle we forward the
	 * handle's own mouse events to the pane (stray presses on the section body are absorbed in
	 * {@link #groupSection}).
	 */
	private static JLabel makeDragHandle(DragAndDropReorderPane reorderPane)
	{
		final JLabel handle = new JLabel(DRAG_ICON);
		handle.setToolTipText("Drag to reorder this group");
		handle.setCursor(Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR));

		final MouseAdapter forwarder = new MouseAdapter()
		{
			@Override
			public void mousePressed(MouseEvent e)
			{
				handle.setIcon(DRAG_HOVER_ICON);
				forward(e);
			}

			@Override
			public void mouseDragged(MouseEvent e)
			{
				forward(e);
			}

			@Override
			public void mouseReleased(MouseEvent e)
			{
				handle.setIcon(DRAG_ICON);
				forward(e);
			}

			private void forward(MouseEvent e)
			{
				reorderPane.dispatchEvent(SwingUtilities.convertMouseEvent(handle, e, reorderPane));
			}
		};
		handle.addMouseListener(forwarder);
		handle.addMouseMotionListener(forwarder);
		return handle;
	}

	private static JLabel hint(String text)
	{
		final JLabel label = new JLabel("<html><body style='width:150px'>" + text + "</body></html>");
		label.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		label.setBorder(new EmptyBorder(4, 14, 4, 2));
		label.setAlignmentX(LEFT_ALIGNMENT);
		return label;
	}

	/** A BorderLayout panel that will not stretch vertically inside the BoxLayout column. */
	private static JPanel boundedPanel()
	{
		final JPanel panel = new JPanel(new BorderLayout())
		{
			@Override
			public Dimension getMaximumSize()
			{
				return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
			}
		};
		panel.setOpaque(false);
		panel.setAlignmentX(LEFT_ALIGNMENT);
		return panel;
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

	/** {@link #makeImageButton} plus a click action. */
	private static JButton iconButton(ImageIcon icon, ImageIcon hoverIcon, String tooltip, Runnable action)
	{
		final JButton button = makeImageButton(icon, hoverIcon, tooltip);
		if (action != null)
		{
			button.addActionListener(e -> action.run());
		}
		return button;
	}

	private static JButton smallButton(String text, String tooltip, Runnable action)
	{
		final JButton button = new JButton(text);
		button.setToolTipText(tooltip);
		button.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		button.setBorder(new EmptyBorder(1, 4, 1, 4));
		button.setContentAreaFilled(false);
		button.setFocusPainted(false);
		button.setMargin(new Insets(0, 0, 0, 0));

		if (action != null)
		{
			button.addActionListener(e -> action.run());
		}
		return button;
	}

	private static void setBoldFont(Component c, float size)
	{
		c.setFont(c.getFont().deriveFont(Font.BOLD, size));
	}
}
