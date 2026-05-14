# LeafInventory

LeafInventory is a Minecraft server plugin for Paper/Folia that provides portable inventory utilities and workstation access.

It is a rebranded fork derived from [percyqaz/UltimateInventory](https://github.com/percyqaz/UltimateInventory).  
This fork preserves the original MIT license and credits as required by the MIT license terms.

---

## Compatibility

LeafInventory is maintained in separate version lines.

| LeafInventory version | Server target | Java | Status | Branch |
| --- | --- | --- | --- | --- |
| 3.x | Paper/Folia 1.21.4 - 1.21.11 | Java 21+ | Maintenance | `legacy/1.21.x` |
| 4.x | Paper/Folia 26.1.x | Java 25+ | Active development | `main` |

### Which version should I use?

- Use **LeafInventory 3.x** if your server runs Paper/Folia 1.21.4 - 1.21.11.
- Use **LeafInventory 4.x** if your server runs Paper/Folia 26.1.x.

---

## Features

### Portable containers and workstations

LeafInventory allows players to open supported containers and workstations directly from their inventory or hand, without placing the block.

Supported features include:

- Shulker boxes
- Ender chests
- Crafting tables
- Smithing tables
- Stonecutters
- Grindstones
- Cartography tables
- Looms
- Anvils
- Enchanting tables
- Furnaces
- Blast furnaces
- Smokers

Most features are configurable and can be permission-gated.

---

## 4.x Roadmap

LeafInventory 4.x targets Paper/Folia 26.1.x and focuses on safer Folia-compatible internals.

Planned 4.x features:

- Virtual workstation backend by default
- 54-slot large ender chest
  - Keeps vanilla ender chest compatibility
  - Uses a virtual GUI
  - First 27 slots may mirror the vanilla ender chest
  - Extra 27 slots are plugin-managed
- 54-slot large shulker box
  - Data is bound to a persistent `shulkerId`
  - The shulker item carries access through PersistentDataContainer
  - Players without create permission may still open existing large shulker boxes, depending on config
  - Placement lifecycle will be handled carefully
  - Hopper interaction will be blocked initially for safety

---

## Permissions

### General permissions

```text
leafinventory.shulkerbox
leafinventory.enderchest
leafinventory.craftingtable
leafinventory.smithingtable
leafinventory.stonecutter
leafinventory.grindstone
leafinventory.cartographytable
leafinventory.loom
leafinventory.anvil
leafinventory.enchantingtable
leafinventory.furnace
leafinventory.blastfurnace
leafinventory.smoker
leafinventory.workstation.bypass
