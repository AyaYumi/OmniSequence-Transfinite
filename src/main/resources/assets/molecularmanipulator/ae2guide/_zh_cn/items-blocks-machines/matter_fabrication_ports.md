---
navigation:
  parent: items-blocks-machines/matter_fabrication_well.md
  title: "构筑井输入输出口"
  icon: molecularmanipulator:matter_fabrication_item_input
  position: 1
item_ids:
- molecularmanipulator:matter_fabrication_item_input
- molecularmanipulator:matter_fabrication_item_output
- molecularmanipulator:matter_fabrication_fluid_input
- molecularmanipulator:matter_fabrication_fluid_output
---

# 构筑井输入输出口

接口让你向[物质构筑井](matter_fabrication_well.md)投入原料、取出成品，既能手动操作，也能连接管道。

<Row>
<BlockImage id="molecularmanipulator:matter_fabrication_item_input" scale="4" />

<BlockImage id="molecularmanipulator:matter_fabrication_item_output" scale="4" />

<BlockImage id="molecularmanipulator:matter_fabrication_fluid_input" scale="4" />

<BlockImage id="molecularmanipulator:matter_fabrication_fluid_output" scale="4" />
</Row>

从左到右：物品输入口、物品输出口、流体输入口、流体输出口。

## 先准备什么

四种接口都可以直接合成，无需研究。准备一座已成型的构筑井、有电的 ME 连接，以及一条已解锁的加工配方。

| 接口 | 放入或取出的内容 | 容量 |
| --- | --- | --- |
| 物品输入口 | 固体原料 | 16 槽 |
| 物品输出口 | 固体产物 | 16 槽 |
| 流体输入口 | 流体原料 | 4 个罐 |
| 流体输出口 | 流体产物 | 4 个罐 |

每罐容量为 2,147,483,647 mB。按配方的输入和产出种类装齐接口；例如会产出流体的配方，需要预留流体输出口。

## 一步步加工

1. 主手或副手拿着接口，将青色框标出的外壳换成接口。橙色框表示位置已被占用。
2. 装齐配方需要的输入口和输出口。接口与样板总成共用 44 个位置，必须替换对应外壳。
3. 将一份配方所需的完整用量放入物品和流体输入口，也可用管道投料。
4. 留出产物空间并持续供电，控制器会自动开始已解锁的匹配配方。每批完成后，输入数量减少，产物出现在对应输出口。
5. 手动取走产物，或用对应管道抽出物品和流体。

接口显示“控制器 AE 网络已连接”时，表示它已连上所属井。研究材料要存入 ME，消耗星讯流的配方则需要[样板总成](matter_fabrication_pattern_assembly.md)，物品口和流体口无法投送星讯。

### 手动转移流体

用鼠标拿起装液容器，右键流体罐即可注入；拿起空容器后右键可取出。每次处理一个容器，需要有足够的流体或接收空间。

### 自动输出

1. 在输出口旁放好能接收产物的容器。
2. 打开输出口，将“自动输出：关”切换为“自动输出：开”。
3. 打开“输出方向 · 可多选”，启用朝向容器的一面，也可同时选择多面。

方向图随接口朝向排列：中心是前方，右下是后方。悬停方向按钮可核对相邻方块。自动输出默认关闭；开启后，产物会送入所选方向的相邻容器，只传送对方能接收的部分。

## 退料与无线连接

右侧栏的“远程连接”设置的是所属井控制器的无线连接，接口共用该连接。操作见[构序无线枢纽设置](sequence_array_controller.md)。

不用的原料可在输入口点击“全部退回 AE”。ME 放不下的部分仍留在接口里，正常拆下时随方块保留。无线绑定属于控制器，会随控制器的正常恢复或建筑打包保留。

## 常见问题

| 问题 | 处理方法 |
| --- | --- |
| 接口显示未连接 | 检查构筑井成型和联网状态，确认接口替换的是高亮外壳位置。 |
| 原料一直不动 | 检查研究、原料种类与数量、供电，以及全部产物的接收空间。 |
| 自动输出没有动作 | 开启自动输出、选择方向，并确认邻居能接收这种物品或流体。 |
| 液体容器没反应 | 用鼠标拿起容器后右键流体罐，检查液体种类和剩余空间。 |
| 退回后还有材料 | 给对应 ME 存储腾出空间，并恢复网络连接。 |

## 接口配方

<RecipeFor id="molecularmanipulator:matter_fabrication_item_input" />

<RecipeFor id="molecularmanipulator:matter_fabrication_item_output" />

<RecipeFor id="molecularmanipulator:matter_fabrication_fluid_input" />

<RecipeFor id="molecularmanipulator:matter_fabrication_fluid_output" />

## 接下来做什么

安装[样板总成](matter_fabrication_pattern_assembly.md)，从 ME 终端下单；或继续[研究](matter_fabrication_research.md)，解锁更多加工配方。
