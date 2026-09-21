# OmniSequence: Transfinite 2.0.5-fix Release Notes

Minecraft 1.21.1 · NeoForge · Java 21

## Fixes

- Fixed an InsaneAE compatibility crash where Omni computation cores were treated as ordinary AE2 crafting units, causing a `ClassCastException` and server shutdown.
- Omni computation controllers and Transfinite Compute Nexuses now satisfy AE2's crafting-unit block contract while retaining their own storage, parallelism, and multiblock behavior.
- Fixed `powered` and `formed` block-state registration and synchronization, preventing registration failures and state overwrites.
- CPU jobs, inventories, and quantum inventory contents are saved before a controller or nexus is removed, preserving portable recovery drops.
- Retained the Data Energistics CPU-lane identity compatibility fix so colocated virtual CPUs do not trigger `Duplicate crafting CPU stable identity`.

## Validation

- `gradlew test` passes.
- The GameTest server completes mod registration and enters the test phase.

## Installation

Remove or disable older OmniSequence JARs and keep only:

```text
omnisequence-transfinite-2.0.5-fix.jar
```

---

# OmniSequence: Transfinite 2.0.5-fix 更新日志

Minecraft 1.21.1 · NeoForge · Java 21

## 修复内容

- 修复与 InsaneAE 兼容时，Omni 计算核心被 AE2 当作普通合成单元处理，导致 `ClassCastException` 并使服务器崩溃的问题。
- Omni 计算控制器和超限算枢现在使用 AE2 合成单元的正确方块契约，同时保留自身的存储、并行和多方块逻辑。
- 修复 `powered` 与 `formed` 方块状态注册及同步问题，避免方块注册失败和状态互相覆盖。
- 拆除控制器或算枢前会先保存 CPU 任务、库存和量子库存，保证可恢复掉落不丢失任务内容。
- 保留 Data Energistics CPU 通道唯一标识兼容，避免多个虚拟 CPU 触发 `Duplicate crafting CPU stable identity`。

## 验证

- `gradlew test` 通过。
- 游戏测试服务器可以完成模组注册并进入测试阶段。

## 安装

删除或停用旧版 OmniSequence JAR，只保留：

```text
omnisequence-transfinite-2.0.5-fix.jar
```
