# Configuration / 配置参考

Current source: 2.0.13-forge, Minecraft 1.20.1 / Forge, Java 17. Names and help text are available in English and Chinese.

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
| `sequence_array.build_blocks_per_tick` | 32 | 1–256 blocks/tick; Sequence Array construction/dismantling only |
| `sequence_array.idle_power` | 128 | 1–4096 AE/t |
| `multiblocks.force_load_chunks` | true | Mod-owned tickets for formed/building multiblock footprints; not permission for hub motion; White Holes are independent |
| `singularity_hub.singularity_collection.item_tags` | `#forge:raw_materials`, `#c:raw_ores`, `#c:raw_materials`, `#minecraft:raw_ores`, `#minecraft:logs`, `#c:logs` | Arbitrary item tags; optional `#`; merge and deduplicate; empty list disables collection |
| `singularity_hub.singularity_collection.item_blacklist` | `[]` | Exact namespaced item IDs; overrides every tag |
| `singularity_hub.singularity_collection.batch_size` | 1000 | 1–1,000,000 items for each eligible type per cycle |
| `singularity_hub.singularity_collection.interval_ticks` | 20 | 1–1200 ticks |
| `singularity_hub.singularity_duplication.energy_priority` | `["fe", "ae"]` | Nonempty fe/ae list, case-insensitive; attempt in order; FE uses adjacent energy capabilities, AE uses the connected grid |
| `singularity_hub.singularity_duplication.fe_per_unit` | 1000 | 1–2,147,483,647 FE/mB |
| `singularity_hub.singularity_duplication.ae_per_unit` | 256 | 1–2,147,483,647 AE/mB |
| `singularity_hub.singularity_duplication.matter_per_black_hole` | 20 | 1–1,000,000 mB per hole per cycle |
| `singularity_hub.singularity_duplication.interval_ticks` | 20 | 1–1200 ticks; missed cycles are not caught up |
| `omni_computation.dispatch.omni_batch_dispatch_enabled` | true | Batch material transfers to compatible providers; false uses single-craft transfers |
| `omni_computation.dispatch.omni_compat_dispatch_max_calls_per_tick` | 2147483647 | 256–2,147,483,647 complete single-craft calls per controller/tick |
| `omni_computation.dispatch.omni_compat_dispatch_max_time_us` | 50000 | 250–50000 microseconds/tick shared by all active Omni cores; shrinks as average MSPT approaches 45 |
| `omni_computation.dispatch.omni_dispatch_max_work_units` | 2147483647 | 64–9,223,372,036,854,775,807 per controller/tick; each input extraction and provider attempt costs one unit, regardless of batch size |
| `omni_computation.dispatch.omni_coalesce_return_notifications` | true | Combine per-resource return notifications after exact ledger settlement |
| `omni_computation.dispatch.omni_direct_native_output_return` | true | Return UselessMod intermediate outputs to the live bound CPU on the same grid; unsupported queues use normal return |
| `transfinite_compute_nexus.idle_power` | 16384 | 1–2,147,483,647 AE/t; powered network and one channel |

## Chunk loading and motion

With `multiblocks.force_load_chunks = false`, keep the hub's entire motion area
loaded through players or another loader. It can still start, run and dock; missing
chunks prevent starting/docking and pause active motion until loaded again.
These checks do not synchronously load missing chunks. Obstacle and permission
checks still apply. Miniature White Holes always keep their own chunks loaded.

## Client option

| Path | Default | Behavior |
| --- | --- | --- |
| `visual.dynamic_effect_level` | 2 | Client default: 0 off, 1 reduced, 2 full; controller overrides take priority |

Each Array, Omni-Computation Core, Fabrication Well and Singularity Hub has an
**Effects** button; the Array's is on its Colors tab. A choice is saved on that
controller and synchronized to all viewers. Shift-click or Shift-Enter/Space
restores inheritance of each client's global setting. Production is unchanged.
Texture-atlas item/fluid animations are independent; view bobbing can stay enabled.

## Pattern upload and one-click migration

- **Auto upload** on the Array overview is native and needs no EAEP core. Successful
  same-network terminal encoding uploads crafting, stonecutting and smithing patterns
  to an enabled, operational Array. Processing patterns remain in the terminal;
  duplicates return a blank. Full or unavailable destinations leave the encoded item there.
- New Arrays default off. A decodable core saved in the removed slot is returned
  through the controller's ME output buffer and preserves old enabled behavior.
  An explicitly saved toggle wins; there is no new global upload config key.
- **Migrate patterns** runs once per click, synchronously on the server thread.
  It moves supported physical items from same-network public `PatternContainer`
  slots and online `IChestOrDrive` / `StorageCell` inventories. Private slots,
  read-only recipe rows, storage-bus proxies and processing patterns are excluded.
  Items retain NBT and duplicate physical copies; no re-encoding or blank is needed.
  Full-library leftovers stay at the source. Counts report committed transfers;
  interrupted runs keep completed transfers. Large migrations can take server time.
- Well JEI upload uses one terminal blank, or one extractable blank from its
  connected ME network if the local slot is empty. The loaded, formed well and team
  identify destinations, not assembly names. Optional Forge EAEP **1.6.2** has no
  newer named-provider API; its automatic selection of renamed assemblies is not promised.

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

The collection page uses the server's effective resource list, batch size and interval,
including on dedicated-server clients; the client's local config is not the authority.
A live interval change starts a fresh cycle instead of waiting for the previous deadline.
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

## Migration and warnings

Old ore/log tag lists merge into `item_tags` without injecting new defaults into
an existing custom/empty list. Existing `item_tags` wins. Older collection and
duplication groups move from `sequence_array` to `singularity_hub`; chunk-loading
moves to `multiblocks`. Retired Matter Rewrite/entropy/Shift-tooltip options are
removed. Valid values survive schema repair; backup files are created before changes.
Values discarded by a previous reset must be restored from backup or configured again;
migration cannot recover them.

Recurring runtime and load warnings share a global one-minute window,
independent of controller count. Startup migration messages describe actual
changes; routine successful crafting does not produce trace logs.

## 中文

配置文件属于正在使用的实例目录。修改已有节，保存并重启；不要重复添加同名节。
上表列出当前全部有效参数、默认值、范围和单位；`build_blocks_per_tick` 仅作用于构序阵列。
采集白名单使用物品标签，默认含 Forge 的 `#forge:raw_materials`；合并去重后按完整物品
ID 黑名单排除。空白名单不生产，同一物品不会重复生产。采集页使用服务端生效的列表、
产量与间隔，而非客户端本地配置；这些参数不控制每 tick 最多 1 件的样品复制。

关闭 `multiblocks.force_load_chunks` 只停用本模组的多方块加载票据。整个运动范围已加载时，
天枢仍可启动、运行和停靠；缺区块则等待／暂停，不同步加载缺失区块。障碍与权限检查仍适用，
微型白洞始终加载自身区块。

各控制器可独立设置 0 关闭、1 精简、2 完整特效，构序阵列按钮位于配色页。设置保存在该
控制器并同步给观看者，不改变产量；未设置时跟随各客户端全局档位，Shift+点击或
Shift+Enter／空格恢复跟随。贴图动画独立于此档位，可保持视角摇晃开启。

构序阵列原生「自动上传」无需 EAEP，处理样板留在终端，重复编码退回空白样板；
满库或未就绪时保留来源。新阵列默认关闭；旧槽可识别核心经输出缓存退回并保留开启行为，
玩家已保存开关值优先。「一键迁移样板」仅在点击时同步执行，从同网络公开实体样板槽和
在线挂载盘提取合成、切石、锻造样板，保留 NBT 与重复实体。私有槽、只读配方行、
存储总线代理及处理样板不参与；满库留在来源，中断保留已完成转移，大型迁移会占用服务端时间。
构筑井上传使用终端或相连 ME 网络的一张空白样板，不依赖总成名称。
可选 Forge EAEP 1.6.2 无新版 named-provider API，不承诺其自动选中重命名总成。

FE 来自控制器相邻六面的能源能力；AE 来自连接的 ME 电网。序质产量与黑洞数量
和每黑洞产量相乘，每 mB 的 FE/AE 费用独立配置。无限磁盘解决存储容量，仍需要
实际供能。复制一件物品固定消耗 1,000 mB（1 B），样品保留。

旧分组和标签自动迁移，已设置的新值包括空列表优先保留。已删除的构序重写相关
选项不会重新生成。此前已被重置而丢弃的值需从备份恢复或重新设置。
重复运行故障保留首次警告或错误，并按固定模板全局限流；
正常合成和输出回收不会持续写入跟踪日志。
