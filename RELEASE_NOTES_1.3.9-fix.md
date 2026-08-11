# OmniSequence: Transfinite 1.3.9-fix — Release Notes

Minecraft 1.21.1 · NeoForge · Build date: 2026-08-11

## English

### Infinite storage compatibility

- Infinite storage cells are detected at the AE2 network-storage boundary by simulated over-extraction instead of hard-coded cell implementation checks.
- Network totals use saturating `long` arithmetic. Combining a finite amount with an infinite cell can no longer overflow into a negative value and make the item disappear from terminals.
- Infinite-cell display remains stable regardless of storage mount order.

### Pattern Access Terminal organization

- Large pattern inventories are exposed as multiple logical containers in the Pattern Access Terminal.
- This applies to both the multiblock Sequence Array and the single-block Molecular Manipulator machines while retaining one physical machine inventory.

### Matter Sequence Rewrite

- Per-type Matter Sequence storage supports up to `Long.MAX_VALUE` (`9223372036854775807`) and defaults to that limit.
- Rewrite entropy capacity is configurable from `1` to `Long.MAX_VALUE` and defaults to `1000000`.
- Base entropy cooling per second is configurable and defaults to `25`.
- The controller shows entropy generated per item, effective cooling rate, and estimated time until the next deconstruction or rewrite can start.
- If one item exceeds the configured entropy capacity by itself, the interface reports a configuration-limit error instead of remaining on Cooling forever.
- Entropy formulas and exact server configuration paths are documented inside `matter_rewrite_rules.json`. Existing files are upgraded to documentation format 4 without replacing their rules.

### Configurable speed-card tiers

| Installed cards | Parallel operations | Batch time | Cooling multiplier |
| ---: | ---: | ---: | ---: |
| 0 | 1 | 20 ticks | 1× |
| 1 | 2 | 10 ticks | 2× |
| 2 | 4 | 5 ticks | 4× |
| 3 | 16 | 2 ticks | 16× |
| 4 | 64 | 1 tick | 64× |

Every tier can be adjusted independently. Effective entropy cooling is `base cooling per second × current speed-card cooling multiplier`. Overflowing multiplication saturates at `Long.MAX_VALUE`.

### Configuration organization

Server options are grouped into `sequence_array`, `sequence_array.matter_rewrite`, `sequence_array.matter_rewrite.speed_cards`, `ae2_crafting`, and the `optimizer`, `cache`, `execution`, and `dispatch` sections below `omni_computation`. Client options are grouped under `tooltips` and `visual`.

All new settings have English and Chinese comments, ranges, defaults, and formula explanations. Existing flat options are migrated to the categorized paths while preserving their values, with a `.toml.bak` backup created before migration.

### Interface and calculation fixes

- Completed Sequence Array and Omni-Computation structures no longer display construction progress.
- The crafting-confirmation path label and the dedicated planning-progress network synchronization have been removed.
- Deterministic AdvancedAE processing patterns and verified reusable `+1` durability tools can use the optimized graph path; unknown or unproven behavior continues through native AE2.
- Cancelled calculations waiting for an Omni execution slot now leave the queue immediately.

### Installation and testing

1. Fully stop Minecraft or the server before replacing the JAR.
2. Keep exactly one enabled `omnisequence-transfinite-*.jar` in each `mods` directory.
3. Install `omnisequence-transfinite-1.3.9-fix.jar` on both client and server.
4. Cancel calculations created by older builds and submit the crafting request again.
5. To collect optimizer timing evidence, enable `omni_computation.optimizer.omni_max_fast_diagnostics` in the server configuration.

---

## 中文

### 无限存储兼容

- 无限存储盘改为在 AE2 网络存储边界通过模拟超量提取识别，不再硬编码具体存储盘实现。
- 网络数量合并改用 `long` 饱和计算。有限数量与无限盘合并时，不会再溢出为负数并导致终端中的物品消失。
- 无论有限盘和无限盘以什么顺序挂载，无限物品数量都能稳定显示。

### 样板终端逻辑容器

- 大型样板库存会在样板终端中拆分为多个逻辑容器。
- 多方块构序阵列和单方块分子操纵机均使用相同方案，同时仍保留一个实际机器库存。

### 物质构序重写

- 每一种物质构序的存储上限支持 `Long.MAX_VALUE`（`9223372036854775807`），默认也是 `Long.MAX_VALUE`。
- 重写熵上限可在 `1`～`Long.MAX_VALUE` 之间配置，默认 `1000000`。
- 基础每秒熵散热速度可配置，默认 `25`。
- 控制器界面会显示单件产生熵值、当前有效散热速度，以及下一次分解或重写开始前的预计冷却时间。
- 如果单件物品产生的熵已经超过配置上限，界面会提示配置上限错误，不再永久显示冷却中。
- `matter_rewrite_rules.json` 中写有熵计算公式和准确的服务器配置路径。已有文件会升级到说明格式 4，但不会替换用户规则。

### 可配置加速卡档位

| 加速卡数量 | 并行量 | 批次时间 | 散热倍率 |
| ---: | ---: | ---: | ---: |
| 0 | 1 | 20 Tick | 1× |
| 1 | 2 | 10 Tick | 2× |
| 2 | 4 | 5 Tick | 4× |
| 3 | 16 | 2 Tick | 16× |
| 4 | 64 | 1 Tick | 64× |

每一档都可以单独调整。有效散热公式为“基础每秒散热 × 当前加速卡档位的散热倍率”。乘法超过 `Long.MAX_VALUE` 时会按上限饱和，不会发生负数溢出。

### 配置文件分类

服务端配置分为 `sequence_array`、`sequence_array.matter_rewrite`、`sequence_array.matter_rewrite.speed_cards`、`ae2_crafting`，以及 `omni_computation` 下的 `optimizer`、`cache`、`execution` 和 `dispatch`。客户端配置分为 `tooltips` 和 `visual`。

所有新配置均提供中英文备注、范围、默认值与公式说明。旧版平铺配置会在保留原数值的情况下迁移到新分类，迁移前自动生成 `.toml.bak` 备份。

### 界面及计算修复

- 构序阵列和万物演算核心完整成型后，不再继续显示施工进度。
- 已移除合成确认界面的路径标签及独立的规划进度网络同步。
- 确定性的 AdvancedAE 处理样板及通过验证的每次 `+1` 耐久工具可使用优化图路径；未知或无法证明安全的行为继续交给 AE2 原版处理。
- 已取消但仍在等待万物演算执行槽的计算会立即退出队列。

### 安装与测试

1. 替换 JAR 前必须完全关闭游戏或服务端。
2. 每个 `mods` 目录只保留一个启用中的 `omnisequence-transfinite-*.jar`。
3. 客户端和服务端都安装 `omnisequence-transfinite-1.3.9-fix.jar`。
4. 取消旧版本创建的计算任务，并重新提交合成请求。
5. 如需收集优化器耗时信息，可在服务端配置中启用 `omni_computation.optimizer.omni_max_fast_diagnostics`。

Artifact SHA-256: `7DE458FE280D9EF3B2C4C90B32C51C698E8C337F8051E136A1C322D180125757`
