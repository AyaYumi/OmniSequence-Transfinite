---
navigation:
  parent: omnisequence-index.md
  title: "装配矩阵构序重写核心"
  icon: molecularmanipulator:assembler_matrix_molecular_core
  position: 1010
item_ids:
- molecularmanipulator:assembler_matrix_molecular_core
---

# 装配矩阵构序重写核心

这个核心装在 ExtendedAE 的装配矩阵内部，负责执行矩阵中保存的配方。使用时仍需样板核心和完整矩阵结构，单独放置或接线无法开工。

<Row>
<BlockImage id="molecularmanipulator:assembler_matrix_molecular_core" scale="4" />
<BlockImage id="expatternprovider:assembler_matrix_pattern" scale="4" />
<BlockImage id="expatternprovider:assembler_matrix_frame" scale="4" />
</Row>

从左到右是负责制作的重写核心、保存已编码样板的矩阵样板核心，以及组成结构的框架。

## 准备

- 安装 ExtendedAE，并准备一座按其规则搭建的装配矩阵。
- 在[构筑井](matter_fabrication_research.md)完成一轮Ⅲ阶：构序工程，按下方配方制作核心。配方用到[分子构序重写阵列](molecular_sequence_rewrite_array.md)，从头制作该原料时，要先解锁 L3“单方块集成”。
- 准备有电、有物品存储的 ME 网络，并接入合成 CPU。CPU 接收和安排订单，矩阵负责制作，原料与成品放在 ME 存储中。
- 给矩阵留足频道，准备已编码的合成样板和原料。

<RecipeFor id="molecularmanipulator:assembler_matrix_molecular_core" fallbackText="在 JEI 中查看构筑井配方、材料和研究要求。" />

## 装入矩阵并制作木板

1. 按 ExtendedAE 的结构要求安放框架和功能部件，把重写核心放在内部合成核心可用的位置。
2. 在矩阵的样板核心里放入一张“橡木原木 → 橡木木板”合成样板。重写核心负责制作，样板保存在样板核心中。
3. 确认矩阵成型，接入上述 ME 网络，检查供电和频道。
4. 向 ME 存入至少 4 个橡木原木，从终端请求制作 16 个橡木木板。
5. 在合成确认界面选择可用 CPU，确认后可查看合成状态。完成后，4 个原木会变成 16 个木板并送回 ME。

核心可执行兼容分子装配室的样板；需要外部机器的处理配方仍要由对应机器完成。

适合的配方会批量制作，需要逐次处理的配方则按单份执行。容器、可复用物品和工具耐久按实际配方处理。同网络的星算引擎可提高制作并行量，实际批量仍受原料、能源和产物接收空间限制。

## 常见问题

| 看到的情况 | 怎么处理 |
| --- | --- |
| 核心独立放置不工作 | 它必须装入有效的 ExtendedAE 装配矩阵。想用单方块设备，请选分子构序重写阵列。 |
| 矩阵不成型 | 按 ExtendedAE 的矩阵规则检查框架、内部部件和核心位置，不能拿重写核心代替样板核心。 |
| 终端找不到配方 | 将已编码样板放到矩阵样板核心，检查矩阵联网状态。 |
| 找不到 CPU 或一直缺料 | 接入空闲合成 CPU，并补齐合成状态中列出的材料；核心本身不接单、不储存整网原料。 |
| 订单等不到产物 | 检查 ME 容量和接收限制。网络放不下时，已完成产物会保存在核心中等待。 |

取消订单会停止未执行的部分，并返还未用材料和当前工具；已经完成的产物会保留。

## 下一步

- 想省掉矩阵结构：使用[分子构序重写阵列](molecular_sequence_rewrite_array.md)。
- 想集中管理更多样板：了解[构序阵列](sequence_array_controller.md)。
- 想扩大接单能力：了解[万物演算核心](omni_computation_core.md)。

搬迁前先结清订单。正常拆下核心时，保留的材料、产物和未完成内容会随核心携带；装回有效矩阵并恢复 ME 连接后可继续。
