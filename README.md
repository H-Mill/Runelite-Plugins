# MLM QOL

Quality of life for the [Motherlode Mine](https://oldschool.runescape.wiki/w/Motherlode_Mine).

## Stop mining when the sack is full

Once your [sack](https://oldschool.runescape.wiki/w/Sack_(Motherlode_Mine)) has no room left, the
`Mine` option is removed from [ore veins](https://oldschool.runescape.wiki/w/Ore_vein) so you can't
keep mining pay-dirt you'd have nowhere to put. The option comes back as soon as you collect from
the sack.

Pay-dirt you're carrying and pay-dirt still travelling the conveyor belt both count, so mining stops
the moment you empty a load into the hopper rather than seconds later when the belt catches up.

| Option | Default | Description |
| --- | --- | --- |
| Block mining when full | on | Removes the `Mine` option from ore veins once the sack reaches the block count |
| Block at | 108 | Pay-dirt count to stop mining at |
| Warn in chat | on | Sends a chat message when the block count is reached and when the sack has room again |

Sack capacity is 108, or 189 after buying the upgrade from Prospector Percy. **Block at** follows the
upgrade automatically, unless you set your own value — that is never overwritten. It can't exceed
what your sack can actually hold.

## Flag the hopper

Each water wheel is held up by a [strut](https://oldschool.runescape.wiki/w/Strut). While *every*
strut is broken the water stops and nothing you put in the
[hopper](https://oldschool.runescape.wiki/w/Hopper_(Motherlode_Mine)) reaches the sack — repairing
either one starts the belt again.

The hopper is labelled with what's going on:

| Situation | Shown |
| --- | --- |
| Your pay-dirt is on a moving belt | `Ore in transit` |
| Both struts broken, nothing on the belt | `Strut broken` |
| Both struts broken with your pay-dirt on it | both |

Broken struts are outlined too, including a single broken strut — the belt keeps running on the
other wheel, but the outline shows you which one to repair.

`Deposit` is hidden only when the belt is stopped *and* there's no room for another load — either
your pay-dirt is already stranded on the belt, or the sack is full. Pay-dirt in your inventory
doesn't count: a stopped belt with room in the sack still takes it and carries it once you repair a
strut.

| Option | Default | Description |
| --- | --- | --- |
| Block deposit when blocked | on | Removes the `Deposit` option while the belt is stopped and there is no room for another load |
| Highlight broken struts | on | Outlines any broken strut, so you can see which one needs repairing |
| Broken wheel colour | red | Colour of the broken strut outline and the `Strut broken` label |
| Ore in transit colour | orange | Colour of the `Ore in transit` label |

## Name the baby mole

Puts a name of your choosing over the baby mole. It only ever shows inside the Motherlode Mine.

| Option | Default | Description |
| --- | --- | --- |
| Baby mole name | off | Shows the name over the baby mole |
| Name | Rufus | The name to show — an empty name shows nothing |
| Name colour | white | Colour of the name |
