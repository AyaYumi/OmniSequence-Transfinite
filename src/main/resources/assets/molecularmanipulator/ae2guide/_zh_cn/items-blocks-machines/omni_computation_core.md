---
navigation:
  parent: omnisequence-index.md
  title: "万物演算核心"
  icon: molecularmanipulator:omni_computation_controller
  position: 1020
item_ids:
- molecularmanipulator:omni_computation_controller
- molecularmanipulator:omni_computation_casing
- molecularmanipulator:omni_computation_glass
- molecularmanipulator:infinite_parallel_matrix
- molecularmanipulator:infinite_crafting_storage
- molecularmanipulator:universal_pattern_matrix
- molecularmanipulator:computation_data_entangler
- molecularmanipulator:computation_energy_stabilizer
- molecularmanipulator:computation_output_node
- molecularmanipulator:computation_crystal_pylon
---

# 万物演算核心

万物演算核心是一座大型合成 CPU。你从终端确认订单后，它会安排各步制作、等待原料并收取结果。接入高阶星算引擎后，一座核心还能同时处理多个独立订单。

<Row>
<ItemImage id="ae2:crafting_terminal" scale="4" />
<BlockImage id="molecularmanipulator:omni_computation_controller" scale="4" />
<BlockImage id="molecularmanipulator:molecular_manipulator" scale="4" />
</Row>

从左到右是下单用的终端、管理订单的核心和实际制作的重写设备。编码样板放在构序阵列、重写设备或样板供应器中，原料和成品存入 ME。

![终端下单，CPU 安排步骤，原料从 ME 交给有样板的制作机器，成品返回 ME](../assets/diagrams/me-crafting-zh_cn.png)

建筑尺寸请用控制器投影确认。

## 准备

- 在[构筑井](matter_fabrication_research.md)完成一轮Ⅲ阶：演算架构，制作控制器和结构部件。部分结构材料还来自“构序工程”路线，需要一并完成对应研究。这条路线使用 ExtendedAE 和 AdvancedAE 的材料。
- 留出 **65 × 65 格、高 35 格**的空间。面向控制器正面时，左右各留 32 格、上下各留 17 格，前方留 22 格、后方留 42 格。放下控制器后再用投影核对。
- 准备有物品存储和终端的 ME 网络，以及至少一份可用配方和实际制作设备。
- 准备足够的 ME 电力和 1 个空闲频道。核心默认基础待机耗电为 **8192 AE/t**，制作时还需要额外能源。

下列是结构部件。在 JEI 结构页查齐数量，再按投影标出的位置搭建。

<ItemGrid>
<ItemIcon id="molecularmanipulator:omni_computation_controller" />
<ItemIcon id="molecularmanipulator:omni_computation_casing" />
<ItemIcon id="molecularmanipulator:omni_computation_glass" />
<ItemIcon id="molecularmanipulator:infinite_parallel_matrix" />
<ItemIcon id="molecularmanipulator:infinite_crafting_storage" />
<ItemIcon id="molecularmanipulator:universal_pattern_matrix" />
<ItemIcon id="molecularmanipulator:computation_data_entangler" />
<ItemIcon id="molecularmanipulator:computation_energy_stabilizer" />
<ItemIcon id="molecularmanipulator:computation_output_node" />
<ItemIcon id="molecularmanipulator:computation_crystal_pylon" />
</ItemGrid>

<RecipeFor id="molecularmanipulator:omni_computation_controller" fallbackText="在 JEI 中查看控制器的构筑井配方与结构材料清单。" />

## 搭建并接收第一单

1. 放置控制器，打开界面并开启“投影”。清理冲突，确认建筑不会超出世界高度。
2. 按 JEI 清单准备材料，放进背包或控制器连接的 ME 网络。点击“一键搭建”，施工先用背包材料，再取 ME 库存。
3. 等待施工完成，确认结构成型、网络在线。把终端、ME 存储和实际制作设备接到同一网络。
4. 在重写设备或构序阵列的主库中准备“橡木原木 → 橡木木板”样板，向 ME 存入至少 4 个原木。
5. 从终端请求 16 个木板，在合成确认页选择万物演算核心作为 CPU 并确认。
6. 在 AE2 合成状态界面查看订单，完成后从 ME 取出成品。样板留在制作设备中，原料放在 ME 存储里。

## 合成存储、并行量和订单数

合成存储是处理订单所需的工作空间，与 ME 物品存储容量分开计算。配方并行量表示订单中可同时推进的制作量；能同时接几个独立订单，则取决于下表中的等级条件。

| 同网络星算引擎状态 | 核心的接单与工作上限 |
| --- | --- |
| 没有在线有效引擎 | 1 MiB 合成存储、配方并行 16，一台核心接一个独立订单。 |
| L1～L6 常态 | 合成存储和配方并行量随等级增加，仍是一个独立订单。 |
| L7～L9 常态 | 可同时接多个独立订单，共享本机的合成存储与工作额度；L9 常态也有上限。 |
| L9，开启且持续支付的超频 | 提供无限合成存储和配方并行上限；实际制作仍受材料、电力、机器和输出空间限制。 |

L1～L8 超频持续消耗同网络的星讯流，将合成存储与配方并行量翻倍。多订单仍需达到 L7。星讯不足时超频结束，恢复对应等级的有限常态能力。同网多台引擎只取当前有效能力最高的一台，不会叠乘。

## 订单卡住时

| 看到的情况 | 怎么处理 |
| --- | --- |
| 终端找不到这台 CPU | 检查结构成型、同网络连接、电力和频道，先确保能持续负担基础待机耗电。 |
| 有 CPU，却没有想做的配方 | 把已编码样板放到构序阵列、重写设备或样板供应器；核心不保存配方。 |
| 提示合成存储不足 | 拆成小单，等待已有订单结束，或提升同网络星算引擎等级。增加普通物品存储盘不会扩大 CPU 的合成存储。 |
| L7 以下不能再接第二单 | 这是单订单阶段。升级有效星算引擎到 L7，或给网络增加另一台 CPU。 |
| 订单停在外部机器步骤 | 检查缺料、样板供应器和机器的输入输出；循环配方还要先存入启动循环的那份材料。 |
| 已做出产物但订单仍等待 | 清理 ME 和机器输出空间，检查产物是否能回到同一网络；工具和返还容器也要有去处。 |
| L9 突然恢复有限上限 | 查看超频开关和星讯流余额；达到 L9 不会自动获得无限能力。 |

核心会按外部机器的接收能力派发工作，尾单只剩一份也能执行。支持的可复用工具会继续用于后续制作，耐久照常变化。

### 使用联动机器时

- NeoECOAE 可选：20.4.2+ 支持普通 AE 任务派发；原生批量和精确数量联动需要 20.5.0+ Forge 版。
- 数据能源仓库联动针对 3.3.0／3.3.5 适配，仓库存入时仍需有真实空位。
- AdvancedAE 量子合成器和反应室的研究许可联动针对 Forge 1.3.6-1.20.1 适配。本核心的默认研究也需要 AdvancedAE 材料。

## 下一步

需要无线接入 ME 时，在右侧栏用“远程连接”绑定核心，见[构序无线枢纽设置](sequence_array_controller.md)。

断开无线枢纽后，也可在量子槽放一枚缠绕态奇点，配对另一枚放入已供电的 AE2 量子环。这种连接额外需要 **512 AE/t 和 1 个频道**。

搬迁前先结束或取消并结清订单，再用“回收包裹”整机封存。包裹会保留内部内容和无线绑定，见[折叠建筑与整机搬迁](folded_buildings.md)。如果无线枢纽被移动或替换，需要重新绑定核心。结构受损或区块卸载时，任务与材料保留，恢复工作条件后可继续。服务器开启多方块强加载时，可保持运行和施工所需区块加载。

想把同样的 CPU 功能缩进一个方块，继续阅读[超限算枢](transfinite_compute_nexus.md)。只想保持常用品库存，不需要接终端订单，则使用[分子自动合成器](molecular_auto_crafter.md)。
