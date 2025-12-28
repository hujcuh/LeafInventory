
# LeafInventory
A Minecraft server plugin to streamline inventory management (Spigot / Paper). [4](https://blog.csdn.net/2503_91515815/article/details/150431805)[5](https://zhuanlan.zhihu.com/p/676396123)

> **Fork / Rebrand Notice**
>
> LeafInventory is a rebranded fork derived from **percyqaz/UltimateInventory**. [5](https://zhuanlan.zhihu.com/p/676396123)[8](https://mvnrepository.com/artifact/io.papermc)  
> Upstream (original) repository: https://github.com/percyqaz/UltimateInventory [5](https://zhuanlan.zhihu.com/p/676396123)[8](https://mvnrepository.com/artifact/io.papermc)  
> This fork preserves the original MIT license and credits as required by the MIT license terms. [6](https://git.moe.team/OpenSource/PaperMC)[7](https://cuteoao-my.sharepoint.com/personal/leaf_cuteoao_onmicrosoft_com/Documents/Microsoft%20Copilot%20Chat%20Files/LeafInventory.java)

---

## Features

### Open ender chests, shulker boxes and crafting tables by right-clicking **in the inventory**
!demo1

### Open ender chests, shulker boxes and crafting tables by right-clicking **in your hand**
!demo2

> [!NOTE]
> Bonus! If you are running **Paper**, you can also open anvils, stonecutters, grindstones, looms, smithing tables and cartography tables the same way as crafting tables. [5](https://zhuanlan.zhihu.com/p/676396123)[11](https://docs.suanlix.cn/github.html)

---

## Requirements / Compatibility

- **Java 21+** recommended/required for modern Paper (1.21+). [12](https://en.wikipedia.org/wiki/MIT_License)[13](https://docs.github.com/en/pull-requests/collaborating-with-pull-requests/proposing-changes-to-your-work-with-pull-requests/creating-a-pull-request-from-a-fork)  
- Targeted at **1.21.x** servers; this fork is currently built against **Paper API 1.21.10** (see `pom.xml`). [1](https://cuteoao-my.sharepoint.com/personal/leaf_cuteoao_onmicrosoft_com/Documents/Microsoft%20Copilot%20Chat%20Files/pom.xml)  
- `plugin.yml` entrypoint: `me.LeafPixel.LeafInventory.LeafInventory`. [2](https://cuteoao-my.sharepoint.com/personal/leaf_cuteoao_onmicrosoft_com/Documents/Microsoft%20Copilot%20Chat%20Files/plugin.yml)

> Tip: `api-version` controls what server versions will load the plugin; from 1.20.5 onward, minor versions can be specified. [3](https://docs.papermc.io/paper/dev/menu-type-api/)[4](https://blog.csdn.net/2503_91515815/article/details/150431805)

---

## Installation

1. Download the latest jar from **Releases**.
2. Put the jar into your server's `plugins/` folder.
3. Restart the server (avoid `/reload` for safety).

---

## Permissions

The plugin registers the following permission nodes (all default to `false`): [2](https://cuteoao-my.sharepoint.com/personal/leaf_cuteoao_onmicrosoft_com/Documents/Microsoft%20Copilot%20Chat%20Files/plugin.yml)

- `leafinventory.shulkerbox`
- `leafinventory.enderchest`
- `leafinventory.craftingtable`
- `leafinventory.smithingtable`
- `leafinventory.stonecutter`
- `leafinventory.grindstone`
- `leafinventory.cartographytable`
- `leafinventory.loom`
- `leafinventory.anvil`
- `leafinventory.enchantingtable`

---

## ⚠️ Caution: Duplication / Inventory Safety

**Caution: while every care has been taken to remove duplication bugs, some may remain.** [5](https://zhuanlan.zhihu.com/p/676396123)  

Please consider your plugins carefully before installing. Duplication risks increase when combined with other inventory-manipulating plugins.

Things to avoid in other plugins:
- Plugins that rearrange inventories/ender chests outside of normal click/drag interactions
- Plugins that let one player change another player's inventory/ender chest while they're online (e.g. `/invsee`-like features) [5](https://zhuanlan.zhihu.com/p/676396123)[11](https://docs.suanlix.cn/github.html)

---

## Known issues
- Doesn't work in the creative inventory. [5](https://zhuanlan.zhihu.com/p/676396123)[11](https://docs.suanlix.cn/github.html)  
- You can put your ender chest into your ender chest and lock it in there by mistake. Try not to do that :) [5](https://zhuanlan.zhihu.com/p/676396123)[11](https://docs.suanlix.cn/github.html)  

---

## Issues / Support
Report bugs and feature requests here:
- https://github.com/hujcuh/LeafInventory/issues

---

## License
MIT License. When redistributing copies or substantial portions, keep the copyright notice and license text. [6](https://git.moe.team/OpenSource/PaperMC)[7](https://cuteoao-my.sharepoint.com/personal/leaf_cuteoao_onmicrosoft_com/Documents/Microsoft%20Copilot%20Chat%20Files/LeafInventory.java)
