# Author reference / 作者参考

OmniSequence **20.3.0.1** for Minecraft **1.20.1 / Forge**, Java **17**.

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

Well research belongs to each well and unlocks its processes. Star research is a permanent team permission. Orrery certification belongs to one core and limits its usable ring level. The default star research is **Single-Block Integration** (`molecularmanipulator:material`): actual L3, zero information cost, no prerequisites, and a manual unlock at a formed, online, own-team engine. Its bundled lock covers only `molecular_manipulator` and `transfinite_compute_nexus`. Machine imports separately require three completions in each Tier III well branch. Built-in well research retains six tiers and nine rounds per research. Neither research depth nor declarations replace real ingredients, power or output capacity.

井内研究、团队星算许可和天体仪认证各自保存，不能互相替代。默认星算许可只额外限制分子构序重写阵列和超限算枢，不限制构筑井、构序阵列和多方块万物演算核心。井内仍为六阶段，每项内置研究九轮，整机导入要求两条Ⅲ阶各累计三轮。研究前置与配方材料不是 TOML 参数，须通过数据包或 KubeJS 调整；示例中未标明为默认的规则，需要作者自行添加。

## Versions and Java contracts / 版本与 Java 契约

Current branch: **1.20.1-forge**, OmniSequence **20.3.0.1**, Minecraft **1.20.1**,
Forge reference **47.4.20** (metadata minimum **47.4.10**), Java **17**, AE2 **15.4.10**,
ExtendedAE **1.20-1.4.20-forge** (`expatternprovider`), GuideME **20.1.15**,
AppliedEnhancements **1.1.2-forge+** (compile API **1.1.4-forge**). Mod ID: `molecularmanipulator`.
The AE2 metadata range is **[15.4.10, 16)**; this is not a promise of compatibility with
all future 15.x internal implementations.

AELIS planning and shared cycle transactions belong to the separately installed AppliedEnhancements mod. Use its [API integration guide](https://github.com/AyaYumi/AppliedEnhancements/blob/1.20.1-forge/docs/API_INTEGRATION.md) for the version you install. OmniSequence's internal planner and Mixin classes are not public integration APIs.

- Use the two-stage batch provider SPI, or its adapter registry, for atomic `long`-count delivery. Runtime ABI: **1**.
- Use the BigInteger capability and exact-output contracts for larger logical counts.
- Use `MatterResearchApi` for per-well progress and recipe permissions. Global star-research locks remain an additional check.
- Use AppliedEnhancements' `AelisBatchExecutionContext` for cycle seeds, extra material extraction and refunds in the same inventory view.

Use `compileOnly` for separate mods' APIs and install those mods at runtime. Keep optional compatibility classes from loading when their mod is absent. Preserve the original AE provider object when registering its capability. Do not bundle dependency classes or copy their API packages. Dependency setup is in [libs/README.md](../libs/README.md).

Research mutations belong on the owning server thread. Live queries also belong there unless the caller has an immutable snapshot. Integrations must check their own permissions. Internal `blockentity`, `mixin` and renderer classes do not promise binary compatibility.

使用 AppliedEnhancements 1.1.4-forge 编译，运行时最低要求 1.1.2-forge；前置单独安装，不嵌入本模组。研究许可、材料归属和 AELIS 计划是不同契约；调用方仍须检查权限，并在所属服务端线程操作实时状态。

Optional integrations have these version limits:

| Integration | Supported target |
| --- | --- |
| NeoECOAE | Runtime 20.4.2+; native batch/exact APIs require 20.5.0+ Forge API; older supported builds use ordinary AE dispatch |
| ExtendedAE Plus provider-selection and well-upload hooks | Forge 1.6.4, including renamed assemblies; native Array upload/migration and well uploads do not require it |
| Data Energistics depot capacity simulation | 3.3.0 / 3.3.5 and the required runtime methods; never enlarges real storage |
| Trinity / Thunderbolt private dispatch hooks | Matching runtime APIs; compile-only CPU ABI declarations provide no implementation |
| AdvancedAE Reaction Chamber and Quantum Crafter recipe locks | Exact Forge 1.3.6-1.20.1 build |
| JEI | Forge reference 15.49.0.188 |
| FTB quest reflection | v2001.4.9 / Forge 1.20.1 |

The supplied pack uses **ECO 20.5.5**, **ExtendedAE Plus 1.6.4** and **AppliedEnhancements 1.1.6**; supported runtime versions are listed above. Optional CPU ABI declarations are excluded from runtime classpaths and release JARs, so install the actual Forge implementations separately. These integrations are optional, and their internal hooks add no public APIs. Do not install NeoForge JARs to satisfy them. See the individual references for execution and ownership rules.

### Forge resource/API differences

Use **`recipes/`, `loot_tables/`, `tags/items/`, `tags/blocks/`, `structures/`** and data-pack
format **15**. Vanilla recipe results use **`item`**, not 1.21's `id`; Forge item/fluid
metadata uses **NBT (`nbt`)**, not data components. Custom plural `results` and `fluid_result`
follow the actual Forge serializers and accept the aliases described in the well API.
Forge Java recipes expose `Recipe.getId()` directly, not 1.21 `RecipeHolder`.

Star upgrades use `requirements` for the gameplay condition tree. Forge reserves root
`conditions` for a load-time array and reads it before the custom serializer. Do not copy
the source branch's `conditions` object into Forge declarations. The bundled L4 to L9 declarations
and the examples use `requirements` throughout.

Permission, node, upgrade, crafting and research references are also packaged in the release JAR.

## Configuration / 配置

Gameplay settings are in `config/omnisequence-transfinite-server.toml`. Despite its name, this is an instance-wide Forge **COMMON** file, not a world's `serverconfig/` file. In multiplayer, edit the server's copy. Rendering settings are in each client's `config/omnisequence-transfinite-client.toml`. Names and help text are available in English and Chinese.

Launch once to create the files, stop the game or server, edit the existing tables, then save and restart. Do not duplicate TOML sections. `/reload` reloads data packs, not these settings. Shared AE2/AELIS settings belong to AppliedEnhancements' own configuration.

玩法配置为实例级 Forge COMMON 文件。联机时修改服务端文件，客户端值不会覆盖服务端产量。先运行一次生成配置，停服或退出单机后修改已有节，保存并重启；不要重复添加同名节。`/reload` 不负责加载 TOML；AE2/AELIS 的共享设置属于 AppliedEnhancements 自身配置。

### Server fields / 服务端字段

| Path | Default | Range and behavior |
| --- | --- | --- |
| `sequence_array.pattern_pages` | 200 | 1 to 300; 36 pattern slots per page |
| `sequence_array.build_blocks_per_tick` | 32 | 1 to 256 blocks/tick; Sequence Array construction and dismantling only |
| `sequence_array.idle_power` | 128 | 1 to 4096 AE/t |
| `multiblocks.force_load_chunks` | true | Mod-owned tickets for formed/building multiblock footprints; not permission for hub motion; White Holes are independent |
| `singularity_hub.singularity_collection.item_tags` | `#forge:raw_materials`, `#c:raw_ores`, `#c:raw_materials`, `#minecraft:raw_ores`, `#minecraft:logs`, `#c:logs` | Item tags; `#` optional; merged and deduplicated; an empty list disables collection |
| `singularity_hub.singularity_collection.item_blacklist` | `[]` | Exact item IDs; overrides tags |
| `singularity_hub.singularity_collection.batch_size` | 1000 | 1 to 1,000,000 of each eligible item per cycle |
| `singularity_hub.singularity_collection.interval_ticks` | 20 | 1 to 1200 ticks |
| `singularity_hub.singularity_duplication.item_blacklist` | `[]` | Exact item IDs blocked from copying on both hubs, including all NBT variants; independent of collection and fluid generation |
| `singularity_hub.singularity_duplication.energy_priority` | `["fe", "ae"]` | Nonempty fe/ae list, case-insensitive; try in order; FE uses adjacent energy capabilities, AE uses the connected grid |
| `singularity_hub.singularity_duplication.fe_per_unit` | 1000 | 1 to 2,147,483,647 FE/mB |
| `singularity_hub.singularity_duplication.ae_per_unit` | 256 | 1 to 2,147,483,647 AE/mB |
| `singularity_hub.singularity_duplication.matter_per_black_hole` | 20 | 1 to 1,000,000 mB per hole per cycle |
| `singularity_hub.singularity_duplication.interval_ticks` | 20 | 1 to 1200 ticks; missed cycles are not repaid |
| `omni_computation.dispatch.omni_batch_dispatch_enabled` | true | Batch material transfers to compatible providers; false uses single-craft transfers |
| `omni_computation.dispatch.omni_compat_dispatch_max_calls_per_tick` | 2147483647 | 256 to 2,147,483,647 complete single-craft calls per controller per tick |
| `omni_computation.dispatch.omni_compat_dispatch_max_time_us` | 50000 | 250 to 50000 microseconds/tick shared by all active Omni cores; reduces as average server MSPT approaches 45 |
| `omni_computation.dispatch.omni_dispatch_max_work_units` | 2147483647 | 64 to 9,223,372,036,854,775,807 per controller/tick; each input extraction and provider attempt costs one unit, regardless of batch size |
| `omni_computation.dispatch.omni_coalesce_return_notifications` | true | Combine per-resource return notifications after exact ledger settlement |
| `omni_computation.dispatch.omni_direct_native_output_return` | true | Return UselessMod intermediate output to its live-bound CPU on the same grid; unsupported queues use normal return |
| `transfinite_compute_nexus.idle_power` | 16384 | 1 to 2,147,483,647 AE/t; also needs a powered network and one channel |
| `star_sea_calculation.output_bits_per_tick` | 128 | 1 to 1,000,000 bits per operating local tick |
| `star_sea_calculation.energy_per_tick` | 1024.0 | Finite double, 0 to 2,147,483,647 AE per operating local tick; zero removes the production charge |
| `star_sea_calculation.detached_signal_output_multiplier` | 8 | 1 to 1,000,000; multiplies output in the Detached Signal Realm without multiplying per-tick energy cost |

The mainframe still needs its complete building, a valid night or realm environment, roof light at most 7, an active ME network, one channel and signal storage. Setting production energy to zero does not remove those requirements. The realm's half-speed/double-speed local simulation separately affects production per real second.

`build_blocks_per_tick` 仅作用于构序阵列。采集默认包含 Forge 的 `#forge:raw_materials`。星海解算主楼按每个有效本地工作刻计产量与耗能：默认 128 bit、1024 AE，离讯之境默认 8 倍产出但不增加每刻能耗。能耗可用小数；设为 0 仍须建筑完整、满足夜间／离讯之境及屋顶光照 ≤7、ME 在线有电、一个频道和星讯存储空间。半速／双速时间另外改变每秒工作次数。

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

Blacklists take exact `namespace:item` IDs, without `#` or wildcards. Unknown valid IDs can remain for optional mods. Collection produces every remaining tagged type at once. Production and the displayed list use the same server-side filtered list; client-local tags or configuration do not replace it. The server sends the resource list, batch size and interval to clients; large lists replace the old display after all parts arrive. A live interval change starts a fresh cycle.

The copying blacklist is separate from collection and defaults to `[]`. Both multiblock and compact hubs block all NBT variants, including renamed samples. Listed samples remain retrievable, and refusing a copy does not consume Sequence Matter. Black-hole fluid production continues, as do delivery of finished outputs and refunds. Copying is limited to one item per tick and requires item storage and 1,000 mB of stored matter. The duplication interval controls fluid generation, not copying speed.

Sequence Matter per cycle is the hole count multiplied by `matter_per_black_hole`. With 64 holes, defaults produce 1,280 mB for either 1,280,000 FE or 327,680 AE. FE comes from six adjacent energy capabilities; AE comes from the connected grid. A storage disk does not supply energy. Fluid and item storage need free space. A copy spends stored fluid without charging its generation energy again. A 20-tick cycle takes one second at 20 TPS; unloaded or missed cycles are not repaid. Output and fluid refunds wait for storage space.

采集名单按标签合并去重后再应用物品 ID 黑名单，空标签名单不生产；同一物品不会重复生产。采集页与生产使用相同的服务端过滤列表、产量及间隔，不读取客户端本地配置；大型列表完整接收后才替换显示。复制黑名单独立生效，默认 `[]`，覆盖重命名及其他 NBT 变体；例如 `["minecraft:dragon_egg", "minecraft:nether_star"]`，不支持标签或通配符。样品可以取回，拒绝复制不扣已存序质，黑洞制液、已完成产物与退款不受影响。复制每件消耗 1,000 mB（1 B）已存序质，最多每 tick 一件；采集参数不控制复制速度，复制分组的间隔控制制液。实际仍需能源及物品／流体存储空间。

### Chunk loading and connections / 区块加载与连接

`multiblocks.force_load_chunks` controls registered multiblock chunk tickets. With it disabled, a Singularity Hub can still start, move and dock when its entire motion footprint is already loaded by players or another loader. Missing chunks prevent startup or docking and pause active motion until loading resumes. These checks do not synchronously load missing chunks. Obstacle and permission checks still apply. Miniature White Holes keep their own chunks loaded regardless of this setting.

Sequence Wireless Hubs add no chunkloading. Both ends must be loaded. Machines retain their existing multiblock loading behavior. Wireless receivers must remove external ME cables and any quantum-slot singularity; both the hub and receiver must belong to the same team. The selector shows only that team's loaded hubs. Moving or replacing a hub requires rebinding.

Loaded Singularity quantum endpoints reconnect through normal AE2 initialization after world or chunk reloads. Destroyed, unloaded or retuned rings do not count as live endpoints because of stale registry entries. An occupied pair still reports a conflict. Paired singularities, a loaded remote ring, power and channels remain required.

关闭多方块加载票据后，只要整个运动范围已经加载，天枢仍可启动、运动和归位；缺少区块时禁止启动／停靠，并暂停运动，等待恢复，不同步加载缺失区块。障碍与权限检查仍适用。微型白洞继续加载自身区块。无线连接本身不加载区块，也不会代替队伍、供电或频道检查。天枢量子端点在世界／区块重载后按 AE2 正常初始化重连；已销毁、未加载或换频的环不会因旧索引误报占频，真实冲突仍拒绝连接。

### Client effects / 客户端特效

| Path | Default | Values |
| --- | --- | --- |
| `visual.dynamic_effect_level` | 2 | Client default: 0 off, 1 reduced, 2 full; controller overrides take priority |

This setting controls large multiblock effects. Item and fluid texture-atlas animations remain active; view bobbing can stay enabled. Array, computation-core, well and Singularity controller screens also offer an **Effects** button. The Array button is on its colors tab. A machine's explicit setting is saved on that controller and shared with viewers. Otherwise, it uses each client's default. Shift-click or Shift-Enter/Space restores the client default. These settings do not change production.

特效档位为 0 关闭、1 精简、2 完整。各控制器可单独设置，构序阵列按钮在配色页。设置保存在本机并同步给观看者，未单独设置时沿用各观看者的客户端默认。Shift 点击或 Shift+Enter／空格恢复沿用，不影响产量。贴图动画独立于此档位，可保持视角摇晃开启。

### Pattern handling / 样板处理

Well uploads consume one blank pattern from the encoding terminal, or one extractable blank from its connected ME storage if the local slot is empty. One network blank is sufficient. Wells and assemblies are identified by loaded machine type, formation and team, not custom names. Additional Forge ExtendedAE Plus **1.6.4** automatic-selection/upload hooks recognize renamed assemblies by machine type and team. Native well uploads do not require the addon.

Array **Auto upload** is native and needs no EAEP core. It receives newly encoded crafting, smithing and stonecutting patterns from native AE2 terminals on the same network. New Arrays start with it off. Processing patterns remain in the terminal. If the Array is full or unavailable, the encoded item stays there; duplicate recipes return a blank. A decodable old core in a removed slot enables upload and returns through the existing ME output buffer unless an explicit saved setting takes precedence. There is no new global upload config key.

**Migrate patterns** runs once per click, synchronously on the server thread. It reads same-network public physical slots through `PatternContainer` and online mounted cells through `IChestOrDrive` / `StorageCell`. It preserves NBT and duplicate physical patterns; no re-encoding or blank is needed. Processing patterns, private slots, read-only recipe rows and storage-bus proxies stay at their source, as do patterns that do not fit. Counts report committed transfers and partial completion; interrupted runs keep completed transfers. Large migrations can take server time.

上传由编码触发，迁移由点击触发。两者都不会在满库或设备未就绪时丢弃样板。构序阵列原生上传无需 EAEP，新阵列默认关闭；旧槽可识别核心经输出缓存退回并保留开启行为，玩家已保存开关优先，没有新的全局上传参数。迁移保留实体样板、NBT 与重复实体，不重编码、不消耗空白样板；只读行、处理样板、私有槽和存储总线代理不参与。满库留在来源，中断保留已完成转移，大型迁移会占用服务端时间。井上传可用终端或相连网络中的一张空白样板，不依赖总成名称；Forge EAEP 自动选择／上传钩子针对 1.6.4，按机器类型和队伍识别。

### Configuration migration / 配置迁移

Legacy ore/log tag lists merge into `item_tags` without injecting new defaults into existing custom or empty lists. Existing `item_tags`, including an empty list, takes precedence. Old collection and duplication groups move from `sequence_array` to `singularity_hub`; chunk loading moves to `multiblocks`. Retired Sequence Array Matter Rewrite, entropy-currency and Shift-tooltip options are removed, along with `omni_profile_exact_returns` and `omni_return_profile_sample_interval` before schema validation. The current star and negative-entropy systems remain. Valid values survive schema repair. Migration backs up files before changing them. Values lost to an earlier reset need restoration from a backup or manual entry; migration cannot recover discarded values.

Recurring runtime failures and load warnings keep their first report and share a global one-minute window per fixed message template across machines. Startup migration messages describe changes. JEI reports a missing ingredient converter once per AE resource type. Successful crafting and routine output delivery do not write trace logs.

迁移会先备份配置，已有新字段优先，空列表也会保留；旧矿物／原木标签合并后不会向自定义或空列表注入默认值。旧分组自动移动，废弃构序重写、熵货币、Shift 提示与性能分析选项不再生成，当前星算和负熵系统仍保留。有效值在修复中保留；此前已经重置丢弃的值需从备份恢复或重新填写。重复运行故障和加载警告按固定消息模板全局限流，正常合成和输出回收不会持续写日志。
