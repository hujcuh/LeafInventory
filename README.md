
# LeafInventory
A Minecraft server plugin to streamline inventory management (Spigot / Paper). [2](https://www.geeksforgeeks.org/git/pushing-changes-to-a-git-repository/)

> **Fork / Rebrand Notice**
>
> LeafInventory is a rebranded fork derived from **percyqaz/UltimateInventory**.  
> Original project: https://github.com/percyqaz/UltimateInventory [2](https://www.geeksforgeeks.org/git/pushing-changes-to-a-git-repository/)[1](https://docs.github.com/articles/about-forks)  
> This fork keeps the original MIT license and credits. [4](https://opensource.org/license/MIT)[5](https://en.wikipedia.org/wiki/MIT_License)

---

## Features

### Open ender chests, shulker boxes and crafting tables by right-clicking **in the inventory**
!demo1

### Open ender chests, shulker boxes and crafting tables by right-clicking **in your hand**
!demo2

> [!NOTE]
> Bonus! If you are running **Paper**, you can also open anvils, stonecutters, grindstones, looms, smithing tables and cartography tables the same way as crafting tables. [6](https://docs.github.com/en/migrations/importing-source-code/using-the-command-line-to-import-source-code/adding-locally-hosted-code-to-github)[2](https://www.geeksforgeeks.org/git/pushing-changes-to-a-git-repository/)

---

## Installation

1. Download the latest jar from **Releases** (recommended). [7](https://docs.vultr.com/how-to-push-changes-in-git)  
2. Put the jar into your server's `plugins/` folder.
3. Restart the server (avoid `/reload` for safety).

---

## Compatibility / Server Versions

- Designed for modern Spigot/Paper-based servers.
- **Paper users** get additional menu-opening features (see note above). [6](https://docs.github.com/en/migrations/importing-source-code/using-the-command-line-to-import-source-code/adding-locally-hosted-code-to-github)[2](https://www.geeksforgeeks.org/git/pushing-changes-to-a-git-repository/)  
- This fork may target newer Paper APIs; check the Release notes for the minimum supported version. [3](https://www.spigotmc.org/wiki/plugin-yml/)[8](https://docs.papermc.io/paper/dev/plugin-yml/)

---

## Configuration & Permissions (optional)

This fork includes a config file and permission nodes (if enabled).  
See `plugin.yml` for registered permissions and default values. [3](https://www.spigotmc.org/wiki/plugin-yml/)[8](https://docs.papermc.io/paper/dev/plugin-yml/)

---

## ⚠️ Caution: Duplication / Inventory Safety

**Caution: while every care has been taken to remove duplication bugs, some may remain.** [2](https://www.geeksforgeeks.org/git/pushing-changes-to-a-git-repository/)  

Please consider your plugins carefully before installing. I can only reasonably guarantee safety when this plugin is not installed alongside other plugins that heavily manipulate inventories.

Things to avoid in other plugins:
- Plugins that rearrange a player's inventory or ender chest outside of normal click/drag interactions
- Plugins that let one player change another's inventory/ender chest while they're online (e.g. `/invsee`-like features) [6](https://docs.github.com/en/migrations/importing-source-code/using-the-command-line-to-import-source-code/adding-locally-hosted-code-to-github)[2](https://www.geeksforgeeks.org/git/pushing-changes-to-a-git-repository/)

### Compatibility list

> [!WARNING]
> If you use plugins other than the ones listed here, think carefully whether they may cause duplication issues.

- Compatible with **ChestSort** (original upstream note). [7](https://docs.vultr.com/how-to-push-changes-in-git)[6](https://docs.github.com/en/migrations/importing-source-code/using-the-command-line-to-import-source-code/adding-locally-hosted-code-to-github)  
- Should be compatible with shulker-box stacking plugins (original upstream note). [6](https://docs.github.com/en/migrations/importing-source-code/using-the-command-line-to-import-source-code/adding-locally-hosted-code-to-github)[7](https://docs.vultr.com/how-to-push-changes-in-git)  

---

## Known issues
- Doesn't work in the creative inventory (original upstream note). [6](https://docs.github.com/en/migrations/importing-source-code/using-the-command-line-to-import-source-code/adding-locally-hosted-code-to-github)[2](https://www.geeksforgeeks.org/git/pushing-changes-to-a-git-repository/)  
- You can put your ender chest into your ender chest and lock it in there by mistake. Try not to do that :) [6](https://docs.github.com/en/migrations/importing-source-code/using-the-command-line-to-import-source-code/adding-locally-hosted-code-to-github)[2](https://www.geeksforgeeks.org/git/pushing-changes-to-a-git-repository/)  

---

## Issues / Support

- Report bugs and feature requests here: **(your repo)**
  - https://github.com/hujcuh/LeafInventory/issues

(If you want to also reference upstream behavior for comparison, link to upstream repo/issues separately.) [1](https://docs.github.com/articles/about-forks)[2](https://www.geeksforgeeks.org/git/pushing-changes-to-a-git-repository/)

---

## License

MIT License. This project is a fork; please keep the copyright notice and license text when redistributing. [4](https://opensource.org/license/MIT)[5](https://en.wikipedia.org/wiki/MIT_License)
