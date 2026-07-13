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

import java.awt.Color;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import java.util.TreeMap;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.swing.SwingUtilities;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.Friend;
import net.runelite.api.FriendContainer;
import net.runelite.api.FontID;
import net.runelite.api.ScriptID;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.JavaScriptCallback;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetTextAlignment;
import net.runelite.api.widgets.WidgetType;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.game.chatbox.ChatboxPanelManager;
import net.runelite.client.ui.components.colorpicker.ColorPickerManager;
import net.runelite.client.ui.components.colorpicker.RuneliteColorPicker;
import net.runelite.client.util.ColorUtil;
import net.runelite.client.util.Text;

/**
 * Reorders the in-game friends list into group clusters with a colored header
 * above each group, using the same idea as the Sailing Reorderer plugin: after
 * the list's build script has run, reposition the row widgets by rewriting their
 * {@code originalY} and revalidating.
 *
 * <p>Unlike the sailing side panel, the friends list is drawn by a callback-driven
 * cs2 script, so whether each row survives as an addressable child widget can only
 * be confirmed in-game. This class is written to fail safe: if it cannot recognise
 * the row layout it makes no changes and logs a diagnostic. The {@code ::fgdump}
 * developer command prints the live layout so the constants here can be verified.
 *
 * <p>A friend in several groups is shown under each of them: the game's own row
 * (a physical row can only occupy one slot) fills the first, and each further
 * appearance is an interactive clone of it (see {@link #cloneRow}), so the same
 * right-click actions work from any group.
 */
@Slf4j
@Singleton
class FriendListReorderer
{
	/** Fallback row height if it cannot be measured (only one friend online). */
	private static final int FALLBACK_ROW_HEIGHT = 14;

	private static final String RENAME_PROMPT = "Rename group<br>"
		+ ColorUtil.prependColorTag("(limit " + FriendGroupManager.MAX_NAME_LENGTH + " characters)", new Color(0, 0, 170));

	private final Client client;
	private final ClientThread clientThread;
	private final FriendGroupManager manager;
	private final FriendGroupsConfig config;
	private final ChatboxPanelManager chatboxPanelManager;
	private final ColorPickerManager colorPickerManager;

	/**
	 * The header widgets this plugin has injected into the friends list. Tracked by
	 * reference (client thread only) so they can be reused and told apart from the game's
	 * own rows - the widget name can't be used as a marker because it is also the text the
	 * right-click menu shows for the header.
	 */
	private final List<Widget> headers = new ArrayList<>();

	/**
	 * Duplicate row widgets this plugin injects for a friend who is in more than one group, so
	 * they appear under each of their groups. Each carries a copy of the real row's text and of
	 * its game click behaviour (Message / Delete / ...), so it is fully interactive. Tracked by
	 * reference (client thread only) for reuse and to skip when detecting the game's own rows.
	 */
	private final List<Widget> clones = new ArrayList<>();

	/**
	 * Row widgets this plugin has hidden for a collapsed group. Only these are revealed on
	 * expand, so the game's own hidden widgets - such as the previous-name indicator shown
	 * when a friend changed their name - are never disturbed. Client thread only.
	 */
	private final Set<Widget> hiddenByMe = Collections.newSetFromMap(new IdentityHashMap<>());

	@Inject
	private FriendListReorderer(Client client, ClientThread clientThread, FriendGroupManager manager,
		FriendGroupsConfig config, ChatboxPanelManager chatboxPanelManager, ColorPickerManager colorPickerManager)
	{
		this.client = client;
		this.clientThread = clientThread;
		this.manager = manager;
		this.config = config;
		this.chatboxPanelManager = chatboxPanelManager;
		this.colorPickerManager = colorPickerManager;
	}

	/** One row of the friends list: the widgets sharing an original Y, and the friend it renders. */
	private static final class Row
	{
		private final int originalY;
		private final List<Widget> widgets = new ArrayList<>();
		private String friendKey;
		private String friendName;

		private Row(int originalY)
		{
			this.originalY = originalY;
		}
	}

	/** A displayed group (or the Ungrouped bucket, when {@code group} is null) and its rows. */
	private static final class Section
	{
		private final FriendGroup group;
		private final boolean collapsed;
		private final List<Row> members = new ArrayList<>();

		private Section(FriendGroup group, boolean collapsed)
		{
			this.group = group;
			this.collapsed = collapsed;
		}
	}

	/** Handles a click on a header: op 1 toggles collapse, ops 2/3 move a real group. */
	private void onHeaderOp(int op, FriendGroup group, boolean collapsed)
	{
		if (group == null)
		{
			switch (op)
			{
				case 1:
					manager.setUngroupedCollapsed(!collapsed);
					break;
				case 2:
					manager.moveUngrouped(-1);
					break;
				case 3:
					manager.moveUngrouped(1);
					break;
				default:
					break;
			}
			return;
		}

		final String name = group.getName();
		switch (op)
		{
			case 1:
				manager.setCollapsed(name, !collapsed);
				break;
			case 2:
				manager.moveGroup(name, -1);
				break;
			case 3:
				manager.moveGroup(name, 1);
				break;
			case 4:
				promptRename(name);
				break;
			case 5:
				pickColor(group);
				break;
			case 6:
				promptDelete(name);
				break;
			default:
				break;
		}
	}

	private void pickColor(FriendGroup group)
	{
		final String name = group.getName();
		final Color current = group.getAwtColor();

		SwingUtilities.invokeLater(() ->
		{
			final RuneliteColorPicker picker = colorPickerManager.create(client, current, "Group color", true);
			picker.setOnClose(color -> manager.setColor(name, color.getRGB() & 0xFFFFFF));
			picker.setVisible(true);
		});
	}

	private void promptRename(String currentName)
	{
		chatboxPanelManager.openTextInput(RENAME_PROMPT)
			.value(currentName)
			.onDone((String value) ->
			{
				final String newName = FriendGroupManager.sanitizeName(value);
				if (newName != null && !newName.equalsIgnoreCase(currentName))
				{
					manager.renameGroup(currentName, newName);
				}
			})
			.build();
	}

	private void promptDelete(String name)
	{
		chatboxPanelManager.openTextMenuInput("Delete group '" + name + "'?")
			.option("Yes", () -> manager.deleteGroup(name))
			.option("No", () ->
			{
			})
			.build();
	}

	/**
	 * Reapplies our in-game layout after the friends list is built: hides offline friends when
	 * that option is on, and in {@link InGameMarker#GROUPED} mode clusters the remaining rows
	 * under a header per group. Runs on the client thread from
	 * {@code ScriptPostFired(FRIENDS_UPDATE)}.
	 */
	void reorder()
	{
		final boolean grouped = config.inGameMarker() == InGameMarker.GROUPED;
		final boolean hideOffline = config.hideOffline();
		if (!grouped && !hideOffline)
		{
			return;
		}

		assert client.isClientThread();

		final Widget list = client.getWidget(InterfaceID.Friends.LIST);
		if (list == null)
		{
			return;
		}

		final Widget[] children = list.getDynamicChildren();
		if (children == null || children.length == 0)
		{
			return;
		}

		final Set<String> friendKeys = friendKeys();
		if (friendKeys.isEmpty())
		{
			return;
		}

		final Set<Widget> present = identitySet(children);

		// Our header and clone widgets from the previous pass that are still in the tree: reuse
		// pools, and the sets to skip while detecting the game's own rows.
		final Deque<Widget> pool = livingHeaders(present);
		final Deque<Widget> clonePool = livingClones(present);

		// Bucket the widgets into rows by their y position, and tag each row with the
		// friend it renders by matching a child's text against the friend container.
		final TreeMap<Integer, Row> byY = new TreeMap<>();
		for (Widget w : children)
		{
			if (w == null || pool.contains(w) || clonePool.contains(w))
			{
				continue;
			}

			final Row row = byY.computeIfAbsent(w.getOriginalY(), Row::new);
			row.widgets.add(w);

			final String text = w.getText();
			if (row.friendKey == null && text != null && !text.isEmpty())
			{
				final String key = FriendGroupManager.key(text);
				if (friendKeys.contains(key))
				{
					row.friendKey = key;
					row.friendName = Text.toJagexName(Text.removeTags(text));
				}
			}
		}

		final List<Row> friendRows = new ArrayList<>();
		for (Row row : byY.values())
		{
			if (row.friendKey != null)
			{
				friendRows.add(row);
			}
		}

		if (friendRows.isEmpty())
		{
			log.debug("Friend Groups: no friend rows recognised in the list widget ({} children); "
				+ "run ::fgdump to inspect the layout", children.length);
			return;
		}

		final int baseY = friendRows.get(0).originalY;
		final int rowHeight = measureRowHeight(friendRows);

		// Hide offline friends first, so both the flat and grouped layouts lay out only the
		// rows that remain visible.
		final List<Row> visible = new ArrayList<>();
		if (hideOffline)
		{
			final Set<String> offline = offlineKeys();
			for (Row row : friendRows)
			{
				if (offline.contains(row.friendKey))
				{
					hideRow(row);
				}
				else
				{
					visible.add(row);
				}
			}
		}
		else
		{
			visible.addAll(friendRows);
		}

		final List<Section> sections = grouped ? buildSections(visible) : Collections.<Section>emptyList();
		if (sections.isEmpty())
		{
			// Not grouped, or no group matches a visible friend: keep the natural order, just
			// pulled together so any hidden offline rows leave no gaps. No headers or clones here.
			parkAll(pool);
			parkClones(clonePool);

			int flatSlot = 0;
			for (Row row : visible)
			{
				placeRow(row, baseY + flatSlot * rowHeight);
				flatSlot++;
			}

			updateScroll(list, baseY + flatSlot * rowHeight);
			hiddenByMe.retainAll(present);
			return;
		}

		// Walk the sections top to bottom: a header takes a slot, then its members follow
		// (unless the section is collapsed, in which case the members are hidden and take no
		// slots so the following headers move up). A friend in several groups appears in each:
		// their real row fills the first slot, and the rest are interactive clones of it.
		clones.clear();
		final Set<Row> materialized = Collections.newSetFromMap(new IdentityHashMap<>());
		int slot = 0;
		final List<int[]> headerSlots = new ArrayList<>(); // {slotIndex, sectionIndex}
		for (int s = 0; s < sections.size(); s++)
		{
			final Section section = sections.get(s);
			headerSlots.add(new int[]{slot, s});
			slot++;

			for (Row row : section.members)
			{
				if (section.collapsed)
				{
					// Hide the real row on its first appearance; a duplicate under a collapsed
					// group simply shows nothing.
					if (materialized.add(row))
					{
						hideRow(row);
					}
					continue;
				}

				final int rowY = baseY + slot * rowHeight;
				if (materialized.add(row))
				{
					placeRow(row, rowY);
				}
				else
				{
					cloneRow(list, row, rowY, clonePool);
				}
				slot++;
			}
		}

		injectHeaders(list, sections, headerSlots, baseY, rowHeight, pool);

		// Park any clones left from a pass that had more duplicates.
		for (Widget stale : clonePool)
		{
			park(stale);
			clones.add(stale);
		}

		updateScroll(list, baseY + slot * rowHeight);

		// Drop references to any rows the game has since recreated, so the set can't grow.
		hiddenByMe.retainAll(present);
	}

	private void placeRow(Row row, int newY)
	{
		for (Widget w : row.widgets)
		{
			// Reveal only widgets we hid for a collapse; never the game's own hidden widgets
			// (e.g. the previous-name indicator on a friend who has not changed their name).
			if (hiddenByMe.remove(w))
			{
				w.setHidden(false);
			}
			w.setOriginalY(newY);
			w.revalidate();
		}
	}

	private void hideRow(Row row)
	{
		for (Widget w : row.widgets)
		{
			if (!w.isHidden())
			{
				w.setHidden(true);
				hiddenByMe.add(w);
				w.revalidate();
			}
		}
	}

	/**
	 * Groups the recognised rows into ordered display sections. Groups come first in the manager's
	 * order, with the Ungrouped section interleaved at its own configurable position; a friend in
	 * several groups appears under each of them (the walk that consumes these sections turns the
	 * extra appearances into interactive clones). Returns empty when no group has a member, so the
	 * natural list is left untouched.
	 */
	private List<Section> buildSections(List<Row> friendRows)
	{
		final List<FriendGroup> groups = manager.getGroups();
		final Set<String> grouped = new HashSet<>();

		// A section per group, kept even when empty so the Ungrouped position stays meaningful.
		final List<Section> groupSections = new ArrayList<>(groups.size());
		boolean anyGroupMembers = false;
		for (FriendGroup group : groups)
		{
			final Section section = new Section(group, group.isCollapsed());
			for (Row row : friendRows)
			{
				if (group.contains(row.friendName))
				{
					section.members.add(row);
					grouped.add(row.friendKey);
					anyGroupMembers = true;
				}
			}
			groupSections.add(section);
		}

		// With nothing actually grouped, leave the game's natural list rather than labelling
		// everything as Ungrouped.
		if (!anyGroupMembers)
		{
			return new ArrayList<>();
		}

		final Section ungrouped = new Section(null, manager.isUngroupedCollapsed());
		for (Row row : friendRows)
		{
			if (!grouped.contains(row.friendKey))
			{
				ungrouped.members.add(row);
			}
		}

		// Emit the non-empty group sections in order, dropping in the Ungrouped section at its
		// configured position.
		final int ungroupedPos = manager.ungroupedPosition();
		final List<Section> sections = new ArrayList<>();
		for (int i = 0; i <= groups.size(); i++)
		{
			if (i == ungroupedPos && !ungrouped.members.isEmpty())
			{
				sections.add(ungrouped);
			}
			if (i < groups.size() && !groupSections.get(i).members.isEmpty())
			{
				sections.add(groupSections.get(i));
			}
		}

		return sections;
	}

	private void injectHeaders(Widget list, List<Section> sections, List<int[]> headerSlots, int baseY, int rowHeight,
		Deque<Widget> pool)
	{
		headers.clear();

		for (int[] hs : headerSlots)
		{
			final Section section = sections.get(hs[1]);
			final FriendGroup group = section.group;
			final boolean collapsed = section.collapsed;
			final String label = group != null ? group.getName() : "Ungrouped";
			final int color = group != null ? group.getColor() : 0x9F9F9F;

			Widget header = pool.poll();
			if (header == null)
			{
				header = list.createChild(-1, WidgetType.TEXT);
			}
			headers.add(header);

			// The name is the target the right-click menu shows after the action, e.g. "Collapse PvM".
			header.setName(label);
			header.setHidden(false);
			header.setText(ColorUtil.wrapWithColorTag((collapsed ? "+ " : "- ") + label, new Color(color)));
			header.setFontId(FontID.PLAIN_12);
			header.setTextShadowed(true);
			header.setOriginalX(0);
			header.setOriginalY(baseY + hs[0] * rowHeight);
			header.setOriginalWidth(list.getWidth());
			header.setOriginalHeight(rowHeight);
			header.setXTextAlignment(WidgetTextAlignment.CENTER);
			header.setYTextAlignment(WidgetTextAlignment.CENTER);

			// Left-click toggles collapse; every header can be reordered with Move up / Move down,
			// and real groups also offer Rename / Set color / Delete on right-click.
			header.clearActions();
			header.setHasListener(true);
			header.setNoClickThrough(true);
			header.setAction(0, collapsed ? "Expand" : "Collapse");
			header.setAction(1, "Move up");
			header.setAction(2, "Move down");
			if (group != null)
			{
				header.setAction(3, "Rename");
				header.setAction(4, "Set color");
				// "Delete group", not "Delete": the Hiscore plugin adds a "Lookup" entry to any
				// friends-list row whose option is exactly "Delete".
				header.setAction(5, "Delete group");
			}
			header.setOnOpListener((JavaScriptCallback) e -> onHeaderOp(e.getOp(), group, collapsed));
			header.revalidate();
		}

		// Park (and keep tracking) any headers left over from a pass with more sections.
		for (Widget stale : pool)
		{
			park(stale);
			headers.add(stale);
		}
	}

	private static Set<Widget> identitySet(Widget[] children)
	{
		final Set<Widget> set = Collections.newSetFromMap(new IdentityHashMap<>());
		for (Widget w : children)
		{
			if (w != null)
			{
				set.add(w);
			}
		}
		return set;
	}

	/** The injected headers still present in the tree, in insertion order. */
	private Deque<Widget> livingHeaders(Set<Widget> present)
	{
		final Deque<Widget> living = new ArrayDeque<>();
		for (Widget header : headers)
		{
			if (present.contains(header))
			{
				living.add(header);
			}
		}
		return living;
	}

	/** The injected duplicate rows still present in the tree, in insertion order. */
	private Deque<Widget> livingClones(Set<Widget> present)
	{
		final Deque<Widget> living = new ArrayDeque<>();
		for (Widget clone : clones)
		{
			if (present.contains(clone))
			{
				living.add(clone);
			}
		}
		return living;
	}

	/** Called when the grouped layout is torn down, to restore the list before a normal redraw. */
	void removeHeaders()
	{
		final Widget list = client.getWidget(InterfaceID.Friends.LIST);
		if (list == null)
		{
			return;
		}

		final Widget[] children = list.getDynamicChildren();
		if (children != null)
		{
			final Set<Widget> present = identitySet(children);
			parkAll(livingHeaders(present));
			parkClones(livingClones(present));
		}
		unhideAll();
	}

	/** Reveals every row this plugin had hidden for a collapse. */
	private void unhideAll()
	{
		for (Widget w : hiddenByMe)
		{
			w.setHidden(false);
			w.revalidate();
		}
		hiddenByMe.clear();
	}

	private void parkAll(Deque<Widget> pool)
	{
		headers.clear();
		for (Widget header : pool)
		{
			park(header);
			headers.add(header);
		}
	}

	private void parkClones(Deque<Widget> pool)
	{
		clones.clear();
		for (Widget clone : pool)
		{
			park(clone);
			clones.add(clone);
		}
	}

	/**
	 * Injects a duplicate of a friend's row at {@code newY}, for showing them under a second (or
	 * further) group. Copies each of the row's text widgets - their text, color and font - and,
	 * for the interactive one, its actions and game op listener, so the copy's right-click menu
	 * (Message / Delete / Add ignore, plus this plugin's Assign group) acts on the same friend.
	 * The small status icon is decorative and is not copied.
	 */
	private void cloneRow(Widget list, Row row, int newY, Deque<Widget> clonePool)
	{
		for (Widget src : row.widgets)
		{
			if (src.getType() != WidgetType.TEXT || src.isHidden())
			{
				continue;
			}

			Widget clone = clonePool.poll();
			if (clone == null)
			{
				clone = list.createChild(-1, WidgetType.TEXT);
			}
			clones.add(clone);

			clone.setHidden(false);
			clone.setText(src.getText());
			clone.setName(src.getName());
			clone.setTextColor(src.getTextColor());
			clone.setTextShadowed(src.getTextShadowed());
			clone.setFontId(src.getFontId());
			clone.setOriginalX(src.getOriginalX());
			clone.setOriginalY(newY);
			clone.setOriginalWidth(src.getOriginalWidth());
			clone.setOriginalHeight(src.getOriginalHeight());
			clone.setWidthMode(src.getWidthMode());
			clone.setXTextAlignment(src.getXTextAlignment());
			clone.setYTextAlignment(src.getYTextAlignment());

			// Carry over the game's click behaviour so ops fire on the copy. The friend is
			// identified by the widget (its name/text), which we copy, so the ops target them.
			clone.clearActions();
			final String[] actions = src.getActions();
			final Object[] onOp = src.getOnOpListener();
			if (actions != null && onOp != null)
			{
				for (int i = 0; i < actions.length; i++)
				{
					if (actions[i] != null && !actions[i].isEmpty())
					{
						clone.setAction(i, actions[i]);
					}
				}
				clone.setOnOpListener(onOp);
				clone.setHasListener(true);
				clone.setNoClickThrough(true);
			}
			else
			{
				clone.setHasListener(false);
			}

			clone.revalidate();
		}
	}

	/** Hides a header widget out of the way; it is reused on the next pass if still present. */
	private static void park(Widget header)
	{
		// A parked header is hidden (so not clickable); its actions/listener are reset fresh
		// if it is reused, so only clear the visible action list here.
		header.setHidden(true);
		header.setText("");
		header.setName("");
		header.setOriginalY(0);
		header.setOriginalHeight(0);
		header.clearActions();
		header.setHasListener(false);
		header.revalidate();
	}

	private void updateScroll(Widget list, int contentHeight)
	{
		list.setScrollHeight(contentHeight);

		final int scrollbar = InterfaceID.Friends.SCROLLBAR;
		final int y = Math.max(0, Math.min(list.getScrollY(), Math.max(0, contentHeight - list.getHeight())));
		clientThread.invokeLater(() -> client.runScript(ScriptID.UPDATE_SCROLLBAR, scrollbar, list.getId(), y));
	}

	private static int measureRowHeight(List<Row> rows)
	{
		int min = Integer.MAX_VALUE;
		for (int i = 1; i < rows.size(); i++)
		{
			final int gap = rows.get(i).originalY - rows.get(i - 1).originalY;
			if (gap > 0 && gap < min)
			{
				min = gap;
			}
		}
		return min == Integer.MAX_VALUE ? FALLBACK_ROW_HEIGHT : min;
	}

	private Set<String> friendKeys()
	{
		final FriendContainer container = client.getFriendContainer();
		if (container == null)
		{
			return new HashSet<>();
		}

		final Friend[] members = container.getMembers();
		if (members == null)
		{
			return new HashSet<>();
		}

		final Set<String> keys = new HashSet<>();
		for (Friend friend : members)
		{
			if (friend != null && friend.getName() != null)
			{
				keys.add(FriendGroupManager.key(friend.getName()));
			}
		}
		return keys;
	}

	/** Keys of friends the game reports as offline (no world), whose rows are hidden. */
	private Set<String> offlineKeys()
	{
		final FriendContainer container = client.getFriendContainer();
		if (container == null)
		{
			return new HashSet<>();
		}

		final Friend[] members = container.getMembers();
		if (members == null)
		{
			return new HashSet<>();
		}

		final Set<String> keys = new HashSet<>();
		for (Friend friend : members)
		{
			if (friend != null && friend.getName() != null && friend.getWorld() <= 0)
			{
				keys.add(FriendGroupManager.key(friend.getName()));
			}
		}
		return keys;
	}

	/**
	 * Logs the live structure of the friends list widget, for verifying the row
	 * layout this class assumes. Wired to the {@code ::fgdump} developer command.
	 */
	void dumpLayout()
	{
		final Widget list = client.getWidget(InterfaceID.Friends.LIST);
		if (list == null)
		{
			log.info("Friend Groups dump: Friends.LIST widget is null (open the friends tab first)");
			return;
		}

		final Widget[] children = list.getDynamicChildren();
		log.info("Friend Groups dump: LIST id={} width={} height={} scrollHeight={} children={}",
			list.getId(), list.getWidth(), list.getHeight(), list.getScrollHeight(),
			children == null ? 0 : children.length);

		if (children == null)
		{
			return;
		}

		for (int i = 0; i < children.length; i++)
		{
			final Widget w = children[i];
			if (w == null)
			{
				continue;
			}

			log.info("  [{}] type={} x={} y={} w={} h={} name='{}' text='{}' actions={} hasListener={} onOp={}",
				i, w.getType(), w.getOriginalX(), w.getOriginalY(), w.getWidth(), w.getHeight(),
				w.getName(), w.getText(), java.util.Arrays.toString(w.getActions()),
				w.hasListener(), java.util.Arrays.toString(w.getOnOpListener()));
		}
	}
}
