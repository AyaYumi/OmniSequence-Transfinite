# OmniSequence: Transfinite 1.3.9 Release Notes

Updated build date: 2026-08-05

## Target Build

- File: `omnisequence-transfinite-1.3.9.jar`
- Minecraft: 1.21.1
- Loader: NeoForge 21.1.220 or later in the 21.1 line
- Applied Energistics 2: 19.2.17 or later
- LDLib2: 2.2.18 or later

## Highlights

- Added the public Omni Batch Provider API v1. Third-party AE2 pattern
  providers can explicitly advertise atomic batch support, receive exact
  multi-craft inputs, and commit or reject material ownership without a
  mod-specific adapter.
- Added a read-only Omni CPU marker so provider mods with their own AE2 CPU
  batching hooks can avoid processing work already owned by an Omni CPU.
- Reworked deterministic MAX_FAST planning so repeated recipe occurrences can
  be compiled and calculated as aggregated long-count graph nodes instead of
  being expanded one craft at a time. This substantially reduces calculation
  time for very large, deeply nested crafting orders.
- Multi-candidate plans now aggregate the first deterministic recipe in AE2
  priority order transactionally and fall back safely when the complete
  candidate cannot satisfy the request.
- Added reusable catalyst and durability-tool pooling for all three molecular
  crafting machines. Stable catalysts and deterministic `+1` durability tools
  can be reserved, executed, persisted, and refunded as exact batched states.

## Crafting Calculation Fixes

- Fixed a recursion-context issue that could report a craftable intermediate
  ingredient as missing instead of expanding its recipe and reporting the
  genuinely unavailable base material.
- Merged recipe occurrences are now validated to a fixed point, including
  occurrences discovered after the shared graph node was first compiled.
- When the same item has different candidate recipes, child structures, or
  terminal behavior in different recursion paths, including occurrences with
  different request-unit amounts, MAX_FAST performs bounded context-aware
  recompilation or depth-first transactional execution for the affected graph.
- Context-sensitive graphs preserve AE2's depth-first input order so surplus
  produced by a parent recipe cannot satisfy descendants that have not yet
  been planned.
- Exact terminal shortages now stop the real fast-planning attempt immediately
  and are staged into the simulated missing-material result in one aggregated
  pass.
- Failed or interrupted speculative paths restore both missing-item accounting
  and mutable recipe-candidate availability before AE2 retries the native
  planner.
- Direct and nested recipes using deterministic `+1` durability tools can
  bulk-plan the required fresh tools and their ingredients after consuming
  existing tool capacity.
- Exact AE2 smithing-table and stonecutting patterns now participate in
  deterministic MAX_FAST graph compilation.
- Patterns with actual substitutions, non-deterministic remainders, reusable
  inputs at unsafe boundaries, or other unsafe boundary behavior continue to
  fall back to AE2's native planner to preserve correctness.

## Execution and Data Safety

- Removed the old 65,536-crafts-per-tick execution slice for accepted reusable
  batches. Molecular crafting machines now settle the complete accepted
  long-count aggregate in one machine tick.
- Accepted machine batches retain input ownership even if a post-commit save,
  event, or wake hook throws, preventing duplicate scheduling of the same AE2
  work.
- Canceling a job refunds every exact remaining reusable-tool state.
- Breaking a stateful molecular crafting machine with active or quarantined
  work now drops one NBT-backed recovery block. Replacing it restores the
  pending batch and long-count output buffer without spawning an unsafe number
  of item entities.

## Compatibility and Interface Changes

- Lowered the minimum NeoForge version from 21.1.230 to 21.1.220 and the
  minimum LDLib2 version from 2.2.29 to 2.2.18 while retaining compatibility
  with newer 21.1 and LDLib2 patch releases.
- Replaced the remaining MixinExtras Expressions injection with a standard
  Sponge Mixin redirect compatible with the MixinExtras version bundled by
  NeoForge 21.1.220.
- Removed the project-added `LD²` badges from LDLib2 interfaces.
- Restored AE2's normal numeric formatting for `Long.MAX_VALUE` network
  amounts instead of replacing the value with an infinity symbol.
- No configuration change is required for this update.

## Installation and Verification

1. Fully close the client and server before replacing the JAR.
2. Keep exactly one active OmniSequence: Transfinite JAR in each `mods`
   directory.
3. Replace the old JAR with the target build and restart the game.
4. Cancel any crafting calculation created by an older build and submit it
   again so the corrected recipe graph is recalculated from clean state.
5. For missing-material verification, confirm that craftable intermediate
   ingredients expand into their recipes and that only the actual unavailable
   base materials remain in the missing list.

SHA-256:

`199B233D0000AB460C3FBE19F5E88BED0616839DC6BD26D9C0784294876AA79F`

---

# 万象构序：超限 1.3.9 更新说明

当前构建日期：2026-08-05

## 目标版本

- 文件：`omnisequence-transfinite-1.3.9.jar`
- Minecraft：1.21.1
- 加载器：NeoForge 21.1.220 或更高的 21.1 系列版本
- Applied Energistics 2：19.2.17 或更高版本
- LDLib2：2.2.18 或更高版本

## 主要更新

- 新增公开的 Omni Batch Provider API v1。第三方 AE2 样板供应器可以明确
  声明原子批处理能力、接收精确的多次合成输入，并在不编写模组专用适配器
  的情况下提交或拒绝材料所有权。
- 新增只读 Omni CPU 标记，使拥有独立 AE2 CPU 批处理钩子的供应器模组可以
  跳过已经由 Omni CPU 接管的材料分配。
- 重构确定性的 MAX_FAST 规划：重复出现的配方可以编译为长整数聚合图节点
  一次计算，不再逐次展开每一份合成，显著缩短超大数量、深层嵌套订单的
  配方计算时间。
- 存在多个候选配方时，现在会按照 AE2 优先级，以事务方式聚合第一个确定性
  候选配方；完整候选无法满足需求时会安全回退。
- 为三种分子合成机器新增可复用催化剂与耐久工具池。稳定催化剂以及每次
  固定增加 `1` 点损耗的工具可以按精确状态批量预留、执行、持久化和返还。

## 配方计算修复

- 修复递归上下文问题：可合成的中间材料不再被错误显示为缺失，而是继续
  展开其配方，并显示真正无法取得的底层材料。
- 合并后的配方出现位置现在会验证到固定点，包括共享图节点首次编译后才
  发现的其他出现位置。
- 同一物品在不同递归路径中具有不同候选配方、子配方结构或终端状态时，
  即使各节点的请求单位数量不同，MAX_FAST 也会进行有限的上下文感知重新
  编译，或对受影响的图改用深度优先事务执行。
- 上下文敏感的配方图现在保持 AE2 的深度优先输入顺序，避免父配方产生的
  余量过早供应尚未完成规划的后代节点。
- 真实快速规划遇到确定缺失的终端材料时会立即停止，并在模拟缺失材料结果
  中一次性写入聚合后的准确缺失量。
- 推测路径失败或中断时，会在 AE2 重试原生规划器之前恢复缺失材料统计和
  可变候选配方状态，避免重复缺失项或候选状态泄漏。
- 直接或嵌套使用固定 `+1` 耐久损耗工具的配方，会先消耗已有工具容量，再
  批量规划缺少的新工具及其下级材料。
- AE2 的精确锻造台与切石机样板现在可以参与确定性的 MAX_FAST 配方图计算。
- 存在实际材料替代、不确定返还物、不安全边界中的可复用输入或其他不安全
  边界行为时，仍会安全回退至 AE2 原生规划器，以保证计算结果正确。

## 执行与数据安全

- 移除了已接受可复用批次原有的每游戏刻 65,536 次合成切片。分子合成机器
  现在会在一个机器游戏刻内结算完整的长整数聚合批次。
- 即使提交后的保存、事件或唤醒钩子抛出异常，机器仍会保留已接受批次的
  输入所有权，避免同一份 AE2 工作被重复调度。
- 取消任务时会按原始精确状态返还所有尚未消耗的可复用工具。
- 带有活动批次、隔离批次或长整数输出缓冲的有状态分子合成机器被破坏时，
  现在只会掉落一个带 NBT 的恢复方块。重新放置后即可恢复待处理状态，
  不会生成数量不安全的物品实体。

## 兼容性与界面调整

- NeoForge 最低版本由 21.1.230 降至 21.1.220，LDLib2 最低版本由 2.2.29
  降至 2.2.18，并继续兼容更新的 21.1 与 LDLib2 补丁版本。
- 将最后一处 MixinExtras Expressions 注入替换为标准 Sponge Mixin 重定向，
  兼容 NeoForge 21.1.220 自带的 MixinExtras 版本。
- 移除了项目额外添加在 LDLib2 界面上的 `LD²` 标记。
- `Long.MAX_VALUE` 网络存储数量恢复使用 AE2 原生数字格式，不再替换为
  无穷符号。
- 本次更新不需要修改配置文件。

## 安装与验证

1. 替换 JAR 前完全关闭客户端和服务端。
2. 每个 `mods` 目录中只保留一个启用中的万象构序 JAR。
3. 使用目标构建替换旧 JAR，然后重新启动游戏。
4. 取消旧构建创建的合成计算并重新提交，使修复后的配方图从干净状态重新
   计算。
5. 验证缺失材料时，应确认可合成的中间材料会继续展开，缺失列表中只保留
   真正无法取得的底层材料。

SHA-256：

`199B233D0000AB460C3FBE19F5E88BED0616839DC6BD26D9C0784294876AA79F`
