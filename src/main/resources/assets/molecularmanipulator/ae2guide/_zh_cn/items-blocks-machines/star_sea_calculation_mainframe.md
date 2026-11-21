---
navigation:
  parent: omnisequence-index.md
  title: "星海解算主楼"
  icon: molecularmanipulator:star_sea_calculation_mainframe
  position: 1215
item_ids:
- molecularmanipulator:star_sea_calculation_mainframe
---

# 星海解算主楼

星海解算主楼在夜晚生产**星讯流**，直接送入 ME 存储。用一个建筑物品就能放下整座主楼，占地 **5 × 5 格，高 9 格**。

<Row>
<ItemImage id="molecularmanipulator:star_sea_calculation_mainframe" scale="4" />

<ItemImage id="molecularmanipulator:star_signal_storage_cell" scale="4" />

<ItemImage id="ae2:fluix_glass_cable" scale="4" />
</Row>

要给机器加速，见[九环星算天体仪](star_computation_engine.md)。

## 先准备什么

- 通过[星髓材料研究与井内加工](star_marrow_crystals.md)做出**星图处理器**，再按下方配方制作主楼。
- 准备**平整、坚固的 5 × 5 地基**，上方留出 **9 格净空**。
- 准备已供电的 ME 网络、**一个空闲频道**，以及有剩余容量的[星讯存储元件](star_signal_flow.md)。网络要能支付每个工作 tick 的 **1024 AE**。
- 第一次推荐在主世界搭建。主楼通过 ME 取电。

<RecipeFor id="molecularmanipulator:star_sea_calculation_mainframe" />

## 一步步放置和接线

1. 手持建筑物品，对准地基前侧中央。主楼正面朝向你，楼身向后延伸。虚影变为青绿色时右键放置。
2. 将 ME 电缆接到主楼正面底层中央的白色方口。电缆只能从端口正面接入。
3. 将星讯存储元件插入同一网络的 ME 驱动或 ME 箱子。检查下文的夜晚和塔顶光照条件。主楼消耗 ME 电能来生产星讯。
4. 空手右键主楼，查看状态、产量和耗能。显示“正在解算”时，可以在 ME 终端看到星讯流库存增长。

虚影一直是红色时，清理整片建筑空间，检查全部地基、世界边界、高度限制和领地放置权限。

## 夜晚与塔顶光照

普通维度需要有天空光和正常的昼夜循环。等到一天内第 **13000～22999 tick** 才能生产。

屋顶正上方一层的全部 **5 × 5 格**都要光照**不高于 7**，方块光和夜间天空光都算。只要有一格达到 8，生产就会暂停。可以移远附近的灯，也可以给屋顶加遮挡。

### 离讯之境

在**离讯之境**，只要塔顶够暗，ME 有电力、频道和存储空间，主楼就能工作。这里的默认产量是普通维度的 **8 倍**，每个工作 tick 的耗电相同。维度时间在半速和双速之间交替，现实每秒的产量和耗电也会跟着变。

## 产量与耗能

| 默认工作地点 | 每个工作 tick 产量 | 每个工作 tick 耗能 |
| --- | --- | --- |
| 满足夜间条件的普通维度 | 128 bit | 1024 AE |
| 离讯之境 | 1024 bit | 1024 AE |

维度通常每秒运行 20 tick。整合包调整参数时，以主楼状态显示的产量和耗能为准。主楼没有待机耗电。

## 不工作时先查这些

| 状态或问题 | 处理方法 |
| --- | --- |
| 星讯存储不足或已满 | 在同一网络安装星讯存储元件，或给现有元件腾出空间。 |
| 能量不足 | 增加 ME 供电，达到状态显示的耗能。 |
| 建筑不完整或区块未加载 | 保持整栋建筑已加载，并检查是否缺少部位。 |
| 星讯数量看不到 | 在终端的**配置可见类型**中启用**星讯流**。 |

## 搬走主楼

用**铁镐或更好的镐**挖掉任意有实体的部位，会拆除整楼，返还一个建筑物品，地基保留。创造模式拆除不掉落。

## 接下来做什么

查看[星讯存储](star_signal_flow.md)，学会保存和找到产物；再用[样板总成](matter_fabrication_pattern_assembly.md)进行可选的[星讯制晶](star_marrow_crystals.md)。
