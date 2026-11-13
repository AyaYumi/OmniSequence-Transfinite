# 天体仪：升级规则与数据包 / KubeJS 配置

在引擎升级页准备星位锚，即可搭建下一环。整合包作者可按下文格式和示例，用数据包或 KubeJS
修改升级费用与条件。结构和按钮说明可在游戏内按 G 查看；安装和第一座井见[玩家指南](../README.zh-CN.md)。

当前对应 OmniSequence 21.3.0.1，Minecraft 1.21.1 / NeoForge、Java 21。
模组 ID 为 `molecularmanipulator`。
English: [Star Orrery upgrades](star-orrery-upgrades.md)。
研究定义另见[物质构筑井研究 API](matter-research-api.zh-CN.md)。

默认升级只需要对应数量的星位锚。搭好中央本体和基座后，打开升级页，
按列表准备材料，再点击自动搭建 L… 星环（L1–L3）或升级到 L…（L4–L9）。
服务端会在实际建筑中补齐缺少的星位锚。默认没有额外升级能量、任务、研究、机器或节点门槛。
点击等级按钮只是预览，不消耗材料。

## 怎么升级

1. 按结构预览搭好中央 27 件（1 主核、22 外壳、四个水平面中央各 1 观测端口）与
   928 个基座（520 雕纹、408 鎏金）；中央不能作为独立 3×3×3 引擎运行。推荐从 L1 开始，
   L4–L9 必须按认证顺序升级；高阶动作也可补齐缺少的内环。
2. 把对应星位锚放进背包。背包不足时可从本机所在且有电、有可用频道的 ME 网络补齐；
   背包够用就不要求联网。
3. 列表只显示简短标题；悬停可查看具体数量、已有和可用材料、完成方法。
   鼠标直接滚动列表或拖动滚动条可查看全部条件，不必先点中某一行。
4. 点击升级。正确位置已有的星位锚计入要求，不会重复消耗；只取缺少的材料，
   优先背包，再从 ME 补齐，自动摆放到所选星环及其内环缺少的位置。
5. 保持目标区块已加载、星位锚位置无阻挡并允许放置方块。空间、区块或权限不满足时，
   根据提示排除问题后重试。升级不会代建中央本体与基座；完成一级后不会自动开始下一级。

L1–L3 仍兼容原有手搭星环后自动生效的方式，内部认证上限仍为 3，但并不免费授予未搭好的环。
L4–L9 即使预搭完整，也要显式点击升级。已认证等级不会因结构损坏清空，修复实物即可恢复可用增益。

升级声明是服务端普通配方，类型为 `molecularmanipulator:star_orrery_upgrade`，
不执行合成，也不产生物品。普通数据包与 KubeJS `ServerEvents.recipes` 使用同一格式，
不必安装 KubeJS。整合包可自定义能量、研究、机器、节点与可选 FTB 条件，
或用 `auto_build: false` 保留手搭星环后能量校准的流程。

## 1. 默认 L1–L9 星位锚要求

L4–L9 默认文件位于 `src/main/resources/data/molecularmanipulator/recipe/star_orrery_upgrade/l4.json`
至 `l9.json`，配方 ID 为 `molecularmanipulator:star_orrery_upgrade/l4` 至 `/l9`。
全部采用 `auto_build: true`、`energy: 0`、`ae_per_tick: 1`，条件仅为 `anchors` 叶子。
格式要求速率为正；总能量为零时不会实际扣升级能量。
L1–L3 使用固定内置条目提供同样的材料搭建界面，不受自定义升级配方覆盖。

| 等级 | 前序完整时新增一环所需 | 累计基准星位锚 | 累计量子星位锚 | 累计终末星位锚 | 额外升级能量 / 任务 / 研究 |
| --- | --- | ---: | ---: | ---: | --- |
| L1 | 24 个基准星位锚 | 24 | 0 | 0 | 无 |
| L2 | 32 个基准星位锚 | 56 | 0 | 0 | 无 |
| L3 | 40 个基准星位锚 | 96 | 0 | 0 | 无 |
| L4 | 48 个量子星位锚 | 96 | 48 | 0 | 无 |
| L5 | 56 个量子星位锚 | 96 | 104 | 0 | 无 |
| L6 | 64 个量子星位锚 | 96 | 168 | 0 | 无 |
| L7 | 72 个终末星位锚 | 96 | 168 | 72 | 无 |
| L8 | 80 个终末星位锚 | 96 | 168 | 152 | 无 |
| L9 | 88 个终末星位锚 | 96 | 168 | 240 | 无 |

累计数量包含正确位置已有的星位锚，每次只需补齐缺少的材料。
若内环被破坏，也须准备其缺少的星位锚。档位 ID 为 `reference`、`quantum`、`terminal`，
对应实际方块 / 物品 `molecularmanipulator:reference_star_position_anchor`、
`quantum_star_position_anchor`、`terminal_star_position_anchor`，不会生成虚拟星环或无材料方块。

仅 L4–L9 的声明条件、能量、抽取上限与 `auto_build` 可配置。虽然 codec 接受 `level: 1..9`，
L1–L3 始终使用固定自动条目；自定义规则也不能跳过认证等级。
以下示例中的研究、任务与能量要求均为整合包可选规则。

## 2. 声明 schema

### 2.1 配方顶层

| 字段 | 要求 |
| --- | --- |
| `type` | 固定为 `molecularmanipulator:star_orrery_upgrade`。 |
| `level` | 必填、精确整数，codec 范围 `1..9`；实际可配置认证等级为 `4..9`。 |
| `energy` | 必填、有限非负 JSON 数字，单位 AE；允许 `0`。 |
| `ae_per_tick` | 必填、有限且 >0 的 JSON 数字，单位 AE/t；即使 `energy: 0` 也不能写 `0`。 |
| `auto_build` | 可选布尔值，默认 `false`；`true` 从背包优先、ME 补充材料，并自动放置缺少的星位锚；`false` 要求先手搭完整星环。 |
| `conditions` | 必填，一个非空条件树对象；可以直接是叶子，也可以是组合组。 |

这里的 `count` / `required` 是 1..2,147,483,647 的精确 JSON 整数，不是研究 API 的
long 字符串；小数不接受。不要把 FTB ID 当数字，也不要把这里的数量写成字符串。

### 2.2 组合组与叶子

- 组合组：`type: "all" | "any" | "at_least"`，使用非空 `children` 数组，不能同时带目标。
  `all` 要求全部直接子条件通过；`any` 至少一个；`at_least` 要求至少 `required` 个。
  子条件可以继续嵌套组合组或叶子。
- 叶子：`type: "ftb_quests" | "machines" | "research" | "nodes" | "anchors"`，使用非空 `targets`
  数组，不能同时带子条件。每个目标为 `{ "id": "...", "count": 1, "title": "..." }`。
- 叶子的 `match` 默认 `"all"`，也接受 `"any"`、`"at_least"`；它只组合本叶子的目标。
  `required` 默认 `1`，在 `at_least` 下表示需要通过的目标项数，不是设备总数或研究总次数。
- `required` 无论用于组还是叶子，都必须在 `1..children.length` 或 `1..targets.length` 内；
  即使 `all` / `any` 不使用阈值，也不能填写越界数值。组的组合方式由 `type` 决定，不由 `match` 决定。
- `count` 默认 `1`：星位锚为累计材料数量，机器为数量，研究为完成次数；FTB 和永久节点只返回 `0/1`，应保持 `count: 1`。
  给它们设置 `count: 2` 不表示任务进度达到 2，而是一个无法满足的布尔目标。
- `same_machine` 默认 `true`，仅影响 `research`；`formed` 默认 `true`，仅影响 `machines`。
- 组、叶子和单个目标都可写 `title`，默认空字符串。它按翻译键解析；没有对应翻译时显示原文，
  因而既可填自定义文字，也可填语言文件中的键。自定义标题替换列表中的默认标题，不改变目标 ID 或判定方式。
  列表不显示内部 ID；次数、完成方法、缺失原因和多目标匹配方式放在悬停提示里。
  自定义短标题只用于列表；提示中的操作指引仍使用游戏内的真实名称，方便玩家找到对应内容。
  `title` 不是升级配方顶层的命名字段。
- 条件最大嵌套深度为 `32`（根深度 `0`），整棵树条件节点与目标合计最多 `1,024` 项。
  未知类型、空组、空目标和非法阈值会被拒绝。

不同目标独立判断，不是分配或消耗资源。一个控制器可同时匹配明确 ID 和包含它的 tag；
多个重复目标也不会自动变成不同设备要求。需要 3 座时用一个目标的 `count: 3`，不要重复写 3 项。

## 3. 五类叶子的判定边界

### `anchors`：实物位置与可用材料

目标 `id` 只接受 `reference`、`quantum`、`terminal`，`count` 是累计实物要求数量。
`auto_build: true` 的升级页预检把目标等级固定槽位中的正确锚与可用背包 / 同网 ME 材料合计，
但预期补建数量不会超过本次目标等级实际包含的槽位数。若自定义 `count` 超过该数量，
必须先手搭对应的更高环；不能只往背包多放材料来绕过。升级运行时只按实际已放好的数量复核。
`auto_build: false` 始终只统计实物，不把背包或 ME 库存当成已搭好的星环。
已有正确锚不再消耗。此叶子本身只检查条件，扣料和放置由 `auto_build: true` 的升级动作执行。
无论条件树怎样配置，自动搭建都必须付出实际缺少的星位锚，不会因降低 `count` 或其他 `any`
分支通过而免费生成方块；`auto_build: false` 不代摆。
其他叶子的行为保持不变，可以与 `anchors` 用组合组叠加。


### `machines`：真实同网控制器

目标 `id` 是方块注册 ID，或带 `#` 前缀的方块 tag，例如
`#kubejs:orrery_support_controllers`；不是物品 tag、BlockEntityType ID 或 JEI 分类。
只统计已加载、未移除、在线且处于本机同一实际 `IGrid` 的控制器 BlockEntity；
原生 AE 主机须匹配有效主节点，同一控制器暴露多个节点也只计一次。
附近方块、同队但异网设备、不拥有机器 BlockEntity 的代理 Part、共享库存的逻辑网络、
控制器物品和折叠包裹都不能替代。
有效量子连接若确实合并为同一个 `IGrid`，可以跨位置甚至维度计入；不额外限制设备团队。

`formed: true` 对已显式支持的原生多方块调用真实成型 API，包括天体仪、构筑井、构序阵列、
万物演算、装配矩阵构序重写核心、天枢以及星海解算主楼。
第三方机器没有通用的 formed 合约：未被识别的控制器即使设为 `true`，也只能证明它是
已加载的活跃控制器候选，不能保证其完整结构已成型；不会猜测 blockstate 的 `formed` 属性。
第三方目标应写真正的控制器 ID / tag，不能把辅助服务端口当控制器。需要更强保证时须另做集成。
`formed: false` 不调用结构 API，但仍要求已加载、在线、同网的有效控制器，而不是允许散放离线方块。

### `research`：同井 / 跨井，不累计

只查同一真实 ME 网内已加载、在线、完整成型且不处于建造 / 拆解中的物质构筑井。
每次都确认目标研究定义仍存在，再读取各井 `completionCount`；旧存档次数不能替代已删除的定义。
`formed: false` 不能取消研究叶子对合格井的要求。

- `same_machine: true`：必须有一座井满足整个叶子的 `targets` 和 `match`。
  要求多项研究在同一座井完成时，应将它们放在同一个叶子；拆成两个叶子即使外面是 `all`，也可能各由不同井满足。
- `same_machine: false`：每个研究目标分别取所有合格井的最大次数，再应用 `match`，不是求和。
- 例如 A 井 `(构序=3, 演算=0)`、B 井 `(构序=0, 演算=2)`，目标 `(3,2)` 在同井模式下失败，
  跨井模式下通过。若 A、B 的构序都只有 `1` 次，跨井模式仍不能满足构序 `≥2`。
- 不会消耗研究次数，也不永久指定某一座井；换用另一座满足整个叶子的井可以继续。
  没有合格井时不会通过，即使存档中有其他井的历史进度。

### `nodes`：只读本机绑定团队位

此叶子只接受五个旧短 ID：`foundation`、`material`、`quantum`、`terminal`、`transfinite`，
不支持任意自定义研究 ID。这是兼容位映射，不代表默认有五项研究；唯一默认定义为
单方块集成 / Single-Block Integration（`molecularmanipulator:material`，旧永久 bit 1），
要求实际 L3、零情报费用、无前置，仍须主动解锁。自定义定义与购买见[星算研究节点文档](star-research-nodes.zh-CN.md)。

读取 `core.getUnlockMask()`，即本机绑定团队的永久解锁记录及其既有迁移位；
不使用 `StarComputationNetwork.unlockMask(grid)` 的同网权限并集，也不取当前 GUI 观看者的团队。
别队引擎提供的加工权限不能代替本机的升级前置。检查不会购买节点或再次扣费；未知节点不通过。

### `ftb_quests`：可选、只读的 Quest / Task 检查

同一个叶子可包含多个 Quest ID 和 Task ID，由服务端查到的实际对象类型区分。
全部写成十六进制字符串，如 `"1234567890ABCDEF"`、`"0x234567890ABCDEF0"`，
不能写 JavaScript 数字、十进制转换值或先经过 `Number` 的结果，以免超过 `2^53-1` 后丢精度。

schema 接受可选 `0x` / `0X` 前缀及 1–16 位十六进制字符；运行时还要求正 signed long：
`1..7FFFFFFFFFFFFFFF`。`0`、`8000000000000000` 和 `FFFFFFFFFFFFFFFF` 不可用。
无前缀的 `"10"` 仍表示十六进制 `0x10`，不要带空白字符。

- 归属始终为天体仪已绑定的团队 UUID，不是点击按钮或观看界面的玩家。
  不回退到观看者，不创建缺失的团队进度。
- Quest 查询 `TeamData.isCompleted(quest)`；Task 比较该 Task 自己的 `getProgress(task)` 与
  `task.getMaxProgress()`（必须有合法正上限）。父 Quest 完成不代表 Task 完成，Task 也不借用父进度。
- 不触发检测、不标记完成、不提交物品、不领取奖励。FTB 目标是完成与否，不暴露自定义任务进度阈值。
- 未装 `ftbquests`、服务端 / 团队数据未就绪、ID 被删、对象不是 Quest / Task、缺依赖或 API
  不兼容时，返回不可用且未完成，不忽略要求。若配置 `any` / `at_least`，其他真实通过的
  分支仍可按配置满足阈值；不可用的 FTB 分支本身绝不会算通过。

反射桥的目标为 Minecraft 1.21.1 / NeoForge 上的
[FTB Quests v2101.1.38 API](https://github.com/FTBTeam/FTB-Quests/tree/v2101.1.38/common/src/main/java/dev/ftb/mods/ftbquests/quest)。
发行文件标注支持 Minecraft 1.21.1，不代表其 API 必然兼容；不保证所有 2100 / 2101 版本、
其他加载器或未来版本均兼容。

桥反射校验 `ServerQuestFile.INSTANCE` / `server` / `isLoading()`、`getBase(long)`、
`getNullableTeamData(UUID)`、`getTeamId()`、`isCompleted(QuestObject)`、
`getProgress(Task)` / `getMaxProgress()` 的 long 返回值及标题 Component。
只缓存反射元数据（含发现失败），每次重新读取当前服务端、团队、目标、标题和完成状态。
配方重载不会固定旧进度；更换模组 / API 应重启，不靠 `/reload` 重新发现不兼容 API。

## 4. 不使用 KubeJS：普通数据包覆盖

在 `<world>/datapacks/orrery_rules/` 新建 `pack.mcmeta`：

```json
{
  "pack": {
    "pack_format": 48,
    "description": "Star Orrery upgrade rules"
  }
}
```

例如新建 `data/molecularmanipulator/recipe/star_orrery_upgrade/l4.json`：

```json
{
  "type": "molecularmanipulator:star_orrery_upgrade",
  "level": 4,
  "energy": 0,
  "ae_per_tick": 1,
  "auto_build": true,
  "conditions": {
    "type": "anchors",
    "match": "all",
    "targets": [
      { "id": "reference", "count": 96 },
      { "id": "quantum", "count": 48 }
    ]
  }
}
```

高优先级数据包用相同 namespace 和路径 / 配方 ID 覆盖内置声明，然后 `/reload`。
L5–L9 同理；Minecraft 1.21.1 的目录为单数 `recipe`，不是 `recipes`。
若改用另一个 namespace 新增同等级声明却保留原声明，将构成冲突，而不是追加条件。

## 5. KubeJS 完整示例 A：自定义六级研究与能量门槛

将脚本保存到 `kubejs/server_scripts/star_orrery_upgrades.js`。它不依赖 FTB，
可按需修改表格数值或条件生成逻辑。示例 A 与示例 B 二选一，不要同时安装。

```javascript
ServerEvents.recipes(event => {
  const upgradeType = 'molecularmanipulator:star_orrery_upgrade';
  event.remove({ type: upgradeType });

  const stages = [
    { level: 4, energy: 614400,   rate: 512,   sequence: 1, omni: 0 },
    { level: 5, energy: 1843200,  rate: 1024,  sequence: 2, omni: 0 },
    { level: 6, energy: 4915200,  rate: 2048,  sequence: 3, omni: 0 },
    { level: 7, energy: 9830400,  rate: 4096,  sequence: 3, omni: 1 },
    { level: 8, energy: 29491200, rate: 8192,  sequence: 3, omni: 2 },
    { level: 9, energy: 98304000, rate: 16384, sequence: 3, omni: 3 }
  ];

  stages.forEach(stage => {
    const research = [
      { id: 'molecularmanipulator:research/sequence_array', count: stage.sequence }
    ];
    if (stage.omni > 0) {
      research.push({ id: 'molecularmanipulator:research/omni_computation', count: stage.omni });
    }
    const children = [
      { type: 'research', same_machine: true, match: 'all', targets: research }
    ];
    if (stage.level >= 5) {
      const machines = [
        { id: 'molecularmanipulator:molecular_center_controller', count: 1 }
      ];
      if (stage.level >= 7) {
        machines.push({ id: 'molecularmanipulator:omni_computation_controller', count: 1 });
      }
      children.push({ type: 'machines', formed: true, match: 'all', targets: machines });
    }
    children.push({ type: 'nodes', match: 'all', targets: [{ id: 'material', count: 1 }] });

    event.custom({
      type: upgradeType,
      level: stage.level,
      auto_build: false,
      energy: stage.energy,
      ae_per_tick: stage.rate,
      conditions: { type: 'all', children: children }
    }).id('kubejs:star_orrery_upgrade/l' + stage.level);
  });
});
```

`event.remove({ type: ... })` 删除全部该类型声明；脚本须重新添加所需的 L4–L9 六份规则。
只补一份会使其他高阶显示未配置，不会恢复内置旧规则。L1–L3 不受此删除影响。

## 6. KubeJS 完整示例 B：多目标与嵌套组合，只替换 L7

本例假定内置六份默认声明仍在，只移除默认 L7，保留其他五级。
ID / tag 的格式可以直接复制，但 FTB ID 是占位示例，须替换为整合包实际存在的 Quest / Task ID。
若此前已自定义 L7，需移除其实际配方 ID，确保最终只有一份 L7。这些路线属于整合包可选规则。

```javascript
ServerEvents.recipes(event => {
  const upgradeType = 'molecularmanipulator:star_orrery_upgrade';
  event.remove({
    type: upgradeType,
    id: 'molecularmanipulator:star_orrery_upgrade/l7'
  });

  event.custom({
    type: upgradeType,
    level: 7,
    auto_build: false,
    energy: 9830400,
    ae_per_tick: 4096,
    conditions: {
      type: 'all',
      title: 'L7：整合包认证',
      children: [
        {
          type: 'nodes', match: 'all', title: '本机团队前置',
          targets: [{ id: 'material' }]
        },
        {
          type: 'research', same_machine: true, match: 'all',
          title: '这两项必须在同一座井达到',
          targets: [
            { id: 'molecularmanipulator:research/sequence_array', count: 3, title: '构序研究' },
            { id: 'molecularmanipulator:research/omni_computation', count: 1, title: '演算研究' }
          ]
        },
        {
          type: 'at_least', required: 2, title: '以下三条路线至少满足两条',
          children: [
            {
              type: 'ftb_quests', match: 'all', title: 'Quest 与 Task 都完成',
              targets: [
                { id: '1234567890ABCDEF', title: '主线 Quest（替换 ID）' },
                { id: '0x234567890ABCDEF0', title: '子 Task（替换 ID）' }
              ]
            },
            {
              type: 'machines', formed: true, match: 'all', title: '同网设备组',
              targets: [
                { id: 'molecularmanipulator:molecular_center_controller', count: 2 },
                { id: 'molecularmanipulator:omni_computation_controller', count: 1 },
                { id: '#kubejs:orrery_support_controllers', count: 3, title: '支持设备 tag' }
              ]
            },
            {
              type: 'any', title: '额外任务组或更深研究',
              children: [
                {
                  type: 'ftb_quests', match: 'at_least', required: 2,
                  title: 'kubejs.orrery.optional_tasks',
                  targets: [{ id: '31' }, { id: '32' }, { id: '33' }]
                },
                {
                  type: 'research', same_machine: false, match: 'any',
                  title: '跨井取最大次数，任选一项',
                  targets: [
                    { id: 'molecularmanipulator:research/sequence_array', count: 4 },
                    { id: 'molecularmanipulator:research/omni_computation', count: 2 }
                  ]
                }
              ]
            }
          ]
        }
      ]
    }
  }).id('kubejs:star_orrery_upgrade/l7');
});
```

配套的方块 tag 可用普通数据包文件
`data/kubejs/tags/block/orrery_support_controllers.json` 定义（KubeJS 目录则为
`kubejs/data/kubejs/tags/block/orrery_support_controllers.json`）：

```json
{
  "replace": false,
  "values": [
    "molecularmanipulator:molecular_center_controller",
    "molecularmanipulator:omni_computation_controller"
  ]
}
```

本例的两座构序阵列与一座万物演算也能计入 tag 的 3 座，不额外要求三座不重叠设备。
组的 `required: 2` 数的是三个子路线中的两条；FTB 叶子的 `required: 2` 数的是
三个目标中的两个。根下的本机节点和同井研究始终必须满足。
未装 FTB 时任务分支仍失败；只有其他路线实际满足组阈值才可通过。
自定义翻译键无语言文件时显示键本身，也可像其他 `title` 一样直接填写文字。

## 7. 核心校准、暂停、重载与保存

### 7.1 实物环数不等于有效等级

全新核心的认证上限为 3，不表示未搭建就有 L3 能力。中央、基座和对应连续星环完整后，
L1–L3 免费自动生效，不需要额外任务、设备、研究、节点、ME 连接或校准付款。
有效等级始终为 `min(服务端验证的连续完整实物环数, 本机认证上限)`；
网络增益与算力门槛都受此限制，预搭九环也不能绕过 L4–L9 的认证。

L4–L9 一次只升一级，目标必须恰好等于本机认证上限 + 1。开始或继续时，服务端检查
中央与基座、唯一合法规则、当前条件和操作权限。`auto_build: true` 会检查材料与搭建空间，
补齐缺少的内环和目标环后再验证结构；`auto_build: false` 则必须先手搭全部所需星环。
只有 `energy > 0` 才要求校准用的活跃 ME 主连接；自定义机器 / 研究条件或从 ME 取材料
仍可能需要网络。旧档须先完成下述迁移；完成一级不自动开始下一级，预览也不改变已有任务。

升级页的条件目录每 10 tick 刷新（20 TPS 下约 0.5 秒），显示目标名称、数量与未满足原因。
校准则在每个运行 tick 检查结构、所需联网、当前规则及条件，付满时再复核一次，
全部通过才写入认证。客户端显示的通过标记不是授权依据。

### 7.2 默认零能耗；自定义能量按实际扣除校准

默认规则补齐星位锚后直接完成本级，不再等待额外能量。下列按 tick 校准流程只适用于
整合包自行设置正 `energy` 的规则；`auto_build` 与能量独立，可组合使用。

校准由核心的 `serverTick()` 执行，不依赖菜单保持打开。每 tick 请求量为
`min(当前 ae_per_tick, max(0, 当前 energy - 已付 AE))`，通过真实 ME 网络的
`extractAEPower(..., Actionable.MODULATE, PowerMultiplier.ONE)` 扣能；只有实际抽到的 AE 才计入进度。
网络仍在线但供能不足时，能抽到多少就记多少，零抽取不增加已付 AE。
`energy: 0` 仍须手动开始且通过条件与最后复核，不会因此自动开始或跳级。

已付 AE 不是挂机时长、模拟抽取或机器历史耗能。原有研究、节点、星位锚的费用不能抵扣校准。
升级校准不扣星讯流，也不消耗研究次数、设备或节点；节点购买、闲置耗能与可选超频仍单独付费。
天体仪超频、生产速度和构筑井并行都不会加快校准。

### 7.3 手动暂停与自动阻塞不同

| 情况 | 当前任务的行为 |
| --- | --- |
| 点击暂停 | 保留目标和已付 AE，清除运行意愿；条件恢复也不会自行继续，须显式点击继续。 |
| 断电、掉频道、结构损坏、实时条件未满足、规则缺失或冲突 | 不推进、不扣校准能量，但保留目标、已付 AE 和运行意愿；恢复合格后自动继续同一级。 |
| 关闭界面、切换页签或预览别的等级 | 不开始、不暂停、不取消任务。只要核心仍在服务端 tick 且条件有效，就继续校准。 |

自动阻塞期间也可手动暂停（仍须有操作权限），之后便须显式继续。
区块卸载或服务端停止时不补算离线进度；这也不意味着强制加载第三方依赖设备。

### 7.4 `/reload` 使用当前规则，不清空已付进度

运行中的任务每 tick 重新查询当前配方，不保存旧规则来放行。重载后的条件、研究定义、
`energy` 与 `ae_per_tick` 立即适用于当前未完成任务：涨价只补差额，降价不会退款；
若新费用已不高于已付 AE，可在下一次合格的运行 tick 通过最终复核完成，手动暂停的任务仍须先继续。
已实际付出的 AE 不因重载被抹掉或返还，规则缺失或冲突也只会自动阻塞，修复后按保留的运行意愿恢复。

已有认证不被追溯撤销，无需按新规则重付；已认证等级的条件列表只是当前规则参考。
结构损坏会降低实物环数与有效等级，但不清空认证，修好后按有效等级公式恢复。

### 7.5 按核心保存，旧档等待实物复核

认证与未完成任务属于这一颗核心，不是团队共享的永久节点位。核心 NBT 的
`star_orrery_upgrade` 保存 `version`、`certified_level`、`target_level`、`paid_ae`、
`running`（运行意愿，不代表当前一定能抽能）和 `migration_pending`。
绑定团队 UUID 另存于 `entropy_team`；不会用当前观看玩家的团队替换它。

- 世界保存、区块卸载和重启保留这些状态。整机回收的原生档案也保留核心数据，重新部署后再验证实物。
- 核心的普通掉落和 AE2 拆卸物品通过 `minecraft:custom_data` 携带升级状态与绑定团队，
  重新放置时恢复；物品数据不授予“已成型”状态，仍须现场复核中央、基座与星环。
- 没有 `star_orrery_upgrade` 的旧存档和旧档案进入待迁移状态，不是永久重置为 L3，
  也不凭保存的成型标记、环数提示或包裹等级直接授予高阶认证。只有中央、基座通过现场验证，
  且运行时星环扫描得到确定结果后，才补认当时实际连续完整的环数：认证上限为
  `max(3, 已验证实物环数)`。其中 3 是前三阶自动规则的上限，并不是免费补出三环实物。
  扫描尚未确定时继续等待，不能靠旧记录补认 L4–L9；迁移完成后的新等级仍按正常流程逐级校准。

### 7.6 配置或目标无效时不放行

- 同级多份：最终 RecipeManager 中同一等级有两份不同 ID 的声明，即使内容完全一样也算冲突；
  不选择字典序第一份 / 最后一份，不把多份隐式合并。用相同配方 ID 的数据包覆盖不会产生两份。
- 缺少 / 无效配置：L4–L9 找不到唯一合法声明时不能开始或继续该级校准，不回退旧声明或隐藏默认规则。
  非法 JSON / schema 会有加载错误，先检查服务端日志。
- 目标删除或不可用：研究定义被删、方块不存在、tag 不存在或为空、FTB 对象 / 团队数据缺失，均不能拿
  存档中的旧完成结果当通过；未知节点同样失败。正常的 `any` / `at_least` 只允许其他真正通过的项替代。
- 实时数据：同网活跃控制器、研究次数、团队节点与 FTB 状态均按当前服务端求值；不以客户端勾选框、
  当前观看者身份或以前打开过 GUI 的状态作为认证证据。

`auto_build: true` 只补齐本机所需星位锚，不造其他机器、不代做研究、不购买节点、不提交 FTB 任务。
默认升级不消耗、也不生产星讯流；生存星讯由独立的星海解算主楼生产。
节点购买、闲置耗能与超频仍按各自原有规则结算。
