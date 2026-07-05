package com.customweaponsfx;

import java.awt.Component;
import java.awt.Container;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javax.swing.JButton;
import javax.swing.SwingUtilities;
import net.runelite.client.game.ItemManager;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;

/**
 * Covers the side-panel button gating: the "change to equipped weapon" and "clone to equipped weapon"
 * actions are usable only while logged in <em>and</em> a weapon is equipped, whereas the other live-state
 * actions only require being logged in. Also verifies the gating survives a {@link CustomWeaponSfxPanel#rebuild}
 * (the per-row buttons are recreated, so they must pick up the current state).
 */
public class CustomWeaponSfxPanelLoginGatingTest
{
	private static final int ABYSSAL_WHIP = 4151;

	// Exact tooltips identify the buttons in the rendered tree.
	private static final String CHANGE_TO_EQUIPPED =
		"Change this weapon to your currently equipped weapon — keeps its sound groups";
	private static final String CLONE_TO_EQUIPPED =
		"Clone this weapons settings to your currently equipped weapon";
	private static final String CHANGE_VIA_SEARCH =
		"Change this weapon by searching — keeps its sound groups";

	private CustomWeaponSfxPanel panel;

	@Before
	public void setUp() throws Exception
	{
		CustomWeaponSfxConfigStore store = mock(CustomWeaponSfxConfigStore.class);
		ItemManager itemManager = mock(ItemManager.class);

		onEdt(() ->
			panel = new CustomWeaponSfxPanel(
				store, itemManager,
				() -> {}, () -> {},
				id -> {}, id -> {}, id -> {}, id -> {}, id -> {},
				(fromId, toIndex) -> {},
				(name, id) -> {}, group -> {},
				() -> {}, () -> {}, () -> {},
				(option, on) -> {},
				raw -> {}, raw -> {}, raw -> {}));

		// One weapon row so the per-row "to equipped weapon" buttons actually exist.
		rebuildWithOneWeapon();
	}

	@Test
	public void equippedActionsDisabledWhileLoggedOut() throws Exception
	{
		// Default state is logged out; the panel starts with everything disabled.
		assertFalse(isEnabled(CHANGE_TO_EQUIPPED));
		assertFalse(isEnabled(CLONE_TO_EQUIPPED));
		assertFalse(isEnabled(CHANGE_VIA_SEARCH));
	}

	@Test
	public void equippedActionsStayDisabledWhenLoggedInButUnarmed() throws Exception
	{
		onEdt(() -> panel.setLoginButtonsEnabled(true, false));

		// Login-only actions become usable...
		assertTrue(isEnabled(CHANGE_VIA_SEARCH));
		// ...but the "to equipped weapon" actions need a weapon equipped.
		assertFalse(isEnabled(CHANGE_TO_EQUIPPED));
		assertFalse(isEnabled(CLONE_TO_EQUIPPED));
	}

	@Test
	public void equippedActionsEnabledWhenLoggedInAndArmed() throws Exception
	{
		onEdt(() -> panel.setLoginButtonsEnabled(true, true));

		assertTrue(isEnabled(CHANGE_VIA_SEARCH));
		assertTrue(isEnabled(CHANGE_TO_EQUIPPED));
		assertTrue(isEnabled(CLONE_TO_EQUIPPED));
	}

	@Test
	public void equippedActionsRequireLoginEvenWhenArmed() throws Exception
	{
		// weaponEquipped is true, but a logged-out client can't read live state at all.
		onEdt(() -> panel.setLoginButtonsEnabled(false, true));

		assertFalse(isEnabled(CHANGE_VIA_SEARCH));
		assertFalse(isEnabled(CHANGE_TO_EQUIPPED));
		assertFalse(isEnabled(CLONE_TO_EQUIPPED));
	}

	@Test
	public void rebuildReappliesEquippedGatingToNewButtons() throws Exception
	{
		// Set state, then rebuild: the freshly-created row buttons must reflect the current state.
		onEdt(() -> panel.setLoginButtonsEnabled(true, true));
		rebuildWithOneWeapon();
		assertTrue(isEnabled(CHANGE_TO_EQUIPPED));
		assertTrue(isEnabled(CLONE_TO_EQUIPPED));

		onEdt(() -> panel.setLoginButtonsEnabled(true, false));
		rebuildWithOneWeapon();
		assertFalse(isEnabled(CHANGE_TO_EQUIPPED));
		assertFalse(isEnabled(CLONE_TO_EQUIPPED));
		// The login-only action is unaffected by the unarmed state.
		assertTrue(isEnabled(CHANGE_VIA_SEARCH));
	}

	private void rebuildWithOneWeapon() throws Exception
	{
		List<WeaponEntry> weapons = new ArrayList<>();
		weapons.add(new WeaponEntry(ABYSSAL_WHIP, "Abyssal whip", new ArrayList<>()));
		panel.rebuild(weapons, Collections.emptyList(), Collections.emptyList(),
			Collections.emptyList(), Collections.emptyList());
		// rebuild() does its work on the EDT via invokeLater; flush it before asserting.
		flushEdt();
	}

	private boolean isEnabled(String tooltip) throws Exception
	{
		JButton button = findButton(panel, tooltip);
		assertNotNull("button with tooltip not found: " + tooltip, button);
		return button.isEnabled();
	}

	private static JButton findButton(Container root, String tooltip)
	{
		for (Component c : root.getComponents())
		{
			if (c instanceof JButton && tooltip.equals(((JButton) c).getToolTipText()))
			{
				return (JButton) c;
			}
			if (c instanceof Container)
			{
				JButton found = findButton((Container) c, tooltip);
				if (found != null)
				{
					return found;
				}
			}
		}
		return null;
	}

	private static void flushEdt() throws InterruptedException, InvocationTargetException
	{
		// A no-op task completes only after all earlier-queued EDT work (the rebuild) has run.
		SwingUtilities.invokeAndWait(() -> {});
	}

	private static void onEdt(Runnable r) throws InterruptedException, InvocationTargetException
	{
		SwingUtilities.invokeAndWait(r);
	}
}
