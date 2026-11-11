# 物质构筑井：配方与研究 API

**面向整合包作者与模组开发者。** 这里说明怎样给井添加加工配方、用研究开放配方，
以及导入其他机器的受支持工艺。只想搭井和使用机器，请先看[玩家指南](../README.zh-CN.md)，
或在游戏内对控制器按 **G**；下面的字段与 Java 接口是作者参考。

自 OmniSequence: Transfinite **2.0.0** 起提供，当前对应 **21.3.0.0**。
目标环境：Minecraft **1.21.1** / NeoForge、Java **21**、AE2 **19.2.18**，以及必需前置
Applied Enhancements **1.1.2+**（编译 API **1.1.4**）。模组 ID 仍为 `molecularmanipulator`。

其他语言：[English](matter-research-api.md)。
另见[接口索引](README.md)与独立的[批量供应器 API v1](omni-batch-provider-api.zh-CN.md)。

本文档覆盖构筑井的三类配方：

| 配方类型 | 作用 |
| --- | --- |
| `molecularmanipulator:matter_fabrication` | 物质构筑井能加工什么。 |
| `molecularmanipulator:matter_research` | 解锁这些配方并提供生产加成的研究。 |
| `molecularmanipulator:matter_machine_import` | 声明整台机器的配方导入、解析规则与研究分支。 |

三者都是 `data/<namespace>/recipe/` 下的普通数据包配方。KubeJS 使用
`ServerEvents.recipes` 与 `event.custom` 添加或替换，不需要额外适配插件。研究进度属于
每个构筑井控制器，不属于玩家，也不属于全局网络。

---

## 1. 快速上手：用研究锁住自定义构筑井配方

安装 KubeJS 后，将下面的完整脚本保存到 `kubejs/server_scripts/well_research.js`，
执行 `/reload`。它添加圆石加工成石头的工艺与对应研究。在成型、接网且有电的井里，
先完成 AE 基础，再完成这项新研究即可加工；本例自行定义了三轮研究，不使用默认九轮表。

```javascript
ServerEvents.recipes(event => {
  // 1) 解锁自定义配方的研究。
  event.custom({
    type: 'molecularmanipulator:matter_research',
    title: '石材深度研究',
    stage: 3,
    sort_order: 100,
    prerequisites: ['molecularmanipulator:research/ae_foundation'],
    ingredients: [{ingredient: {item: 'ae2:fluix_crystal'}, count: '32'}],
    duration: 1200,
    ae_per_tick: 128,
    unlocks: ['kubejs:stone_processing'],
    depths: [
      {parallel: '4'},
      {material_multiplier: '3', parallel: '1024'},
      {
        ingredients: [
          {ingredient: {item: 'ae2:fluix_crystal'}, count: '512'},
          {ingredient: {item: 'minecraft:diamond'}, count: '64'}
        ],
        parallel: '9223372036854775807'
      }
    ]
  }).id('kubejs:stone_research');

  // 2) 构筑井配方本身。
  event.custom({
    type: 'molecularmanipulator:matter_fabrication',
    ingredients: [{ingredient: {item: 'minecraft:cobblestone'}, count: 1}],
    results: [{id: 'minecraft:stone', count: 1}],
    processing_time: 200,
    ae_per_tick: 64,
    requires_research: true
  }).id('kubejs:stone_processing');
});
```

`requires_research: true` 表示该配方锁定，直到 `unlocks` 中列出它的**任意一个**研究完成
至少 1 次——此处即 `kubejs:stone_research`。

### 1.1 把配方锁在更高的研究等级之后

配方权限是二元的：完成 1 次即开放，配方本身没有等级字段。若要要求特定深度——例如
**万物演算 9/9**——应新增一个“门槛研究”，把等级要求写在它的前置上，再由它解锁配方，
而不是由内置研究直接解锁：

```json
{
  "type": "molecularmanipulator:matter_research",
  "title": "kubejs.research.late_stone",
  "prerequisites": ["molecularmanipulator:research/omni_computation"],
  "prerequisite_levels": {
    "molecularmanipulator:research/omni_computation": 9
  },
  "ingredients": [{"ingredient": {"item": "ae2:fluix_crystal"}, "count": "64"}],
  "duration": 2400,
  "ae_per_tick": 512,
  "stage": 3,
  "sort_order": 200,
  "unlocks": ["kubejs:stone_processing"]
}
```

这样 `kubejs:stone_processing` 必须先完成一次门槛研究才能加工，而门槛研究本身要等
万物演算完成 9 次后才能开始。把 `9` 换成字符串 `"max"`，即要求该前置当前的最高次数。

注意：

- 这里的“次”指累计完成次数，不是研究上仅用于显示的 `stage` 阶段编号。
- 数值门槛不会被自动调低。若超过前置研究的 `depths` 长度，门槛研究将永远无法开始，
  配置时应确保在范围内。
- `prerequisite_levels` 的键会自动并入 `prerequisites`，因此只写映射也能建立依赖；
  映射中未列出的已有前置默认要求 1 次。

---

## 1.2 整机导入与解析规则：`molecularmanipulator:matter_machine_import`

在 `kubejs/server_scripts` 中使用 `ServerEvents.recipes` 添加声明，不需要额外的 KubeJS 插件。
下面的例子导入熔炉配方，并为它们创建独立研究分支：

```javascript
ServerEvents.recipes(event => {
  event.custom({
    type: 'molecularmanipulator:matter_machine_import',
    machine: 'minecraft:furnace',
    serializers: ['minecraft:smelting'],
    inputs: {items: ['/ingredient']},
    outputs: {items: ['/result']},
    processing_time: 200,
    ae_per_tick: 256,
    research: {
      title: '熔炉研究',
      stage: 2,
      sort_order: 1100,
      prerequisites: ['molecularmanipulator:research/ae_foundation'],
      ingredients: [{ingredient: {item: 'minecraft:furnace'}, count: '1'}],
      duration: 600,
      ae_per_tick: 512
    }
  }).id('kubejs:furnace_import');
});
```

所有整机导入研究（含下例自定义声明）都会自动追加**构序工程≥3且演算架构≥3**，有效显示阶段至少为4；声明可增加更严前置，不能降低这个最低条件。

该声明的研究 ID 为 `molecularmanipulator:research/machine/kubejs/furnace_import`，导入配方 ID
位于 `molecularmanipulator:fabrication/import/machine/kubejs/furnace_import/`。
声明 ID 应保持稳定；修改它会产生另一个研究 ID，原控制器的研究完成次数不会自动迁移。

| 声明字段 | 含义 |
| --- | --- |
| `machine` | 必需，机器物品 ID；物品不存在时不导入。机器可由 KubeJS 注册。 |
| `serializers` | 必需、非空，对应源配方 JSON 的 `type`，即配方**序列化器 ID**，不是 JEI 分类 ID。 |
| `research` | 必需，使用现有 `matter_research` 的全部字段，包括材料、前置、`depths`、`required_mods`。导入配方自动加入其 `unlocks`；额外填写的 `unlocks` 也保留。 |
| `inputs` / `outputs` | 可选，分别替换输入或输出的自动解析。省略时复用现有解析器；提供时必须覆盖该方向的全部资源。 |
| `processing_time` | 导入配方的基础耗时，默认 400 tick（20 秒）；显式自定义值仍会保留。 |
| `ae_per_tick` | 导入配方的基础功耗，默认 256 AE/t；与 `research.ae_per_tick` 分开配置。 |

### 字段路径

`inputs` 和 `outputs` 使用相同的配置格式：

| 映射字段 | 内容 |
| --- | --- |
| `items` | 物品字段路径列表。输入接受 `{item: ...}`、`{tag: ...}` 或 `{ingredient: ..., count: ...}`；输出接受 `{id: ..., count: ..., components: ...}`，也接受 `item` 代替 `id`。 |
| `fluids` | 流体字段路径列表，格式 `{id: ..., amount: ..., components: ...}`，也接受 `fluid` 代替 `id`；数量单位 mB。 |
| `resources` | 已序列化 AE 堆栈的字段路径列表，接受含 `#t` 与 `#` 的 `GenericStack`，或 `{key_type: ..., resource: ..., amount: ...}`。 |
| `ae_keys` | 从普通配方字段构造已注册 AE 资源的规则列表，见下例。 |

路径遵循 JSON Pointer：`/inputs/items` 读取嵌套字段，`/inputs/0` 读取数组第一项，
空字符串读取整个配方；字段名中的 `/` 写成 `~1`、`~` 写成 `~0`。路径必须对应源配方
经其序列化器重新编码后的 JSON；某些序列化器会省略默认值，原始 KubeJS JSON 与编码结果可能不同。
字段值为数组时逐项读取；物品输入的可替换原料数组应放进 `ingredient`，例如
`{ingredient: [{item: 'minecraft:iron_ingot'}, {item: 'minecraft:gold_ingot'}], count: 2}`。
显式映射字段必须可读取、数组必须非空，数量必须是正整数。某项无法解析时整条配方跳过，
日志会列出对应配方 ID，避免只保留部分原料或产物。

### 闪电等特殊 AE 资源

下面的规则将 `lightningTier` 和 `lightningCost` 映射为闪电科技原有的 AE 闪电类型。
这是一个完整声明；它覆盖内置闪电组装室导入规则，研究会由此声明生成：

```javascript
ServerEvents.recipes(event => {
  event.custom({
    type: 'molecularmanipulator:matter_machine_import',
    machine: 'ae2lt:lightning_assembly_chamber',
    serializers: ['ae2lt:lightning_assembly'],
    inputs: {
      items: ['/inputs'],
      ae_keys: [{
        key_type: 'ae2lt:lightning',
        amount_path: '/lightningCost',
        amount_default: 4,
        fields: {tier: 'high_voltage'},
        field_paths: {tier: '/lightningTier'}
      }]
    },
    outputs: {items: ['/result']},
    research: {
      title: '闪电组装研究',
      stage: 2,
      required_mods: ['ae2lt'],
      prerequisites: ['molecularmanipulator:research/ae_foundation'],
      ingredients: [{ingredient: {item: 'ae2lt:lightning_assembly_chamber'}, count: '1'}],
      duration: 600,
      ae_per_tick: 512
    }
  }).id('kubejs:lightning_assembly_import');
});
```

`key_type` 必须是模组已经注册的 AEKey 类型。`amount_path` 读取数量；字段缺失时使用
`amount_default`，默认 0，必须填写有效默认值或提供有效来源数量。`fields` 提供 JSON 常量（可含嵌套对象），
`field_paths` 把源字段的 JSON 值复制到同名 AEKey 字段；来源字段缺失时使用
`fields` 中的同名默认值，没有默认值则跳过配方。`#t` 和 `#` 由系统生成，不能通过这两张表覆盖。
极高压配方读取到 `extreme_high_voltage` 后保留该类型；不会生成包裹通用堆栈。

如资源 ID 写在普通字段中，也可用 `ae_keys` 构造数据资源：设置
`key_type: 'data_energistics:digitalization'`，`fields: {resource: 'data_energistics:data_flow'}`，
以及对应的 `amount_path`。规则只解析已存在的 AE 类型；新的 AEKey 类型及 JEI 原料类型
仍由提供资源的模组注册。没有原生 JEI 转换器时，构筑井不会用包裹物品伪造身份。

### 重载与覆盖

- 每次配方重载或客户端收到配方同步后，按当前生效配方重建导入与研究，包含 KubeJS 的修改。
- 同一序列化器只分配给一个导入声明；自定义声明覆盖内置配置。多个自定义声明重复指定时，
  按声明 ID 字典序排序，后者优先，建议避免重叠。
- 机器缺失、`research.required_mods` 不满足或没有成功导入的配方时，不显示对应研究。
- 同一机器、同一完整产物及数量、同一原料及数量的配方合并；不同原料分支保留并在研究列表中统计。
- 当前每条构筑井配方最多 12 个物品/AE 输入，另加一个流体输入；最多 6 个物品输出、6 个 AE 输出，
  另加一个流体输出。额外流体转为 AE 流体堆栈并占用 AE 槽位，超限配方跳过。
- 燃料、概率产物、催化剂不消耗、温度等机器行为不会由字段规则自动模拟；仅添加能准确表达为
  构筑井输入与确定产物的配方规则。

---

## 2. 研究定义：`molecularmanipulator:matter_research`

位置：`data/<namespace>/recipe/<path>.json`。

| 字段 | 含义 |
| --- | --- |
| `title` | 名称或翻译键。必填，不能为空字符串。 |
| `stage` | 非负显示阶数，默认 `1`；`0` 表示特殊研究，不加数字阶数前缀。列表顺序由 `sort_order` 决定，门槛由前置决定。 |
| `sort_order` | 导航顺序，默认 `0`。定义按 `sort_order` 排序，其次按 ID 排序。 |
| `prerequisites` | 前置研究 ID 列表，每项默认要求完成 1 次；自引用与循环无效。 |
| `prerequisite_levels` | 可选映射 `{研究 ID: 所需完成次数或 "max"}`，逐项覆盖门槛；其中的 ID 会自动加入前置列表。 |
| `ingredients` | 基础轮次耗材，必填。每项为 `{ingredient: {item: ...} 或 {tag: ...}, count: 正 long}`。仅支持物品类原料——研究不能收取流体或通用 AE 资源。 |
| `duration` | 每轮耗时（tick），至少 `1`，默认 `1200`。 |
| `ae_per_tick` | 研究期间功耗，有限非负，默认 `256`。 |
| `required_mods` | 必须全部加载后该定义才会显示并运行。 |
| `unlocks` | 本研究发现并加成的完整**配方 ID**（不是物品 ID）。 |
| `depths` | 每轮费用与完成后总加成。数组长度即研究总次数；省略时使用 §3 的默认九次表。 |

### 2.1 `depths` 每一项

| 字段 | 含义 |
| --- | --- |
| `material_multiplier` | 本轮费用 = 基础 `ingredients` × 该值，默认 `1`。 |
| `ingredients` | 可选。提供时**完整替代**本轮材料并忽略倍率；`[]` 表示免费一轮。 |
| `parallel` | 完成后并行上限。必填正 long。 |
| `speed_multiplier` | 旧版正 long 字段，默认 `1`；仅保留解码兼容，不再生效。 |
| `processing_ticks` | 旧版非负 tick 字段，默认 `0`；仅保留解码兼容，包括 `1` 在内均不再生效。 |

### 2.2 long 数值与 `2^53 - 1`

所有**研究定义的** long 字段（`count`、`material_multiplier`、`parallel`、`speed_multiplier`）
都可写十进制字符串，推荐始终加引号。该规则不适用于构筑配方物品数量、通用堆栈的 `#` 或
星算研究的 `information_cost`；这些字段各有自己的数值格式。超过 JavaScript 精确整数上限 `2^53 - 1` 时**必须**使用字符串：
写 `'9223372036854775807'`，不能用浮点数或 `'9.22E18'`。系统不会替你检测精度损失，
写错的 JavaScript 数字只会静默变成错误的值。费用乘法超出 long 上限时拒绝开始，不会变成
负数或免费研究。

---

## 3. 默认九次进度

省略 `depths` 时使用。内置研究沿用该并行进度，但可用显式 `ingredients` 替换各轮材料费用（见 §6）。
每轮费用独立收取，不是从上一轮补差额。`parallel` 表示一次加工可执行多少份完整配方。

| 累计完成次数 | 本轮材料 / 基础材料 | 完成后的并行上限 |
| --- | --- | --- |
| 1 | ×1 | 1 |
| 2 | ×2 | 256 |
| 3 | ×4 | 65,536 |
| 4 | ×8 | 16,777,216 |
| 5 | ×16 | 4,294,967,296 |
| 6 | ×32 | 1,099,511,627,776 |
| 7 | ×64 | 281,474,976,710,656 |
| 8 | ×128 | 72,057,594,037,927,936 |
| 9 | ×256 | 9,223,372,036,854,775,807（约 9.22E18） |

深度研究仅提升所属配方并行，包括第 9 轮。多个研究解锁同一配方时取最高并行上限。
生产速度由控制器所在 ME 网络的星算引擎提供：普通等级采用
`max(1, ceil(配方基础ticks / 有效speedMultiplier))`，有效倍率已包含超频；L9 超频强制
1 tick。没有引擎时倍率为 1。所有内置构筑配方与桥接生成默认均为 400 tick（20 秒）；
显式自定义 `processing_time` 仍会保留。研究自身的 `duration` 独立于生产速度与此默认值。

没有归属研究且 `requires_research: false` 的基础配方仍然无需研究即可使用，但会继承一阶
基础研究 `molecularmanipulator:research/ae_foundation` 已完成深度的并行上限。尚未完成
该研究（或定义不存在）时，并行上限仍为 `1`。明确归属研究的配方只使用所属研究的并行
上限。两种情况下的生产速度都由网络中的星算引擎提供；该回退不会开放研究门控配方。

---

## 4. 构筑井配方：`molecularmanipulator:matter_fabrication`

| 字段 | 含义 |
| --- | --- |
| `ingredients` | 计数物品输入，每项 `{ingredient: {item: ...} 或 {tag: ...}, count: ...}`。`count` 为数值整数 `1..2147483647`，省略时为 `1`；列表默认空。不是研究费用使用的 long 字符串格式。 |
| `catalysts` | 可选催化剂条件，格式同 `ingredients`，默认空。由同一座成型构筑井所有样板总成的催化剂库存共同提供；合成不消耗，数量不乘以批量或研究并行。自动编码时不作为样板输入。 |
| `results` | 最多 **6** 种物品产物，严格格式 `{id: ..., count: ...}`。 |
| `fluid_input` | 可选流体输入 `{id: ..., amount: ...}`，单位 mB。不计入十二项输入上限。 |
| `fluid_result` | 可选单种流体产物，格式相同。 |
| `ae_inputs` | 最多 **12** 项通用 AEKey 输入，见 §4.1。 |
| `ae_outputs` | 最多 **6** 项通用 AEKey 输出，与 `ae_inputs` 使用相同的堆栈格式。 |
| `processing_time` | 基础 tick，默认 `400`（20 秒）；小于 `1` 会被提升为 `1`。 |
| `ae_per_tick` | 基础功耗，默认 `64.0`；负值会被提升为 `0`。 |
| `requires_research` | 默认 `false`，见 §4.2。 |

加载期错误条件：`ingredients` + `catalysts` + `ae_inputs` 合计不得超过 **12** 项；`ingredients`、
`fluid_input`、`ae_inputs` 至少要有一项；每项 `ae_inputs` / `ae_outputs` 数量必须为正；
且至少要有一种物品、流体或 AE 资源产物。与研究定义不同，格式错误的构筑井配方会直接加载失败。

### 4.1 通用输入（`ae_inputs`）

`ae_inputs` 接受所有已注册的 AEKey 类型，气体、化学品等第三方资源由此输入。每项使用 AE2
的 `GenericStack.CODEC`：`#t` 是已注册的 AEKey 类型 ID，`#` 是每份配方的原始数量。

| 字段 | 含义 |
| --- | --- |
| `#t` | AEKey 类型 ID。AE2 物品为 `ae2:i`，AE2 流体为 `ae2:f`；附属模组注册自己的 ID。 |
| `#` | 每份配方的原始数量，1 至 9223372036854775807。物品类型单位为个，NeoForge 流体单位为 mB。 |
| `id`、`components` 等 | 由所选类型解码；应使用该附属模组的 Codec，不要假设与物品/流体格式相同。 |

```json
{
  "type": "molecularmanipulator:matter_fabrication",
  "ingredients": [{"ingredient": {"item": "minecraft:diamond"}, "count": 2}],
  "ae_inputs": [
    {"#t": "ae2:i", "id": "minecraft:diamond", "#": 2},
    {"#t": "ae2:f", "id": "minecraft:water", "#": 1000}
  ],
  "fluid_input": {"id": "minecraft:water", "amount": 250},
  "results": [{"id": "minecraft:obsidian", "count": 1}],
  "processing_time": 200,
  "ae_per_tick": 64
}
```

`#` 使用 AE2 的数值 `Codec.LONG`，**不是**研究字段使用的十进制字符串格式，因此
`"3000000000"` 这种写法会被拒绝。需要超过 JavaScript 精确整数范围时，请在数据包 JSON 中写
整数字面量，或在 Java 中用 `new GenericStack(key, longAmount)` 构造，不要先经 JavaScript 数字
转换。

通用资源按完整 AEKey 精确匹配（含组件），而普通 `ingredients` 保持 Ingredient/tag 匹配。
重复或重叠的需求会累加——同一份库存不会重复抵扣两个不同需求。

含 `ae_inputs` 或 `ae_outputs` 的配方必须使用**样板总成**。手动物品与流体接口无法提供通用 AE 缓存，
会直接拒绝这类配方。

含通用输入的 Java 构造器：

```java
new MatterFabricationRecipe(ingredients, results, fluidInput, fluidResult,
        aeInputs, processingTime, aePerTick, requiresResearch); // aeInputs 为 List<GenericStack>
```

旧的 6、7、8 参数构造器保持可用，省略的通用输入或输出列表默认为空。9 参数构造器同时接受两张列表：

```java
new MatterFabricationRecipe(ingredients, results, fluidInput, fluidResult,
        aeInputs, aeOutputs, processingTime, aePerTick, requiresResearch);
```

需要保留催化剂时，使用 10 参数重载：

```java
new MatterFabricationRecipe(ingredients, catalysts, results, fluidInput, fluidResult,
        aeInputs, aeOutputs, processingTime, aePerTick, requiresResearch);
```

完整 record 另有最后一个可空的 `ResourceLocation sourceRecipeId` 参数。运行时整机导入通过
`withSourceRecipeId(...)` 附加它，以保留来源配方锁检查；它**不是 JSON 字段**，普通原生配方留空。

### 4.2 研究权限

当某项研究在 `unlocks` 中列出该配方，或配方 `requires_research` 为 `true` 时，配方处于锁定
状态。只要任一归属研究完成至少 1 次即可使用；多个研究归属时任意一个完成即可。
`requires_research: true` 在无人授予权限、授予研究被移除或缺少依赖模组时保持锁定。授予研究
当前不可用（例如缺少 AdvancedAE）时，配方同样锁定，而不是静默开放。

这套井内研究权限不全局拦截其他机器；已经建成的高级机器仍能使用。独立的
[星算研究配方锁](recipe-permissions.zh-CN.md)会在已覆盖的制作入口（包括构筑井）另行检查。

---

### 4.3 保留催化剂与编码契约

以下自定义配方每次消耗两个铁锭，同时要求成型构筑井内的催化剂库存保留一枚紫水晶碎片：

```json
{
  "type": "molecularmanipulator:matter_fabrication",
  "ingredients": [{"ingredient": {"item": "minecraft:iron_ingot"}, "count": 2}],
  "catalysts": [{"ingredient": {"item": "minecraft:amethyst_shard"}, "count": 1}],
  "results": [{"id": "minecraft:iron_block", "count": 1}],
  "processing_time": 200,
  "ae_per_tick": 64,
  "requires_research": false
}
```

每个样板总成有 36 槽催化剂库存，同一座成型构筑井内已加载的总成共同提供条件。
批量和研究并行会放大消耗原料及产物，不放大催化剂需求；催化剂保留在库存中。
接收/开始任务时检查条件，缺少催化剂不能执行。

JEI 编码以服务端配方定义和玩家选择的合法物品替代项为准，包含所有消耗物品、
流体、通用 AE 输入及全部副产物；催化剂不写入样板输入。上传需要有效的编码
终端、一张可提取的空白样板，以及玩家所属队伍已加载、已成型构筑井中有空位的总成。
先用终端空白槽，槽为空时再从相连 ME 网络提取；网络中只有一张也可使用。总成名称仅用于
显示，不作为目标身份。新增的 ExtendedAE Plus 自动选取供应器与上传兼容仅针对 1.6.4.1，
按机器类型和队伍识别，支持重命名总成。服务端重新检查当前配方、输入选择和目标，客户端
数量不能改写配方。上传辅助类和载荷属于内部实现，不是公开 Java API。

## 5. 材料、扣料与保存

- 研究进度每 5 tick 同步，选中研究的库存每 20 tick 采样。同一轮检查内，备料与
  研究准入共享当前库存快照，再核验并抽取真实整轮费用；显示库存不代表材料预留。
  研究只使用控制器 AE 网络中的物品，玩家背包和接口缓存不属于研究供料。
- 普通下单持续备齐下一轮材料；Shift 下单汇总所有剩余轮次的实际费用（包括各轮替换材料），完成一次直接到最高次数。备料时保存定义快照，满阶任务使用一次研究的耗时与功耗。
- 每 20 tick 对库存与本研究缓存进行联合分配，按缺口替换订单量，扣除在途剩余产物，避免重复下单。多个备料任务共享一次库存扫描与物品索引；普通物品/标签只匹配候选物品，定制 Ingredient 保留原谓词。
- 下单产物直接进入本研究缓存。其他合成消耗已存基础材料后自动补单；停止备料只取消本研究的订单，并持久化缓存退款。已正式开始的下单研究拒绝停止和暂停。
- 原生 AE CPU 连接与剩余产物一起保存，世界重载重新连接原任务；便携控制器保存未完成需求并取消旧连接，放回后重新备料。
- 所有材料必须齐全才能开始。重叠的物品/标签要求通过一次联合分配求解，同一份库存不会重复
  计入两个需求，也不需要手工调整材料声明顺序。
- 若提供者在核验与实际提取之间发生变化，研究不启动，已提取材料退回；网络暂不接收的退款由
  控制器保存并重试，退款未完成前不接受新研究。
- 费用在开始时一次性扣完。暂停、断网、缺电、结构损坏或搭建/拆除期间都保留材料与进度，
  恢复研究不重复收费。
- 所有前置研究都必须达到各自要求的完成次数（默认 1 次）。若正在进行的后续研究因指令或配置
  变更不再满足前置要求，会保留材料与进度并等待。
- 同一研究同时至多运行一轮；不同分支独立计时、独立耗能，可与普通生产同时共享网络供电。
- 已开始任务保存材料、轮数、深度表、耗时与功耗快照；重载定义不会重新收费。完成次数按研究
  ID 保存，加工加成与解锁列表从当前定义计算。缩短深度表时保留原完成次数，并按新的最后一项
  重新计算加成。
- 旧存档的“已完成”迁移为完成 1 次；旧部分取材任务暂停，点击继续时一次性补齐尚未支付的
  部分，保留原进度与轮次。
- 控制器存档、正常掉落和 AE 拆卸物品都会保存完成次数、在研任务与待退款。记忆卡不复制这些
  内容。无法解码的任务或退款会原样保留，兼容环境重载后可恢复。

---

## 6. 内置研究与解锁链

当前为六阶段、十项主研究，另有异态物质学与机器联动支线。
AdvancedAE 虽非启动必需，但默认量子材料、演算架构、视界、恒星和超限分支均需要它。
未安装时这些研究不可用，不会自动换成另一条通往相同设备的路线。

| 研究 ID（前缀 `molecularmanipulator:research/`） | 阶段 | 前置（默认完成1次） | 时间 / 功率 | 首轮开放 |
| --- | --- | --- | --- | --- |
| `ae_foundation` | Ⅰ | 无 | 600tick / 256AE/t | AE基础、样板总成、自动合成器 |
| `star_marrow_materials` | Ⅱ | AE | 900 / 512 | 五种星髓材料、量子与终末锚 |
| `star_marrow_crystallization` | Ⅱ | 星髓 | 1200 / 512 | 星讯与水增殖水晶，母岩保留催化 |
| `eae_materials` | Ⅱ | AE | 900 / 512 | 14条固定EAE材料工艺 |
| `quantum_materials` | Ⅱ | EAE | 1200 / 1024 | 13条固定AAE材料工艺 |
| `sequence_array` | Ⅲ | EAE、星髓 | 1200 / 1024 | 构序与重写部件、无限存储 / 并行矩阵 |
| `omni_computation` | Ⅲ | 量子、星髓 | 1800 / 2048 | 演算结构、全知演算矩阵 |
| `event_horizon` | Ⅳ | 构序、演算 | 1800 / 4096 | 黑白洞与九类天枢结构 |
| `stellar_fabrication` | Ⅴ | 视界 | 2400 / 8192 | 超新星、天枢主控、资源核心 |
| `transfinite_fabrication` | Ⅵ | 恒星 | 3600 / 16384 | 超限算枢 |
| `nomai_materials` | 特殊0 | AE | 600 / 256 | 幽灵物质、引力水晶 |
| `machine/<key>` | Ⅳ阶后支线 | **构序≥3且演算≥3** | 默认600 / 512 | 对应整机工艺 |

没有独立的井内 `terminal_fabrication` 研究；自动合成器归属 AE 基础。
星算研究是另一套团队永久制作许可，唯一默认节点为**单方块集成 / Single-Block Integration**
（`molecularmanipulator:material`，沿用旧永久 bit 1）：核心**实际 L3**、
`information_cost: 0`、`prerequisites: []`，仍须玩家在已成型、在线且绑定本队的引擎中主动解锁。

唯一随附锁 `molecularmanipulator:recipe_locks/material` 只限制
`molecularmanipulator:molecular_manipulator` 与 `molecularmanipulator:transfinite_compute_nexus`
两条单方块配方；各自井内研究仍须完成，超限算枢配方仍仅在安装 AdvancedAE 时加载。
矩阵、高级星位锚、自动合成器，以及物质构筑井、构序阵列、万物演算核心的多方块控制器
均无默认星算研究门槛，原有井内研究与其他配方条件不变。配置与覆盖范围见
[星算研究节点](star-research-nodes.zh-CN.md)及[配方权限文档](recipe-permissions.zh-CN.md)。

所有静态研究均为9轮，后续各轮显式 `ingredients` 为可持续扩产材料，避免重复收原机器；只授予并行，生产速度由星算引擎提供。自定义持续时间与深度保持可配置。

所有内置与自定义整机导入自动追加 `sequence_array: 3` 与 `omni_computation: 3`；已有更严数值或 `max` 不会被降低。3表示含首轮在内的累计完成次数。研究开始、下单、备料与进行中任务都读当前前置；联动配方使用时也重查双三轮最低条件，旧完成记录、管理接口赋值或另一研究归属不能绕过。

Ⅱ阶 `fabrication/research_materials/<modid>/<item>` 是固定启动材料工艺，不是整机导入，因此可在Ⅲ阶前生产。实际整机配方继续位于 `fabrication/import/machine/<key>/`，按机器独立研究；重载后重新建立索引与条件。

五种星髓材料使用原生井配方，晶粉、处理器、合金、星讯组件保留原配方ID；观测环水晶转板与旧机器/工作台来源移除。增殖配方 `fabrication/internal/star_marrow_crystal`：`ae_inputs: [{"#t":"molecularmanipulator:star_signal_flow","#":1024}]`、水8000mB、保留母岩1，产晶体64，400tick/512AE/t。需样板总成，本配方不生产星讯。生存星讯流由独立的星海解算主楼生产，不是井研究奖励。

`star_signal_storage_component` 是研究后的井内加工配方，每份产出 8 个**星讯存储组件**；
`star_signal_storage_cell` 才是完成的**星讯存储元件**，仍为有序工作台配方，三行为
**`GDG / DCD / IPI`**。其中 `G = ae2:quartz_vibrant_glass`、`D = star_marrow_crystal_dust`、
`C = star_signal_storage_component`、`I = star_marrow_alloy_ingot`、`P = star_chart_processor`
（未写命名空间的材料 ID 均属于 `molecularmanipulator`），不要把元件和组件的配方互换。

视界首轮消耗构序水晶64、演算尖塔64、三种矩阵各32、AE奇点1024。超新星及天枢核心归恒星，超限算枢归超限；黑白洞配方保留其物料成本和20秒生产耗时。

旧进度一次性映射：AE→星髓/EAE/量子，演算→超限，视界→恒星；不会赠送构序次数或新增结晶。旧任务保留费用时间快照、按当前前置等待，完成时迁移原权限。旧水晶转板任务退还原晶体。已有物品与已建建筑继续有效，矩阵新生产按当前研究归属。

---

## 7. 批量加工、配方匹配与已知限制

手动接口加工与样板总成使用同一研究加成。手动物品缓存按实际库存、输出空间和供电计算本轮
份数；不会把 long 数量塞进普通 ItemStack。

样板总成实现公开的 `OmniBatchCraftingProvider` API，支持本模组演算系统的 long 批次。输入
支持所有已注册的 AEKey 类型，额外资源通过 `ae_inputs` 声明。输入与输出以 AE Key + long
持久保存，处理工作量取决于材料种类数，不随份数逐份循环。AE 普通单份投料也使用同一持久
批次，可在开始加工前合并同配方投料。

样板总成自行持有输入、输出与退款缓存。产物与待退回原料自动写回其 ME 网络；网络不接收时
保留在总成内，不转存到手动输出口。输出堵塞或保存重载不会丢弃已接收材料，单独拆除一块
也不会收走其他总成的库存。

配方查询按**完整产物及数量**建索引，并复用研究定义与配方归属索引。同产物候选仍按
RecipeManager 的顺序匹配，输入逐次完整校验。索引随配方管理器的配方快照替换自动重建，
覆盖数据包重载与 `replaceRecipes`。各控制器的完成次数实时读取，解锁、撤销和存档恢复不会
沿用其他控制器或旧状态的权限。

并行是上限而非保证：实际批次仍受材料、供电、输出接收能力和每种 AEKey 的 long 数量上限
限制。例如每份输出 16 个相同物品时，单批份数最多 `Long.MAX_VALUE / 16`。功耗按原配方每
tick 功耗乘本批份数计收。

批量准入会拒绝乘法或每种资源的缓存溢出，且不消耗输入。持久批次缓存在 NBT 保存后
仍保持 long 数量精度；这种容量不提供免费材料或供电。

已开始的任务保留原料、产物与功率快照；加工耗时按当前配方与控制器网络收益重新计算，
包括引擎等级和超频变化，已完成进度按比例换算并向下取整到完整 tick。旧存档中
`duration = 1` 的批次同样重新计算。手动加工也保存耗时，供重载后按比例换算；没有该字段的
旧手动存档保留已计 tick，并钳制在完成前。排队批次保存原料所有权与配方 ID，开工前按当前
配方、权限与参数重新检查。无法继续加工时，原料仍留在总成内。

### 7.1 21.3.0.0 尚存的边界

- 产物相同、可替代原料范围重叠时，拆分出的原料可能重新匹配另一条配方并改用其耗时和能耗。
- 重载时新增更靠前的匹配配方，可能导致已有队列等待，即使原配方仍存在。
- 无样板队列只在样板定义消失时退款，匹配到的配方变化不会触发退款。

产物索引与 API 准入都不应被视为这些问题的修复。

---

## 8. JEI 与接口

JEI 配方顶部标注负责开放此配方的研究阶段与名称；鼠标悬停可查看完整研究条件，多个研究授予
同一配方时会全部列出。没有研究限制的配方显示“基础配方 · 无需研究”，研究条件未配置的配方
显示“研究条件未配置”。底部的时间和功耗为基础参数；研究并行与引擎速度由控制器实际应用。

| 接口 | 控制方式 |
| --- | --- |
| 物品输入口、流体输入口 | 已绑定控制器且 AE 在线时，点击“全部退回 AE”将本接口缓存退回该网络；接收不下的保留 |
| 物品输出口、流体输出口 | “自动输出”默认关闭；开启后每 5 tick 向已选方向的相邻容器转移，按实际接收量扣除缓存 |
| 输出方向 | 上、下、北、南、西、东独立切换，新接口六方向默认全部关闭。已开启方向使用 AE2 的高亮（浅蓝）按钮贴图并带方括号，鼠标悬停时按钮转为薄荷色，可多选；按钮显示该方向相邻方块的图标，悬停查看名称，方块改变后自动更新；使用世界方向，连接相邻容器朝向本接口的面 |
| 样板总成 | 显示 AE 连接状态和样板占用数；支持命名，窗口缩放保留尚未保存的名称 |

缓存容量为 16 个物品槽（4×4）与 4 个各 2,147,483,647 mB 的独立流体储罐；灌注时会优先合并到
已有的同类储罐。

流体输入口、流体输出口的四个缓存槽都支持手动双向存取：鼠标拿着流体容器右键指定槽，有流体
就尝试倒入，空容器就尝试装出；每次处理一个容器。单个水桶会变为空桶，单个空桶会变为水桶，
创造模式也进行实际容器交换。堆叠空桶取水时，水桶放进背包，鼠标上空桶减一；若背包放不下
结果，操作不生效。槽满、流体不兼容或不足以装满水桶时不会吞掉容器或流体。左键保留输入口
倒入、输出口取出的原操作。

输出缓存的自动转移无需保持界面打开。未选任何方向时停止输出；相邻区块未加载时不强制加载。
物品和流体输出仅发送到相邻容器，不直接丢到世界中。已保存的方向设置不会因更新默认值而重置。
输出开关、方向随世界保存，并保留在 AE 拆卸设置和控制器分批拆解打包的接口物品中。普通破坏
接口时，只有缓存中仍存有材料才会保存这些设置；空缓存被普通破坏只掉落普通方块，自动输出与
方向设置会丢失。

---

## 9. 管理指令

需要开启作弊或具有 OP 2 级权限。省略坐标时，准心需指向 16 格以内的物质构筑井控制器；也可
在指令末尾加 `x y z`，支持 `~` 相对坐标。服务器控制台必须指定坐标，目标区块必须已经加载。
指令仅操作目标控制器，不要求结构成型或 AE 在线，也不扣材料。

| 指令 | 效果 |
| --- | --- |
| `/matter_research unlock_all` | 将目标控制器的所有可用研究直接设为各自满级，包括 KubeJS 新增研究 |
| `/matter_research complete <研究 ID> true` | 将单项研究设为满级 |
| `/matter_research complete <研究 ID> false` | 将单项研究设为未完成，即 0 次 |
| `/matter_research set <研究 ID> <次数>` | 设置单项完成次数；0 表示未完成，超过上限自动设为最高次数 |

```mcfunction
/matter_research unlock_all
/matter_research set molecularmanipulator:research/ae_foundation 5
/matter_research complete molecularmanipulator:research/sequence_array true
/matter_research complete molecularmanipulator:research/sequence_array false
/matter_research set molecularmanipulator:research/omni_computation 999 100 64 200
```

研究 ID 支持 Tab 补全。计数参数接受 0 至 9223372036854775807，实际保存值会按该研究当前
`depths` 长度钳制。未加载 AdvancedAE 时，万物演算不会出现在补全和全部解锁列表中，单独
指定也会提示不可用。

设置某项完成状态或次数会结束该项正在进行的研究，已投入的材料不返还，也不会额外扣料；
`unlock_all` 会结束所有被设为满级的在研任务。单项设置不会影响其他分支的完成次数、在研任务
和已投入材料。撤销前置后，尚未完成的后续研究暂停推进，直到当前前置重新满足；既有完成
记录保留。普通配方保留已授予权限，联动机器配方则持续重查构序工程、演算架构各三轮的条件。
修改结果保存到控制器 NBT，并同步到已打开的界面。

---

## 10. Java API

```java
// 变更操作必须在该控制器所属的服务端线程执行。
MatterResearchApi.start(controller, "molecularmanipulator:research/ae_foundation");
MatterResearchApi.setPaused(controller, "molecularmanipulator:research/sequence_array", true);
int rounds = MatterResearchApi.completionCount(controller, "molecularmanipulator:research/ae_foundation");
int effective = MatterResearchApi.setCompletionCount(controller, "molecularmanipulator:research/ae_foundation", 999L);
MatterResearchApi.setCompleted(controller, "molecularmanipulator:research/sequence_array", true);
MatterResearchApi.unlockAll(controller);
boolean unlocked = MatterResearchApi.isRecipeUnlocked(controller, "molecularmanipulator:molecular_center_controller");
var profile = MatterResearchApi.productionProfile(controller, fabricationRecipeHolder);
long parallel = profile.parallel();
int ticks = profile.ticks();
var definitions = MatterResearchApi.definitions(serverLevel);
```

类名为 `com.atir.molecularmanipulator.research.MatterResearchApi`。开发时对该模组 JAR 使用
`compileOnly`，运行时单独安装前置，不复制或嵌入这些类。研究 API 没有数值 ABI 协商方法；
独立的批量 API 保持 v1。

| 入口 | 契约 |
| --- | --- |
| `definitions(Level)` | 可用定义，按 `sort_order` 其次按 ID 排序；`required_mods` 未加载的定义会被过滤掉。 |
| `orderMissing(controller, resourceId, toMaximum)`、`stopPreparation(controller, resourceId)` | 在服务端所有者线程调用，返回操作是否被接受。前者持续备料；`true` 选择所有剩余轮次。后者只允许停止备料，正式研究开始后返回 `false`。 |
| `start(controller, id)`、`setPaused(controller, id, paused)` | 返回操作是否被接受。`start` 对已存在的任务执行恢复而不是失败；研究不存在、不可用、已满级、有待退款或不满足前置时返回 `false`。下单启动的任务拒绝 `setPaused`；普通手动任务的 `setPaused(..., false)` 会重新检查结构、网络、退款与前置并补扣未支付部分，因此也可能返回 `false`。 |
| `completionCount`、`isCompleted` | 查询完成次数；`isCompleted` 表示至少完成 1 次，不是满级。 |
| `canUseRecipe`、`isRecipeUnlocked` | 检查本井研究、全局配方锁，以及导入配方的来源 ID 和双Ⅲ阶三轮最低条件。其他执行器须自行调用此入口才能实施这些规则。`canUseRecipe` 接收 `RecipeHolder<MatterFabricationRecipe>`。 |
| `productionProfile` | 返回 `(parallel, ticks)`：取已完成归属研究的最高并行；无归属且无需研究的配方继承 `ae_foundation` 已完成深度的并行，否则为 `1`。耗时由当前配方基础时间与 `StarComputationNetwork.forGrid(controller.getMainNode().getGrid()).processingTicks(baseTicks)` 决定，独立于研究深度。 |
| `setCompletionCount`、`setCompleted`、`unlockAll` | 管理性修改，按当前最大深度钳制并结束受影响的在研任务；退回备料缓存，已开始研究的费用仍被消耗。负数会抛异常；次数为 `int`，`unlockAll` 返回处理过的定义数量。三者都不检查权限——由调用方负责。 |
| `prerequisitesMet`、`requiredPrerequisiteLevel` | 每个前置默认要求 1 次；前置不在传入的 `available` 列表中时返回 `false`，因此应传入 `definitions(level)`。Java 映射值 `0` 对应数据包中的 `"max"`。 |

在非属主服务端线程调用变更方法会抛出 `IllegalStateException`；实时查询也应在所属服务端线程使用；跨线程只使用调用方取得的不可变快照。
完整进度可通过 `controller.getResearch().save(serverLevel.registryAccess())` 读取。

Java 模组可用复制 API 生成修改后的定义：

```java
var parent = ResourceLocation.parse("molecularmanipulator:research/ae_foundation");
var three = existingResearch.withPrerequisiteLevels(Map.of(parent, 3));
var full = existingResearch.withPrerequisiteLevels(Map.of(parent, 0)); // Java 的 0 对应数据中的 "max"
int required = MatterResearchApi.requiredPrerequisiteLevel(three, parentRecipeHolder);
boolean eligible = MatterResearchApi.prerequisitesMet(three,
        MatterResearchApi.definitions(serverLevel), controller.getResearch()::completionCount);
```

`withPrerequisiteLevels` 返回新定义，不修改旧定义与玩家进度；它**替换**而不是合并等级映射。
返回的新定义需通过正常配方注册或替换流程安装。原有的 9 参数与 10 参数构造器继续可用，
默认每个前置要求 1 次；完整规范构造器的最后一项是
`Map<ResourceLocation, Integer> prerequisiteLevels`。Java 映射接受正数（固定次数）或 `0`
（满级），而 JSON/KubeJS 接受正整数或字符串 `"max"`，不接受数值 `0` 和负数。

其他配方执行器需要自行调用权限与生产参数接口。`productionProfile` 只计算批量和耗时上限，
不检查制作许可、不预留材料，也不保证网络能供给整个批次。
