# LeafInventory

LeafInventory is a Paper/Folia plugin that provides portable inventory utilities, virtual workstation access, a 54-slot large ender chest, and 54-slot large shulker boxes.

This project is a rebranded fork derived from [percyqaz/UltimateInventory](https://github.com/percyqaz/UltimateInventory).  
The original MIT license and credits are preserved.

---

## Compatibility

| LeafInventory version | Server target | Java | Status | Branch |
|---|---|---|---|---|
| 3.x | Paper/Folia 1.21.4 - 1.21.11 | Java 21+ | Maintenance | `legacy/1.21.x` |
| 4.x | Paper/Folia 26.1.x | Java 25+ | Beta / active development | `main` |

Use **LeafInventory 3.x** for Paper/Folia 1.21.4 - 1.21.11.  
Use **LeafInventory 4.x** for Paper/Folia 26.1.x.

---

## Features

### Portable containers and menus

Supported portable features:

- Regular shulker boxes
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

Most features can be enabled or disabled in `config.yml` and can be permission-gated.

---

## 54-slot Large Ender Chest

- LeafInventory 4.x adds a virtual 54-slot ender chest.

---

## 54-slot Large Shulker Box

- LeafInventory 4.x adds plugin-managed 54-slot large shulker boxes.
- **Shift+Right-Click (empty hand on normal shulker):** Creates a bound 54-slot Large Shulker Box.

---

## Commands

Main command:

```text
/leafinventory
/li
```

Admin commands:

```text
/li status
/li save
/li largeshulker info
/li largeshulker listplaced
/li largeshulker unlock <shulkerId>
/li largeender info [player]
```

Permission:

```text
leafinventory.admin
```

---

## Permissions

General permissions:

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
```

Large storage and admin permissions:

```text
leafinventory.enderchest.large
leafinventory.shulkerbox.large.create
leafinventory.shulkerbox.large.open
leafinventory.admin
```

Wildcard:

```text
leafinventory.*
```

---

## Known Limitations

- Large shulker hopper automation is not supported yet.
- Large shulker contents are plugin-managed and should not be treated as vanilla shulker NBT.
- WorldEdit or other block-copying tools may create duplicate block shells; LeafInventory handles these conservatively.
- The high‑capacity shulker box cannot preview its contents.

