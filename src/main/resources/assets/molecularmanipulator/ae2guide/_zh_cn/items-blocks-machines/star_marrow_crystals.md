---
navigation:
  parent: omnisequence-index.md
  title: "星髓晶体、晶核与晶种"
  icon: molecularmanipulator:star_marrow_crystal
  position: 1210
item_ids:
- molecularmanipulator:primordial_crystal_core
- molecularmanipulator:star_marrow_seed
- molecularmanipulator:star_marrow_crystal
- molecularmanipulator:star_marrow_crystal_block
- molecularmanipulator:star_marrow_crystal_dust
- molecularmanipulator:star_marrow_alloy_ingot
- molecularmanipulator:star_chart_circuit_board
- molecularmanipulator:star_chart_processor
- molecularmanipulator:star_marrow_crystal_bud_small
- molecularmanipulator:star_marrow_crystal_bud_medium
- molecularmanipulator:star_marrow_crystal_bud_large
- molecularmanipulator:star_marrow_crystal_cluster
- molecularmanipulator:heavily_damaged_star_marrow_parent_rock
- molecularmanipulator:damaged_star_marrow_parent_rock
- molecularmanipulator:flawed_star_marrow_parent_rock
- molecularmanipulator:flawless_star_marrow_parent_rock
---

# 星髓晶体、晶核与晶种

在星髓母岩上种出星髓晶体，再用物质构筑井加工成星图电路板、星图处理器和星髓合金锭。

<Row>
<ItemImage id="molecularmanipulator:primordial_crystal_core" scale="5" />

<ItemImage id="molecularmanipulator:star_marrow_seed" scale="5" />

<BlockImage id="molecularmanipulator:flawed_star_marrow_parent_rock" scale="4" />

<ItemImage id="molecularmanipulator:star_marrow_crystal" scale="5" />
</Row>

## 准备材料

- 用充能赛特斯石英水晶、天空石粉、紫水晶碎片、雪球和末影珍珠粉各一个，合成 **2 个原初晶核**。
- 找到 ExtendedAE 的恩特罗化福鲁伊克斯母岩。恩特罗化越轻，转化需要的晶种越少。
- 带上镐，并准备向末地的**双生奇点观测环**提供 **16 AE/t** 或等效 FE。观测环的供电台不需要频道。

<RecipeFor id="molecularmanipulator:primordial_crystal_core" />

## 1. 把晶核变成晶种

去末地主岛南侧 **X 0、Z 224** 附近的小型遗迹，按顺序记下三枚坐标铭文。同一个世界的符号不会变。把末影珍珠放进折跃台插槽，空手右键三个坐标按键，分别调到对应的符号。

站在折跃台上，空手右键台面中心。首次传送要等目的地加载，走下台可取消。

新实验室的折跃台在 Y 189，比旧版高 24 格。已经生成的实验室和返程台留在原位。

1. 保持观测环完整，给**供电台**接入 AE 或 FE。
2. 手持原初晶核右键**输入托架**，放入 **1～64 个晶核**。
3. 空手右键输入托架或中央供电台，开始加工整叠晶核。**输出托架**必须能放下整批晶种。
4. 加工需要 **160 tick** 的供电时间，正常速度下为 **8 秒**。空手右键输出托架取走晶种。每个晶核产出一个晶种，一整叠与一个耗时相同。每批都要手动启动。

按住 Shift 空手右键输入托架，可取回还没开工的晶核。缺电或输出托架放不下产物时，任务暂停并保留。托架没有界面，要手动存取，不能用漏斗或 ME 网络搬运。

## 2. 转化母岩，采集晶体

手持**星髓晶种**右键母岩。每次消耗一个晶种，推进一级。

**完全 → 多半 → 半 → 几乎未恩特罗化福鲁伊克斯母岩 → 重损星髓母岩 → 破损 → 微瑕**

转化成星髓母岩后就能种植。晶种最多修到微瑕，修复已有星髓母岩无需 ExtendedAE。保持区块加载，给母岩留出空位，六个面都能长芽。小晶芽依次长成中晶芽、大晶芽和晶簇。

<Row>
<BlockImage id="molecularmanipulator:star_marrow_crystal_bud_small" scale="4" />

<BlockImage id="molecularmanipulator:star_marrow_crystal_bud_medium" scale="4" />

<BlockImage id="molecularmanipulator:star_marrow_crystal_bud_large" scale="4" />

<BlockImage id="molecularmanipulator:star_marrow_crystal_cluster" scale="4" />
</Row>

用镐采集成熟晶簇，留下母岩继续种植。晶簇掉落 **3 个晶体**，时运可增加掉落量，精准采集则会掉落晶簇本身。

每次长出新芽或晶芽长大一级时，微瑕、破损和重损母岩都有 **1/12** 的概率掉一级。微瑕变破损，破损变重损，重损变成无法继续长芽的**星髓晶体块**。无瑕母岩不会掉级。

在工作台九宫格放满 **9 个星髓晶体**，也能合成 1 个星髓晶体块。

<RecipeFor id="molecularmanipulator:star_marrow_crystal_block" />

## 3. 在井中加工星髓材料

将 **16 个星髓晶体**和研究页列出的其他材料存入 ME，完成[构筑井研究](matter_fabrication_research.md)中的**Ⅱ阶：星髓材料工程**。

以下五种配方都在物质构筑井里加工，基础耗时均为 **20 秒**，星算引擎可以加速。这些配方不需要星讯、无瑕母岩或星算研究。可用[接口](matter_fabrication_ports.md)或[处理样板](matter_fabrication_pattern_assembly.md)投料。

- 星图电路板不需要催化剂。用电路板、红石和硅板制作星图处理器。
- 在井中把星髓晶体加工成星髓晶粉。用晶粉加工星髓合金锭时要供水。
- 用星讯存储组件组装[星讯存储元件](star_signal_flow.md)。

<RecipeFor id="molecularmanipulator:star_chart_circuit_board" />

<RecipeFor id="molecularmanipulator:star_chart_processor" />

<RecipeFor id="molecularmanipulator:star_marrow_crystal_dust" />

<RecipeFor id="molecularmanipulator:star_marrow_alloy_ingot" />

<RecipeFor id="molecularmanipulator:star_signal_storage_component" />

## 用星讯和水制晶（可选）

用第一批星图处理器制作[星海解算主楼](star_sea_calculation_mainframe.md)，再接好[星讯存储](star_signal_flow.md)。用[模拟灯笼](simulation_lantern.md)进入模拟，亲自挖下微瑕星髓母岩，拿到作催化剂用的无瑕母岩。

1. 完成星髓材料工程后，研究**Ⅱ阶：星讯诱导结晶**。
2. 把**一块无瑕星髓母岩**放进这座井任意样板总成的“催化剂”页。母岩可以重复使用，批量加工也只需一块。
3. 在处理样板的原料中填写**星讯流 1024 bit 和水 8000 mB**，再把样板放进这座井的样板总成。母岩留在催化剂槽。
4. 从 ME 下单。每份产出 **64 个星髓晶体**，基础耗时 **20 秒**，耗电 **512 AE/t**。星讯、水和用电随加工份数增加。

<RecipeFor id="molecularmanipulator:star_marrow_crystal" />

## 常见问题

| 问题 | 处理方法 |
| --- | --- |
| 托架不收材料 | 输入托架只收原初晶核。星图电路板在井里做。 |
| 星讯配方一直等 | 检查井内研究、ME 中的星讯和水，以及“催化剂”页的无瑕母岩。 |
