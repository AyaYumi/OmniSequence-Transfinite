# Configuration / 配置参考

Current source: 2.1.0, Minecraft 1.21.1 / NeoForge, AE2 19.2.18. Names and help text are available in English and Chinese.

Global files live under the active instance's `config/` directory:

- `omnisequence-transfinite-server.toml`: common gameplay settings.
- `omnisequence-transfinite-client.toml`: rendering detail.

Edit the existing table once, save and restart the instance. Do not create
duplicate TOML sections. Settings shared with AE2/AELIS belong to the separate
AppliedEnhancements configuration.

## Common options

| Path | Default | Allowed / behavior |
| --- | --- | --- |
| `sequence_array.pattern_pages` | 200 | 1–300; 36 slots/page |
| `sequence_array.build_blocks_per_tick` | 32 | 1–256; construction/dismantling blocks per tick |
| `sequence_array.idle_power` | 128 | 1–4096 AE/t |
| `multiblocks.force_load_chunks` | true | Control mod-owned chunk tickets for registered multiblock footprints, not Singularity motion permission |
| `singularity_hub.singularity_collection.item_tags` | `#c:raw_ores`, `#c:raw_materials`, `#minecraft:raw_ores`, `#minecraft:logs`, `#c:logs` | Arbitrary item tags; optional `#`; merge and deduplicate; empty list disables production |
| `singularity_hub.singularity_collection.item_blacklist` | `[]` | Exact namespaced item IDs; overrides every tag |
| `singularity_hub.singularity_collection.batch_size` | 1000 | 1–1,000,000 items for each eligible type per cycle |
| `singularity_hub.singularity_collection.interval_ticks` | 20 | 1–1200 ticks |
| `singularity_hub.singularity_duplication.energy_priority` | `["fe", "ae"]` | Attempt sources in order; FE uses adjacent energy capabilities, AE uses the connected grid |
| `singularity_hub.singularity_duplication.fe_per_unit` | 1000 | 1–2,147,483,647 FE/mB |
| `singularity_hub.singularity_duplication.ae_per_unit` | 256 | 1–2,147,483,647 AE/mB |
| `singularity_hub.singularity_duplication.matter_per_black_hole` | 20 | 1–1,000,000 mB per hole per cycle |
| `singularity_hub.singularity_duplication.interval_ticks` | 20 | 1–1200 ticks; missed cycles are not caught up |
| `omni_computation.dispatch.omni_batch_dispatch_enabled` | true | Explicit safe batch dispatch |
| `omni_computation.dispatch.omni_compat_dispatch_max_calls_per_tick` | 2147483647 | 256–2,147,483,647; complete fallback calls per controller/tick safety ceiling |
| `omni_computation.dispatch.omni_compat_dispatch_max_time_us` | 50000 | 250–50000 microseconds; shared compatibility deadline |
| `omni_computation.dispatch.omni_dispatch_max_work_units` | 2147483647 | 64–Long.MAX_VALUE; extraction/provider work budget per controller/tick |
| `omni_computation.dispatch.omni_coalesce_return_notifications` | true | Notify each changed key after settling its exact ledgers |
| `omni_computation.dispatch.omni_direct_native_output_return` | true | Direct live-bound compatible native intermediate-output return |
| `transfinite_compute_nexus.idle_power` | 16384 | 1–2,147,483,647 AE/t; powered network and one channel |

## Chunk loading and Singularity motion

`multiblocks.force_load_chunks` controls this mod's chunk tickets for registered
multiblock footprints; it is not permission to move a Singularity Hub. Set it to
`false` to disable those tickets. The hub can still start, run and dock when every
chunk in its entire motion footprint is already loaded, for example by players
or another chunk loader.

If any required chunk is unavailable, motion cannot start or dock, and active
motion pauses/freezes until the required chunks are loaded again. These checks
do not synchronously load unavailable chunks. Obstacle and permission checks
still apply.

Miniature White Holes remain an exception: they keep their own chunks loaded
regardless of this setting.

## Client option

| Path | Default | Behavior |
| --- | --- | --- |
| `visual.dynamic_effect_level` | 2 | 0 off, 1 reduced, 2 full multiblock effects |

Texture-atlas item/fluid animations remain active independently of large-world
effect detail. Reduce this option when world effects cost too much render time.
View bobbing can stay enabled.

Each Sequence Array, Omni Computation Core, Fabrication Well and Singularity Hub controller
screen has an **Effects** button: 0 off, 1 reduced, 2 full. The Array button
is on its colors tab. An explicit choice is saved on that individual controller
and synchronized to every viewer; it does not change production or other machines.
Machines without an explicit choice inherit each client's `visual.dynamic_effect_level`.
Shift-click or Shift-Enter/Space restores that inheritance.

## Pattern upload and migration

- A Fabrication Well recipe upload consumes one blank pattern from the encoding
  terminal, or from its connected ME network when the local slot is empty. One
  network blank is sufficient. Destination identity uses the loaded, formed well
  and team, never its assembly's custom display name. ExtendedAE Plus 1.6.4.1's
  automatic provider selection also recognizes renamed assemblies by machine type.
- The Array overview has **Auto upload** and **Migrate patterns** buttons. Successful
  terminal encoding on the same network uploads crafting, stonecutting and smithing patterns to an
  enabled, operational Array. Processing patterns stay in the terminal. A full or
  unavailable Array leaves the encoded item there; duplicates return a blank.
- New Arrays default to auto upload off. A still-decodable core in an old removed
  slot enables auto upload and is returned through the existing ME output buffer;
  it is not discarded. An explicitly saved button setting takes precedence.
- Migration is one click, not continuous collection. It uses shared AE2 interfaces:
  `PatternContainer` for public physical pattern slots, and `IChestOrDrive` /
  `StorageCell` for mounted disks. Private slots, read-only recipe rows,
  storage-bus proxies and processing patterns stay untouched. No pattern is
  re-encoded; duplicate physical items and all components are preserved. Full-library
  leftovers remain at the source. The button reports moved counts and partial completion.
  Large migrations run synchronously as part of that click.

## Resource production example

```toml
[singularity_hub.singularity_collection]
item_tags = ["#c:raw_ores", "#minecraft:logs"]
item_blacklist = ["minecraft:raw_iron", "minecraft:oak_log"]
batch_size = 1000
interval_ticks = 20

[singularity_hub.singularity_duplication]
energy_priority = ["fe", "ae"]
fe_per_unit = 1000
ae_per_unit = 256
matter_per_black_hole = 20
interval_ticks = 20
```

Blacklist entries use `namespace:item`, without `#` or wildcards. Unknown valid
IDs are harmless and can remain for optional mods. Every remaining tagged type
is produced simultaneously and shown in the collection page.

The collection page receives its complete resource list, effective batch size and
interval from the server; client-local tags or configuration do not replace that
list. Production uses the same server-side filtered list. Large lists synchronize
in bounded chunks and replace the displayed list only when complete. A live
interval change starts a fresh cycle instead of waiting for the previous deadline.
Collection settings do not control sample copying, which is limited to one item
per tick with sufficient stored matter and item space. The duplication group's
`interval_ticks` controls Sequence Matter generation.

Sequence Matter output per cycle = hole count × `matter_per_black_hole` mB.
FE/AE theoretical cost = produced mB × the chosen per-unit cost. With 64 holes,
defaults produce 1,280 mB and cost either 1,280,000 FE or 327,680 AE. Actual
production is limited by energy and ME fluid capacity. FE is extracted from six
adjacent capabilities; the existence of a disk does not itself supply FE. AE is
extracted from the connected network. One copied item consumes 1,000 mB of stored
fluid and does not repeat the generation energy charge.

20 ticks is one second only while the server maintains 20 TPS. Generation does
not repay skipped/unloaded cycles. Storage pressure retains owned copy output
and fluid refunds for a later retry.

## Singularity quantum link recovery

After a save reload or chunk reload, loaded Singularity quantum endpoints reconnect
through normal AE2 initialization. A bridge that was destroyed, unloaded or retuned away from this
pair is not treated as a live endpoint merely because an old registry entry remains.
A genuinely occupied pair still reports a frequency conflict; the link still
requires a valid paired singularity, loaded remote ring, power and channel.

## Migration and warnings

Old ore/log tag lists merge into `item_tags` without injecting new defaults into
an existing custom/empty list. Existing `item_tags` wins. Older collection and
duplication groups move from `sequence_array` to `singularity_hub`; chunk-loading
moves to `multiblocks`. Retired Sequence Array Matter Rewrite, entropy-currency
and Shift-tooltip options are removed; this does not remove the current star or
negative-entropy systems. The obsolete profiling options `omni_profile_exact_returns` and
`omni_return_profile_sample_interval` are removed before schema validation.
Backup files are created before changes. Already-reset values must be restored
from a backup or configured again; migration cannot recover discarded values.

Recurring runtime and load warnings share a global one-minute window,
independent of controller count. Startup migration messages describe actual
changes; routine successful crafting does not produce trace logs.

## 中文

配置文件属于正在使用的实例目录。修改已有节，保存并重启；不要重复添加同名节。
升级时会在配置校验前清理废弃的性能分析选项，修改前会创建备份。
若此前已经被重置，需要从备份恢复或重新设置；迁移不会自动找回已丢弃的值。
上表列出当前全部有效参数。采集白名单统一为物品标签，先合并去重，再按完整物品
ID 黑名单排除。空白名单不生产，多个命中标签不会让一个物品重复生产。

`multiblocks.force_load_chunks` 只控制本模组为已登记多方块占地区块持有的加载票据，
不决定天枢是否允许运动。设为 `false` 后，只要整个运动范围所需区块都已加载，
奇点天枢仍可启动、运行和停靠；所需区块不可用时，禁止启动或停靠，并暂停／冻结
进行中的运动，等待区块重新加载，不会同步加载缺失区块。障碍与权限检查仍然适用。
微型白洞仍为例外，不论该选项为何值都会保持其所在区块加载。

FE 来自控制器相邻六面的能源能力；AE 来自连接的 ME 电网。序质产量与黑洞数量
和每黑洞产量相乘，每 mB 的 FE/AE 费用独立配置。无限磁盘解决存储容量，仍需要
实际供能。复制一件物品固定消耗 1,000 mB（1 B），样品保留。

资源页的完整物品列表、单次产量与间隔均由服务端同步，生产使用同一份过滤后的列表；
不会用客户端本地标签或配置补写。大型列表分段传输，完整接收后才替换显示。

世界或区块重载后，天枢已加载的量子端点通过 AE2 正常初始化重连。已销毁、未加载或已经换频的
量子环不会因残留索引误报占频；真正被占用的频率仍会拒绝连接，供电和频道要求不变。

各多方块控制器界面提供独立「特效」按钮，三档与配置一致：0 关闭、1 精简、2 完整；
构序矩阵按钮位于颜色页。独立档位保存在该控制器并同步给所有观看者，不影响产量。
未设置的机器沿用各客户端全局配置；Shift+点击或 Shift+Enter／空格恢复沿用。

构筑井上传可消耗终端或相连 ME 网络中的一张空白样板；识别不依赖总成名称。
EAEP 1.6.4.1 自动选择供应器时也按构筑井类型和队伍识别，支持重命名总成。
矩阵总览提供「自动上传」开关和「一键迁移样板」。上传在编码时触发；迁移仅在点击时
执行，通过 `PatternContainer` 和 `IChestOrDrive`／`StorageCell` 公共接口发现来源，
将合成、切石、锻造实体样板移入主样板库。私有槽、只读配方行、存储总线代理及处理
样板保持原处；重复实体和全部组件原样保留。
矩阵未就绪或满库时不丢弃来源物品。旧槽核心进入原有输出缓存退回网络，并保留原先
开启行为；新矩阵默认关闭，玩家已保存的开关值优先。

大型迁移在点击后同步执行，不是持续运行的后台任务。

旧分组和标签自动迁移，已设置的新值包括空列表优先保留。已删除的构序重写相关
选项不会重新生成。重复运行故障保留首次警告或错误，并按固定模板全局限流；
正常合成和输出回收不会持续写入跟踪日志。
