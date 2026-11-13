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

星髓母岩可以反复长出晶体。采下的晶体能在构筑井中加工成星图电路板、处理器和合金，用于制作后续设备。

<Row>
<ItemImage id="molecularmanipulator:primordial_crystal_core" scale="5" />

<ItemImage id="molecularmanipulator:star_marrow_seed" scale="5" />

<BlockImage id="molecularmanipulator:flawed_star_marrow_parent_rock" scale="4" />

<ItemImage id="molecularmanipulator:star_marrow_crystal" scale="5" />
</Row>

从左到右是原初晶核、星髓晶种、微瑕母岩和晶体。先把晶核加工成晶种，用晶种转化母岩，再种植、采集晶体。

## 先准备什么

- 合成原初晶核：充能赛特斯石英水晶、天空石粉、紫水晶碎片、雪球、末影珍珠粉各一个，每次产出 2 个晶核。
- 找到 AE2 的赛特斯石英母岩，四种品质都能用。首次转化消耗一个晶种；想一路修到微瑕，需要备齐三个。
- 带上镐，并准备向末地的双生奇点观测环提供 **16 AE/t** 或等效 FE。观测环的供电台不需要频道。

<RecipeFor id="molecularmanipulator:primordial_crystal_core" />

## 1. 把晶核变成晶种

从末地主岛南侧、约 X 0、Z 224 的小型遗迹前往观测环。将末影珍珠放入折跃台的插槽，空手右键三个坐标按键切换符号，使它们与遗迹铭文一致。然后站上台中央，空手右键中央确认传送。

抵达后，找到观测环的供电台、输入托架和输出托架。

1. 向供电台接入 AE 或 FE，并保持观测环完整。
2. 手持原初晶核右键输入托架，放入 1～64 个晶核。
3. 空手右键输入托架，开始加工整叠晶核。输出托架必须能一次放下整批晶种。
4. 默认供电下等待 8 秒。每个晶核变成一个晶种，做一整叠和做一个的时间相同。
5. 空手右键输出托架取走晶种。每批都要手动开始，做完不会自动启动下一批。

Shift+空手右键输入托架可取回尚未开始加工的晶核。缺电或输出空间不足时，整批加工会暂停并保留进度。托架直接右键操作，没有界面，也不能接漏斗或 ME 物品自动化。

## 2. 转化母岩，采集晶体

手持星髓晶种右键母岩，每次消耗一个晶种，沿下列路线推进一步：

任意品质的赛特斯石英母岩 → 重损星髓母岩 → 破损星髓母岩 → 微瑕星髓母岩

首次转化得到重损母岩就能开始种植。从赛特斯石英母岩修到微瑕共需三个晶种；**晶种最多修到微瑕**，无瑕母岩需要用后文的模拟灯笼取得。

1. 给母岩的面留出空间，六个面都能长芽。
2. 等待晶芽按下方四个阶段生长，并保持所在区域已加载。
3. 用镐只采集**成熟晶簇**，保留母岩继续生产。

<Row>
<BlockImage id="molecularmanipulator:star_marrow_crystal_bud_small" scale="4" />

<BlockImage id="molecularmanipulator:star_marrow_crystal_bud_medium" scale="4" />

<BlockImage id="molecularmanipulator:star_marrow_crystal_bud_large" scale="4" />

<BlockImage id="molecularmanipulator:star_marrow_crystal_cluster" scale="4" />
</Row>

小晶芽依次长成中晶芽、大晶芽，最后成熟为晶簇。成熟晶簇掉落 3 个晶体，时运可增加数量；精准采集得到晶簇方块本身。持续种植需要保留母岩。

破损、微瑕母岩会在生长过程中有机会掉一级。重损不会继续下降，无瑕永不掉级。

## 3. 在井中加工星髓材料

先用自然生长的晶体完成[构筑井研究](matter_fabrication_research.md)中的Ⅱ阶：星髓材料工程。它需要 16 个晶体，以及研究页列出的其他材料；研究材料都要存入 ME。

完成后，下列五种产物都可由物质构筑井加工，使用[接口](matter_fabrication_ports.md)或[处理样板](matter_fabrication_pattern_assembly.md)投料。未受星算引擎加速时，基础耗时均为 20 秒。这些配方不需要星讯、无瑕母岩或星算研究许可。

- 先做星图电路板，再用电路板、红石和硅板制作星图处理器。电路板无需催化剂，处理器用的是硅板，不能用散装硅代替。
- 将晶体加工成星髓晶粉，再做星髓合金锭；合金配方还需要水。
- 做星讯存储组件，用于组装[星讯存储元件](star_signal_flow.md)。

<RecipeFor id="molecularmanipulator:star_chart_circuit_board" />

<RecipeFor id="molecularmanipulator:star_chart_processor" />

<RecipeFor id="molecularmanipulator:star_marrow_crystal_dust" />

<RecipeFor id="molecularmanipulator:star_marrow_alloy_ingot" />

<RecipeFor id="molecularmanipulator:star_signal_storage_component" />

## 可选：用星讯和水生产晶体

自然种植足够完成起步。想改为机器生产，先加工[星海解算主楼](star_sea_calculation_mainframe.md)、[星讯存储](star_signal_flow.md)和[模拟灯笼](simulation_lantern.md)所需材料。

### 取得第一块无瑕母岩

1. 用晶种将母岩修到微瑕。
2. 手持点燃的模拟灯笼，按[灯笼操作说明](simulation_lantern.md)进入模拟。推荐副手拿灯、主手拿镐。
3. 在模拟中亲自挖下微瑕母岩，得到永久的无瑕母岩。可以放置种植，也可以保留作催化剂。

这一操作不耗灯笼燃料、AE 或额外晶种。挖下前，世界中的母岩仍是微瑕，生长时还可能掉级；若已掉级，挖下后只会得到对应的较低品质母岩。自动挖掘无法获得模拟中的无瑕母岩。

### 设置星讯诱导结晶

1. 完成星髓材料工程之后，研究Ⅱ阶：星讯诱导结晶。
2. 把一块无瑕星髓母岩放进同井任意总成的“催化剂”页。它不消耗、不降级，批量加工也只需一块。
3. 按下方配方编码处理样板，填写星讯流 1024 bit 和水 8000 mB。母岩留在催化剂槽，不要写进消耗材料。
4. 在 ME 中请求晶体。每份得到 **64 个晶体**，基础耗时 20 秒、耗电 512 AE/t，不消耗现成晶体。

星讯必须通过样板总成供应，物品和流体接口无法投送。星讯、水和用电随加工份数增加。

<RecipeFor id="molecularmanipulator:star_marrow_crystal" />

## 常见问题

| 问题 | 处理方法 |
| --- | --- |
| 拿晶核点母岩没反应 | 先在观测环把晶核转成**晶种**。 |
| 托架不收材料或一直等 | 它只收晶核；检查观测环供电，以及能否放下整批晶种。电路板要在井里做。 |
| 晶种不再生效 | 确认目标是 AE2 的赛特斯石英母岩，或重损／破损星髓母岩。修到**微瑕**就停止；无瑕走灯笼路线。 |
| 一直没有晶体 | 清空母岩表面，保持区域加载，不要提前挖掉未成熟的芽。 |
| 星讯配方一直等 | 检查研究、ME 中的星讯与水，以及本井催化剂槽中的无瑕母岩。加工完成后，晶体送回 ME，母岩仍留在槽中。 |

## 接下来做什么

用第一批处理器制作[星海解算主楼](star_sea_calculation_mainframe.md)，再装好[星讯存储](star_signal_flow.md)；灯笼的模拟操作见[模拟灯笼](simulation_lantern.md)。
