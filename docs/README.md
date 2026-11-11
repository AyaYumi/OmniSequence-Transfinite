# Author reference: recipes, research and integration APIs / 作者参考

For OmniSequence **21.3.0.0**, Minecraft **1.21.1 / NeoForge**, Java **21**.

These documents help **modpack authors** change recipes and research, and **mod authors**
connect their machines to OmniSequence's crafting system. For installation, device choices
and your first well, use the [player README](../README.md) and the in-game guide (**G**).
Players do not need to implement these APIs.

这里主要面向**整合包作者与模组开发者**。想安装模组、选择设备或搭第一座井，请先看
[中文玩家指南](../README.zh-CN.md)，游戏内对物品按 **G** 打开指南；下面的字段与接口不是玩家必读内容。

## Choose by what you want to do / 按用途选择

| Goal / 用途 | English | 中文 |
| --- | --- | --- |
| Add well recipes, per-well research or another machine's recipe imports / 添加井内配方、研究、整机工艺导入 | [Well and research API](matter-research-api.md) | [构筑井与研究 API](matter-research-api.zh-CN.md) |
| Require team star research before crafting selected recipes / 用团队研究限制指定配方 | Chinese guide → | [配方权限](recipe-permissions.zh-CN.md) |
| Add permanent star research to the Recipe Tree / 给配方树添加永久星算研究 | Chinese guide → | [星算研究节点](star-research-nodes.zh-CN.md) |
| Change Orrery upgrade requirements / 修改天体仪升级材料和条件 | [Star Orrery upgrades](star-orrery-upgrades.md) | [天体仪升级规则](star-orrery-upgrades.zh-CN.md) |
| Let an AE provider receive a whole batch in one transaction / 让 AE 供应器一次接收整批材料 | [Batch provider SPI](omni-batch-provider-api.md) | [批量供应器 SPI](omni-batch-provider-api.zh-CN.md) |
| Support exact counts above `long` / 支持超出 long 的精确数量 | [Exact-count API](omni-exact-provider-api.md) | 同页中文说明 |

Global power, production and visual options are in [Configuration](../CONFIGURATION.md),
not recipe JSON. Data-pack and KubeJS examples describe optional custom rules unless
explicitly labeled as defaults.

**Keep the research systems distinct:** well research belongs to each well and unlocks its
processes; star research is a team-permanent permission; Orrery certification belongs to
one core and limits its usable ring level. Default star research has only **Single-Block
Integration** (`molecularmanipulator:material`): actual L3, zero information cost, no prerequisite,
and manual unlocking. Its sole bundled lock targets only the two single-block recipes,
`molecular_manipulator` and `transfinite_compute_nexus`; it does not lock the Well, Sequence
Array or Omni Computation multiblock controllers. Machine imports retain the separate
requirement of three completions in **each** Tier III well branch.

## Versions and integration boundaries

Current source: OmniSequence **21.3.0.0**, Minecraft **1.21.1**, NeoForge **21.1.220+**,
Java **21**, AE2 reference version **19.2.18**, AppliedEnhancements **1.1.2+**
(compile API **1.1.4**). Mod ID: `molecularmanipulator`.
The loader metadata admits AE2 **19.2.17+**; version-specific internal integrations in
these documents target **19.2.18**, not every future AE2 release.

AELIS planning, cycle execution and shared AE enhancements belong to the separate
AppliedEnhancements mod. Use its [API integration guide](https://github.com/AyaYumi/AppliedEnhancements/blob/1.21.1-neoforge/docs/API_INTEGRATION.md), matching the API version to the JAR you actually install.
This branch compiles against 1.1.4 and supports runtime 1.1.2 or later. Omni's
internal planner/Mixin classes are not integration entry points.

Optional ECO integration requires **21.2.10+**. The additional ExtendedAE Plus
provider-selection/upload hooks target **1.6.4.1 only**; the Data Energistics depot
capacity-simulation fix targets **3.3.0 and 3.3.5 only**. AdvancedAE's optional recipe-lock
adapters target the Reaction Chamber and Quantum Crafter in **1.6.12-1.21.1 only**.
These version-specific hooks do not add public APIs or make those mods mandatory.
See [dependency setup](../libs/README.md) and the detailed permission guide for coverage.

Use `compileOnly` with the separate JARs and install each mod at runtime. Do not
shade or copy their API packages. Optional compatibility classes must load only
when the corresponding mod is present. Keep the original AE provider object when
registering a capability. Mutate research on the owning server thread; integrations
must enforce their own permissions. Live game-state queries also belong on that
thread unless the caller has obtained an immutable snapshot.

## Java contract selection / Java 接口选择

- Atomic long-count delivery: the two-stage batch provider SPI, or its optional adapter registry; runtime ABI **1**.
- Exact logical counts above `long`: the BigInteger capability and exact-output contracts.
- Per-well permissions and progress: `MatterResearchApi`; global star-research recipe locks remain an additional check.
- Cycle seeds and batch rollback: AppliedEnhancements' `AelisBatchExecutionContext`; first extraction, extra materials and refunds share its inventory view.

使用 AppliedEnhancements 1.1.4 编译，运行时最低要求 1.1.2；前置单独安装，不嵌入本模组。
`blockentity`、`mixin` 和渲染内部类不承诺通用二进制兼容。配方使用许可、长期材料归属和
AELIS 计划是不同契约，不能相互替代。
