# BetterRTP

Lightweight, safe random teleportation for **Paper / Purpur 26.2** (Minecraft Java 26.2 only, **Java 25** required).
`/rtp` opens a three-button menu (Overworld, Nether, End): a chest GUI for Java players and a native
**Floodgate/Cumulus SimpleForm** for Bedrock players. No economy, no database, no network access.

## Installation
1. Run Paper or Purpur 26.2 on Java 25.
2. Drop `BetterRTP-1.0.0.jar` into `plugins/` and start the server.
3. Optional: install Geyser + Floodgate for the native Bedrock menu (soft dependency, never bundled).
4. Edit `plugins/BetterRTP/config.yml`, then `/rtp reload`.

## Commands
| Command | Permission | Description |
|---|---|---|
| `/rtp` (alias `/brtp`) | `betterrtp.use` | Opens the menu (or teleports to the Overworld if `open-on-rtp: false`) |
| `/rtp biome <biome>` | `betterrtp.biome` | RTP to a safe spot in a biome (current world if enabled, else the Overworld) |
| `/rtp world <world>` | `betterrtp.world` | RTP inside a specific enabled world |
| `/rtp reload` | `betterrtp.reload` | Reload config (console allowed) |
| `/rtp help` | - | Command list |
| `/rtpgui` | `betterrtp.use` | Opens the menu; can be disabled with `rtpgui-command-enabled: false` |

Other permissions: `betterrtp.bypass.cooldown`, `betterrtp.admin` (grants everything). Player-only commands reject the console politely.

## How it works
* **Worlds are resolved by Environment**: Overworld = `world:` setting (else first enabled NORMAL world in `worlds:`),
  Nether = `<overworld>_nether` / `world_nether` / first enabled NETHER world, End likewise. An unavailable destination keeps its button, shown as unavailable.
* **Area**: SQUARE only, centre +/- radius (default 3000, centre = per-world `center` or the WorldBorder centre), intersected with the live WorldBorder minus a 2-block footprint margin.
* **Search** (bounded, sequential): one random X/Z at a time -> async chunk load (`getChunkAtAsync`, optionally without generating) -> safety checks on the main thread -> `teleportAsync`.
  Limits: `safe-location-attempts`, `teleport-max-search-seconds`, per-chunk timeout, global concurrent searches, a bounded queue and a global candidates-per-second cap.
* **Safety** (`min-y`/`max-y` are the *standing* Y, floor + 1): solid, reasonably tall floor; feet/head (+clearance) free; no water/lava/fire/cactus/magma/campfires/powder snow/berry bush/wither rose/cobweb/portals (built-in `HazardCatalog`) and nothing from `blacklisted-blocks`; hazards in the 8 neighbouring columns; WorldBorder; Y limits.
  * Overworld/End: heightmap surface (leaves ignored by default) + ceiling scan so caves/overhangs are rejected; void columns rejected.
  * Nether: bounded downward scan inside `min-y..max-y`, required open height above the floor and at least 3 of 4 open sides (no tunnels/pockets); lava rejected.
  * Biome RTP samples the 3D biome at the standing position and still applies every rule.
* **Countdown** (default 5 s) after the GUI click: actionbar / title / bossbar, sound, particles. Moving, damage, world change, teleport by something else, death and disconnect cancel it. No cooldown is consumed unless the teleport succeeds.
* **State machine**: `COUNTDOWN -> (QUEUED) -> SEARCHING -> TELEPORTING -> COMPLETED`, or `CANCELLED/FAILED`. Every callback re-checks state, so a late chunk/teleport callback can never teleport a cancelled request.

## Performance philosophy
Idle cost is ~zero: no repeating task without an active request (the 1 s countdown ticker and the `PlayerMoveEvent` listener exist only while a request is active), no database, no HTTP, no NMS, no reflection, no stored `Chunk`/`Location` objects.
The optional location cache (off by default) stores packed X/Z longs only, refills at most one entry per world per interval, never when nobody is online or searches are running, and every entry is fully re-validated on use.

## Geyser / Floodgate
Floodgate is a soft dependency. All Floodgate/Cumulus references live in `gui.floodgate.FloodgateBedrockSupport`, created only when Floodgate is enabled and wrapped in a `Throwable` guard,
so Java-only servers never see a `ClassNotFoundException`. Button text, title, content and optional images (`PATH`/`URL`, invalid image config falls back to a plain button) are configurable.
If `bedrock-gui.enabled` is false or the form cannot be sent, Bedrock players get the Java inventory menu.

## Configuration
Everything (messages, GUI texts, Bedrock texts, effects, safety, performance) is in `config.yml` with comments. Text uses MiniMessage. Invalid values print one warning and fall back to safe defaults.

## Known limitations
* SQUARE only. Folia is not supported (Bukkit scheduler is used).
* Because the countdown runs before the search, a very slow chunk generation can add up to `teleport-max-search-seconds` of waiting, during which moving still cancels.
* In the End most of a 3000-block square is void; the 50 default attempts may fail often. Raise `safe-location-attempts` or lower `radius` for the End.
* Bedrock button `enabled: false` keeps the button (always exactly three) but answers "unavailable".
* Particles that need extra data (dust, items, ...) are not supported.
* `Chunk` loading uses Paper's async API; very busy servers may see the "busy" message instead of long queues (by design).

## Build
```
./gradlew clean build        # needs JDK 25; produces build/libs/BetterRTP-1.0.0.jar
```
If `gradle/wrapper/gradle-wrapper.jar` is missing, run `gradle wrapper --gradle-version 9.1.0` once.
