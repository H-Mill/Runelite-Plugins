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

import com.google.gson.Gson;
import com.google.inject.Provides;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import javax.swing.SwingUtilities;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.Friend;
import net.runelite.api.FriendContainer;
import net.runelite.api.GameState;
import net.runelite.api.Ignore;
import net.runelite.api.Menu;
import net.runelite.api.MenuAction;
import net.runelite.api.MenuEntry;
import net.runelite.api.Nameable;
import net.runelite.api.NameableContainer;
import net.runelite.api.ScriptID;
import net.runelite.api.events.CommandExecuted;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.MenuEntryAdded;
import net.runelite.api.events.NameableNameChanged;
import net.runelite.api.events.RemovedFriend;
import net.runelite.api.events.ScriptCallbackEvent;
import net.runelite.api.events.ScriptPreFired;
import net.runelite.api.events.ScriptPostFired;
import net.runelite.api.events.VarClientIntChanged;
import net.runelite.api.events.WidgetLoaded;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetUtil;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.events.OverlayMenuClicked;
import net.runelite.client.events.RuneScapeProfileChanged;
import net.runelite.client.game.ChatIconManager;
import net.runelite.client.game.chatbox.ChatboxPanelManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.ui.components.colorpicker.ColorPickerManager;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.ui.overlay.OverlayMenuEntry;
import net.runelite.client.util.ColorUtil;
import net.runelite.client.util.ImageUtil;
import net.runelite.client.util.Text;

@Slf4j
@PluginDescriptor(
		name = "Friend Groups",
		description = "Organise your friends and ignore lists into named, re-orderable groups",
		tags = {"friend", "friends", "ignore", "group", "groups", "social", "list"},
		configName = "FriendGroups"
)
public class FriendGroupsPlugin extends Plugin
{
	private static final String ASSIGN_GROUP = "Assign group";
	private static final String NEW_GROUP = "New group...";
	private static final String NEW_GROUP_PROMPT = "Group name<br>"
		+ ColorUtil.prependColorTag("(limit " + GroupStore.MAX_NAME_LENGTH + " characters)", new Color(0, 0, 170));

	/** One colored square in the group grid, in pixels. Shrink or grow to taste. */
	private static final int DOT_SIZE = 4;
	/** Gap between squares in the grid. */
	private static final int DOT_GAP = 1;
	/** The grid is 2 wide; four groups fill one 2x2 block, further groups spill into more blocks. */
	private static final int GRID_COLS = 2;
	private static final int GRID_CELLS = GRID_COLS * GRID_COLS;
	/** Sprite box height; the grid is centred in it so it lines up with the row text. */
	private static final int DOT_BOX_HEIGHT = 11;

	/** Cap on how many 2x2 grids a row shows, past which the dots outgrow the list column. */
	private static final int MAX_GRIDS = 3;

	/** Prefix the game puts before the world number in each online friend's row. */
	private static final String WORLD_PREFIX = "World ";

	@Inject
	private Client client;

	@Inject
	private ClientThread clientThread;

	@Inject
	private FriendGroupsConfig config;

	@Inject
	private ConfigManager configManager;

	@Inject
	@Named("friendGroups")
	private GroupStore friendStore;

	@Inject
	@Named("ignoreGroups")
	private GroupStore ignoreStore;

	@Inject
	private FriendGroupsPanel panel;

	@Inject
	private FriendGroupsOverlay overlay;

	@Inject
	private ChatIconManager chatIconManager;

	@Inject
	private ChatboxPanelManager chatboxPanelManager;

	@Inject
	private ClientToolbar clientToolbar;

	@Inject
	private OverlayManager overlayManager;

	@Inject
	private EventBus eventBus;

	@Inject
	@Named("friendList")
	private ListReorderer friendReorderer;

	@Inject
	@Named("ignoreList")
	private ListReorderer ignoreReorderer;

	@Inject
	@Named("developerMode")
	private boolean developerMode;

	/** Name hovered in an in-game list, or null; paired with {@link #hoveredList}. Read by the overlay. */
	private String hoveredName;
	private GroupList hoveredList;

	private NavigationButton navButton;

	/** Ordered colors of one 2x2 grid batch (up to four) -> chat icon id. Client thread only. */
	private final Map<List<Integer>, Integer> dotIcons = new HashMap<>();

	private final Consumer<Boolean> friendListener = dirty -> onGroupsChanged(GroupList.FRIENDS, dirty);
	private final Consumer<Boolean> ignoreListener = dirty -> onGroupsChanged(GroupList.IGNORE, dirty);

	private Map<String, Integer> friendWorlds = Collections.emptyMap();

	/** Our own world last pushed to the panel, so the panel colors friends by same/other world. */
	private int playerWorld;

	/** Normalized names of the current friends / ignores. Client thread only. */
	private Set<String> friendKeys = Collections.emptySet();
	private Set<String> ignoreKeys = Collections.emptySet();

	/** Display names of the current ignores, to detect changes (the ignore list carries no world). */
	private List<String> ignoreNames = Collections.emptyList();

	/**
	 * Which list's build script is currently running, so the shared row-decoration callback (fired
	 * for the friends, ignore and friends-chat lists alike) decorates using the right list's store.
	 * Set from {@link #onScriptPreFired}. Client thread only.
	 */
	private GroupList activeList = GroupList.FRIENDS;

	/** Pixel width added by the grid icon on the row being laid out, so the x-shift matches the text. */
	private int rowDotShift;

	@Provides
	FriendGroupsConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(FriendGroupsConfig.class);
	}

	@Provides
	@Singleton
	@Named("friendGroups")
	GroupStore provideFriendStore(ConfigManager configManager, Gson gson)
	{
		return new GroupStore(configManager, gson, GroupList.FRIENDS);
	}

	@Provides
	@Singleton
	@Named("ignoreGroups")
	GroupStore provideIgnoreStore(ConfigManager configManager, Gson gson)
	{
		return new GroupStore(configManager, gson, GroupList.IGNORE);
	}

	@Provides
	@Singleton
	@Named("friendList")
	ListReorderer provideFriendReorderer(Client client, ClientThread clientThread,
		@Named("friendGroups") GroupStore store, FriendGroupsConfig config,
		ChatboxPanelManager chatboxPanelManager, ColorPickerManager colorPickerManager)
	{
		return new ListReorderer(client, clientThread, store, config, chatboxPanelManager, colorPickerManager,
			GroupList.FRIENDS);
	}

	@Provides
	@Singleton
	@Named("ignoreList")
	ListReorderer provideIgnoreReorderer(Client client, ClientThread clientThread,
		@Named("ignoreGroups") GroupStore store, FriendGroupsConfig config,
		ChatboxPanelManager chatboxPanelManager, ColorPickerManager colorPickerManager)
	{
		return new ListReorderer(client, clientThread, store, config, chatboxPanelManager, colorPickerManager,
			GroupList.IGNORE);
	}

	private GroupStore storeFor(GroupList list)
	{
		return list == GroupList.FRIENDS ? friendStore : ignoreStore;
	}

	private ListReorderer reordererFor(GroupList list)
	{
		return list == GroupList.FRIENDS ? friendReorderer : ignoreReorderer;
	}

	private Set<String> keysFor(GroupList list)
	{
		return list == GroupList.FRIENDS ? friendKeys : ignoreKeys;
	}

	/**
	 * Carries retired config keys onto their replacements so a user's saved choices survive
	 * rather than silently resetting. Each runs once: the old key is unset afterwards, so later
	 * starts skip it. Applies to the friends list only (the ignore list is new).
	 * <ul>
	 *   <li>{@code reorderInGame} (boolean) -&gt; the {@link InGameMarker#GROUPED} marker mode.</li>
	 *   <li>{@code showOffline} (boolean) -&gt; {@code hideOffline}, itself now folded into
	 *       {@code offlineDisplay} below.</li>
	 *   <li>{@code hideOffline} (boolean) -&gt; {@link OfflineDisplay#HIDDEN} when set.</li>
	 *   <li>the retired {@code TAG} ("Group names") marker -&gt; {@link InGameMarker#DOT}.</li>
	 * </ul>
	 */
	private void migrateConfig()
	{
		final String reorder = configManager.getConfiguration(FriendGroupsConfig.GROUP, "reorderInGame");
		if (reorder != null)
		{
			if (Boolean.parseBoolean(reorder))
			{
				configManager.setConfiguration(FriendGroupsConfig.GROUP, "inGameMarker", InGameMarker.GROUPED);
			}
			configManager.unsetConfiguration(FriendGroupsConfig.GROUP, "reorderInGame");
		}

		if ("TAG".equals(configManager.getConfiguration(FriendGroupsConfig.GROUP, "inGameMarker")))
		{
			configManager.setConfiguration(FriendGroupsConfig.GROUP, "inGameMarker", InGameMarker.DOT);
		}

		final String showOffline = configManager.getConfiguration(FriendGroupsConfig.GROUP, "showOffline");
		if (showOffline != null)
		{
			// showOffline defaulted true; only a user who turned it off wants offline friends hidden.
			if (!Boolean.parseBoolean(showOffline))
			{
				configManager.setConfiguration(FriendGroupsConfig.GROUP, "hideOffline", true);
			}
			configManager.unsetConfiguration(FriendGroupsConfig.GROUP, "showOffline");
		}

		// Runs after the showOffline step above, so a user still on that key migrates through
		// hideOffline to offlineDisplay in one start.
		final String hideOffline = configManager.getConfiguration(FriendGroupsConfig.GROUP, "hideOffline");
		if (hideOffline != null)
		{
			// hideOffline defaulted false, matching the offlineDisplay default; only a user who
			// turned it on has a choice to carry over.
			if (Boolean.parseBoolean(hideOffline))
			{
				configManager.setConfiguration(FriendGroupsConfig.GROUP, "offlineDisplay", OfflineDisplay.HIDDEN);
			}
			configManager.unsetConfiguration(FriendGroupsConfig.GROUP, "hideOffline");
		}
	}

	@Override
	protected void startUp() throws Exception
	{
		migrateConfig();
		friendStore.load();
		ignoreStore.load();
		friendStore.addListener(friendListener);
		ignoreStore.addListener(ignoreListener);

		rebuildNavButton();
		overlayManager.add(overlay);

		panel.setOnOpenConfig(this::openConfiguration);
		showGroups(friendStore.isActive());

		refreshInGame(GroupList.FRIENDS);
		refreshInGame(GroupList.IGNORE);
	}

	@Override
	protected void shutDown() throws Exception
	{
		friendStore.removeListener(friendListener);
		ignoreStore.removeListener(ignoreListener);
		if (navButton != null)
		{
			clientToolbar.removeNavigation(navButton);
		}
		overlayManager.remove(overlay);

		navButton = null;
		hoveredName = null;
		hoveredList = null;
		rowDotShift = 0;
		friendWorlds = Collections.emptyMap();
		friendKeys = Collections.emptySet();
		ignoreKeys = Collections.emptySet();
		ignoreNames = Collections.emptyList();
		playerWorld = 0;

		// Redraw both lists without our decorations. ChatIconManager has no way to drop the dot
		// icons we registered, so they are deliberately left behind for a later start.
		if (client.getGameState() == GameState.LOGGED_IN)
		{
			clientThread.invokeLater(() ->
			{
				friendReorderer.removeHeaders();
				ignoreReorderer.removeHeaders();
				rebuildList(GroupList.FRIENDS);
				rebuildList(GroupList.IGNORE);
			});
		}
	}

	/**
	 * Re-adds the toolbar nav button so a changed {@link FriendGroupsConfig#sidePanelPriority()} takes
	 * effect live, or removes it entirely when {@link FriendGroupsConfig#hideSidePanel()} is set.
	 */
	private void rebuildNavButton()
	{
		if (navButton != null)
		{
			clientToolbar.removeNavigation(navButton);
			navButton = null;
		}

		if (config.hideSidePanel())
		{
			return;
		}

		navButton = NavigationButton.builder()
			.tooltip("Friend Groups")
			.icon(ImageUtil.loadImageResource(getClass(), "friend_groups_icon.png"))
			.priority(config.sidePanelPriority())
			.panel(panel)
			.build();
		clientToolbar.addNavigation(navButton);
	}

	/**
	 * Opens this plugin's configuration panel. We don't have access to the ConfigPlugin directly, so
	 * we emulate the overlay "Configure" click it listens for, carrying a throwaway overlay bound to
	 * this plugin so ConfigPlugin can resolve which config to show.
	 */
	private void openConfiguration()
	{
		final Overlay overlay = new Overlay(this)
		{
			@Override
			public java.awt.Dimension render(Graphics2D graphics)
			{
				return null;
			}
		};
		eventBus.post(new OverlayMenuClicked(
			new OverlayMenuEntry(MenuAction.RUNELITE_OVERLAY_CONFIG, null, null), overlay));
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		switch (event.getGameState())
		{
			case LOGGED_IN:
				showGroups(friendStore.isActive());
				refreshInGame(GroupList.FRIENDS);
				refreshInGame(GroupList.IGNORE);
				break;
			case LOGIN_SCREEN:
				friendWorlds = Collections.emptyMap();
				friendKeys = Collections.emptySet();
				ignoreKeys = Collections.emptySet();
				ignoreNames = Collections.emptyList();
				playerWorld = 0;
				SwingUtilities.invokeLater(() ->
				{
					panel.setLoggedIn(false);
					panel.setFriends(Collections.emptyMap(), 0);
					panel.setIgnores(Collections.emptyList());
				});
				break;
		}
	}

	/**
	 * The active character changed, so both stores are reloaded onto it and everything showing their
	 * groups is redrawn. Fires on login and logout, and on hopping between game modes - a leagues or
	 * beta world is its own RuneScape profile, and so keeps its own groups.
	 */
	@Subscribe
	public void onRuneScapeProfileChanged(RuneScapeProfileChanged event)
	{
		friendStore.load();
		ignoreStore.load();

		showGroups(friendStore.isActive());

		refreshInGame(GroupList.FRIENDS);
		refreshInGame(GroupList.IGNORE);
	}

	/**
	 * Shows the loaded character's groups in the side panel, or the logged-out hint when there are
	 * none to show.
	 */
	private void showGroups(boolean active)
	{
		SwingUtilities.invokeLater(() -> panel.setLoggedIn(active));
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		if (!FriendGroupsConfig.GROUP.equals(event.getGroup()))
		{
			return;
		}

		if (FriendGroupsConfig.SIDE_PANEL_PRIORITY.equals(event.getKey())
			|| FriendGroupsConfig.HIDE_SIDE_PANEL.equals(event.getKey()))
		{
			SwingUtilities.invokeLater(this::rebuildNavButton);
		}
		else if ("offlineDisplay".equals(event.getKey()) || "hideWorldPrefix".equals(event.getKey()))
		{
			// Friends list only. The rebuild redraws every friend (including ones we had hidden) at
			// their natural positions before re-laying them out, and re-strips (or restores) the
			// world column.
			refreshInGame(GroupList.FRIENDS);

			// The offline display drives the side panel's sections too.
			if ("offlineDisplay".equals(event.getKey()))
			{
				SwingUtilities.invokeLater(() -> panel.rebuild(GroupList.FRIENDS));
			}
		}
		else if ("inGameMarker".equals(event.getKey()))
		{
			onMarkerChanged(GroupList.FRIENDS);
		}
		else if ("ignoreInGameMarker".equals(event.getKey()))
		{
			onMarkerChanged(GroupList.IGNORE);
		}
	}

	/** Leaving grouped mode drops the header rows before the list is drawn normally again. */
	private void onMarkerChanged(GroupList list)
	{
		if (list.marker(config) != InGameMarker.GROUPED)
		{
			clientThread.invokeLater(reordererFor(list)::removeHeaders);
		}
		refreshInGame(list);
	}

	/**
	 * A list has finished (re)building, so its row widgets exist and can be regrouped. This fires
	 * whenever a list is drawn - opening the tab, a member's status changing, or our own rebuild.
	 */
	@Subscribe
	public void onScriptPostFired(ScriptPostFired event)
	{
		if (event.getScriptId() == ScriptID.FRIENDS_UPDATE)
		{
			if (config.hideWorldPrefix())
			{
				stripWorldPrefixes();
			}
			friendReorderer.reorder();
		}
		else if (event.getScriptId() == ScriptID.IGNORE_UPDATE)
		{
			ignoreReorderer.reorder();
		}
	}

	/**
	 * Records which list's build script is running, so the shared decoration callback picks the
	 * right store. Fires just before {@link #onScriptPostFired} for the same script.
	 */
	@Subscribe
	public void onScriptPreFired(ScriptPreFired event)
	{
		if (event.getScriptId() == ScriptID.FRIENDS_UPDATE)
		{
			activeList = GroupList.FRIENDS;
		}
		else if (event.getScriptId() == ScriptID.IGNORE_UPDATE)
		{
			activeList = GroupList.IGNORE;
		}
	}

	/**
	 * Some content rebuilds a whole social interface (e.g. {@link InterfaceID#FRIENDS}) rather than
	 * redrawing its rows: drinking from a Pool of Refreshment, for one, tears the interface down and
	 * reloads it. That reload repaints the list in the game's default ungrouped order <em>without</em>
	 * firing the list's update script, so {@link #onScriptPostFired} never runs and our layout is lost.
	 * Reapply it whenever the friends or ignore interface reloads.
	 */
	@Subscribe
	public void onWidgetLoaded(WidgetLoaded event)
	{
		final GroupList list = reloadedList(event.getGroupId());
		if (list != null)
		{
			refreshInGame(list);
		}
	}

	/**
	 * The list whose interface just loaded, or null for any other interface. Both the friends and
	 * ignore interfaces repaint on reload without firing their update script, so both need our layout
	 * reapplied; every other interface shares the same load event and must be left alone.
	 */
	static GroupList reloadedList(int groupId)
	{
		for (GroupList list : GroupList.values())
		{
			if (list.interfaceGroup == groupId)
			{
				return list;
			}
		}
		return null;
	}

	/**
	 * The Name / Recent / World / Legacy sort buttons change the list's sort var and redraw it
	 * ungrouped, without re-applying our layout. Force a clean rebuild so the reorder re-runs from the
	 * freshly sorted rows: in grouped mode this re-clusters them, and with offline friends hidden or
	 * separated it re-applies that to the rows the sort redrew - otherwise they revert.
	 */
	@Subscribe
	public void onVarClientIntChanged(VarClientIntChanged event)
	{
		if (client.getGameState() != GameState.LOGGED_IN)
		{
			return;
		}

		for (GroupList list : GroupList.values())
		{
			if (event.getIndex() == list.sortVar
				&& shouldRebuildOnSort(list.marker(config), list.offlineDisplay(config)))
			{
				clientThread.invokeLater(() -> rebuildList(list));
				return;
			}
		}
	}

	/**
	 * Whether a sort-button click needs a forced list rebuild. Grouped mode has to re-cluster the
	 * freshly sorted rows, and any offline handling other than {@link OfflineDisplay#IN_GROUP} (in
	 * any marker mode) has to re-hide or re-sink the offline rows the sort redrew - without it they
	 * revert. Leaving both alone leaves the game's own sorted list untouched.
	 */
	static boolean shouldRebuildOnSort(InGameMarker marker, OfflineDisplay offlineDisplay)
	{
		return marker == InGameMarker.GROUPED || offlineDisplay != OfflineDisplay.IN_GROUP;
	}

	@Subscribe
	public void onCommandExecuted(CommandExecuted event)
	{
		if (developerMode && "fgdump".equals(event.getCommand()))
		{
			clientThread.invokeLater(() ->
			{
				friendReorderer.dumpLayout();
				ignoreReorderer.dumpLayout();
			});
		}
	}

	/**
	 * There is no event for a friend/ignore coming online or being added, so the panel's view of both
	 * lists is polled. Each container holds at most a few hundred entries and this runs once per tick,
	 * off the render path.
	 */
	@Subscribe
	public void onGameTick(GameTick event)
	{
		pollFriends();
		pollIgnores();
	}

	private void pollFriends()
	{
		final FriendContainer container = client.getFriendContainer();
		if (container == null)
		{
			return;
		}

		final Map<String, Integer> current = new LinkedHashMap<>();
		final Friend[] members = container.getMembers();
		if (members != null)
		{
			for (Friend friend : members)
			{
				if (friend != null && friend.getName() != null)
				{
					current.put(friend.getName(), friend.getWorld());
				}
			}
		}

		final int world = client.getWorld();

		if (!current.equals(friendWorlds) || world != playerWorld)
		{
			friendWorlds = current;
			playerWorld = world;

			final Set<String> keys = new HashSet<>();
			for (String name : current.keySet())
			{
				keys.add(GroupStore.key(name));
			}
			friendKeys = keys;

			SwingUtilities.invokeLater(() -> panel.setFriends(current, world));
		}
	}

	private void pollIgnores()
	{
		final NameableContainer<? extends Nameable> container = client.getIgnoreContainer();
		if (container == null)
		{
			return;
		}

		final List<String> current = new ArrayList<>();
		final Nameable[] members = container.getMembers();
		if (members != null)
		{
			for (Nameable member : members)
			{
				if (member != null && member.getName() != null)
				{
					current.add(member.getName());
				}
			}
		}

		if (current.equals(ignoreNames))
		{
			return;
		}

		final Set<String> keys = new HashSet<>();
		for (String name : current)
		{
			keys.add(GroupStore.key(name));
		}

		// Drop group membership for players who were un-ignored (there is no RemovedIgnore event).
		// Only prune against a non-empty container, so a transient empty read never wipes memberships.
		if (!keys.isEmpty())
		{
			for (String oldKey : ignoreKeys)
			{
				if (!keys.contains(oldKey))
				{
					ignoreStore.forgetFriend(oldKey);
				}
			}
		}

		ignoreNames = current;
		ignoreKeys = keys;

		final List<String> forPanel = new ArrayList<>(current);
		SwingUtilities.invokeLater(() -> panel.setIgnores(forPanel));
	}

	@Subscribe
	public void onMenuEntryAdded(MenuEntryAdded event)
	{
		final int groupId = WidgetUtil.componentToInterface(event.getActionParam1());
		final GroupList list = reloadedList(groupId);
		if (list == null || !event.getOption().equals(list.anchorOption))
		{
			hoveredName = null;
			hoveredList = null;
			return;
		}

		final GroupStore store = storeFor(list);
		final String name = friendFromTarget(event.getTarget());
		hoveredName = name;
		hoveredList = list;

		final MenuEntry parent = client.getMenu().createMenuEntry(-1)
			.setOption(ASSIGN_GROUP)
			.setTarget(event.getTarget())
			.setType(MenuAction.RUNELITE);

		final Menu submenu = parent.createSubMenu();

		// Menu entries are rebuilt every frame while hovering, so resolve membership
		// through the index rather than scanning each group's member list.
		final Set<String> memberOf = new HashSet<>();
		for (FriendGroup group : store.groupsFor(name))
		{
			memberOf.add(group.getName());
		}

		for (FriendGroup group : store.getGroups())
		{
			final String groupName = group.getName();
			final boolean member = memberOf.contains(groupName);

			submenu.createMenuEntry(-1)
				.setOption((member ? "Remove from " : "Add to ")
					+ ColorUtil.wrapWithColorTag(groupName, group.getAwtColor()))
				.setType(MenuAction.RUNELITE)
				.onClick(e ->
				{
					if (member)
					{
						store.removeMember(groupName, name);
					}
					else
					{
						store.addMember(groupName, name);
					}
				});
		}

		submenu.createMenuEntry(-1)
			.setOption(NEW_GROUP)
			.setType(MenuAction.RUNELITE)
			.onClick(e -> promptNewGroup(store, name));
	}

	@Subscribe
	public void onRemovedFriend(RemovedFriend event)
	{
		friendStore.forgetFriend(event.getNameable().getName());
	}

	@Subscribe
	public void onNameableNameChanged(NameableNameChanged event)
	{
		final Nameable nameable = event.getNameable();
		if (nameable.getPrevName() == null)
		{
			return;
		}

		if (nameable instanceof Friend)
		{
			friendStore.renameFriend(nameable.getPrevName(), nameable.getName());
		}
		else if (nameable instanceof Ignore)
		{
			ignoreStore.renameFriend(nameable.getPrevName(), nameable.getName());
		}
	}

	@Subscribe
	public void onScriptCallbackEvent(ScriptCallbackEvent event)
	{
		// Only 'Colored dots' decorates individual rows. 'Off' does nothing, and 'Grouped list'
		// clusters the list under group headers that already convey membership. The marker is that
		// of the list whose build script is running (see activeList).
		if (activeList.marker(config) != InGameMarker.DOT)
		{
			return;
		}

		switch (event.getEventName())
		{
			case "friendsChatSetText":
				decorateRow();
				break;
			case "friendsChatSetPosition":
				if (rowDotShift > 0)
				{
					final int[] intStack = client.getIntStack();
					final int size = client.getIntStackSize();
					intStack[size - 4] += rowDotShift;
				}
				break;
		}
	}

	private void decorateRow()
	{
		final Object[] objectStack = client.getObjectStack();
		final int size = client.getObjectStackSize();
		final String rsn = (String) objectStack[size - 1];

		rowDotShift = 0;

		// This callback is shared across the friends, ignore and friends-chat lists, so rows that are
		// not a member of the list currently building must be left alone.
		final String name = Text.toJagexName(Text.removeTags(rsn));
		if (!keysFor(activeList).contains(GroupStore.key(name)))
		{
			return;
		}

		final List<Integer> colors = dotColors(storeFor(activeList), name);
		if (colors.isEmpty())
		{
			return;
		}

		final StringBuilder text = new StringBuilder(rsn);
		int shift = 0;
		for (List<Integer> grid : dotGrids(colors))
		{
			final int index = dotIconIndex(grid);
			if (index == -1)
			{
				// A sprite for this batch is not registered yet; a refresh will add it and redraw.
				return;
			}

			text.append(" <img=").append(index).append('>');
			shift += gridWidth(grid.size()) + 1;
		}

		objectStack[size - 1] = text.toString();
		rowDotShift = shift;
	}

	/** Ordered group colors for a member, capped at the grids a row shows; empty when ungrouped. */
	private List<Integer> dotColors(GroupStore store, String name)
	{
		final List<FriendGroup> groups = store.groupsFor(name);
		if (groups.isEmpty())
		{
			return Collections.emptyList();
		}

		final int max = GRID_CELLS * MAX_GRIDS;
		final List<Integer> colors = new ArrayList<>(Math.min(groups.size(), max));
		for (FriendGroup group : groups)
		{
			if (colors.size() >= max)
			{
				break;
			}
			colors.add(group.getColor());
		}
		return colors;
	}

	/** Splits a member's colors into successive 2x2 batches of up to four, each drawn as one sprite. */
	private static List<List<Integer>> dotGrids(List<Integer> colors)
	{
		final List<List<Integer>> grids = new ArrayList<>((colors.size() + GRID_CELLS - 1) / GRID_CELLS);
		for (int start = 0; start < colors.size(); start += GRID_CELLS)
		{
			grids.add(new ArrayList<>(colors.subList(start, Math.min(start + GRID_CELLS, colors.size()))));
		}
		return grids;
	}

	/**
	 * Reads a member's name out of a list menu target. Rows carry color tags for online status, and
	 * 'Colored dots' appends an {@code <img>} icon; a RuneScape name holds neither, so stripping the
	 * tags leaves the name.
	 */
	static String friendFromTarget(String target)
	{
		return Text.toJagexName(Text.removeTags(target));
	}

	private void onGroupsChanged(GroupList list, boolean inGameDirty)
	{
		SwingUtilities.invokeLater(() -> panel.rebuild(list));

		if (inGameDirty)
		{
			refreshInGame(list);
		}
	}

	private void refreshInGame(GroupList list)
	{
		if (client.getGameState() != GameState.LOGGED_IN)
		{
			return;
		}

		clientThread.invokeLater(() ->
		{
			final boolean registered = registerDotIcons();
			rebuildList(list);

			// A freshly registered icon has no sprite index until ChatIconManager's own
			// queued refresh runs, so redraw the list once more behind that refresh.
			if (registered)
			{
				clientThread.invokeLater(() -> rebuildList(list));
			}
		});
	}

	private void promptNewGroup(GroupStore store, String friend)
	{
		chatboxPanelManager.openTextInput(NEW_GROUP_PROMPT)
			.onDone((String value) ->
			{
				final String name = GroupStore.sanitizeName(value);
				if (name == null)
				{
					return;
				}

				store.addGroup(name);
				store.addMember(name, friend);
			})
			.build();
	}

	/**
	 * Ensures a grid sprite exists for every 2x2 color batch a grouped member (in either list) needs.
	 * Batches are registered lazily (only what actual memberships use), since the theoretical set
	 * across all groups is combinatorial. Driven off the stored memberships rather than the live
	 * containers, so the sprites exist as soon as the config loads - the containers are still empty
	 * for a tick or two after login.
	 *
	 * @return true if at least one new icon was registered
	 */
	private boolean registerDotIcons()
	{
		boolean registered = false;
		for (GroupList list : GroupList.values())
		{
			final GroupStore store = storeFor(list);
			final Set<String> seen = new HashSet<>();
			for (FriendGroup group : store.getGroups())
			{
				for (String member : group.getMembers())
				{
					if (!seen.add(GroupStore.key(member)))
					{
						continue;
					}

					for (List<Integer> grid : dotGrids(dotColors(store, member)))
					{
						if (!dotIcons.containsKey(grid))
						{
							dotIcons.put(grid, chatIconManager.registerChatIcon(createDotGrid(grid)));
							registered = true;
						}
					}
				}
			}
		}
		return registered;
	}

	private int dotIconIndex(List<Integer> colors)
	{
		final Integer iconId = dotIcons.get(colors);
		return iconId == null ? -1 : chatIconManager.chatIconIndex(iconId);
	}

	/** Horizontal advance of the grid sprite for {@code count} dots: one column for one dot, else two. */
	private static int gridWidth(int count)
	{
		final int cols = Math.min(count, GRID_COLS);
		return cols * DOT_SIZE + (cols - 1) * DOT_GAP;
	}

	/**
	 * Builds a single sprite packing up to four group colors into a 2x2 grid, filled left-to-right
	 * then top-to-bottom. The full two-row grid is centred vertically in the row's line box, so a
	 * partial batch (one or two dots) sits on the top row rather than floating in the middle.
	 * Colors are the member's live group colors, so custom-picked colors render as-is.
	 */
	private static BufferedImage createDotGrid(List<Integer> colors)
	{
		final int count = colors.size();

		final BufferedImage image = new BufferedImage(gridWidth(count), DOT_BOX_HEIGHT, BufferedImage.TYPE_INT_ARGB);
		final Graphics2D graphics = image.createGraphics();
		final int gridHeight = GRID_COLS * DOT_SIZE + (GRID_COLS - 1) * DOT_GAP;
		final int top = (DOT_BOX_HEIGHT - gridHeight) / 2;
		// No antialiasing: the sprite is quantised to an indexed palette, and soft edges muddy it.
		for (int i = 0; i < count; i++)
		{
			final int x = (i % GRID_COLS) * (DOT_SIZE + DOT_GAP);
			final int y = top + (i / GRID_COLS) * (DOT_SIZE + DOT_GAP);
			graphics.setColor(new Color(colors.get(i)));
			graphics.fillRect(x, y, DOT_SIZE, DOT_SIZE);
		}
		graphics.dispose();
		return image;
	}

	/**
	 * Drops the "World " prefix the game writes before each online friend's world number,
	 * leaving just the number. The world sits in its own row widget, separate from the name,
	 * so it is rewritten here after the list is built rather than through the name callback.
	 * Runs on the client thread from {@code ScriptPostFired(FRIENDS_UPDATE)}. Friends list only -
	 * the ignore list carries no world.
	 */
	private void stripWorldPrefixes()
	{
		final Widget list = client.getWidget(InterfaceID.Friends.LIST);
		if (list == null)
		{
			return;
		}

		final Widget[] children = list.getDynamicChildren();
		if (children == null)
		{
			return;
		}

		for (Widget w : children)
		{
			if (w == null)
			{
				continue;
			}

			final String text = w.getText();
			if (text == null || text.isEmpty())
			{
				continue;
			}

			// Match on the tag-stripped text so a color-wrapped "World 369" is caught, but keep
			// the original (tagged) string when rewriting so the world keeps its color.
			final String plain = Text.removeTags(text);
			if (plain.length() > WORLD_PREFIX.length()
				&& plain.startsWith(WORLD_PREFIX)
				&& Character.isDigit(plain.charAt(WORLD_PREFIX.length())))
			{
				w.setText(text.replace(WORLD_PREFIX, ""));
				w.revalidate();
			}
		}
	}

	private void rebuildList(GroupList list)
	{
		log.debug("Rebuilding {} list", list.label);
		client.runScript(list.rebuildScript());
	}

	/** The groups of the currently hovered in-game row, resolved from the right list. Read by the overlay. */
	List<FriendGroup> hoveredGroups()
	{
		if (hoveredName == null || hoveredList == null)
		{
			return Collections.emptyList();
		}
		return storeFor(hoveredList).groupsFor(hoveredName);
	}
}
