# Schematica_survival (English README)

## Overview
`Schematica_survival` is a survival-focused Schematica extension for the MITE/FishModLoader environment.
It provides schematic loading, moving, rotation, mirroring, paste/undo, selection save/create, and an interactive `schematica_printer` block.

Current project info:
- Mod name: `Schematica_survival`
- Mod id: `schematica_survival`
- Version: `0.1.0`
- Entrypoint: `com.github.lunatrius.schematica.SchematicaSurvival`

## Main Features
- Survival command set: `/schematica ...`
- Printer block with recipe
- Printer GUI:
  - Rescan schematic files
  - Load projection (outline)
  - Print / Undo
  - Rotate and mirror
  - Material supply panel (one button per material)
  - Draggable scroll bar (mouse wheel and Up/Down supported)
- Printer-stored materials:
  - Printing consumes the printer inventory
  - No direct player-inventory consumption in printer print flow
  - Incremental supply via `printer provide`
- Safe print behavior:
  - If a target position is non-air and differs from the projected block, printing is blocked and the coordinate is returned
  - If the world block already matches the projected block, that position is skipped (no placement, no material cost)

## Quick Start
1. Build/compile: `./gradlew.bat compileJava`
2. Put `.schematic` files into `run/schematics/`
3. Craft and place the printer block
4. Open printer GUI:
   - Select a schematic and load the outline first
   - Provide required materials from the lower panel
   - Start printing

Printer recipe:
- `S S S`
- `S R S`
- `S S S`
- `S = ingotIron`, `R = Redstone`

## Common Commands
- `/schematica help`
- `/schematica list`
- `/schematica load <name>`
- `/schematica unload`
- `/schematica status`
- `/schematica origin here`
- `/schematica move <x> <y> <z>`
- `/schematica nudge <dx> <dy> <dz>`
- `/schematica rotate <90|180|270>`
- `/schematica mirror <x|z>`
- `/schematica paste [replace|solid|nonair]`
- `/schematica undo`
- `/schematica save <x1> <y1> <z1> <x2> <y2> <z2> <name>`
- `/schematica create <name>`
- `/schematica sel status`
- `/schematica sel clear`
- `/schematica printer print <x> <y> <z> [replace|solid|nonair]`
- `/schematica printer provide <x> <y> <z> <itemId> <subtype> [count]`

## Localization
- Language files:
  - `src/main/resources/assets/minecraft/lang/en_US.lang`
  - `src/main/resources/assets/minecraft/lang/zh_CN.lang`
- GUI and command messages are managed through language keys.

## Notes
- Survival paste consumes materials.
- The printer GUI currently sends print requests using `replace` mode by default.
- For detailed history, see `CHANGELOG.md`.
