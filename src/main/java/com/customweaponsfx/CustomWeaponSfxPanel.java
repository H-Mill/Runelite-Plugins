package com.customweaponsfx;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.JComponent;
import javax.swing.JOptionPane;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSlider;
import javax.swing.JSpinner;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.border.EmptyBorder;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.util.ImageUtil;
import net.runelite.client.util.LinkBrowser;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.ui.components.DragAndDropReorderPane;
import net.runelite.client.util.AsyncBufferedImage;

public class CustomWeaponSfxPanel extends PluginPanel
{
	static final String BUNDLED_PREFIX = "bundled:";
	private static final String BUILTIN_SUFFIX = " (built-in)";

	static final String RECEIVED_GROUPS_PREFIX = "defaultReceived";
	static final String GLOBAL_WEAPON_GROUPS_PREFIX = "globalWeapon";

	private static final float TITLE_SIZE = 17f;
	private static final float SECTION_TITLE_SIZE = 16f;

	private static final ImageIcon DELETE_ICON;
	private static final ImageIcon DELETE_HOVER_ICON;
	private static final ImageIcon EDIT_ICON;
	private static final ImageIcon EDIT_HOVER_ICON;
	private static final ImageIcon CLONE_SEARCH_ICON;
	private static final ImageIcon CLONE_SEARCH_HOVER_ICON;
	private static final ImageIcon CLONE_EQUIPPED_ICON;
	private static final ImageIcon CLONE_EQUIPPED_HOVER_ICON;
	private static final ImageIcon ADD_SEARCH_ICON;
	private static final ImageIcon ADD_SEARCH_HOVER_ICON;
	private static final ImageIcon ADD_PLUS_ICON;
	private static final ImageIcon ADD_PLUS_HOVER_ICON;
	private static final ImageIcon FOLDER_ICON;
	private static final ImageIcon FOLDER_HOVER_ICON;
	private static final ImageIcon EXPAND_ICON;
	private static final ImageIcon EXPAND_HOVER_ICON;
	private static final ImageIcon COLLAPSE_ICON;
	private static final ImageIcon COLLAPSE_HOVER_ICON;
	private static final ImageIcon TRASH_ICON;
	private static final ImageIcon TRASH_HOVER_ICON;
	private static final ImageIcon REFRESH_ICON;
	private static final ImageIcon REFRESH_HOVER_ICON;
	private static final BufferedImage REFRESH_IMG;
	private static final ImageIcon TEST_ICON;
	private static final ImageIcon TEST_HOVER_ICON;
	private static final ImageIcon CONFIG_ICON;
	private static final ImageIcon CONFIG_HOVER_ICON;
	private static final ImageIcon KOFI_ICON;
	private static final ImageIcon KOFI_HOVER_ICON;
	private static final ImageIcon DRAG_ICON;
	private static final ImageIcon DRAG_HOVER_ICON;
	private static final String KOFI_URL = "https://ko-fi.com/hmill8";

	/** Client property on each weapon row holding its {@code Integer} item id, so a drop can resolve which weapon moved. */
	private static final String ROW_ITEM_ID_KEY = "customWeaponSfxItemId";

	static
	{
		final BufferedImage deleteImg = ImageUtil.loadImageResource(CustomWeaponSfxPlugin.class, "icon_delete.png");
		DELETE_ICON = new ImageIcon(deleteImg);
		DELETE_HOVER_ICON = new ImageIcon(ImageUtil.luminanceOffset(deleteImg, -100));

		final BufferedImage editImg = ImageUtil.loadImageResource(CustomWeaponSfxPlugin.class, "icon_edit.png");
		EDIT_ICON = new ImageIcon(editImg);
		EDIT_HOVER_ICON = new ImageIcon(ImageUtil.luminanceOffset(editImg, -100));

		final BufferedImage cloneSearchImg = ImageUtil.loadImageResource(CustomWeaponSfxPlugin.class, "icon_clone_search.png");
		CLONE_SEARCH_ICON = new ImageIcon(cloneSearchImg);
		CLONE_SEARCH_HOVER_ICON = new ImageIcon(ImageUtil.luminanceOffset(cloneSearchImg, -100));

		final BufferedImage cloneEquippedImg = ImageUtil.loadImageResource(CustomWeaponSfxPlugin.class, "icon_clone_equipped.png");
		CLONE_EQUIPPED_ICON = new ImageIcon(cloneEquippedImg);
		CLONE_EQUIPPED_HOVER_ICON = new ImageIcon(ImageUtil.luminanceOffset(cloneEquippedImg, -100));

		final BufferedImage addSearchImg = ImageUtil.loadImageResource(CustomWeaponSfxPlugin.class, "icon_add_search.png");
		ADD_SEARCH_ICON = new ImageIcon(addSearchImg);
		ADD_SEARCH_HOVER_ICON = new ImageIcon(ImageUtil.luminanceOffset(addSearchImg, -100));

		final BufferedImage addEquippedImg = ImageUtil.loadImageResource(CustomWeaponSfxPlugin.class, "icon_add_equipped.png");
		ADD_PLUS_ICON = new ImageIcon(addEquippedImg);
		ADD_PLUS_HOVER_ICON = new ImageIcon(ImageUtil.luminanceOffset(addEquippedImg, -100));

		final BufferedImage folderImg = ImageUtil.loadImageResource(CustomWeaponSfxPlugin.class, "icon_folder.png");
		FOLDER_ICON = new ImageIcon(folderImg);
		FOLDER_HOVER_ICON = new ImageIcon(ImageUtil.luminanceOffset(folderImg, -100));

		final BufferedImage expandImg = ImageUtil.loadImageResource(CustomWeaponSfxPlugin.class, "icon_right_arrow.png");
		EXPAND_ICON = new ImageIcon(expandImg);
		EXPAND_HOVER_ICON = new ImageIcon(ImageUtil.luminanceOffset(expandImg, -100));

		final BufferedImage collapseImg = ImageUtil.loadImageResource(CustomWeaponSfxPlugin.class, "icon_down_arrow.png");
		COLLAPSE_ICON = new ImageIcon(collapseImg);
		COLLAPSE_HOVER_ICON = new ImageIcon(ImageUtil.luminanceOffset(collapseImg, -100));

		final BufferedImage trashImg = ImageUtil.loadImageResource(CustomWeaponSfxPlugin.class, "icon_delete.png");
		TRASH_ICON = new ImageIcon(trashImg);
		TRASH_HOVER_ICON = new ImageIcon(ImageUtil.luminanceOffset(trashImg, -100));

		final BufferedImage refreshImg = ImageUtil.loadImageResource(CustomWeaponSfxPlugin.class, "icon_refresh.png");
		REFRESH_IMG = refreshImg;
		REFRESH_ICON = new ImageIcon(refreshImg);
		REFRESH_HOVER_ICON = new ImageIcon(ImageUtil.luminanceOffset(refreshImg, -100));

		final BufferedImage testImg = ImageUtil.loadImageResource(CustomWeaponSfxPlugin.class, "icon_test_sound.png");
		TEST_ICON = new ImageIcon(testImg);
		TEST_HOVER_ICON = new ImageIcon(ImageUtil.luminanceOffset(testImg, -100));

		final BufferedImage configImg = ImageUtil.loadImageResource(CustomWeaponSfxPlugin.class, "icon_cog.png");
		CONFIG_ICON = new ImageIcon(configImg);
		CONFIG_HOVER_ICON = new ImageIcon(ImageUtil.luminanceOffset(configImg, -100));

		final BufferedImage kofiImg = ImageUtil.loadImageResource(CustomWeaponSfxPlugin.class, "icon_kofi.png");
		KOFI_ICON = new ImageIcon(kofiImg);
		KOFI_HOVER_ICON = new ImageIcon(ImageUtil.luminanceOffset(kofiImg, -100));

		final BufferedImage dragImg = ImageUtil.loadImageResource(CustomWeaponSfxPlugin.class, "icon_drag.png");
		DRAG_ICON = new ImageIcon(dragImg);
		DRAG_HOVER_ICON = new ImageIcon(ImageUtil.luminanceOffset(dragImg, -100));
	}

	private List<String> bundledSounds = new ArrayList<>();
	private final Set<Integer> expandedWeapons = new HashSet<>();
	private final Set<String> expandedDefaults = new HashSet<>();

	/** Whether actions that read live game state (search/equipped/copy) are currently usable. */
	private boolean loggedIn;
	/** Whether the player currently has a weapon equipped; gates the "to equipped weapon" actions. */
	private boolean weaponEquipped;
	/** Login-gated buttons in the always-present top panel (built once). */
	private final List<JButton> topLoginButtons = new ArrayList<>();
	/** Login-gated buttons in the weapon rows; rebuilt with the list, so cleared each {@link #rebuild}. */
	private final List<JButton> weaponLoginButtons = new ArrayList<>();
	/**
	 * Weapon-row buttons that act on the equipped weapon (change/clone to equipped); gated by both login
	 * and having a weapon equipped. Rebuilt with the list, so cleared each {@link #rebuild}.
	 */
	private final List<JButton> equippedWeaponButtons = new ArrayList<>();

	/** Active spin animation for the refresh button, if any; restarted (not stacked) on rapid re-clicks. */
	private Timer refreshSpinTimer;

	private final CustomWeaponSfxConfigStore store;
	private final ItemManager itemManager;
	private final Runnable onOpenSearch;
	private final Runnable onAddEquipped;
	private final Consumer<Integer> onRemoveWeapon;
	private final Consumer<Integer> onEditWeaponSearch;
	private final Consumer<Integer> onEditWeaponEquipped;
	private final Consumer<Integer> onCopyWeapon;
	private final Consumer<Integer> onCopyWeaponEquipped;
	private final BiConsumer<Integer, Integer> onReorderWeapon;
	private final BiConsumer<String, Integer> onTestSound;
	private final Consumer<TriggerGroup> onTestGroup;
	private final Runnable onReset;
	private final Runnable onRefreshSounds;
	private final Runnable onOpenConfig;
	private final BiConsumer<SfxOption, Boolean> onOptionToggled;
	private final Consumer<String> onExcludedNpcIdsChanged;
	private final Consumer<String> onExcludedNpcNamesChanged;
	private final Consumer<String> onMutedWeaponSoundIdsChanged;

	private JCheckBox ignoreSmallMaxCheckBox;
	private JCheckBox ignoreZeroPrayerCheckBox;
	private JCheckBox ignoreZeroThrallCheckBox;
	private JTextField excludedNpcIdsField;
	private JTextField excludedNpcNamesField;
	private JTextField mutedWeaponSoundIdsField;

	private final JPanel weaponListPanel;
	private final JPanel mainContent;
	public CustomWeaponSfxPanel(CustomWeaponSfxConfigStore store,
		ItemManager itemManager,
		Runnable onOpenSearch,
		Runnable onAddEquipped,
		Consumer<Integer> onRemoveWeapon,
		Consumer<Integer> onEditWeaponSearch,
		Consumer<Integer> onEditWeaponEquipped,
		Consumer<Integer> onCopyWeapon,
		Consumer<Integer> onCopyWeaponEquipped,
		BiConsumer<Integer, Integer> onReorderWeapon,
		BiConsumer<String, Integer> onTestSound,
		Consumer<TriggerGroup> onTestGroup,
		Runnable onReset,
		Runnable onRefreshSounds,
		Runnable onOpenConfig,
		BiConsumer<SfxOption, Boolean> onOptionToggled,
		Consumer<String> onExcludedNpcIdsChanged,
		Consumer<String> onExcludedNpcNamesChanged,
		Consumer<String> onMutedWeaponSoundIdsChanged)
	{
		this.store = store;
		this.itemManager = itemManager;
		this.onOpenSearch = onOpenSearch;
		this.onAddEquipped = onAddEquipped;
		this.onRemoveWeapon = onRemoveWeapon;
		this.onEditWeaponSearch = onEditWeaponSearch;
		this.onEditWeaponEquipped = onEditWeaponEquipped;
		this.onCopyWeapon = onCopyWeapon;
		this.onCopyWeaponEquipped = onCopyWeaponEquipped;
		this.onReorderWeapon = onReorderWeapon;
		this.onTestSound = onTestSound;
		this.onTestGroup = onTestGroup;
		this.onReset = onReset;
		this.onRefreshSounds = onRefreshSounds;
		this.onOpenConfig = onOpenConfig;
		this.onOptionToggled = onOptionToggled;
		this.onExcludedNpcIdsChanged = onExcludedNpcIdsChanged;
		this.onExcludedNpcNamesChanged = onExcludedNpcNamesChanged;
		this.onMutedWeaponSoundIdsChanged = onMutedWeaponSoundIdsChanged;

		setLayout(new BorderLayout());
		setBorder(new EmptyBorder(10, 10, 10, 10));

		weaponListPanel = boxColumn(null);

		mainContent = boxColumn(null);
		mainContent.add(buildTopPanel());
		mainContent.add(weaponListPanel);

		add(mainContent, BorderLayout.NORTH);
	}

	private JPanel buildTopPanel()
	{
		JPanel top = boxColumn(null);

		JPanel titleRow = new JPanel();
		titleRow.setLayout(new BoxLayout(titleRow, BoxLayout.X_AXIS));
		titleRow.setAlignmentX(Component.LEFT_ALIGNMENT);

		JLabel title = new JLabel("Custom Weapon SFX");
		title.setForeground(ColorScheme.BRAND_ORANGE);
		setBoldFont(title, TITLE_SIZE);
		titleRow.add(title);

		top.add(titleRow);
		top.add(Box.createVerticalStrut(4));

		// Action buttons sit in their own row directly beneath the title.
		JPanel btnRow = new JPanel();
		btnRow.setLayout(new BoxLayout(btnRow, BoxLayout.X_AXIS));
		btnRow.setAlignmentX(Component.LEFT_ALIGNMENT);

		JButton addSearchBtn = makeImageButton(ADD_SEARCH_ICON, ADD_SEARCH_HOVER_ICON,
			"Search for a weapon to configure");
		addSearchBtn.addActionListener(e -> onOpenSearch.run());
		registerLoginButton(addSearchBtn, topLoginButtons);
		btnRow.add(addSearchBtn);

		btnRow.add(Box.createHorizontalStrut(4));

		JButton addEquippedBtn = makeImageButton(ADD_PLUS_ICON, ADD_PLUS_HOVER_ICON,
			"Add your currently equipped weapon to configure");
		addEquippedBtn.addActionListener(e -> onAddEquipped.run());
		registerLoginButton(addEquippedBtn, topLoginButtons);
		btnRow.add(addEquippedBtn);

		btnRow.add(Box.createHorizontalStrut(4));

		JButton openFolderBtn = makeImageButton(FOLDER_ICON, FOLDER_HOVER_ICON,
			"<html>Open .runelite/customweaponsfx/<br>" +
					"Put custom SFX here!</html");
		openFolderBtn.addActionListener(e -> LinkBrowser.open(SoundLibrary.SOUNDS_DIR.toString()));
		btnRow.add(openFolderBtn);

		btnRow.add(Box.createHorizontalStrut(4));

		JButton refreshSoundsBtn = makeImageButton(REFRESH_ICON, REFRESH_HOVER_ICON,
			"Rescan .runelite/customweaponsfx/ for new .wav files");
		refreshSoundsBtn.addActionListener(e ->
		{
			spinRefreshButton(refreshSoundsBtn);
			onRefreshSounds.run();
		});
		btnRow.add(refreshSoundsBtn);

		btnRow.add(Box.createHorizontalStrut(4));

		JButton configBtn = makeImageButton(CONFIG_ICON, CONFIG_HOVER_ICON, "Open plugin config");
		configBtn.addActionListener(e -> onOpenConfig.run());
		btnRow.add(configBtn);

		btnRow.add(Box.createHorizontalStrut(4));

		JButton kofiBtn = makeImageButton(KOFI_ICON, KOFI_HOVER_ICON, "Buy me a coffee :)");
		kofiBtn.addActionListener(e -> LinkBrowser.browse(KOFI_URL));
		btnRow.add(kofiBtn);

		btnRow.add(Box.createHorizontalStrut(4));

		JButton resetBtn = makeImageButton(TRASH_ICON, TRASH_HOVER_ICON, "Reset All Data");
		resetBtn.addActionListener(e ->
		{
			if (confirmYesNo("Reset all weapons and sound groups back to defaults?", "Reset All Data"))
				onReset.run();
		});
		btnRow.add(resetBtn);

		btnRow.add(Box.createHorizontalGlue());
		btnRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, btnRow.getPreferredSize().height));

		top.add(btnRow);
		top.add(Box.createVerticalStrut(8));

		top.add(Box.createVerticalStrut(6));
		top.add(buildTogglesSection());
		top.add(Box.createVerticalStrut(4));

		return top;
	}

	/** Spins the refresh icon one full turn (~500ms) as click feedback, then restores the static icon. */
	private void spinRefreshButton(JButton button)
	{
		if (refreshSpinTimer != null && refreshSpinTimer.isRunning())
			refreshSpinTimer.stop();

		final int durationMs = 500;
		final long start = System.currentTimeMillis();
		// Rollover would otherwise paint the dark hover icon over our rotated frames while hovered.
		button.setRolloverEnabled(false);
		refreshSpinTimer = new Timer(15, null);
		refreshSpinTimer.addActionListener(ev ->
		{
			long elapsed = System.currentTimeMillis() - start;
			if (elapsed >= durationMs)
			{
				button.setIcon(REFRESH_ICON);
				button.setRolloverEnabled(true);
				refreshSpinTimer.stop();
				return;
			}
			double angle = -2 * Math.PI * (elapsed / (double) durationMs);
			button.setIcon(new ImageIcon(rotateImage(REFRESH_IMG, angle)));
		});
		refreshSpinTimer.setInitialDelay(0);
		refreshSpinTimer.start();
	}

	/** Returns a copy of {@code src} rotated {@code radians} about its centre, same dimensions. */
	private static BufferedImage rotateImage(BufferedImage src, double radians)
	{
		int w = src.getWidth();
		int h = src.getHeight();
		BufferedImage dst = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = dst.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g.rotate(radians, w / 2.0, h / 2.0);
		g.drawImage(src, 0, 0, null);
		g.dispose();
		return dst;
	}

	private JPanel buildTogglesSection()
	{
		JPanel section = boxColumn(ColorScheme.DARKER_GRAY_COLOR);
		section.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createLineBorder(ColorScheme.MEDIUM_GRAY_COLOR),
			new EmptyBorder(4, 6, 4, 6)
		));

		JPanel headerRow = new JPanel(new BorderLayout(6, 0));
		headerRow.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		headerRow.setAlignmentX(Component.LEFT_ALIGNMENT);

		JButton collapseBtn = makeCollapseButton(true);
		headerRow.add(collapseBtn, BorderLayout.WEST);

		JLabel headerLabel = new JLabel("Options");
		headerLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		headerLabel.setToolTipText("<html>Global settings that filter out unwanted SFX and let you<br>"
			+ "exclude specific NPCs from triggering sounds.</html>");
		setBoldFont(headerLabel, SECTION_TITLE_SIZE);
		headerRow.add(headerLabel, BorderLayout.CENTER);

		section.add(headerRow);

		JPanel content = boxColumn(ColorScheme.DARKER_GRAY_COLOR);
		content.setVisible(false);

		content.add(Box.createVerticalStrut(4));

		ignoreSmallMaxCheckBox = buildOptionCheckBox(SfxOption.IGNORE_SMALL_MAX, "Ignore max hits ≤ 3",
			"<html>When enabled, max hit SFX will not play if the hit<br>"
				+ "damage is 3 or less. Prevents thrall max hits from<br>"
				+ "triggering max hit sounds.</html>");
		content.add(ignoreSmallMaxCheckBox);

		ignoreZeroPrayerCheckBox = buildOptionCheckBox(SfxOption.IGNORE_RECEIVED_ZERO_PRAYER, "Ignore zeroes with prayer",
			"<html>When enabled, the Received Attacks 'Regular zero'<br>"
				+ "trigger will not fire while Protect from Melee,<br>"
				+ "Protect from Ranged, or Protect from Magic is active.</html>");
		content.add(ignoreZeroPrayerCheckBox);

		ignoreZeroThrallCheckBox = buildOptionCheckBox(SfxOption.IGNORE_ZERO_THRALL, "Ignore zeroes with thrall",
			"<html>When enabled, zero-damage triggers will not fire<br>"
				+ "while a thrall is active.</html>");
		content.add(ignoreZeroThrallCheckBox);

		content.add(Box.createVerticalStrut(6));
		content.add(buildExcludedNpcRow(
			"Excluded NPC IDs:",
			"<html>Comma-separated NPC ids whose hitsplats never trigger SFX.<br>"
				+ "Example: 11706, 11707</html>",
			store.getExcludedNpcIdsRaw(), onExcludedNpcIdsChanged, f -> excludedNpcIdsField = f));

		content.add(Box.createVerticalStrut(6));
		content.add(buildExcludedNpcRow(
			"Excluded NPC Names:",
			"<html>Comma-separated NPC names whose hitsplats never trigger SFX.<br>"
				+ "Matching is case-insensitive.<br>"
				+ "Example: Vorkath, zulrah</html>",
			store.getExcludedNpcNamesRaw(), onExcludedNpcNamesChanged, f -> excludedNpcNamesField = f));

		content.add(Box.createVerticalStrut(6));
		content.add(buildExcludedNpcRow(
			"Muted Weapon Sound IDs:",
			"<html>Comma-separated game sound-effect ids to silence, so your<br>"
				+ "custom SFX replaces the default in-game weapon sound.<br>"
				+ "Example: 3892 (the ACB/ZCB special attack \"REEEE\" sound)</html>",
			store.getMutedWeaponSoundIdsRaw(), onMutedWeaponSoundIdsChanged, f -> mutedWeaponSoundIdsField = f));

		section.add(content);

		collapseBtn.addActionListener(e ->
		{
			boolean nowVisible = content.isVisible();
			content.setVisible(!nowVisible);
			applyCollapseState(collapseBtn, nowVisible);
			section.revalidate();
			section.repaint();
		});

		return section;
	}

	private JCheckBox buildOptionCheckBox(SfxOption option, String label, String tooltip)
	{
		JCheckBox box = new JCheckBox(label, store.getBool(option));
		box.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		box.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		box.setToolTipText(tooltip);
		box.setAlignmentX(Component.LEFT_ALIGNMENT);
		box.addActionListener(e -> onOptionToggled.accept(option, box.isSelected()));
		return box;
	}

	private JPanel buildExcludedNpcRow(String label, String tooltip, String initialValue,
		Consumer<String> onChange, Consumer<JTextField> fieldSink)
	{
		JPanel panel = boxColumn(ColorScheme.DARKER_GRAY_COLOR);

		JLabel lbl = new JLabel(label);
		lbl.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		lbl.setAlignmentX(Component.LEFT_ALIGNMENT);
		lbl.setToolTipText(tooltip);
		panel.add(lbl);
		panel.add(Box.createVerticalStrut(2));

		JTextField field = new JTextField(initialValue);
		field.setAlignmentX(Component.LEFT_ALIGNMENT);
		field.setMaximumSize(new Dimension(Integer.MAX_VALUE, field.getPreferredSize().height));
		field.setToolTipText(tooltip);
		field.addActionListener(e -> onChange.accept(field.getText()));
		field.addFocusListener(new FocusAdapter()
		{
			@Override
			public void focusLost(FocusEvent e)
			{
				onChange.accept(field.getText());
			}
		});
		panel.add(field);

		fieldSink.accept(field);
		return panel;
	}

	public void resetToggles()
	{
		SwingUtilities.invokeLater(() ->
		{
			if (ignoreSmallMaxCheckBox != null) ignoreSmallMaxCheckBox.setSelected(true);
			if (ignoreZeroPrayerCheckBox != null) ignoreZeroPrayerCheckBox.setSelected(true);
			if (ignoreZeroThrallCheckBox != null) ignoreZeroThrallCheckBox.setSelected(true);
			if (excludedNpcIdsField != null) excludedNpcIdsField.setText("");
			if (excludedNpcNamesField != null) excludedNpcNamesField.setText("");
			if (mutedWeaponSoundIdsField != null) mutedWeaponSoundIdsField.setText("");
		});
	}

	/** Registers a button whose action needs a logged-in client; it starts in the current login state. */
	private void registerLoginButton(JButton button, List<JButton> bucket)
	{
		button.setEnabled(loggedIn);
		bucket.add(button);
	}

	/**
	 * Registers a button that acts on the equipped weapon; it needs both a logged-in client and a weapon
	 * equipped, and starts in the current state.
	 */
	private void registerEquippedWeaponButton(JButton button)
	{
		button.setEnabled(loggedIn && weaponEquipped);
		equippedWeaponButtons.add(button);
	}

	/**
	 * Enables or disables every state-gated button (top panel + weapon rows). The "to equipped weapon"
	 * actions additionally require a weapon to be equipped. Call on the EDT.
	 */
	public void setLoginButtonsEnabled(boolean loggedIn, boolean weaponEquipped)
	{
		this.loggedIn = loggedIn;
		this.weaponEquipped = weaponEquipped;
		for (JButton b : topLoginButtons) b.setEnabled(loggedIn);
		for (JButton b : weaponLoginButtons) b.setEnabled(loggedIn);
		for (JButton b : equippedWeaponButtons) b.setEnabled(loggedIn && weaponEquipped);
	}

	public void rebuild(List<WeaponEntry> weapons, List<String> availableSounds,
		List<String> bundledSounds, List<TriggerGroup> receivedGroups, List<TriggerGroup> globalWeaponGroups)
	{
		this.bundledSounds = bundledSounds;
		SwingUtilities.invokeLater(() ->
		{
			weaponListPanel.removeAll();
			// Weapon rows (and their login-gated buttons) are recreated below; drop the stale references.
			weaponLoginButtons.clear();
			equippedWeaponButtons.clear();

			weaponListPanel.add(buildDefaultRowGroups(
				"Received Attacks", "<html>Sounds that play when <b>you take a hit</b>, regardless of which<br>"
					+ "weapon you have equipped. Configure sound groups and triggers<br>"
					+ "for incoming attacks here.</html>",
				RECEIVED_GROUPS_PREFIX, receivedGroups, availableSounds,
				EnumSet.of(Triggers.REGULAR_ZERO, Triggers.REGULAR_HIT, Triggers.ALL, Triggers.PLAYER_DEATH), SfxOption.RECEIVED_ENABLED));
			weaponListPanel.add(Box.createVerticalStrut(4));

			weaponListPanel.add(buildDefaultRowGroups(
				"Global (All Weapons)", "<html>Sounds that play for <b>every weapon</b> you attack with,<br>"
					+ "in addition to any weapon-specific sounds configured below.<br><br>"
					+ "<b>Note:</b> if the equipped weapon has its own sound group for a<br>"
					+ "trigger, that weapon <b>overrides</b> the global sound for that trigger —<br>"
					+ "they do not stack (unless that weapon's <b>Don't override Global</b><br>"
					+ "toggle is enabled).</html>",
				GLOBAL_WEAPON_GROUPS_PREFIX, globalWeaponGroups, availableSounds,
				EnumSet.complementOf(EnumSet.of(Triggers.REGULAR_HIT, Triggers.PLAYER_DEATH)), SfxOption.GLOBAL_ENABLED));
			weaponListPanel.add(Box.createVerticalStrut(4));


			// Weapon rows live in a reorder pane so the user can drag them (by each row's handle) to
			// re-sort. The Received/Global sections above stay fixed. A fresh pane each rebuild is fine —
			// rebuilds are infrequent — and its drop listener persists the new order via onReorderWeapon.
			DragAndDropReorderPane reorderPane = new DragAndDropReorderPane();
			reorderPane.setAlignmentX(Component.LEFT_ALIGNMENT);
			reorderPane.addDragListener(comp ->
			{
				Object id = ((JComponent) comp).getClientProperty(ROW_ITEM_ID_KEY);
				if (id instanceof Integer)
					onReorderWeapon.accept((Integer) id, reorderPane.getPosition(comp));
			});

			for (int i = 0; i < weapons.size(); i++)
			{
				WeaponEntry weapon = weapons.get(i);
				JPanel row = buildRow(weapon, availableSounds, i, reorderPane);
				row.putClientProperty(ROW_ITEM_ID_KEY, weapon.getItemId());
				reorderPane.add(row);
			}
			weaponListPanel.add(reorderPane);

			weaponListPanel.revalidate();
			weaponListPanel.repaint();
		});
	}

	private JPanel buildDefaultRowGroups(String label, String tooltip, String prefix,
		List<TriggerGroup> groups, List<String> availableSounds, Set<Triggers> visibleTriggers,
		SfxOption enabledOption)
	{
		JLabel nameLabel = new JLabel(label);
		nameLabel.setForeground(ColorScheme.BRAND_ORANGE);
		nameLabel.setToolTipText(tooltip);
		setBoldFont(nameLabel, SECTION_TITLE_SIZE);

		List<Component> eastControls = new ArrayList<>();
		if (enabledOption != null)
		{
			JCheckBox enabledBox = new JCheckBox();
			enabledBox.setSelected(store.getBool(enabledOption));
			enabledBox.setBackground(ColorScheme.DARKER_GRAY_COLOR);
			enabledBox.setToolTipText("Enable or disable this section");
			enabledBox.addActionListener(e -> onOptionToggled.accept(enabledOption, enabledBox.isSelected()));
			eastControls.add(enabledBox);
		}

		return buildCollapsibleGroupsPanel(
			expandedDefaults, prefix,
			ColorScheme.DARKER_GRAY_COLOR, ColorScheme.BRAND_ORANGE,
			null, null, nameLabel, eastControls, false, 0,
			null,
			groups, availableSounds,
			() -> store.saveDefaultGroups(prefix, groups), visibleTriggers);
	}

	private static final Color ROW_COLOR_A = ColorScheme.DARKER_GRAY_COLOR;
	private static final Color ROW_COLOR_B = new Color(40, 38, 35);

	/** Background for each sound's box — matches the weapon boxes to set sounds apart from the group. */
	private static final Color SOUND_BOX_COLOR = ColorScheme.DARKER_GRAY_COLOR;

	private JPanel buildRow(WeaponEntry entry, List<String> availableSounds, int index,
		DragAndDropReorderPane reorderPane)
	{
		Color bg = (index % 2 == 0) ? ROW_COLOR_A : ROW_COLOR_B;

		JLabel dragHandle = makeDragHandle(reorderPane);

		JLabel iconLabel = new JLabel();
		iconLabel.setPreferredSize(new Dimension(32, 32));
		iconLabel.setToolTipText(entry.getWeaponName());
		AsyncBufferedImage icon = itemManager.getImage(entry.getItemId());
		if (icon != null)
		{
			icon.addTo(iconLabel);
		}

		JLabel nameLabel = new JLabel(entry.getWeaponName());
		nameLabel.setForeground(entry.isEnabled() ? Color.WHITE : Color.GRAY);
		setBoldFont(nameLabel, SECTION_TITLE_SIZE);
		nameLabel.setToolTipText(entry.getWeaponName());

		JCheckBox enabledBox = new JCheckBox();
		enabledBox.setSelected(entry.isEnabled());
		enabledBox.setBackground(bg);
		enabledBox.setToolTipText("Enable or disable this weapon");
		enabledBox.addActionListener(e ->
		{
			entry.setEnabled(enabledBox.isSelected());
			nameLabel.setForeground(entry.isEnabled() ? Color.WHITE : Color.GRAY);
			store.saveWeaponEnabled(entry.getItemId(), entry.isEnabled());
		});

		JButton editSearchBtn = makeImageButton(ADD_SEARCH_ICON, ADD_SEARCH_HOVER_ICON,
			"Change this weapon by searching — keeps its sound groups");
		editSearchBtn.addActionListener(e -> onEditWeaponSearch.accept(entry.getItemId()));
		registerLoginButton(editSearchBtn, weaponLoginButtons);

		JButton editEquippedBtn = makeImageButton(ADD_PLUS_ICON, ADD_PLUS_HOVER_ICON,
			"Change this weapon to your currently equipped weapon — keeps its sound groups");
		editEquippedBtn.addActionListener(e -> onEditWeaponEquipped.accept(entry.getItemId()));
		registerEquippedWeaponButton(editEquippedBtn);

		JButton copyBtn = makeImageButton(CLONE_SEARCH_ICON, CLONE_SEARCH_HOVER_ICON,
			"Clone this weapons settings to another weapon via search");
		copyBtn.addActionListener(e -> onCopyWeapon.accept(entry.getItemId()));
		registerLoginButton(copyBtn, weaponLoginButtons);

		JButton copyEquippedBtn = makeImageButton(CLONE_EQUIPPED_ICON, CLONE_EQUIPPED_HOVER_ICON,
			"Clone this weapons settings to your currently equipped weapon");
		copyEquippedBtn.addActionListener(e -> onCopyWeaponEquipped.accept(entry.getItemId()));
		registerEquippedWeaponButton(copyEquippedBtn);

		JButton removeBtn = makeRemoveButton("Remove weapon");
		removeBtn.addActionListener(e ->
		{
			if (confirmYesNo("Remove " + entry.getWeaponName() + " and all its sound groups?", "Remove Weapon"))
				onRemoveWeapon.accept(entry.getItemId());
		});

		// The toggle leads (it's pulled hard-left in the action row); the icons trail on the right.
		List<Component> eastControls = new ArrayList<>();
		eastControls.add(enabledBox);
		eastControls.add(editSearchBtn);
		eastControls.add(editEquippedBtn);
		eastControls.add(copyBtn);
		eastControls.add(copyEquippedBtn);
		eastControls.add(removeBtn);

		JCheckBox dontOverrideGlobalBox = new JCheckBox("Don't override Global", entry.isDontOverrideGlobal());
		dontOverrideGlobalBox.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		dontOverrideGlobalBox.setBackground(bg);
		dontOverrideGlobalBox.setAlignmentX(Component.LEFT_ALIGNMENT);
		dontOverrideGlobalBox.setBorder(BorderFactory.createEmptyBorder(0, 0, 6, 0));
		dontOverrideGlobalBox.setToolTipText("<html>When enabled, this weapon's triggers no longer override<br>"
			+ "the matching Global (All Weapons) triggers — both sounds play.</html>");
		dontOverrideGlobalBox.addActionListener(e ->
		{
			entry.setDontOverrideGlobal(dontOverrideGlobalBox.isSelected());
			store.saveWeaponDontOverrideGlobal(entry.getItemId(), entry.isDontOverrideGlobal());
		});

		JPanel row = buildCollapsibleGroupsPanel(
			expandedWeapons, entry.getItemId(),
			bg, ColorScheme.MEDIUM_GRAY_COLOR,
			dragHandle, iconLabel, nameLabel, eastControls, true, 36,
			dontOverrideGlobalBox,
			entry.getGroups(), availableSounds,
			() -> store.saveWeaponGroups(entry),
			EnumSet.complementOf(EnumSet.of(Triggers.REGULAR_HIT, Triggers.PLAYER_DEATH)));

		// Only the drag handle starts a reorder: an otherwise-empty listener on the row consumes presses on
		// its dead areas so they don't bubble to the reorder pane and begin a whole-row drag.
		row.addMouseListener(new MouseAdapter() {});
		// The reorder pane is a plain BoxLayout with no inter-row gap, so add the row spacing here. A matte
		// (not empty) border paints the gap in the panel background — an empty border would leave the row's
		// own opaque background showing below its line border.
		row.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createMatteBorder(0, 0, 8, 0, ColorScheme.DARK_GRAY_COLOR), row.getBorder()));
		return row;
	}

	/**
	 * A grip-icon label that drags its weapon row. {@link DragAndDropReorderPane} begins a drag from a press
	 * anywhere on a child, so to confine dragging to this handle we forward the handle's own mouse events to
	 * the pane (and absorb stray presses on the row body — see {@link #buildRow}).
	 */
	private JLabel makeDragHandle(DragAndDropReorderPane reorderPane)
	{
		JLabel handle = new JLabel(DRAG_ICON);
		handle.setToolTipText("Drag to reorder this weapon");
		handle.setCursor(Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR));
		MouseAdapter forwarder = new MouseAdapter()
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

	/**
	 * Builds a bordered, collapsible panel whose body is a {@link #rebuildGroupsSection} list of
	 * sound groups. Shared by the weapon rows and the Received/Global default sections; the only
	 * differences are the colours, the optional leading icon, the header's east controls, and which
	 * expansion set tracks the collapsed state (keyed by {@code key}).
	 */
	private <T> JPanel buildCollapsibleGroupsPanel(
		Set<T> expandedSet, T key,
		Color bg, Color borderColor,
		Component actionLeading,
		Component westLeading,
		JLabel nameLabel,
		List<Component> eastControls,
		boolean actionsAboveHeader,
		int headerMaxHeight,
		Component bodyLeading,
		List<TriggerGroup> groups, List<String> availableSounds,
		Runnable onSave, Set<Triggers> visibleTriggers)
	{
		boolean collapsed = !expandedSet.contains(key);

		JPanel panel = boxColumn(bg);
		panel.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createLineBorder(borderColor),
			new EmptyBorder(6, 6, 6, 6)
		));

		JButton collapseBtn = makeCollapseButton(collapsed);

		// When requested, action controls sit in their own row above the icon/name header so they read
		// as a distinct toolbar. The toggle leads, then the expand/minimize button, then stretch space
		// pushes the remaining icons to the right edge.
		if (actionsAboveHeader && eastControls != null && !eastControls.isEmpty())
		{
			JPanel actionRow = new JPanel();
			actionRow.setLayout(new BoxLayout(actionRow, BoxLayout.X_AXIS));
			actionRow.setBackground(bg);
			actionRow.setAlignmentX(Component.LEFT_ALIGNMENT);

			if (actionLeading != null)
			{
				actionRow.add(actionLeading);
				actionRow.add(Box.createHorizontalStrut(4));
			}
			actionRow.add(eastControls.get(0));
			actionRow.add(Box.createHorizontalStrut(4));
			actionRow.add(collapseBtn);
			actionRow.add(Box.createHorizontalGlue());
			for (int i = 1; i < eastControls.size(); i++)
			{
				if (i > 1) actionRow.add(Box.createHorizontalStrut(4));
				actionRow.add(eastControls.get(i));
			}
			actionRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, actionRow.getPreferredSize().height));
			panel.add(actionRow);
		}

		JPanel headerRow = new JPanel(new BorderLayout(6, 0));
		headerRow.setBackground(bg);
		headerRow.setAlignmentX(Component.LEFT_ALIGNMENT);
		if (headerMaxHeight > 0)
			headerRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, headerMaxHeight));

		if (actionsAboveHeader)
		{
			// Collapse button lives in the action row above; the header is just the icon + name.
			if (westLeading != null)
				headerRow.add(westLeading, BorderLayout.WEST);
		}
		else if (westLeading != null)
		{
			JPanel westBlock = new JPanel(new BorderLayout(4, 0));
			westBlock.setBackground(bg);
			westBlock.add(collapseBtn, BorderLayout.WEST);
			westBlock.add(westLeading, BorderLayout.EAST);
			headerRow.add(westBlock, BorderLayout.WEST);
		}
		else
		{
			headerRow.add(collapseBtn, BorderLayout.WEST);
		}

		headerRow.add(nameLabel, BorderLayout.CENTER);

		if (!actionsAboveHeader && eastControls != null && !eastControls.isEmpty())
		{
			JPanel eastBlock = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
			eastBlock.setBackground(bg);
			for (Component c : eastControls) eastBlock.add(c);
			headerRow.add(eastBlock, BorderLayout.EAST);
		}

		panel.add(headerRow);

		Component strut = Box.createVerticalStrut(6);
		strut.setVisible(!collapsed);
		panel.add(strut);

		// Optional body content (e.g. a weapon's "Don't override Global" toggle) sits above the sound
		// groups and collapses with them.
		if (bodyLeading != null)
		{
			bodyLeading.setVisible(!collapsed);
			panel.add(bodyLeading);
		}

		JPanel groupsHolder = boxColumn(bg);
		groupsHolder.setVisible(!collapsed);
		rebuildGroupsSection(groupsHolder, groups, availableSounds, onSave, visibleTriggers);
		panel.add(groupsHolder);

		collapseBtn.addActionListener(e ->
		{
			boolean nowCollapsed = expandedSet.contains(key);
			if (nowCollapsed)
				expandedSet.remove(key);
			else
				expandedSet.add(key);
			applyCollapseState(collapseBtn, nowCollapsed);
			strut.setVisible(!nowCollapsed);
			if (bodyLeading != null) bodyLeading.setVisible(!nowCollapsed);
			groupsHolder.setVisible(!nowCollapsed);
			panel.revalidate();
			panel.repaint();
		});

		return panel;
	}

	private void rebuildGroupsSection(JPanel holder, List<TriggerGroup> groups,
		List<String> availableSounds, Runnable onSave, Set<Triggers> visibleTriggers)
	{
		holder.removeAll();

		for (int i = 0; i < groups.size(); i++)
		{
			final int idx = i;
			TriggerGroup group = groups.get(i);

			JPanel groupPanel = boxColumn(ColorScheme.DARK_GRAY_COLOR);
			groupPanel.setBorder(BorderFactory.createCompoundBorder(
				BorderFactory.createLineBorder(ColorScheme.MEDIUM_GRAY_COLOR),
				new EmptyBorder(4, 4, 4, 4)
			));

			JPanel groupHeader = new JPanel(new BorderLayout());
			groupHeader.setBackground(ColorScheme.DARK_GRAY_COLOR);
			groupHeader.setAlignmentX(Component.LEFT_ALIGNMENT);

			String groupName = group.getName() != null ? group.getName() : "Sound Group " + (i + 1);
			JLabel groupLabel = new JLabel(groupName);
			groupLabel.setForeground(ColorScheme.BRAND_ORANGE);
			groupLabel.setToolTipText("<html>A sound group plays when its triggers are met. If the group has<br>"
				+ "multiple sounds, one is picked at random, weighted by each sound's weight.<br><br>"
				+ "You can add multiple sound groups — if several groups share<br>"
				+ "overlapping triggers, they all play. Use separate groups for<br>"
				+ "different triggers, or to stack multiple sounds on the same trigger.</html>");
			setBoldFont(groupLabel, SECTION_TITLE_SIZE);
			groupHeader.add(groupLabel, BorderLayout.WEST);

			JPanel headerButtons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 2, 0));
			headerButtons.setBackground(ColorScheme.DARK_GRAY_COLOR);

			JButton testGroupBtn = makeImageButton(TEST_ICON, TEST_HOVER_ICON,
				"Test this group — rolls its chance, then plays one sound chosen by weight at that sound's volume");
			testGroupBtn.addActionListener(e -> onTestGroup.accept(group));
			headerButtons.add(testGroupBtn);

			JButton renameGroupBtn = makeImageButton(EDIT_ICON, EDIT_HOVER_ICON, "Rename sound group");
			renameGroupBtn.addActionListener(e ->
			{
				String input = JOptionPane.showInputDialog(this, "Group name:", groupName);
				if (input == null) return; // cancelled
				String trimmed = input.trim();
				if (trimmed.isEmpty())
				{
					JOptionPane.showMessageDialog(this,
						"Name cannot be empty.", "Invalid name", JOptionPane.WARNING_MESSAGE);
					return;
				}
				if (TriggerGroup.isNameTaken(groups, trimmed, group))
				{
					JOptionPane.showMessageDialog(this,
						"A sound group named \"" + trimmed + "\" already exists.",
						"Duplicate name", JOptionPane.WARNING_MESSAGE);
					return;
				}
				group.setName(trimmed);
				onSave.run();
				rebuildGroupsSection(holder, groups, availableSounds, onSave, visibleTriggers);
			});
			headerButtons.add(renameGroupBtn);

			JButton removeGroupBtn = makeRemoveButton("Remove sound group");
			removeGroupBtn.addActionListener(e ->
			{
				if (!confirmYesNo("Remove \"" + groupName + "\"?", "Remove Sound Group")) return;
				groups.remove(idx);
				onSave.run();
				rebuildGroupsSection(holder, groups, availableSounds, onSave, visibleTriggers);
			});
			headerButtons.add(removeGroupBtn);

			groupHeader.add(headerButtons, BorderLayout.EAST);

			JPanel soundsHolder = boxColumn(ColorScheme.DARK_GRAY_COLOR);
			rebuildSoundsHolder(soundsHolder, group, availableSounds, onSave);

			groupPanel.add(groupHeader);
			groupPanel.add(Box.createVerticalStrut(4));
			groupPanel.add(buildSliderRow(ColorScheme.DARK_GRAY_COLOR, "Chance:",
				"Probability that this sound group plays when its trigger fires (100 = always, 0 = never)",
				group.getChance(), v -> { group.setChance(v); onSave.run(); }));
			groupPanel.add(Box.createVerticalStrut(4));
			groupPanel.add(soundsHolder);
			groupPanel.add(Box.createVerticalStrut(4));
			groupPanel.add(buildTriggersPanel(group.getTriggers(), onSave, visibleTriggers));

			holder.add(groupPanel);
			if (i < groups.size() - 1)
				holder.add(Box.createVerticalStrut(4));
		}

		holder.add(Box.createVerticalStrut(4));
		JButton addGroupBtn = new JButton("+ Add Sound Group");
		addGroupBtn.setToolTipText("Add a new sound group with its own triggers, chance, and sounds");
		addGroupBtn.setAlignmentX(Component.LEFT_ALIGNMENT);
		addGroupBtn.addActionListener(e ->
		{
			List<SoundEntry> newSounds = new ArrayList<>();
			newSounds.add(new SoundEntry("", 75));
			TriggerGroup newGroup = new TriggerGroup(EnumSet.noneOf(Triggers.class), newSounds, 100);
			newGroup.setName(TriggerGroup.defaultName(groups));
			groups.add(newGroup);
			onSave.run();
			rebuildGroupsSection(holder, groups, availableSounds, onSave, visibleTriggers);
		});
		holder.add(addGroupBtn);

		holder.revalidate();
		holder.repaint();
	}

	private JPanel buildTriggersPanel(Set<Triggers> enabledTriggers, Runnable onSave, Set<Triggers> visibleTriggers)
	{
		JPanel panel = boxColumn(ColorScheme.DARK_GRAY_COLOR);

		JLabel lbl = new JLabel("Triggers:");
		lbl.setForeground(Color.WHITE);
		lbl.setAlignmentX(Component.LEFT_ALIGNMENT);
		lbl.setToolTipText("Which hit outcomes cause this sound group to play");
		panel.add(lbl);
		panel.add(Box.createVerticalStrut(2));

		for (Triggers trigger : Triggers.values())
		{
			if (!visibleTriggers.contains(trigger)) continue;
			JCheckBox box = new JCheckBox(trigger.getName());
			box.setForeground(Color.LIGHT_GRAY);
			box.setBackground(ColorScheme.DARK_GRAY_COLOR);
			box.setToolTipText(triggerTooltip(trigger));
			box.setSelected(enabledTriggers.contains(trigger));
			box.setAlignmentX(Component.LEFT_ALIGNMENT);
			box.addActionListener(e ->
			{
				if (box.isSelected()) enabledTriggers.add(trigger);
				else enabledTriggers.remove(trigger);
				onSave.run();
			});
			panel.add(box);
		}

		return panel;
	}

	/** A short description of when each trigger fires, shown as the trigger checkbox's tooltip. */
	private static String triggerTooltip(Triggers trigger)
	{
		switch (trigger)
		{
			case REGULAR_ZERO:
				return "Fires when a regular (non-special) attack deals 0 damage";
			case REGULAR_HIT:
				return "Fires when a regular (non-special) attack deals 1 or more damage";
			case REGULAR_MAX:
				return "Fires when a regular (non-special) attack deals your maximum possible hit";
			case SPECIAL_ZERO:
				return "Fires when a special attack deals 0 damage";
			case SPECIAL_HIT:
				return "Fires when a special attack deals 1 or more damage";
			case SPECIAL_MAX:
				return "Fires when a special attack deals your maximum possible hit";
			case ALL:
				return "Fires on every attack, regardless of the outcome";
			case KILL:
				return "Fires when your attack kills the target";
			case PLAYER_DEATH:
				return "Fires when you die";
			default:
				return null;
		}
	}

	private void rebuildSoundsHolder(JPanel holder, TriggerGroup group,
		List<String> availableSounds, Runnable onSave)
	{
		holder.removeAll();
		List<SoundEntry> sounds = group.getSounds();
		final boolean weighted = sounds.size() > 1;
		final List<JLabel> weightReadouts = new ArrayList<>();

		// Re-derives each weight row's effective "%" from the current weights across all sounds.
		Runnable refreshWeights = () ->
		{
			double total = 0;
			for (SoundEntry s : sounds) total += Math.max(0, s.getWeight());
			for (int k = 0; k < weightReadouts.size(); k++)
			{
				double w = Math.max(0, sounds.get(k).getWeight());
				weightReadouts.get(k).setText(total <= 0 ? "0%" : Math.round(w * 100 / total) + "%");
			}
		};

		for (int j = 0; j < sounds.size(); j++)
		{
			final int idx = j;
			SoundEntry se = sounds.get(j);

			// Each sound lives in its own box, styled like the weapon boxes, to set it apart.
			JPanel soundBox = boxColumn(SOUND_BOX_COLOR);
			soundBox.setBorder(BorderFactory.createCompoundBorder(
				BorderFactory.createLineBorder(ColorScheme.MEDIUM_GRAY_COLOR),
				new EmptyBorder(6, 6, 6, 6)
			));

			// BorderLayout keeps the remove button pinned to the right edge so it can't be
			// pushed off-panel when the box border narrows the available width.
			JPanel soundRow = new JPanel(new BorderLayout(4, 0));
			soundRow.setBackground(SOUND_BOX_COLOR);
			soundRow.setAlignmentX(Component.LEFT_ALIGNMENT);

			JPanel soundControls = flowRow(SOUND_BOX_COLOR);
			JLabel lbl = new JLabel("Sound:");
			lbl.setForeground(Color.WHITE);
			lbl.setToolTipText("Sound file to play. Built-in sounds are bundled with the plugin; custom sounds load from .runelite/customweaponsfx/");
			soundControls.add(lbl);

			String[] options = buildSoundOptions(availableSounds);
			JComboBox<String> box = new JComboBox<>(options);
			box.setPreferredSize(new Dimension(75, box.getPreferredSize().height));
			box.setSelectedItem(configToDisplay(se.getSoundFile()));
			box.addActionListener(e ->
			{
				se.setSoundFile(displayToConfig((String) box.getSelectedItem()));
				onSave.run();
			});
			soundControls.add(box);

			JButton testBtn = makeImageButton(TEST_ICON, TEST_HOVER_ICON, "Test sound");
			testBtn.addActionListener(e -> onTestSound.accept(
				displayToConfig((String) box.getSelectedItem()), se.getVolume()));
			soundControls.add(testBtn);

			soundRow.add(soundControls, BorderLayout.CENTER);

			if (sounds.size() > 1)
			{
				JButton removeBtn = makeRemoveButton("Remove this sound");
				removeBtn.addActionListener(e ->
				{
					sounds.remove(idx);
					onSave.run();
					rebuildSoundsHolder(holder, group, availableSounds, onSave);
				});
				soundRow.add(removeBtn, BorderLayout.EAST);
			}

			soundRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, soundRow.getPreferredSize().height));
			soundBox.add(soundRow);
			soundBox.add(buildSliderRow(SOUND_BOX_COLOR, "Volume:",
				"Playback volume for this sound (0 = silent, 100 = full volume)",
				se.getVolume(), v -> { se.setVolume(v); onSave.run(); }));

			if (weighted)
				soundBox.add(buildWeightRow(SOUND_BOX_COLOR, se, weightReadouts, refreshWeights, onSave));

			holder.add(soundBox);
			if (j < sounds.size() - 1)
				holder.add(Box.createVerticalStrut(4));
		}

		refreshWeights.run();

		holder.add(Box.createVerticalStrut(2));
		JButton addSoundBtn = new JButton("+ Add Sound");
		addSoundBtn.setToolTipText("Add another sound to this group — when it fires, one is picked at random, weighted by each sound's weight");
		addSoundBtn.setAlignmentX(Component.LEFT_ALIGNMENT);
		addSoundBtn.addActionListener(e ->
		{
			sounds.add(new SoundEntry("", 75));
			onSave.run();
			rebuildSoundsHolder(holder, group, availableSounds, onSave);
		});
		holder.add(addSoundBtn);

		holder.revalidate();
		holder.repaint();
	}

	/**
	 * A "Weight:" spinner row whose readout shows this sound's <em>effective</em> chance
	 * (its weight relative to the group's total). Live-updates every row's readout via
	 * {@code refreshWeights} and commits via {@code onSave}.
	 */
	private JPanel buildWeightRow(Color bg, SoundEntry se, List<JLabel> weightReadouts,
		Runnable refreshWeights, Runnable onSave)
	{
		JPanel row = flowRow(bg);
		JLabel lbl = new JLabel("Weight:");
		lbl.setForeground(Color.WHITE);
		lbl.setToolTipText("Relative weight for picking this sound. Its chance to play is this "
			+ "weight divided by the sum of all weights in the group (0 = never). "
			+ "The readout shows the resulting chance.");
		row.add(lbl);

		double initial = Math.max(0, se.getWeight());
		JSpinner spinner = new JSpinner(new SpinnerNumberModel(initial, 0d, 1000d, 0.5d));
		spinner.setPreferredSize(new Dimension(60, spinner.getPreferredSize().height));
		JLabel readout = new JLabel();
		readout.setForeground(Color.LIGHT_GRAY);
		weightReadouts.add(readout);
		spinner.addChangeListener(e ->
		{
			se.setWeight(((Number) spinner.getValue()).doubleValue());
			refreshWeights.run();
			onSave.run();
		});
		row.add(spinner);
		row.add(readout);
		return row;
	}

	/** A label + 0–100 slider + live "%" readout row; commits via {@code onCommit} on release. */
	private JPanel buildSliderRow(Color bg, String label, String tooltip, int initial, IntConsumer onCommit)
	{
		JPanel row = flowRow(bg);
		JLabel lbl = new JLabel(label);
		lbl.setForeground(Color.WHITE);
		lbl.setToolTipText(tooltip);
		row.add(lbl);
		JSlider slider = new JSlider(0, 100, initial);
		slider.setPreferredSize(new Dimension(100, 20));
		JLabel val = new JLabel(initial + "%");
		val.setForeground(Color.LIGHT_GRAY);
		slider.addChangeListener(e ->
		{
			int v = slider.getValue();
			val.setText(v + "%");
			if (!slider.getValueIsAdjusting())
			{
				onCommit.accept(v);
			}
		});
		row.add(slider);
		row.add(val);
		return row;
	}

	private String[] buildSoundOptions(List<String> userSounds)
	{
		String[] options = new String[bundledSounds.size() + userSounds.size()];
		for (int i = 0; i < bundledSounds.size(); i++)
			options[i] = bundledSounds.get(i) + BUILTIN_SUFFIX;
		for (int i = 0; i < userSounds.size(); i++)
			options[bundledSounds.size() + i] = userSounds.get(i);
		return options;
	}

	private String configToDisplay(String configValue)
	{
		if (configValue == null || configValue.isEmpty())
			return "squeak" + BUILTIN_SUFFIX;
		if (configValue.startsWith(BUNDLED_PREFIX))
			return configValue.substring(BUNDLED_PREFIX.length()) + BUILTIN_SUFFIX;
		return configValue;
	}

	private static String displayToConfig(String display)
	{
		if (display == null) return BUNDLED_PREFIX + "squeak";
		if (display.endsWith(BUILTIN_SUFFIX))
			return BUNDLED_PREFIX + display.substring(0, display.length() - BUILTIN_SUFFIX.length());
		return display;
	}

	// ----- shared widget factories ----------------------------------------------------------

	/** A vertical (Y_AXIS), left-aligned panel; {@code bg} may be {@code null} to stay transparent. */
	private static JPanel boxColumn(Color bg)
	{
		JPanel p = new JPanel();
		p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
		if (bg != null) p.setBackground(bg);
		p.setAlignmentX(Component.LEFT_ALIGNMENT);
		return p;
	}

	/** A left-aligned {@link FlowLayout} row with a tight 4px horizontal gap. */
	private static JPanel flowRow(Color bg)
	{
		JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
		p.setBackground(bg);
		p.setAlignmentX(Component.LEFT_ALIGNMENT);
		return p;
	}

	/** A borderless, transparent button showing only {@code icon}, swapping to {@code hoverIcon} on rollover. */
	private static JButton makeImageButton(ImageIcon icon, ImageIcon hoverIcon, String tooltip)
	{
		JButton b = new JButton(icon);
		if (hoverIcon != null)
		{
			b.setRolloverIcon(hoverIcon);
		}
		b.setToolTipText(tooltip);
		b.setMargin(new Insets(0, 0, 0, 0));
		b.setBorderPainted(false);
		b.setContentAreaFilled(false);
		b.setFocusPainted(false);
		b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		return b;
	}

	private static JButton makeRemoveButton(String tooltip)
	{
		return makeImageButton(DELETE_ICON, DELETE_HOVER_ICON, tooltip);
	}

	private static JButton makeCollapseButton(boolean collapsed)
	{
		JButton b = makeImageButton(null, null, null);
		applyCollapseState(b, collapsed);
		return b;
	}

	private static void applyCollapseState(JButton btn, boolean collapsed)
	{
		btn.setIcon(collapsed ? EXPAND_ICON : COLLAPSE_ICON);
		btn.setRolloverIcon(collapsed ? EXPAND_HOVER_ICON : COLLAPSE_HOVER_ICON);
		btn.setToolTipText(collapsed ? "Expand" : "Minimize");
	}

	private static void setBoldFont(Component c, float size)
	{
		c.setFont(c.getFont().deriveFont(Font.BOLD, size));
	}

	private boolean confirmYesNo(String message, String title)
	{
		return JOptionPane.showConfirmDialog(this, message, title,
			JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE) == JOptionPane.YES_OPTION;
	}
}
