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

星讯存储元件用来保存主楼生产的**星讯流**，让 ME 自动合成能将它用于晶体增殖等配方。

<Row>
<ItemImage id="molecularmanipulator:star_signal_storage_component" scale="5" />

<ItemImage id="molecularmanipulator:star_signal_storage_cell" scale="5" />

<ItemImage id="molecularmanipulator:creative_star_signal_disk" scale="5" />
</Row>

从左到右：用来做元件的组件、生存用存储元件、仅创造模式使用的磁盘。

星讯按 **bit** 计量，在 ME 中单独显示，不是能放进背包的物品，也不是能用桶装走的流体。

## 先准备什么

- 在[构筑井研究](matter_fabrication_research.md)中完成**Ⅱ阶：星髓材料工程**。
- 沿[星髓材料路线](star_marrow_crystals.md)准备晶体、晶粉、合金和处理器。
- 准备已供电的 ME 网络，以及 ME 驱动或 ME 箱子。
- 准备[星海解算主楼](star_sea_calculation_mainframe.md)来生产第一批星讯。制造组件和元件本身**不需要星讯**。

## 1. 在井中制作组件

用构筑井加工：**星髓晶体 ×32 + 红石 ×32 + 星图处理器 ×8 → 星讯存储组件 ×8**。基础耗时 **20 秒、256 AE/t**，可用接口或匹配的处理样板。

<RecipeFor id="molecularmanipulator:star_signal_storage_component" />

## 2. 组装并安装存储元件

1. 在工作台按下方有序配方摆放：**聚能石英玻璃 ×2、星髓晶粉 ×3、组件 ×1、星髓合金锭 ×2、星图处理器 ×1**。
2. 组件放在正中央；最下面一行是**合金锭—处理器—合金锭**。
3. 将得到的**星讯存储元件**插入 ME 驱动或 ME 箱子，与主楼接入**同一个 ME 网络**。

元件支持一种资源，容量为 **256 MiB**。普通物品或流体存储元件不能代替它。

<RecipeFor id="molecularmanipulator:star_signal_storage_cell" />

## 3. 生产并使用星讯

1. 给主楼接线、供电，等待夜晚，并保持塔顶光照**不高于 7**；具体接线口和环境条件见[主楼说明](star_sea_calculation_mainframe.md)。
2. 打开 ME 终端，寻找**星讯流**。主楼直接把产物送入网络存储，默认每个有效工作 tick **产出 128 bit、消耗 1024 AE**。
3. 构筑井配方需要星讯时，将它和其他消耗原料一起编码为处理样板，再放进[样板总成](matter_fabrication_pattern_assembly.md)。

不必先把星讯取成物品；合成 CPU 会为配方送入存好的星讯。

## 成功时看哪里

ME 终端里出现以 bit 计量的**星讯流**，主楼解算时数量持续增加。下单加工后，配方会连同其他原料一起扣除这些星讯。

### 终端隐藏了星讯怎么办？

1. 打开终端左侧工具栏的**配置可见类型**。
2. 启用**星讯流**。
3. 清除可能隐藏该条目的搜索或显示筛选。

## 不工作时先查这些

| 问题 | 处理方法 |
| --- | --- |
| 元件合成不出来 | 按图摆放有序配方，注意最下行中央的处理器；组件本身要在井里做。 |
| 主楼提示星讯存储不足或已满 | 在同一个 ME 网络中安装星讯存储元件，并检查剩余空间。 |
| 元件一直是空的 | 空手右键主楼，检查夜晚、塔顶光照、频道和电力。 |
| 已存入但终端看不到 | 按上面的方法启用星讯流可见类型。 |
| 配方收不到星讯 | 使用样板总成和处理样板，不要用物品或流体输入口。 |

## 创造磁盘

**创造星讯磁盘**仅创造模式提供无限星讯。插入 ME 驱动后，终端显示约 **2.1G**，这不是有限库存上限。它与生存元件使用同一种可见资源类型。

## 接下来做什么

设置[星海解算主楼](star_sea_calculation_mainframe.md)，再通过[样板总成](matter_fabrication_pattern_assembly.md)尝试可选的[星讯诱导结晶](star_marrow_crystals.md)。
