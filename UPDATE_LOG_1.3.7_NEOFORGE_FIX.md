# OmniSequence: Transfinite 1.3.7 NeoForge Fix

Release date: 2026-07-30

## Target Build

- File: `omnisequence-transfinite-1.3.7.jar`
- Minecraft: 1.21.1
- Loader: NeoForge
- Applied Energistics 2: 19.2.17+

## Fixes

- Fixed substitution-enabled crafting patterns falling back to one craft per
  provider dispatch in the Sequence Array and the Assembler Matrix Sequence
  Rewrite Core.
- Fixed infinite reusable recipe items with `MAX_DAMAGE=0`, such as Mystical
  Agriculture's Master Infusion Crystal, being incorrectly classified as
  finite-durability inputs on Minecraft 1.21.1.
- Same-key remainders with an explicit `UNBREAKABLE` component are now treated
  as stable reusable inputs.
- AE2 can now select additional materials from every valid substitute instead
  of requiring the first selected material to supply the complete batch.
- A compressed crafting input may contain several actual substitute keys. The
  molecular providers reconstruct and validate those selections in bounded
  groups before producing the aggregated output.

## Safety and Compatibility

- Actual extracted AE2 keys remain the source of truth for substitution.
- Every reconstructed group must produce the encoded pattern output, consume
  the complete aggregate, and produce no untracked remainder.
- Finite-durability, Unbreaking-enchanted, random, contextual, or unsupported
  remainder transitions retain the conservative one-craft execution path.
- AE2's native pattern-power calculation remains in use.
- No configuration change is required.

## Installation and Verification

1. Fully close the client and server before replacing the JAR.
2. Keep only one active OmniSequence: Transfinite JAR in the `mods` directory.
3. Restart the game. For a clean verification, cancel the old persisted
   crafting job and submit it again.

SHA-256:

`DB6A7AA75D80942A342EDDDD26905A13E2EF5B9B71A76CA08EE33549507D74FC`

---

# 万象构序：超限 1.3.7 NeoForge 修复更新

发布日期：2026-07-30

## 目标版本

- 文件：`omnisequence-transfinite-1.3.7.jar`
- Minecraft：1.21.1
- 加载器：NeoForge
- Applied Energistics 2：19.2.17+

## 修复内容

- 修复开启物品替换的合成样板在构序阵列和装配矩阵构序重写核心中退回为每次只合成一份的问题。
- 修复 Minecraft 1.21.1 将 `MAX_DAMAGE=0` 的无限返还物误判为有限耐久物品的问题，例如神秘农业的无尽灌注水晶。
- 明确带有 `UNBREAKABLE` 组件、且合成后同键返还的物品现在会被视为稳定可复用输入。
- 批量扩展时，AE2 可以从全部有效替代材料中继续选料，不再要求第一次选中的材料独自供应整个批次。
- 同一个压缩合成输入可以包含多种实际替代物。分子合成器会按有限数量的选择组还原并验证配方，再汇总生成产物。

## 安全与兼容性

- 替换逻辑始终以 AE2 实际提取到的物品或流体键为准。
- 每个还原后的选择组都必须产生样板记录的产物、完整消耗聚合输入，并且不能产生未登记的返还物。
- 有限耐久、带随机不毁附魔、依赖上下文或无法安全判断的返还物，仍走保守的逐份执行路径。
- 继续使用 AE2 原生样板能耗计算。
- 不需要修改配置。

## 安装与验证

1. 替换 JAR 前请完全关闭客户端和服务端。
2. `mods` 目录中只保留一个启用中的万象构序 JAR。
3. 重启游戏。为了干净验证，建议取消之前持久化的旧合成任务，然后重新发起同一任务。

SHA-256：

`DB6A7AA75D80942A342EDDDD26905A13E2EF5B9B71A76CA08EE33549507D74FC`
