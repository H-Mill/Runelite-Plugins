# Custom Weapon SFX

Play custom sound effects when you attack or get hit in Old School RuneScape.

## Features

- Assign sounds to specific weapons
- Play different sounds for misses, regular hits, and max hits — on both regular and special attacks
- Dedicated **Player kill** trigger that fires when your attack kills another player
- Dedicated **Player death** trigger (Received Attacks) that fires when your character dies
- Separate sounds for when *you* take damage (Received Attacks)
- **Global (All Weapons)** fallback section — plays sounds for any weapon that doesn't have its own configuration for a given trigger
- Multiple sound groups per weapon or section, each with its own triggers, sounds, volume, and activation chance
- Multiple sounds per group — one is picked at random each time the group fires, with an adjustable **weight** to make some sounds more likely than others
- Per-group activation chance (0–100%) for randomized playback
- Enable or disable individual weapons and sections without losing their configuration
- Reorder your configured weapons by dragging each row's handle
- Use built-in sounds or drop in your own `.wav` files
- Per-sound volume control and in-panel preview
- Mute the game's default weapon sounds by sound ID, so your custom SFX replaces them instead of layering on top

## Setup

1. Open the **Custom Weapon SFX** panel from the sidebar.
2. Add a weapon — click **Add (Equipped)** while holding a weapon, or click **Add (Search)** to find one by name (requires being logged in).
3. Expand the weapon and configure its sound group(s).
4. To play a sound when you take damage, expand the **Received Attacks** section at the top.
5. To play a sound for any weapon without its own configuration, expand the **Global (All Weapons)** section.

### Managing weapons

Each weapon row has buttons to re-point or copy its configuration without rebuilding it from scratch:

- **Change (Search)** / **Change (Equipped)** — move this weapon entry to a different weapon (by name search or your currently equipped weapon), **keeping** its sound groups. Use this when, for example, you upgrade to a new version of a weapon and want to carry its sounds over.
- **Clone (Search)** / **Clone (Equipped)** — create a *new* weapon entry that copies this weapon's settings (sound groups, triggers, volumes, weights) onto another weapon, leaving the original in place.

To **reorder** your weapons, drag a row by its handle (the grip icon at the left of the row's toolbar) and drop it in the new position. The order is saved automatically.

## Sound Groups

Each weapon and section contains one or more **sound groups**. A sound group fires when its selected triggers match the outcome of an attack (see [Triggers](#triggers) for exact matching rules).

Click **+ Add Sound Group** inside an expanded weapon or section to add another group. New groups get a default name (**Sound Group 1**, **Sound Group 2**, …); click the **edit** (pencil) icon on the group header to rename it. Names must be unique within a weapon or section. Each group has:

- **Chance** — probability the group plays when its trigger fires (100% = always, 0% = never). Useful for adding occasional variation.
- **Sound(s)** — one or more sound files; if multiple are added, one is chosen at random each time the group fires. Click the **test** icon to preview a sound.
- **Volume** — playback volume per sound (0–100).
- **Weight** — how likely each sound is to be picked when a group has more than one. A sound's chance is its weight divided by the total of all weights in the group, and the **%** beside it shows the resulting chance. Leave them all equal for an even random pick, or raise one to make it play more often.
- **Triggers** — which hit outcomes cause this group to fire (see below).

Click **+ Add Sound** inside a group to add more sounds to the random pool. Sound groups can be removed with the **delete** (trash) icon on the group header, including the last remaining one.

Click the **test** icon on the group header to test the whole group. It rolls the group's chance, then plays one sound picked by weight at that sound's volume — so you can preview the combined effect of chance, weights, and volume. (Because it rolls the chance, a group set below 100% may play nothing on some presses.)

Multiple sound groups on the same weapon (or section) with matching triggers all fire together, so you can define two or more groups with the same trigger to play several SFX at once. (A single group with multiple sounds plays just *one* of them at random — use separate groups when you want every sound to play.)

## Triggers

| Trigger | When it fires |
|---|---|
| Regular attack zero | Regular attack that deals 0 damage |
| Regular attack max | Regular attack that is a max hit |
| Special attack zero | Special attack that deals 0 damage |
| Special attack hit | Special attack that deals non-max damage |
| Special attack max | Special attack that is a max hit |
| All attacks | Every attack, including zero-damage hits |
| Player kill | Your attack kills another player |

The **Received Attacks** section supports a subset: Regular attack zero, Regular attack hit, All attacks, and Player death.

### Player kill trigger

The **Player kill** trigger fires when your hit reduces another player's health to zero. It only works in PvP (wilderness, PvP worlds, etc.) — NPC kills do not activate it.

**Priority:** when a weapon has at least one group with Player kill enabled, that group takes exclusive priority on kills. All other groups on that weapon are suppressed for that attack, even if their own triggers would have matched.

**Combining with damage triggers:** if you add Player kill to a group alongside one or more damage triggers (Regular attack max, Special attack hit, Special attack max, etc.), **both conditions must be met** for the group to fire. For example:

- `Player kill` + `Special attack max` → fires only when you kill a player with a special attack max hit
- `Player kill` + `Regular attack max` + `Special attack max` → fires when you kill a player with either a regular or special max hit
- `Player kill` alone → fires on any killing blow, regardless of hit type

This lets you set up a generic kill sound alongside a more specific one for, say, a one-shot special — only the most specific matching group fires.

### Player death trigger

The **Player death** trigger is available only in the **Received Attacks** section. It fires when your character dies, regardless of what killed you (another player, an NPC, poison, etc.).

It fires independently of the hitsplat system — a death from poison or a delayed hit will still trigger it. It cannot be added to weapon sound groups.

When Player death fires, all other Received Attacks triggers are suppressed for that same hit. Only the death sound plays.

## Global (All Weapons)

The **Global (All Weapons)** section acts as a fallback for any weapon hit that is not handled by a weapon-specific sound group.

When your attack lands, the plugin checks whether the weapon you used has a group configured for that trigger. **By default, weapon-specific triggers override global triggers:** if the weapon has a group for that trigger, the weapon-specific group fires and the matching global group is suppressed for that trigger so the two do not stack. If it does not, the global group fires instead.

This lets you set a single default "on hit" sound that plays for every weapon, then override it selectively for specific weapons.

If you would rather have both the weapon-specific and global sounds play for overlapping triggers, enable that weapon's **Don't override Global** toggle (see below).

**Example:** configure a generic hit sound in Global with the *All attacks* trigger. Add your scythe with only a *Regular attack max* group. Scythe max hits play the scythe sound; every other hit type on the scythe, and all hits with any other weapon, play the global sound.

The global section only fires for outgoing hits — it is not affected by incoming damage.

## Enabling and Disabling Weapons and Sections

Each weapon row, the **Received Attacks** section, and the **Global (All Weapons)** section each have a checkbox in their header. Unchecking it disables that weapon or section — its sound groups will not fire — without removing any configuration. Check it again to re-enable.

## Don't override Global (per weapon)

By default, when a weapon has its own sound group for a trigger, it overrides the matching **Global (All Weapons)** sound for that trigger so the two do not stack. Expand a weapon and enable its **Don't override Global** toggle to make the weapon-specific and Global sounds for the same trigger *both* play. This is configured per weapon and is disabled by default.

## Using Your Own Sounds

1. Place `.wav` files in `.runelite/customweaponsfx/` — click the **Open SFX folder** button (folder icon) in the panel toolbar to open it directly.
2. Click **Refresh Sounds** in the panel.
3. Your files will appear in the sound dropdowns alongside the built-in sounds.

Need sounds to get started? I've compiled a collection of `.wav` SFX you can download and drop in: [H-Mill/Runelite-Plugins (sfx branch)](https://github.com/H-Mill/Runelite-Plugins/tree/sfx).

## Options

The **Options** section at the top of the panel (collapsed by default) contains three global toggles, two NPC exclusion lists, and a default-sound mute list:

**Ignore max hits ≤ 3** — when enabled, max hit SFX will not play if the hit deals 3 or fewer damage. This prevents thrall max hits from triggering your *Regular/Special attack max* sounds. Enabled by default.

**Ignore zeroes with prayer** — when enabled, the Received Attacks *Regular attack zero* trigger will not fire while Protect from Melee, Protect from Ranged, or Protect from Magic is active. Useful if you don't want a "blocked" sound every time a prayer absorbs a hit. Enabled by default.

**Ignore zeroes with thrall** — when enabled, zero-damage triggers will not fire while a thrall is active. Prevents thrall misses from triggering zero-damage sounds. Enabled by default.

### Excluding NPCs

Two text fields let you stop your hits on specific NPCs from playing any SFX. Both accept a **comma-separated list** and apply only to *outgoing* hits on NPCs — your weapon, Global, and Received Attacks sounds are otherwise unaffected. Changes save automatically when you press Enter or click away from the field.

**Excluded NPC IDs** — comma-separated NPC ids (e.g. `11706, 11707`). Any hit you land on a matching NPC plays no sound.

**Excluded NPC Names** — comma-separated NPC names (e.g. `Vorkath, Zulrah`). Matching is **case-insensitive** and must be the NPC's full name (not a partial match). Useful when an encounter reuses the same name across several NPC ids.

An NPC is excluded if it matches *either* list (or the built-in ids), so you can mix and match. Invalid or blank entries are ignored.

### Muting default weapon sounds

By default your custom SFX play *on top of* the game's own weapon sounds. The **Muted Weapon Sound IDs** field lets you silence the game's stock sounds so only your custom SFX is heard.

Enter a **comma-separated list** of the game sound-effect ids you want muted. Any matching sound is silenced — both the sound you hear and the one nearby players hear for that effect. Changes save automatically when you press Enter or click away from the field. Invalid or blank entries are ignored.

This mutes sounds purely by id, independent of which weapon is configured — so you can use it to quiet any annoying default sound, not just the weapons you've set up custom SFX for.

**Common sound ids:**

| Sound ID | What it is |
|---|---|
| `3892` | The Armadyl crossbow / Zaryte crossbow (ACB/ZCB) special attack "REEEE" sound |

## Plugin Configuration

A few settings live in RuneLite's standard configuration panel — click the **gear/cog icon** next to **Custom Weapon SFX** in the plugin list, or the **Open config** button (cog icon) in the side panel's toolbar, which opens the same configuration directly.

**Side Panel Priority** — controls where the Custom Weapon SFX icon sits in the RuneLite sidebar relative to other plugins' icons. **Lower numbers sit higher** (closer to the top); higher numbers sit lower. The default is `1`. Changing it reorders the icon.

## Resetting

Click **Reset All Data** to clear all weapons and sound groups and restore defaults. A confirmation dialog will appear first.

## Known Issues

**Thrall hits** — if you have a thrall active and a *Regular attack zero* or *All attacks* trigger set, thrall zeros and damage will cause the sound to play. Thrall hits are indistinguishable from player hits in the data provided by the game client, so this cannot be fully filtered out. The **Ignore max hits ≤ 3** and **Ignore zeroes with thrall** toggles mitigate thrall hits specifically.

**Splashing** - Your players splashing, like thrall zeros, cannot be distinguished from other players splashes, so I opted to not include them in the plugin (everyones splashes around you would cause a SFX).

## Version History

### 2.11

- **Reorder weapons by dragging** — each weapon row has a drag handle (grip icon at the left of its toolbar); drag a row and drop it to change the order. The new order is saved automatically.
- Move **Don't override Global** from a single global toggle to a per-weapon setting — expand a weapon to enable it just for that weapon, so its triggers and the matching Global (All Weapons) triggers both play.
- Add a **Muted Weapon Sound IDs** field to the Options section — a comma-separated list of game sound-effect ids to silence, so your custom SFX replaces the game's default weapon sound instead of playing over it. Mutes both your own sound and the area sound nearby players hear, by id. For example, id `3892` mutes the ACB/ZCB special attack "REEEE" sound.

### 2.10

- Add an **Open SFX folder** button (folder icon) to the panel toolbar that opens `.runelite/customweaponsfx/` directly, so you no longer have to find the folder yourself to drop in custom `.wav` files.
- Add an **Open config** button (cog icon) to the panel toolbar that opens the plugin's RuneLite configuration directly, instead of having to find the gear icon in the plugin list.
- Weapon-specific **Change** buttons — re-point an existing weapon entry at a different weapon while keeping all of its sound groups:
  - **Change (Search)** — pick the new weapon by name search.
  - **Change (Equipped)** — switch the entry to your currently equipped weapon.
- Weapon-specific **Clone** buttons — copy an existing weapon's settings (sound groups, triggers, volumes, weights) onto another weapon:
  - **Clone (Search)** — clone the settings onto a weapon picked by name search.
  - **Clone (Equipped)** — clone the settings onto your currently equipped weapon.
- UI and icon updates.

### 2.9

- Add a **Weight** control to each sound in a group with multiple sounds. Set how likely each sound is to play relative to the others — the **%** beside it shows the resulting chance. Existing groups keep an even random pick.
- Add a **test** button to each sound group header that rolls the group's chance and plays a weighted sound at its volume, so you can preview chance, weights, and volume together. The per-sound preview button now uses the same icon in place of the previous Unicode glyph.
- Each sound now sits in its own box in the panel to make multi-sound groups easier to read.

### 2.8

- Add a **Side Panel Priority** setting (in the plugin's config cog) to control where the Custom Weapon SFX icon sits in the RuneLite sidebar. Lower numbers sit higher; changes apply instantly without a restart.
- Small UI updates

### 2.7

- Fix the wrong sound playing on a ranged/magic hit when you switch weapons right after attacking. The hit now plays the sound of the weapon you actually attacked with.

### 2.6

- Add **Don't override Global** option — when enabled, weapon-specific triggers no longer override the matching Global (All Weapons) triggers, so both sounds play. Disabled by default.
- Add more tooltips throughout the panel to better explain how things work.
- Replace the panel's collapse, rename, and delete buttons with PNG icons in place of the previous Unicode glyphs (▶/▼, ✎, ✕), which rendered inconsistently across operating systems.
- Fix SFX continuing to play for a weapon after it was manually unequipped.

### 2.5

- Sound groups can now be **renamed** — click the **✎** button on a group header to give it a custom name. Names are saved to your config and must be unique within each weapon or section. The delete confirmation now shows the group's name.
- Add **Excluded NPC IDs** and **Excluded NPC Names** lists to the Options section — comma-separated entries that stop your hits on the listed NPCs from playing any SFX. Name matching is case-insensitive.

### 2.4

Internal refactor — no user-facing changes. Extracted config persistence into a single `CustomWeaponSfxConfigStore`, split the per-tick hit aggregation out of the plugin into `HitAggregator`, collapsed the five option toggles behind an `SfxOption` enum, and replaced the hardcoded special-attack varp with the `VarPlayerID.SA_ENERGY` gameval constant. Removed the unused update-notification panel.

### 2.3

- Fix wrong weapon sound playing when switching weapons before the hitsplat lands (ranged/magic).
- Add **Player Kill** trigger — use it alone or with other damage triggers to play SFX when you send another player to the shadow realm.
- Add **Player Death** trigger (Received Attacks only) — plays SFX when your character dies regardless of the cause. When it fires, all other Received Attacks triggers are suppressed for that hit.
- Add **Global (All Weapons)** section — configure fallback sounds that play for any weapon that doesn't have its own group for a given trigger. Weapon-specific groups always take priority.
- Add enable/disable toggles to the Received Attacks and Global (All Weapons) sections.
- Move the three options toggles (ignore max hits ≤ 3, ignore zeroes with prayer, ignore zeroes with thrall) into a collapsible Options section.
- Sound groups can now be deleted even when only one remains.
- **All attacks** trigger now fires on zero-damage hits as well as non-zero hits.
- Custom sounds: place any `.wav` file in `.runelite/customweaponsfx/`, click Refresh Sounds, and it will appear in all sound dropdowns.

### 2.2

Add **Ignore zeroes with thrall** toggle, until I find a better way to avoid issues with thralls (this may be never).

### 2.1

Add **Ignore max hits ≤ 3** and **Ignore zeroes with prayer** toggles.

### 2.0

Rebranded from Squeaky Toy to Custom Weapon SFX.

This is a full overhaul of the original plugin, which supported only a single sound at a fixed volume triggered by all player attacks / player zeros, or all attacks received / zeros received.

- Custom sounds — place `.wav` files in `.runelite/customweaponsfx/` and select them in the panel
- Per-weapon configuration — add weapons individually by name search or by equipping them
- Sound groups — each weapon supports multiple sound groups, each independently configured
- Triggers — control exactly when each group fires: zero damage, regular hit, max hit, special zero, special hit, special max, or any damaging hit
- Trigger chance — set a probability (0–100%) that a group fires when its trigger condition is met
- Per-group volume — each group has its own volume level
- Multi-sound groups — assign multiple sounds to a group and one is chosen at random on each fire
- Received Attacks — a dedicated section for sounds that play when you take damage, separate from outgoing attacks

### 1.0

Created Squeaky Toy Plugin.
