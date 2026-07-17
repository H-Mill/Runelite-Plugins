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

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

/**
 * The side panel's name search. The display names the game hands us carry non-breaking spaces, so
 * both the name and the typed text are normalized through {@link GroupStore#key} before matching -
 * these pin the cases where a naive {@code toLowerCase().contains()} would quietly find nothing.
 */
public class SearchFilterTest
{
	/** The non-breaking space (U+00A0) the game uses between display-name words. */
	private static final String NBSP = String.valueOf((char) 0x00A0);

	/** A display name as the game hands it to us, spaced with U+00A0. */
	private static final String NBSP_NAME = "Zezima" + NBSP + "Jr";

	@Test
	public void findsNameByPrefix()
	{
		assertTrue(search("Zezima", "zez"));
	}

	@Test
	public void findsNameByInteriorFragment()
	{
		assertTrue(search("Zezima", "zim"));
	}

	@Test
	public void ignoresCase()
	{
		assertTrue(search("Zezima", "ZEZIMA"));
	}

	@Test
	public void doesNotMatchUnrelatedName()
	{
		assertFalse(search("Zezima", "durial"));
	}

	@Test
	public void typedSpaceMatchesNonBreakingSpaceInName()
	{
		// The regression: the game's names use U+00A0, so a typed ' ' would otherwise never match.
		assertTrue(search(NBSP_NAME, "zezima jr"));
	}

	@Test
	public void typedUnderscoreMatchesSpaceInName()
	{
		// key() folds _ and - to spaces, so the usual forum spelling of a name still finds it.
		assertTrue(search(NBSP_NAME, "zezima_jr"));
	}

	@Test
	public void typedHyphenMatchesSpaceInName()
	{
		assertTrue(search(NBSP_NAME, "zezima-jr"));
	}

	@Test
	public void ignoresSurroundingWhitespace()
	{
		assertTrue(search("Zezima", "  zez  "));
	}

	@Test
	public void emptySearchMatchesEverything()
	{
		assertTrue(search("Zezima", ""));
	}

	@Test
	public void whitespaceOnlySearchMatchesEverything()
	{
		// Normalizing trims to empty, so a space-only box filters nothing rather than everything.
		assertTrue(GroupStore.key("   ").isEmpty());
		assertTrue(search("Zezima", "   "));
	}

	/** Matches {@code typed} against {@code name} the way the search box does. */
	private static boolean search(String name, String typed)
	{
		return GroupListView.matchesSearch(name, GroupStore.key(typed));
	}
}
