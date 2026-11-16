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
`worldedit.region.insetfaces`, `worldedit.clipboard.list`. `//measure` uses `worldedit.selection.size`.
In single-player, and for operators, everything is allowed.
