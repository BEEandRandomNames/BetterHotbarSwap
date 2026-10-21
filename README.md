# BetterHotbarSwap

Makes Minecraft's hotkey assignment system more functional and smarter.

A client-side Fabric mod. It needs no server install: everything is done with vanilla slot clicks, so it also works on multiplayer servers and with modded containers.

## Features

- **Send crafting results to any hotbar slot with a hotkey, even an occupied one.** Hover the result of any crafting block (crafting table, furnace, stonecutter, modded ones too) and press a hotbar key. The old item is not lost; it stays in your hotbar.
- **Hotbar Priority:** a displaced item first stacks with the same item in your hotbar, otherwise moves to the nearest empty hotbar slot.
- **Assigned Crafting:** Shift + hotkey on a crafting result takes everything you can craft at once.
- **Tidy Collecting:** in chests, hold Shift + a hotkey and Shift-click items; they fill from that slot to the right, never to the left.
- **Saved toolbars in every game mode:** save and load your whole layout (hotbar, inventory, offhand). Items are only moved, never created.
- **Offhand support**, mouse-button hotkeys, 11 languages.

## Controls

| Key | Action |
|---|---|
| Hotbar keys (1-9) | Assign the hovered item to that slot |
| Shift + hotbar key | Assigned Crafting / Tidy Collecting |
| N + hotbar key | Save a toolbar |
| M + hotbar key | Load a toolbar |
| H | Open the toolbar list |

All keys can be changed in Controls. Settings are available through Mod Menu or a bindable key.

## Supported versions

Minecraft 1.20.1 and 1.20.2 with Fabric Loader and Fabric API. Ports to newer versions are in progress; each version lives on its own `mc/<version>` branch.

## Building

```
./gradlew build
```

The jar is written to `build/libs/`.

## License

[MIT](LICENSE)
