# Author reference / 作者参考

OmniSequence **21.3.0.1** for Minecraft **1.21.1 / NeoForge**, Java **21**.

Use these references to add recipes and research, change server settings, or connect another mod's machines to OmniSequence. For installation and gameplay, start with the [English README](../README.md), [中文 README](../README.zh-CN.md) or the in-game guide, opened with **G**.

这里面向整合包作者、服主和模组开发者，保留配方格式、配置字段与 Java 接口。玩家可以直接使用默认配置，按游戏内指南搭建和使用设备。

## Recipes and integration APIs / 配方与集成接口

| Task / 用途 | English | 中文 |
| --- | --- | --- |
| Add well recipes, research or machine recipe imports / 添加井内配方、研究、机器工艺导入 | [Well and research API](matter-research-api.md) | [构筑井与研究 API](matter-research-api.zh-CN.md) |
| Require team research for selected recipes / 为指定配方添加团队研究条件 | See Chinese reference | [配方权限](recipe-permissions.zh-CN.md) |
| Add permanent research to the Recipe Tree / 添加永久星算研究 | See Chinese reference | [星算研究节点](star-research-nodes.zh-CN.md) |
| Change Orrery upgrade requirements / 修改天体仪升级材料与条件 | [Orrery upgrades](star-orrery-upgrades.md) | [天体仪升级规则](star-orrery-upgrades.zh-CN.md) |
| Accept a whole batch through an AE provider / 让 AE 供应器一次接收整批材料 | [Batch provider SPI](omni-batch-provider-api.md) | [批量供应器 SPI](omni-batch-provider-api.zh-CN.md) |
| Support exact counts above `long` / 支持超出 long 的精确数量 | [Exact-count API](omni-exact-provider-api.md) | 同页含中文说明 |

Well research belongs to each well and unlocks its processes. Star research is a permanent team permission. Orrery certification belongs to one core and limits its usable ring level. The default star research is **Single-Block Integration** (`molecularmanipulator:material`): actual L3, zero information cost, no prerequisites, and a manual unlock. Its bundled lock covers only `molecular_manipulator` and `transfinite_compute_nexus`. Machine imports separately require three completions in each Tier III well branch.

井内研究、团队星算许可和天体仪认证各自保存，不能互相替代。默认星算许可只额外限制分子构序重写阵列和超限算枢，不限制构筑井、构序阵列和多方块万物演算核心。数据包与 KubeJS 示例中未标明为默认的规则，需要作者自行添加。

## Versions and Java contracts / 版本与 Java 契约

This branch uses NeoForge **21.1.220+**, AE2 **19.2.18** as its reference, and AppliedEnhancements **1.1.4** for compilation. The runtime minimum for AppliedEnhancements is **1.1.2**. The loader accepts AE2 **19.2.17+**, while version-specific internal hooks target **19.2.18**. Mod ID: `molecularmanipulator`.

AELIS planning and shared cycle transactions belong to the separately installed AppliedEnhancements mod. Use its [API integration guide](https://github.com/AyaYumi/AppliedEnhancements/blob/1.21.1-neoforge/docs/API_INTEGRATION.md) for the version you install. OmniSequence's internal planner and Mixin classes are not public integration APIs.

- Use the two-stage batch provider SPI, or its adapter registry, for atomic `long`-count delivery. Runtime ABI: **1**.
- Use the BigInteger capability and exact-output contracts for larger logical counts.
- Use `MatterResearchApi` for per-well progress and recipe permissions. Global star-research locks remain an additional check.
- Use AppliedEnhancements' `AelisBatchExecutionContext` for cycle seeds, extra material extraction and refunds in the same inventory view.

Use `compileOnly` for separate mods' APIs and install those mods at runtime. Keep optional compatibility classes from loading when their mod is absent. Preserve the original AE provider object when registering its capability. Do not bundle dependency classes or copy their API packages. Dependency setup is in [libs/README.md](../libs/README.md).

Research mutations belong on the owning server thread. Live queries also belong there unless the caller has an immutable snapshot. Integrations must check their own permissions. Internal `blockentity`, `mixin` and renderer classes do not promise binary compatibility.

使用独立 JAR 的公开接口集成，不把前置打包进本模组。研究许可、材料归属和 AELIS 计划是不同契约；调用方仍须检查权限，并在所属服务端线程操作实时状态。

Optional integrations have these version limits:

| Integration | Supported target |
| --- | --- |
| Neo ECO AE Extension | 21.2.10 or later |
| ExtendedAE Plus provider-selection and well-upload hooks | 1.6.4.1 only |
| Data Energistics depot capacity simulation | 3.3.0 and 3.3.5 only |
| AdvancedAE Reaction Chamber and Quantum Crafter recipe locks | 1.6.12-1.21.1 only |

These hooks do not make the optional mods required or add new public APIs. See the individual references for their execution and ownership rules.

## Configuration / 配置

Gameplay settings are in `config/omnisequence-transfinite-server.toml`. Despite its name, this is an instance-wide NeoForge **COMMON** file, not a world's `serverconfig/` file. In multiplayer, edit the server's copy. Rendering settings are in each client's `config/omnisequence-transfinite-client.toml`.

Launch once to create the files, stop the game or server, edit the existing tables, then save and restart. Do not duplicate TOML sections. `/reload` reloads data packs, not these settings. Shared AE2/AELIS settings belong to AppliedEnhancements' own configuration.

玩法配置为实例级 COMMON 文件。联机时修改服务端文件，客户端值不会覆盖服务端产量。先运行一次生成配置，停服或退出单机后修改已有节，保存并重启；不要重复添加同名节。

### Server fields / 服务端字段

| Path | Default | Range and behavior |
| --- | --- | --- |
| `sequence_array.pattern_pages` | 200 | 1 to 300; 36 pattern slots per page |
| `sequence_array.build_blocks_per_tick` | 32 | 1 to 256; construction and dismantling blocks per tick |
| `sequence_array.idle_power` | 128 | 1 to 4096 AE/t |
| `multiblocks.force_load_chunks` | true | Mod-owned tickets for registered multiblock footprints |
| `singularity_hub.singularity_collection.item_tags` | `#c:raw_ores`, `#c:raw_materials`, `#minecraft:raw_ores`, `#minecraft:logs`, `#c:logs` | Item tags; `#` optional; merged and deduplicated; an empty list disables collection |
| `singularity_hub.singularity_collection.item_blacklist` | `[]` | Exact item IDs; overrides tags |
| `singularity_hub.singularity_collection.batch_size` | 1000 | 1 to 1,000,000 of each eligible item per cycle |
| `singularity_hub.singularity_collection.interval_ticks` | 20 | 1 to 1200 ticks |
| `singularity_hub.singularity_duplication.item_blacklist` | `[]` | Exact item IDs blocked from copying on both hubs, including every component variant |
| `singularity_hub.singularity_duplication.energy_priority` | `["fe", "ae"]` | Try energy sources in this order |
| `singularity_hub.singularity_duplication.fe_per_unit` | 1000 | 1 to 2,147,483,647 FE/mB |
| `singularity_hub.singularity_duplication.ae_per_unit` | 256 | 1 to 2,147,483,647 AE/mB |
| `singularity_hub.singularity_duplication.matter_per_black_hole` | 20 | 1 to 1,000,000 mB per hole per cycle |
| `singularity_hub.singularity_duplication.interval_ticks` | 20 | 1 to 1200 ticks; missed cycles are not repaid |
| `omni_computation.dispatch.omni_batch_dispatch_enabled` | true | Enable safe batch dispatch |
| `omni_computation.dispatch.omni_compat_dispatch_max_calls_per_tick` | 2147483647 | 256 to 2,147,483,647 complete fallback calls per controller per tick |
| `omni_computation.dispatch.omni_compat_dispatch_max_time_us` | 50000 | 250 to 50000 microseconds shared by active controllers; reduces as average server MSPT approaches 45 |
| `omni_computation.dispatch.omni_dispatch_max_work_units` | 2147483647 | 64 to Long.MAX_VALUE extraction/provider work units per controller per tick |
| `omni_computation.dispatch.omni_coalesce_return_notifications` | true | Notify each changed key after settling exact ledgers |
| `omni_computation.dispatch.omni_direct_native_output_return` | true | Return UselessMod intermediate output directly to its live-bound CPU on the same network |
| `transfinite_compute_nexus.idle_power` | 16384 | 1 to 2,147,483,647 AE/t; also needs a powered network and one channel |
| `star_sea_calculation.output_bits_per_tick` | 128 | 1 to 1,000,000 bits per operating local tick |
| `star_sea_calculation.energy_per_tick` | 1024.0 | Finite double, 0 to 2,147,483,647 AE per operating local tick; zero removes the production charge |
| `star_sea_calculation.detached_signal_output_multiplier` | 8 | 1 to 1,000,000; multiplies output in the Detached Signal Realm without multiplying per-tick energy cost |

The mainframe still needs its complete building, a valid night or realm environment, roof light at most 7, an active ME network, one channel and signal storage. Setting production energy to zero does not remove those requirements. The realm's changing local time speed separately affects production per real second.

### Resource production / 资源产出

```toml
[singularity_hub.singularity_collection]
item_tags = ["#c:raw_ores", "#minecraft:logs"]
item_blacklist = ["minecraft:raw_iron", "minecraft:oak_log"]
batch_size = 1000
interval_ticks = 20

[singularity_hub.singularity_duplication]
item_blacklist = []
energy_priority = ["fe", "ae"]
fe_per_unit = 1000
ae_per_unit = 256
matter_per_black_hole = 20
interval_ticks = 20
```

Blacklists take exact `namespace:item` IDs, without `#` or wildcards. Unknown valid IDs can remain for optional mods. Collection produces every remaining tagged type at once. The server sends the resource list, batch size and interval to clients; large lists replace the old display after all parts arrive. A live interval change starts a fresh cycle.

The copying blacklist is separate from collection. Listed samples remain retrievable, and refusing a copy does not consume Sequence Matter. Black-hole fluid production continues, as do delivery of finished outputs and refunds. Copying is limited to one item per tick and requires item storage and 1,000 mB of matter. The duplication interval controls fluid generation, not copying speed.

Sequence Matter per cycle is the hole count multiplied by `matter_per_black_hole`. With 64 holes, defaults produce 1,280 mB for either 1,280,000 FE or 327,680 AE. FE comes from adjacent energy capabilities; AE comes from the connected grid. Fluid and item storage need free space. A copy spends stored fluid without charging its generation energy again. A 20-tick cycle takes one second at 20 TPS; unloaded or missed cycles are not repaid. Output and fluid refunds wait for storage space.

采集名单按标签合并去重后再应用物品 ID 黑名单，空标签名单不生产。复制黑名单独立生效，覆盖重命名和其他组件变体；样品可以取回，黑洞制液不受影响。复制每件消耗 1,000 mB 已存序质，最多每 tick 一件，仍受原料、电力和输出空间限制。

### Chunk loading and connections / 区块加载与连接

`multiblocks.force_load_chunks` controls registered multiblock chunk tickets. With it disabled, a Singularity Hub can still move and dock when its entire motion footprint is already loaded. Missing chunks prevent startup or docking and pause active motion until loading resumes. Obstacle and permission checks still apply. Miniature White Holes keep their own chunks loaded regardless of this setting.

Sequence Wireless Hubs add no chunkloading. Both ends must be loaded. Machines retain their existing multiblock loading behavior. Wireless receivers must remove external ME cables and any quantum-slot singularity; both the hub and receiver must belong to the same team. The selector shows only that team's loaded hubs. Moving or replacing a hub requires rebinding.

Loaded Singularity quantum endpoints reconnect through normal AE2 initialization after world or chunk reloads. Destroyed, unloaded or retuned rings do not count as live endpoints because of stale registry entries. An occupied pair still reports a conflict. Paired singularities, a loaded remote ring, power and channels remain required.

关闭多方块加载票据后，只要整个运动范围已经加载，天枢仍可运动；缺少区块时暂停，等待恢复。微型白洞继续加载自身区块。无线连接本身不加载区块，也不会代替队伍、供电或频道检查。

### Client effects / 客户端特效

| Path | Default | Values |
| --- | --- | --- |
| `visual.dynamic_effect_level` | 2 | 0 off, 1 reduced, 2 full |

This setting controls large multiblock effects. Item and fluid animations remain active; view bobbing can stay enabled. Array, computation-core, well and Singularity controller screens also offer an **Effects** button. The Array button is on its colors tab. A machine's explicit setting is saved on that controller and shared with viewers. Otherwise, it uses each client's default. Shift-click or Shift-Enter/Space restores the client default. These settings do not change production.

特效档位为 0 关闭、1 精简、2 完整。各控制器可单独设置，未单独设置时沿用观看者的客户端默认。Shift 点击或 Shift+Enter／空格恢复沿用，不影响产量。

### Pattern handling / 样板处理

Well uploads consume one blank pattern from the encoding terminal, or its connected ME storage if the local slot is empty. Wells and assemblies are identified by loaded machine type, formation and team, not custom names. The ExtendedAE Plus 1.6.4.1 automatic-provider hook supports renamed assemblies.

Array **Auto upload** receives newly encoded crafting, smithing and stonecutting patterns from native AE2 terminals on the same network. New Arrays start with it off. Processing patterns remain in the terminal. If the Array is full or unavailable, the encoded item stays there; duplicate recipes return a blank. An old core in a removed slot enables upload and returns through the existing output buffer unless an explicit saved setting takes precedence.

**Migrate patterns** runs only when clicked. It reads public physical slots through `PatternContainer` and mounted cells through `IChestOrDrive` / `StorageCell`. It preserves duplicate physical patterns and all components. Processing patterns, private slots, read-only recipe rows and storage-bus proxies stay at their source, as do patterns that do not fit. Large migrations run synchronously and report moved counts and partial completion.

上传由编码触发，迁移由点击触发。两者都不会在满库或设备未就绪时丢弃样板。迁移保留实体样板及全部组件；处理样板、私有槽和存储总线代理不参与。

### Configuration migration / 配置迁移

Existing `item_tags`, including an empty list, take precedence over legacy ore/log tag fields. Old collection and duplication groups move from `sequence_array` to `singularity_hub`; chunk loading moves to `multiblocks`. Retired Matter Rewrite, entropy-currency and Shift-tooltip options are removed, along with `omni_profile_exact_returns` and `omni_return_profile_sample_interval`. The current star and negative-entropy systems remain. Migration backs up files before changing them. Values lost to an earlier reset need restoration from a backup or manual entry.

Recurring runtime failures keep their first report and share a one-minute window per fixed message template across machines. Startup migration messages describe changes. JEI reports a missing ingredient converter once per AE resource type. Successful crafting and routine output delivery do not write trace logs.

迁移会先备份配置，已有新字段优先，空列表也会保留。此前已经重置的值需要从备份恢复或重新填写。重复运行故障按固定消息模板限流，正常合成和输出回收不会持续写日志。
