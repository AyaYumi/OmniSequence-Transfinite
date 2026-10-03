# Configuration / 配置参考

Current source: 2.0.7-forge. Names and help text are available in English and Chinese.

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
| `multiblocks.force_load_chunks` | true | Keep registered multiblock footprint loaded |
| `singularity_hub.singularity_collection.item_tags` | `#forge:raw_materials`, `#c:raw_ores`, `#c:raw_materials`, `#minecraft:raw_ores`, `#minecraft:logs`, `#c:logs` | Arbitrary item tags; optional `#`; merge and deduplicate; empty list disables production |
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
| `omni_computation.dispatch.omni_profile_exact_returns` | false | Opt-in global performance summaries; not required for crafting |
| `omni_computation.dispatch.omni_return_profile_sample_interval` | 64 | 1–4096; sample timing once per this many exact insertions |
| `omni_computation.dispatch.omni_direct_native_output_return` | true | Direct live-bound compatible native intermediate-output return |
| `transfinite_compute_nexus.idle_power` | 16384 | 1–2,147,483,647 AE/t; powered network and one channel |

## Client option

| Path | Default | Behavior |
| --- | --- | --- |
| `visual.dynamic_effect_level` | 2 | 0 off, 1 reduced, 2 full multiblock effects |

Texture-atlas item/fluid animations remain active independently of large-world
effect detail. Reduce this option when world effects cost too much render time.

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

## Migration and diagnostics

Old ore/log tag lists merge into `item_tags` without injecting new defaults into
an existing custom/empty list. Existing `item_tags` wins. Older collection and
duplication groups move from `sequence_array` to `singularity_hub`; chunk-loading
moves to `multiblocks`. Retired Matter Rewrite/entropy/Shift-tooltip options are
removed. Valid values survive schema repair; backup files are created before changes.

Recurring runtime/load warning and error templates share a global one-minute
window, independent of controller count. AELIS and exact-dispatch trace categories
also share that limit. Opt-in performance measurements aggregate at ten-second
intervals. Startup migration messages describe actual changes, not tick activity.

## 中文

配置文件属于正在使用的实例目录。修改已有节，保存并重启；不要重复添加同名节。
上表列出当前全部有效参数。采集白名单统一为物品标签，先合并去重，再按完整物品
ID 黑名单排除。空白名单不生产，多个命中标签不会让一个物品重复生产。

FE 来自控制器相邻六面的能源能力；AE 来自连接的 ME 电网。序质产量与黑洞数量
和每黑洞产量相乘，每 mB 的 FE/AE 费用独立配置。无限磁盘解决存储容量，仍需要
实际供能。复制一件物品固定消耗 1,000 mB（1 B），样品保留。

旧分组和标签自动迁移，已设置的新值包括空列表优先保留。已删除的构序重写相关
选项不会重新生成。性能诊断默认关闭，重复故障按固定模板全局限流。
