# Halo Client (Fabric 1.21.11)

Black-and-white glass HUD and click GUI. **Open the menu with Right Shift.**

## Modules
- Combat: TriggerBot, AimHelper, ShieldStatus, Hit SFX
- Visual: Xray (presets + per-ore dropdowns), Fog, Fullbright, Zoom (hold C), Knives (3D butterfly knife, any sword), Hats (13 hats), Atmosphere
- Movement: ToggleSprint

Every module has its own settings and keybind (click the Keybind button, press a key; Backspace or right-click clears it).
Settings are saved to `config/halo.json`.


## Settings UI
Choice settings are dropdowns, multi-choice settings are dropdowns with checkboxes, colors fold open on click.
Mouse wheel scrolls inside a long dropdown list (e.g. Hit SFX).

## Knives
Replaces the sword models with a 3D crimson/black butterfly knife. Pick which swords in the **Swords** dropdown
(wooden, stone, copper, iron, golden, diamond, netherite). Each sword is its own tiny resource pack, so toggling reloads resources.
If the blade points the wrong way in your hand, change `ANGLE` in the model generator from -45 to 45 (or tell me).

## Not included
Glossy Rain and real shaders: those need custom shader/render-pipeline work that I could not do safely for 1.21.11.
Atmosphere is a sky + horizon tint with presets, not a shader. For wet/glossy rain use Iris with a shader pack.

## Build
Needs JDK 21. Run `gradle build` (Gradle 9.3+) or push to GitHub (the workflow builds the jar).
Put the jar and Fabric API 0.141.3+1.21.11 in your mods folder.

## If something does not build or work
Written against the 1.21.11 Yarn docs, not compiled in my sandbox.
- Hit SFX plays when an entity you were just aiming at gets hurt right after you swing. If it never plays, tell me.
- Build error inside `HatFeature.java`: set `enable_hats=false` in `gradle.properties`.
- Build error inside `HudStyle.java`: set `enable_hud_style=false` in `gradle.properties`.
- Zoom / Xray / Fog / Atmosphere / ShieldStatus do nothing: they are mixins; check `logs/latest.log` for "Mixin" lines.
- Knives toggling reloads resource packs (short freeze is normal).
