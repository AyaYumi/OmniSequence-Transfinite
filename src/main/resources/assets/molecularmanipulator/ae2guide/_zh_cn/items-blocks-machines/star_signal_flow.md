---
navigation:
  parent: omnisequence-index.md
  title: "星讯存储"
  icon: molecularmanipulator:star_signal_storage_cell
  position: 1220
item_ids:
- molecularmanipulator:star_signal_storage_component
- molecularmanipulator:star_signal_storage_cell
- molecularmanipulator:creative_star_signal_disk
---

# 星讯存储

星讯存储元件用来保存[星海解算主楼](star_sea_calculation_mainframe.md)生产的星讯流，供晶体增殖等自动合成配方取用。

<Row>
<ItemImage id="molecularmanipulator:star_signal_storage_component" scale="5" />

<ItemImage id="molecularmanipulator:star_signal_storage_cell" scale="5" />

<ItemImage id="molecularmanipulator:creative_star_signal_disk" scale="5" />
</Row>

从左到右：用来做元件的组件、生存用存储元件、仅创造模式使用的磁盘。

星讯按 bit 计量，在 ME 中单独显示，只能通过支持星讯的设备存取，无法取成背包物品或用桶装走。

## 先准备什么

- 在[构筑井研究](matter_fabrication_research.md)中完成Ⅱ阶：星髓材料工程。
- 沿[星髓材料路线](star_marrow_crystals.md)准备晶体、晶粉、合金和处理器。
- 准备已供电的 ME 网络，以及 ME 驱动或 ME 箱子。
- 准备[星海解算主楼](star_sea_calculation_mainframe.md)生产第一批星讯。制造组件和元件本身无需星讯。

## 1. 在井中制作组件

在构筑井投入星髓晶体 ×32、红石 ×32、星图处理器 ×8，每份产出星讯存储组件 ×8。基础耗时 20 秒，耗电 256 AE/t，可用接口或匹配的处理样板投料。

<RecipeFor id="molecularmanipulator:star_signal_storage_component" />

## 2. 组装并安装存储元件

1. 在工作台按下方配方摆放聚能石英玻璃 ×2、星髓晶粉 ×3、组件 ×1、星髓合金锭 ×2、星图处理器 ×1。
2. 组件放在正中央，最下面一行依次是合金锭、处理器、合金锭，摆放顺序必须与配方一致。
3. 将得到的星讯存储元件插入 ME 驱动或 ME 箱子，与主楼接入同一个 ME 网络。

元件可存一种资源，容量为 256 MiB。普通物品或流体存储元件无法存放星讯。

<RecipeFor id="molecularmanipulator:star_signal_storage_cell" />

## 3. 生产并使用星讯

1. 给主楼接线、供电，等待夜晚，并保持塔顶光照不高于 7。接线口和环境条件见[主楼说明](star_sea_calculation_mainframe.md)。
2. 打开 ME 终端查看星讯流库存。主楼直接把产物送入网络存储，默认每个有效工作 tick 产出 128 bit、消耗 1024 AE。
3. 构筑井配方需要星讯时，将它和其他消耗原料一起编码为处理样板，再放进[样板总成](matter_fabrication_pattern_assembly.md)。合成 CPU 会投送存好的星讯，配方运行时扣除相应数量。

终端中的星讯以 bit 显示。若找不到这个条目，先检查可见类型设置：

1. 打开终端左侧工具栏的**配置可见类型**。
2. 启用**星讯流**。
3. 清除可能隐藏该条目的搜索或显示筛选。

## 常见问题

| 问题 | 处理方法 |
| --- | --- |
| 元件合成不出来 | 按图摆放有序配方，注意最下行中央的处理器；组件本身要在井里做。 |
| 主楼提示星讯存储不足或已满 | 在同一个 ME 网络中安装星讯存储元件，并检查剩余空间。 |
| 元件一直是空的 | 空手右键主楼，检查夜晚、塔顶光照、频道和电力。 |
| 已存入但终端看不到 | 按上面的方法启用星讯流可见类型。 |
| 配方收不到星讯 | 使用样板总成和处理样板，不要用物品或流体输入口。 |

## 创造磁盘

创造星讯磁盘仅供创造模式使用，可提供无限星讯。插入 ME 驱动后，终端显示约 2.1G，使用时不会耗尽。它与生存元件使用同一种可见资源类型。

## 接下来做什么

设置[星海解算主楼](star_sea_calculation_mainframe.md)，再通过[样板总成](matter_fabrication_pattern_assembly.md)尝试可选的[星讯诱导结晶](star_marrow_crystals.md)。
