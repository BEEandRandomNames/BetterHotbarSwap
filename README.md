# BetterHotbarSwap

BetterHotbarSwap is a Fabric 1.20.1 client-side inventory quality-of-life mod.

The first development target is a server-friendly client-only mode: the mod does not require a server installation and expresses its behavior through vanilla slot clicks whenever possible. Optional enhanced server support can be added later without replacing the core planning logic.

## Current Scope

- Fabric 1.20.1
- Client-side hotbar key detection using Minecraft's remappable hotbar keybinds
- Safe vanilla click planning for hotbar assignment
- Crafting/output quick-swap detection with conservative fallbacks
- Hotbar Priority behavior for displaced hotbar items
- Shift + Hotbar Key + Left Click container pickup
- JSON config and an in-game settings keybind

## Safety Rules

- If the current screen, slot, cursor, or inventory state is ambiguous, the mod does nothing.
- The mod avoids direct inventory mutation in client-only mode.
- The mod prefers vanilla slot click actions so modded containers keep their own server-side behavior.
