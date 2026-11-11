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

## 用途：让装配矩阵实际执行重写合成

这是 **ExtendedAE 装配矩阵内部的加工部件**，不是独立机器。装进成型矩阵后，它使用矩阵中保存的配方制作物品； 只把核心放在地上或接上线缆不能开工。

<Row>
<BlockImage id="molecularmanipulator:assembler_matrix_molecular_core" scale="4" />
<BlockImage id="extendedae:assembler_matrix_pattern" scale="4" />
<BlockImage id="extendedae:assembler_matrix_frame" scale="4" />
</Row>

从左到右：本核心负责制作，矩阵样板核心保存**样板**（在样板编码终端写好配方的卡片），框架等结构件组成完整矩阵。本核心不替代后两者。

## 准备

- 安装 ExtendedAE，并准备一座按其规则搭建的装配矩阵。
- 在[构筑井](matter_fabrication_research.md)完成一轮**Ⅲ阶：构序工程**，按下方配方制作核心。配方用到[分子构序重写阵列](molecular_sequence_rewrite_array.md)；从头制作该原料时，先解锁它所需的 L3“单方块集成”。
- 一个有电、有物品存储的 ME 网络，以及**合成 CPU**——接收并安排合成订单的设备。矩阵负责制作，CPU 负责订单，原料与成品放在 ME 存储中。
- 给矩阵留足**频道**，也就是 ME 网络提供给设备的连接名额，并准备已编码的合成样板和原料。

<RecipeFor id="molecularmanipulator:assembler_matrix_molecular_core" fallbackText="在 JEI 中查看构筑井配方、材料和研究要求。" />

## 步骤：装入矩阵后试单

1. 按 ExtendedAE 的结构要求安放框架和功能部件，把重写核心放在**内部合成核心可用的位置**。
2. 在矩阵的**样板核心**里放入一张“橡木原木 → 橡木木板”的合成样板。样板不放进重写核心。
3. 确认矩阵成型，接入上述 ME 网络，检查供电和频道。
4. 向 ME 存入至少 **4 个橡木原木**，从终端请求制作 **16 个橡木木板**。
5. 在合成确认界面选择可用 CPU，确认后查看合成状态，最后从 ME 取出成品。

核心可执行兼容分子装配室的样板；需要外部机器的处理配方仍要由对应机器完成。

## 成功标志

- ExtendedAE 矩阵确认结构有效，终端能找到样板的产物。
- 测试订单结束，4 个原木换成 16 个木板并回到 ME。
- 扩大订单后，核心按实际可用材料和能源加工，输出进入网络。

适合的配方会批量制作；需要逐次处理的配方按单份执行。 容器、可复用物品和工具耐久按实际配方处理。 同网络星算引擎可提高核心的制作并行量， 不能替代原料、供电或接收产物的空间。

## 排错

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
- 想扩大接单能力：了解[万物演算核心](omni_computation_core.md)，不要把接单能力和核心的制作能力混为一谈。

搬迁前先结清订单。 正常拆下核心时，保留的材料、产物和未完成内容会随核心携带； 装回有效矩阵并恢复 ME 后可继续。
