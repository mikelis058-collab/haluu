# Halo Client (Fabric 1.21.11)

Black-and-white glass HUD and click GUI. **Open the menu with Right Shift** (press it again, or Esc, to close).

## Modules
- Combat: TriggerBot, AimAssist, ShieldStatus, HitSfx
- Visual: Xray, Fog, Mist, Atmosphere, Fullbright, Zoom (hold C), Knives, Hats, Wings, Auras, Fire, Drugs, Block Outliner, Impact, GhostBlock
- HUD: Interface, HUD Style, Crosshairs, CPS, FPS, Coordinates, Keystrokes
- Movement: ToggleSprint, BoatFly
- Server: FakePotion

Every module has its own settings and a keybind: click the Keybind button and press a key; a small "x" button
next to it (once a key is bound) or right-click clears it. Settings save to `config/halo.json`.

**BoatFly** needs a one-time confirmation each session ("Are you sure the server has no AC?") before its
switch or keybind will turn it on, since it can get you kicked on servers that check vehicle movement.

## What's approximate, not real
A few things here are built as overlays/tricks rather than true engine features, because the real version
(a shader, a packet-level item spoof, true dupe exploits) is out of reach from this environment or, for item
duplication, something I chose not to build: dupe exploits hit server economies and other players who never
agreed to that, which is a different kind of harm than a visual or combat module that only affects your own
game. So Duper is not in this build. (Ghost Block turned out to just mean a client-only fake block, not duplication, so that one is built.)
- **Drugs**: a plain screen-color tint, not a real saturation/distortion shader.
- **Fire**: recolors the screen tint while burning; it tries to cancel the vanilla orange overlay first,
  but if that hook doesn't match your version you may see both layered.
- **FakePotion**: a throw animation + sound with no real item used, not a true spoofed item.
- **Atmosphere → Void Sky**: a solid black sky, not a rendered End dome.
- **Block Outliner**: drawn with colored particles along the block's edges, not a true wireframe.
- **Knives**: the blade texture and hand pose are my best redraw and best-guess rotation from your photo;
  the exact "sideways" angle may need a small tweak — in each resourcepacks/knives_*/assets/halo/models/item/knife.json,
  adjust the third number in each "rotation" array.
- Minecraft has no copper sword, so Knives covers wood, stone, iron, gold, diamond and netherite only.

## Build
Needs JDK 21. Run `gradle build` (Gradle 9.3+, Loom 1.15) or push to GitHub (the included workflow builds the jar).
Put the jar and Fabric API 0.141.3+1.21.11 in your mods folder.

## If something does not build or work
Written against the 1.21.11 Yarn docs, not compiled in my sandbox — if the build fails, send me the error log.
- Build error inside `HatFeature.java`: set `enable_hats=false` in `gradle.properties`.
- Build error inside `WingFeature.java`: set `enable_wings=false` in `gradle.properties`.
- Build error inside `HudStyle.java`: set `enable_hud_style=false` in `gradle.properties`.
- Zoom / Xray / Fog / Mist / Atmosphere / ShieldStatus / Fire / HitSfx do nothing: they rely on mixins into
  Minecraft's own code; check `logs/latest.log` for "Mixin" lines.
- Knives toggling reloads resource packs (a short freeze is normal).
