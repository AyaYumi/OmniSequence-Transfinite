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

万物演算核心是一座大型 ME 合成 CPU，在高阶星算引擎增益下可同时运行多个独立合成请求。它负责管理合成任务，配方加工仍由网络中的样板供应器和机器完成。

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

## 搭建

先在构筑井完成**二阶：万物演算**研究，再制作控制器与结构部件。

| 空间 | 范围 |
| --- | --- |
| 建筑外形 | 65 × 65，高 35 格 |
| 相对控制器 | 上下各 17 格，左右各 32 格 |
| 前后空间 | 前方 22 格，后方 42 格 |

1. 放置控制器，在 JEI 查看结构清单，并开启**投影**。
2. 清理冲突、备齐材料后点击**一键搭建**。材料先取背包，再取 ME。
3. 接入有电、有频道的 ME 网络，确认结构成型。
4. 从 ME 终端下单，在 AE2 合成状态界面查看任务。

## 合成请求

没有星算引擎增益时，核心为 **1 MiB 合成存储、并行 16**，使用实体 CPU，不提供无限虚拟任务。[星算引擎](star_computation_engine.md)提高上限，L7 开启虚拟 CPU 通道，只有持续付费的 L9 超频提供无限存储与并行。大量订单仍需足够的材料、能源、样板供应器、机器和输出空间。

普通供应器按机器实际接收量自适应推送完整配方：在同一 tick 增加调用直到机器忙碌，20 tick 后再探测容量。只剩一份的尾单也可执行，不必凑批。支持续用的返还工具和确定耐久变化会留给后续合成，不复制工具，也不会静默换成全新工具。

循环配方需要初始种子材料；受阻阶段会让路给其他可执行任务，不会独占调度。停滞时检查缺料、种子和机器输入输出状态。可选 ECO 兼容要求 **21.2.10+**，整批、单份尾单和阻挡回退都保留任务身份。

数据能源 **3.3.0／3.3.5** 的数字存储仓容量模拟按每个槽位的真实空余计算一次，不扩大存储，也不在检查容量时写入物品。

控制器的**特效**按钮可选 0 关闭、1 精简、2 完整，档位保存到本机并同步观看者。Shift+点击恢复沿用各客户端全局配置，不改变产量。

## 量子连接

量子槽放入一枚**缠绕态奇点**，远端已供电的 AE2 量子环放入配对的另一枚。链路额外需要 **512 AE/t 和 1 个频道**。

## 回收与结构更新

结构损坏或区块卸载时，任务、材料和进度保留，恢复工作条件后继续。正常拆下控制器时，任务与量子槽内容随方块保存。

**回收包裹**将控制器、已安装结构和内部内容封存为轻量整机包裹，完整数据保存在当前世界档案中。请先结束或取消并结算合成任务，详见[折叠建筑与整机搬迁](folded_buildings.md)。服务器启用多方块强加载后，成型、施工与结构更新期间保持所需区块加载；建筑占用区块阻止自然刷怪。

旧版 **1.3.9** 径向结构可以使用**更新结构**。先检查新投影和所需材料，点击一次进入确认，再于 5 秒内点击执行。更新会迁移控制器与任务。

需要紧凑 CPU 时，可使用[超限算枢](transfinite_compute_nexus.md)。

## 配方

<RecipeFor id="molecularmanipulator:omni_computation_controller" fallbackText="当前整合包未提供可用配方，请查看 JEI 和研究条件。" />
