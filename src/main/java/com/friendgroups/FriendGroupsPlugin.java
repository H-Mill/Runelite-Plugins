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

import com.google.inject.Provides;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
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
import javax.swing.SwingUtilities;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.Friend;
import net.runelite.api.FriendContainer;
import net.runelite.api.GameState;
import net.runelite.api.Menu;
import net.runelite.api.MenuAction;
import net.runelite.api.MenuEntry;
import net.runelite.api.Nameable;
import net.runelite.api.ScriptID;
import net.runelite.api.events.CommandExecuted;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.MenuEntryAdded;
import net.runelite.api.events.NameableNameChanged;
import net.runelite.api.events.RemovedFriend;
import net.runelite.api.events.ScriptCallbackEvent;
import net.runelite.api.events.ScriptPostFired;
import net.runelite.api.events.VarClientIntChanged;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.VarClientID;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetUtil;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.events.OverlayMenuClicked;
import net.runelite.client.game.ChatIconManager;
import net.runelite.client.game.WorldService;
import net.runelite.client.game.chatbox.ChatboxPanelManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.ui.overlay.OverlayMenuEntry;
import net.runelite.client.util.ColorUtil;
import net.runelite.client.util.ImageUtil;
import net.runelite.client.util.Text;
import net.runelite.client.util.WorldUtil;
import net.runelite.http.api.worlds.World;
import net.runelite.http.api.worlds.WorldResult;

@Slf4j
@PluginDescriptor(
		name = "Friend Groups",
		description = "Organise your friends list into named, re-orderable groups",
		tags = {"friend", "friends", "group", "groups", "social", "list"},
		configName = "FriendGroups"
)
public class FriendGroupsPlugin extends Plugin
{
	private static final String ASSIGN_GROUP = "Assign group";
	private static final String NEW_GROUP = "New group...";
	private static final String NEW_GROUP_PROMPT = "Group name<br>"
		+ ColorUtil.prependColorTag("(limit " + FriendGroupManager.MAX_NAME_LENGTH + " characters)", new Color(0, 0, 170));

	/** Sprite box for one dot; the swatch is centred in it so it lines up with the row text. */
	private static final int DOT_WIDTH = 8;
	private static final int DOT_HEIGHT = 11;
	private static final int DOT_SIZE = 7;

	/** Cap, past which the row's dots outgrow the friends list column. */
	private static final int MAX_DOTS = 4;

	/** Game ticks to keep trying to open the world switcher before giving up on a hop. */
	private static final int MAX_HOP_ATTEMPTS = 20;

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
	private FriendGroupManager manager;

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
	private FriendListReorderer reorderer;

	@Inject
	private WorldService worldService;

	@Inject
	@Named("developerMode")
	private boolean developerMode;

	/** Friend hovered in the in-game friends list, or null. Read by the overlay. */
	@Getter
	private String hoveredFriend;

	private NavigationButton navButton;

	/** Group color -> chat icon id. Client thread only. */
	private final Map<Integer, Integer> dotIcons = new HashMap<>();

	private final Consumer<Boolean> groupsChangedListener = this::onGroupsChanged;

	private Map<String, Integer> friendWorlds = Collections.emptyMap();

	/** Our own world last pushed to the panel, so a hop re-colours friends by same/other world. */
	private int playerWorld;

	/** Normalized names of the current friends. Client thread only. */
	private Set<String> friendKeys = Collections.emptySet();

	/** Quick-hop target set by a double-click; consumed over the next few game ticks. */
	private net.runelite.api.World quickHopTargetWorld;
	private int hopAttempts;

	/** Dots emitted for the row currently being laid out, so the x-shift matches the text. */
	private int rowDots;

	@Provides
	FriendGroupsConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(FriendGroupsConfig.class);
	}

	/**
	 * Carries retired config keys onto their replacements so a user's saved choices survive
	 * rather than silently resetting. Each runs once: the old key is unset afterwards, so later
	 * starts skip it.
	 * <ul>
	 *   <li>{@code reorderInGame} (boolean) -&gt; the {@link InGameMarker#GROUPED} marker mode.</li>
	 *   <li>{@code showOffline} (boolean) -&gt; its inverse, {@code hideOffline}.</li>
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
	}

	@Override
	protected void startUp() throws Exception
	{
		migrateConfig();
		manager.load();
		manager.addListener(groupsChangedListener);

		rebuildNavButton();
		overlayManager.add(overlay);

		panel.setOnHopFriend(this::hopToFriend);
		panel.setOnOpenConfig(this::openConfiguration);
		final boolean loggedIn = client.getGameState() == GameState.LOGGED_IN;
		SwingUtilities.invokeLater(() ->
		{
			panel.setLoggedIn(loggedIn);
			panel.rebuild();
		});
		refreshInGame();
	}

	@Override
	protected void shutDown() throws Exception
	{
		manager.removeListener(groupsChangedListener);
		clientToolbar.removeNavigation(navButton);
		overlayManager.remove(overlay);

		navButton = null;
		hoveredFriend = null;
		rowDots = 0;
		friendWorlds = Collections.emptyMap();
		friendKeys = Collections.emptySet();
		playerWorld = 0;
		quickHopTargetWorld = null;
		hopAttempts = 0;

		// Redraw without our decorations. ChatIconManager has no way to drop the dot
		// icons we registered, so they are deliberately left behind for a later start.
		if (client.getGameState() == GameState.LOGGED_IN)
		{
			clientThread.invokeLater(() ->
			{
				reorderer.removeHeaders();
				rebuildFriendsList();
			});
		}
	}

	/** Re-adds the toolbar nav button so a changed {@link FriendGroupsConfig#sidePanelPriority()} takes effect live. */
	private void rebuildNavButton()
	{
		if (navButton != null)
		{
			clientToolbar.removeNavigation(navButton);
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
				SwingUtilities.invokeLater(() -> panel.setLoggedIn(true));
				refreshInGame();
				break;
			case LOGIN_SCREEN:
				friendWorlds = Collections.emptyMap();
				friendKeys = Collections.emptySet();
				playerWorld = 0;
				SwingUtilities.invokeLater(() ->
				{
					panel.setLoggedIn(false);
					panel.setFriends(Collections.emptyMap(), 0);
				});
				break;
		}
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		if (!FriendGroupsConfig.GROUP.equals(event.getGroup()))
		{
			return;
		}

		if (FriendGroupsConfig.SIDE_PANEL_PRIORITY.equals(event.getKey()))
		{
			SwingUtilities.invokeLater(this::rebuildNavButton);
		}
		else if ("hideOffline".equals(event.getKey()))
		{
			// Affects both the side panel and the in-game list. When turned off, the in-game
			// rebuild redraws every friend (including the ones we had hidden) at their natural
			// positions, restoring the normal list.
			SwingUtilities.invokeLater(panel::rebuild);
			refreshInGame();
		}
		else if ("inGameMarker".equals(event.getKey()))
		{
			// Leaving grouped mode: drop the header rows before the list is drawn normally again.
			if (config.inGameMarker() != InGameMarker.GROUPED)
			{
				clientThread.invokeLater(reorderer::removeHeaders);
			}
			refreshInGame();
		}
		else if ("hideWorldPrefix".equals(event.getKey()))
		{
			// Redraw so the world column is re-stripped, or restored to "World nnn" when turned off.
			refreshInGame();
		}
	}

	/**
	 * The friends list has finished (re)building, so its row widgets exist and can be
	 * regrouped. This fires whenever the list is drawn - opening the tab, a friend's
	 * status changing, or our own {@link #rebuildFriendsList()}.
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
			reorderer.reorder();
		}
	}

	/**
	 * The Name / Recent / World / Legacy sort buttons change {@link VarClientID#FRIENDS_SORT}
	 * and redraw the list ungrouped. Force a clean {@link ScriptID#FRIENDS_UPDATE} so the
	 * reorder re-runs from the freshly sorted rows, giving a sort that applies within groups.
	 */
	@Subscribe
	public void onVarClientIntChanged(VarClientIntChanged event)
	{
		if (event.getIndex() == VarClientID.FRIENDS_SORT
			&& config.inGameMarker() == InGameMarker.GROUPED
			&& client.getGameState() == GameState.LOGGED_IN)
		{
			clientThread.invokeLater(this::rebuildFriendsList);
		}
	}

	@Subscribe
	public void onCommandExecuted(CommandExecuted event)
	{
		if (developerMode && "fgdump".equals(event.getCommand()))
		{
			clientThread.invokeLater(reorderer::dumpLayout);
		}
	}

	/**
	 * There is no event for a friend coming online or being added, so the panel's
	 * view of the friends list is polled. The container holds at most a few hundred
	 * entries and this runs once per tick, off the render path.
	 */
	@Subscribe
	public void onGameTick(GameTick event)
	{
		driveQuickHop();

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
				keys.add(FriendGroupManager.key(name));
			}
			friendKeys = keys;

			SwingUtilities.invokeLater(() -> panel.setFriends(current, world));
		}
	}

	/**
	 * Hops to a friend's current world, mirroring the World Hopper plugin's quick-hop: it
	 * takes a couple of game ticks to open the world switcher and then hop. Called from the
	 * side panel on the Swing thread.
	 */
	private void hopToFriend(String friendName)
	{
		clientThread.invoke(() ->
		{
			final FriendContainer container = client.getFriendContainer();
			if (container == null)
			{
				return;
			}

			final Friend friend = container.findByName(friendName);
			final int world = friend == null ? 0 : friend.getWorld();
			if (world <= 0)
			{
				client.addChatMessage(ChatMessageType.CONSOLE, "",
					"Friend Groups: " + friendName + " is not online, or their world is hidden.", null);
				return;
			}

			if (world == client.getWorld())
			{
				client.addChatMessage(ChatMessageType.CONSOLE, "",
					"Friend Groups: you are already on " + friendName + "'s world (W" + world + ").", null);
				return;
			}

			startQuickHop(world, friendName);
		});
	}

	private void startQuickHop(int worldId, String friendName)
	{
		final WorldResult worldResult = worldService.getWorlds();
		if (worldResult == null)
		{
			return;
		}

		final World world = worldResult.findWorld(worldId);
		if (world == null)
		{
			return;
		}

		final net.runelite.api.World rsWorld = client.createWorld();
		rsWorld.setActivity(world.getActivity());
		rsWorld.setAddress(world.getAddress());
		rsWorld.setId(world.getId());
		rsWorld.setPlayerCount(world.getPlayers());
		rsWorld.setLocation(world.getLocation());
		rsWorld.setTypes(WorldUtil.toWorldTypes(world.getTypes()));

		if (client.getGameState() == GameState.LOGIN_SCREEN)
		{
			client.changeWorld(rsWorld);
			return;
		}

		client.addChatMessage(ChatMessageType.CONSOLE, "",
			"Friend Groups: hopping to " + friendName + "'s world (W" + worldId + ")...", null);
		quickHopTargetWorld = rsWorld;
		hopAttempts = 0;
	}

	/** Runs each tick while a quick-hop is pending: open the switcher, then hop. */
	private void driveQuickHop()
	{
		if (quickHopTargetWorld == null)
		{
			return;
		}

		if (client.getWidget(InterfaceID.Worldswitcher.BUTTONS) == null)
		{
			client.openWorldHopper();

			if (++hopAttempts >= MAX_HOP_ATTEMPTS)
			{
				client.addChatMessage(ChatMessageType.CONSOLE, "", "Friend Groups: failed to hop worlds.", null);
				quickHopTargetWorld = null;
				hopAttempts = 0;
			}
		}
		else
		{
			client.hopToWorld(quickHopTargetWorld);
			quickHopTargetWorld = null;
			hopAttempts = 0;
		}
	}

	@Subscribe
	public void onMenuEntryAdded(MenuEntryAdded event)
	{
		final int groupId = WidgetUtil.componentToInterface(event.getActionParam1());
		if (groupId != InterfaceID.FRIENDS || !event.getOption().equals("Message"))
		{
			hoveredFriend = null;
			return;
		}

		final String friend = friendFromTarget(event.getTarget());
		hoveredFriend = friend;

		final MenuEntry parent = client.getMenu().createMenuEntry(-1)
			.setOption(ASSIGN_GROUP)
			.setTarget(event.getTarget())
			.setType(MenuAction.RUNELITE);

		final Menu submenu = parent.createSubMenu();

		// Menu entries are rebuilt every frame while hovering, so resolve membership
		// through the index rather than scanning each group's member list.
		final Set<String> memberOf = new HashSet<>();
		for (FriendGroup group : manager.groupsFor(friend))
		{
			memberOf.add(group.getName());
		}

		for (FriendGroup group : manager.getGroups())
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
						manager.removeMember(groupName, friend);
					}
					else
					{
						manager.addMember(groupName, friend);
					}
				});
		}

		submenu.createMenuEntry(-1)
			.setOption(NEW_GROUP)
			.setType(MenuAction.RUNELITE)
			.onClick(e -> promptNewGroup(friend));
	}

	@Subscribe
	public void onRemovedFriend(RemovedFriend event)
	{
		manager.forgetFriend(event.getNameable().getName());
	}

	@Subscribe
	public void onNameableNameChanged(NameableNameChanged event)
	{
		final Nameable nameable = event.getNameable();
		if (nameable instanceof Friend && nameable.getPrevName() != null)
		{
			manager.renameFriend(nameable.getPrevName(), nameable.getName());
		}
	}

	@Subscribe
	public void onScriptCallbackEvent(ScriptCallbackEvent event)
	{
		// Only 'Coloured dots' decorates individual rows. 'Off' does nothing, and 'Grouped list'
		// clusters the list under group headers that already convey membership.
		if (config.inGameMarker() != InGameMarker.DOT)
		{
			return;
		}

		switch (event.getEventName())
		{
			case "friendsChatSetText":
				decorateRow();
				break;
			case "friendsChatSetPosition":
				if (rowDots > 0)
				{
					final int[] intStack = client.getIntStack();
					final int size = client.getIntStackSize();
					intStack[size - 4] += (DOT_WIDTH + 1) * rowDots;
				}
				break;
		}
	}

	private void decorateRow()
	{
		final Object[] objectStack = client.getObjectStack();
		final int size = client.getObjectStackSize();
		final String rsn = (String) objectStack[size - 1];

		rowDots = 0;

		// This callback is shared with the ignore and friends-chat lists, so rows for
		// non-friends must be left alone.
		final String name = Text.toJagexName(Text.removeTags(rsn));
		if (!friendKeys.contains(FriendGroupManager.key(name)))
		{
			return;
		}

		final List<FriendGroup> groups = manager.groupsFor(name);
		if (groups.isEmpty())
		{
			return;
		}

		final StringBuilder text = new StringBuilder(rsn);
		for (FriendGroup group : groups)
		{
			if (rowDots >= MAX_DOTS)
			{
				break;
			}

			final int index = dotIconIndex(group.getColor());
			if (index == -1)
			{
				continue;
			}

			text.append(" <img=").append(index).append('>');
			rowDots++;
		}

		if (rowDots > 0)
		{
			objectStack[size - 1] = text.toString();
		}
	}

	/**
	 * Reads a friend's name out of a friends list menu target. Friends list entries carry colour
	 * tags for online status, and 'Coloured dots' appends an {@code <img>} icon; a RuneScape name
	 * holds neither, so stripping the tags leaves the name.
	 */
	static String friendFromTarget(String target)
	{
		return Text.toJagexName(Text.removeTags(target));
	}

	private void onGroupsChanged(boolean inGameDirty)
	{
		SwingUtilities.invokeLater(panel::rebuild);

		if (inGameDirty)
		{
			refreshInGame();
		}
	}

	private void refreshInGame()
	{
		if (client.getGameState() != GameState.LOGGED_IN)
		{
			return;
		}

		clientThread.invokeLater(() ->
		{
			final boolean registered = registerDotIcons();
			rebuildFriendsList();

			// A freshly registered icon has no sprite index until ChatIconManager's own
			// queued refresh runs, so redraw the list once more behind that refresh.
			if (registered)
			{
				clientThread.invokeLater(this::rebuildFriendsList);
			}
		});
	}

	private void promptNewGroup(String friend)
	{
		chatboxPanelManager.openTextInput(NEW_GROUP_PROMPT)
			.onDone((String value) ->
			{
				final String name = FriendGroupManager.sanitizeName(value);
				if (name == null)
				{
					return;
				}

				manager.addGroup(name);
				manager.addMember(name, friend);
			})
			.build();
	}

	/** @return true if at least one new icon was registered */
	private boolean registerDotIcons()
	{
		boolean registered = false;
		for (FriendGroup group : manager.getGroups())
		{
			if (!dotIcons.containsKey(group.getColor()))
			{
				dotIcons.put(group.getColor(), chatIconManager.registerChatIcon(createDot(group.getAwtColor())));
				registered = true;
			}
		}
		return registered;
	}

	private int dotIconIndex(int color)
	{
		final Integer iconId = dotIcons.get(color);
		return iconId == null ? -1 : chatIconManager.chatIconIndex(iconId);
	}

	private static BufferedImage createDot(Color color)
	{
		final BufferedImage image = new BufferedImage(DOT_WIDTH, DOT_HEIGHT, BufferedImage.TYPE_INT_ARGB);
		final Graphics2D graphics = image.createGraphics();
		// No antialiasing: the sprite is quantised to an indexed palette, and soft edges muddy it.
		graphics.setColor(color);
		graphics.fillRect(0, (DOT_HEIGHT - DOT_SIZE) / 2, DOT_SIZE, DOT_SIZE);
		graphics.dispose();
		return image;
	}

	/**
	 * Drops the "World " prefix the game writes before each online friend's world number,
	 * leaving just the number. The world sits in its own row widget, separate from the name,
	 * so it is rewritten here after the list is built rather than through the name callback.
	 * Runs on the client thread from {@code ScriptPostFired(FRIENDS_UPDATE)}.
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

			// Match on the tag-stripped text so a colour-wrapped "World 369" is caught, but keep
			// the original (tagged) string when rewriting so the world keeps its colour.
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

	private void rebuildFriendsList()
	{
		log.debug("Rebuilding friends list");
		client.runScript(
			ScriptID.FRIENDS_UPDATE,
			InterfaceID.Friends.LIST_CONTAINER,
			InterfaceID.Friends.SORT_NAME,
			InterfaceID.Friends.SORT_RECENT,
			InterfaceID.Friends.SORT_WORLD,
			InterfaceID.Friends.SORT_LEGACY,
			InterfaceID.Friends.LIST,
			InterfaceID.Friends.SCROLLBAR,
			InterfaceID.Friends.LOADING,
			InterfaceID.Friends.TOOLTIP
		);
	}
}
