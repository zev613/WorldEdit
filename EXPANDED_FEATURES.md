WorldEdit Expanded
==================

This fork adds extra commands, patterns and masks on top of upstream WorldEdit.
Everything lives in `worldedit-core`, so it works on every platform. NeoForge is the primary target (Fabric, Bukkit/Paper and Sponge also work).

## Running it (NeoForge, Minecraft 26.3)

You need **JDK 25** installed (e.g. [Adoptium Temurin 25](https://adoptium.net/)). Check with `java -version`.
The first build downloads Gradle, Minecraft and all dependencies, so expect it to take several minutes.

### Option A: launch a dev Minecraft client straight from the repo (easiest)

```sh
# macOS / Linux
./gradlew :worldedit-neoforge:runClient

# Windows
gradlew.bat :worldedit-neoforge:runClient
```

This starts Minecraft 26.3 with NeoForge and this WorldEdit already loaded, using an offline dev account.
Create a creative world with cheats enabled and use the commands below.
`:worldedit-neoforge:runServer` starts a dedicated server instead (accept the EULA in `worldedit-neoforge/run/eula.txt` first).

### Option B: build a jar and install it in your normal game

```sh
./gradlew :worldedit-neoforge:build
```

Copy `worldedit-neoforge/build/libs/worldedit-neoforge-mc26.3-<version>-dist.jar` into the `mods` folder of a
NeoForge 26.3 install (the NeoForge version this was built against is `neoforge` in `gradle/libs.versions.toml`).
Remove any other WorldEdit jar first. Use the `-dist` jar, not `-dist-slim` (an intermediate jar missing the embedded jar-in-jar dependencies).

Other platforms work the same way, e.g. `./gradlew :worldedit-fabric:runClient` for Fabric.
`./gradlew build` builds everything, with jars in `worldedit-<platform>/build/libs`.

## Edit preview

`//preview` turns preview mode on (run it again to turn it off). While it's on, your edits (commands *and* brushes)
are not built. Instead you see them as "ghost" blocks that only you can see, so it works with a plain vanilla client.
Previewed edits stack: a second command sees the result of the first.

| Command | What it does |
|---|---|
| `//preview [on\|off]` | Turn preview mode on or off. Turning it off throws away anything not yet applied. |
| `//preview apply` | Build everything you're previewing, as one edit you can `//undo`. |
| `//preview cancel` | Throw away the previewed changes and show the real world again. |
| `//preview show` | Re-send the ghost blocks (they vanish if you walk far away and the chunks reload). |
| `//preview status` | Is preview mode on, and how many changes are waiting. |

Notes: only blocks are previewed. Biome changes and pasted entities are skipped while previewing, and commands that
*remove* entities (`//butcher`, `//remove`) still act immediately. `//undo`/`//redo` always work on the real world.
At most `max-preview-blocks` (default 100,000) ghost blocks are shown at once; bigger previews can still be applied.

## Large edits without freezing the server

Like AsyncWorldEdit, big edits (50,000+ changed blocks by default) are written into the world a slice at a time,
using at most 20 ms of each server tick, so the server keeps running while a huge `//set` or paste goes in.
You get a message when it starts and finishes.

* The edit is still *calculated* all at once; what's spread out is placing the blocks, which is usually the slow part.
* Starting another edit in the same world first finishes any edits still being placed there, so nothing gets out of
  order. Stopping the server finishes them too.
* `//jobs` lists edits being placed, `//jobs cancel <id>` stops one (the part already placed can be `//undo`ne),
  and `//jobs finish` places the rest immediately.
* Works on NeoForge and Fabric. Settings in `worldedit.properties`: `time-sliced-edits`, `time-sliced-edit-threshold`,
  `time-sliced-edit-tick-budget-ms`, `max-preview-blocks`.

## Terrain painting and heightmaps

`//texture <rules> [-d depth] [-s noiseScale]` paints the top of the terrain in the selection by rules, picking the
first rule that matches each column. A rule is `pattern@condition&condition...`, or just `pattern` for "anything else".
Conditions: `y>N`, `y<N`, `slope>D`, `slope<D` (degrees), `noise>F`, `noise<F` (0-1), and `>=`/`<=` also work.

```
//texture snow_block@y>140,stone@slope>45,coarse_dirt@noise>0.7&slope>25,grass_block
```

`//heightmap export <file> [-c] [-f]` saves the selection's terrain heights as a 16-bit grayscale PNG
(`-c`: a colour map of the top blocks instead; `-f`: overwrite). `//heightmap import <file> <pattern> [maxHeight] [-t topPattern] [-k]`
builds terrain from a PNG stretched to fit the selection: black is the bottom of the selection, white is `maxHeight`
blocks up (default: the top of the selection). `-t` sets the surface block, and `-k` keeps what's above the new surface
instead of clearing it. Files go in the `heightmaps` folder of WorldEdit's config directory.

## Landscaping

| Command | What it does |
|---|---|
| `//path <pattern> [width] [maxSlope] [-s stairs]` (alias `//road`) | Road that follows the terrain between your selection points (pos1 → pos2, or through the points of a polygon/convex selection). It finds a gentle route, grades it so each step changes by at most one block, tunnels through hills (3 blocks of headroom), and puts pillars under bridges. `-s` puts stairs on the steps. Width defaults to 3; use 2 or more. |
| `//river [width] [depth] [-l maxLength] [-b bed]` | Carves a river downhill from your position until it reaches water, `-l` blocks (default 256), or a dip, where it makes a pond. The water never flows uphill, and low banks get levees. The bed defaults to sand. |
| `//scatter <count> [minSpacing] [-k name] [-o yOffset] [-r seed] [-n] [-a]` | Places `count` randomly rotated copies of your clipboard (or named clipboard `-k`), spread out across the surface of the selection, e.g. rocks, ruins or bushes. `-n`: no rotation, `-a`: skip air, `-r`: repeatable result. The selection must reach down to the ground. |
| `//ptree <log> <leaves> [height] [branches] [leafRadius] [-l lean°] [-t 1\|2] [-r seed] [-d]` | A custom tree at your position with a leaning, curving trunk (`-t 2` for 2×2), branches ending in leaf clusters, and a crown. Leaves are made persistent unless you pass `-d`. |

## Building tools

| Command | What it does |
|---|---|
| `//retheme <from> <to> [-m mask]` | Swaps a whole material family in the selection and keeps stair facing, door hinges, sign text and so on. Examples: `//retheme oak spruce`, `//retheme oak crimson` (logs become stems, leaves become wart blocks), `//retheme cobblestone mossy_cobblestone`, `//retheme stone_brick deepslate_brick`. Blocks with no equivalent are left alone and listed. `oak` doesn't touch `dark_oak`/`pale_oak`. |
| `//symmetry [x\|z\|xz\|off] [-p]` (alias `//sym`) | Mirrors everything you build, with commands *and* brushes, across a plane through where you stand (`-p`: through the selection centre). `xz` mirrors 4 ways. Stairs, doors etc. are flipped correctly, and one `//undo` undoes both sides. It only applies in the world where you turned it on, and is forgotten on restart. (`//mirror` was already taken by `//flip`.) |
| `//lightfill [minLight] [-b block] [-s spacing]` | Makes the selection mob-proof: adds light sources (invisible `light` blocks by default, or e.g. `-b torch`) until every spot a mob could spawn on has at least `minLight` block light (default 1). Lower `-s` spreads lights more densely. The lighting is an approximation of the game's. |
| `//find <mask> [limit] [-r radius] [-c]` | Lists the nearest blocks matching the mask in the selection (or within `-r` blocks of you) plus the total. Click coordinates to set pos1, or `[tp]` to teleport. `-c` shows which chunks have the most matches. |

## New commands

| Command | What it does |
|---|---|
| `//helix <pattern> <radius>[,<radiusZ>] <height> [turns] [thickness] [-h]` | Spiral around your position. `//hhelix` is the hollow version. |
| `//torus <pattern> <major radius> <minor radius> [-h]` | Donut around your position. `//htorus` is the hollow version. |
| `//catenary <pattern> [sag] [thickness] [-h]` | Hanging rope/chain between pos1 and pos2 (or between convex-selection vertices). `sag 0` picks a sag automatically. |
| `//strata <p1,p2,...> [thickness] [dip°] [azimuth°] [waviness]` | Fills the selection with tilted rock layers. |
| `//erode [iterations]` | Thermal erosion: blocks on 2+ high drops slide down to the lowest neighbouring column. |
| `//weather [severity 0-1] [-m] [-c]` | Cracks stone/deepslate/blackstone bricks. `-m` adds moss, `-c` advances copper oxidation by one stage. |
| `//autostairs <stair pattern> [slab pattern] [-u]` | Turns step edges in the selection into correctly facing stairs (and slabs). `-u` handles ceilings. |
| `//roof [gable\|hip\|shed\|curved\|mansard] <pattern> [overhang] [pitch] [-h]` | Builds a roof over the selection footprint. |
| `//insetfaces [margin] <pattern> [depth]` | Recessed panels on the selection's six faces. |
| `//measure [x,y,z]` | Size, distances, angles, volume and surface area of the selection, or from pos1 to a point. |
| `//copy -k <name>`, `//paste -k <name>` | Copy into or paste from a named clipboard. |
| `//clipboards [list\|save\|load] [name]` | Manage named clipboards (they last for the current session only). |

## New patterns

* `#gradient[axis][palette]` or `#gradient[axis,min,max][palette]`: dithered blend along `x`, `y` or `z`.
  Without bounds it uses your current selection. Example: `//set #gradient[y][stone,andesite,cobblestone]`
* `#voronoi[scale][palette]`: cellular, crazy-paving-style pattern. Example: `//set #voronoi[4][stone,andesite,tuff]`

## New masks

* `#slope`, `#slope[min,max]`: surface blocks whose terrain slope is within the angle range in degrees. The default is 35-90.
* `#adjacent[mask]`, `#adjacent[count,mask]`, `#adjacent[min,max,mask]`: blocks with the given number of touching
  neighbours matching `mask`. Example: `//replace #adjacent[water] sand`
* `#exposed[up|down|north|south|east|west|horizontal]`: like `#exposed` but only checks the given side(s).

## Permissions

On servers with a permissions plugin, the new nodes are:
`worldedit.generation.helix`, `worldedit.generation.torus`, `worldedit.region.catenary`, `worldedit.region.strata`,
`worldedit.region.erode`, `worldedit.region.weather`, `worldedit.region.autostairs`, `worldedit.region.roof`,
`worldedit.region.insetfaces`, `worldedit.clipboard.list`, `worldedit.preview`, `worldedit.jobs`
(plus `worldedit.jobs.others` to cancel/finish other players' jobs), `worldedit.region.texture`,
`worldedit.heightmap.import`, `worldedit.heightmap.export`, `worldedit.generation.path`, `worldedit.generation.river`,
`worldedit.generation.scatter`, `worldedit.generation.ptree`, `worldedit.region.retheme`, `worldedit.symmetry`,
`worldedit.region.lightfill`, `worldedit.find`. `//measure` uses `worldedit.selection.size`.
In single-player, and for operators, everything is allowed.
