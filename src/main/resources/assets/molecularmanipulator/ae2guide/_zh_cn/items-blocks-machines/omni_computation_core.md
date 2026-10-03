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

大型 ME 合成 CPU。悬浮星冕仪成型后，可同时管理多个合成请求。

## 建筑与部件

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

| 准备项 | 要求 |
| --- | --- |
| 解锁 | 构筑井完成二阶：万物演算 |
| 结构范围 | 65 × 65，高 35 格 |
| 相对控制器 | 上下各 17 格，左右各 32 格 |
| 前后空间 | 前方 22 格，后方 42 格 |

1. 制作控制器和部件，放置控制器并查看**投影**与 JEI 结构页。
2. 清除冲突，点击**一键搭建**，优先背包取材，再取 ME 材料。
3. 接入在线、已供电的 ME 网络，检测并确认结构成型。
4. 在 ME 终端发起合成请求，核心自动创建相互独立的 CPU 通道。

## 合成运行

<Row>
<BlockImage id="molecularmanipulator:infinite_crafting_storage" scale="4" />

<BlockImage id="molecularmanipulator:infinite_parallel_matrix" scale="4" />

<ItemImage id="ae2:crafting_terminal" scale="4" />
</Row>

| 核心提供 | 实际仍需准备 |
| --- | --- |
| 大规模逻辑合成存储 | 任务所需原料 |
| 并行的虚拟 CPU 通道 | 接受投料的样板供应器与机器 |
| 在线时的加速规划 | 能源和产物接收空间 |

循环配方仍需种子材料。任务派发随接收能力与服务器负载调整，显示的逻辑容量不代表固定产出速度。

## 量子连接与保存

专用量子槽放入配对的缠绕态奇点，另一枚放入远端 AE2 量子环。链路额外消耗 **512 AE/t、1 个频道**；先解决冲突网络再连接。

| 情况 | 结果 |
| --- | --- |
| 结构损坏或暂时卸载 | 保存任务、材料与进度，恢复结构后继续 |
| 正常拆下控制器 | 运行内容与量子槽随方块保存 |
| 一键拆除 | 保留控制器，材料先回 ME，再进背包 |
| 回收空间不足 | 暂停并保留拆除进度 |

开启多方块强加载配置后，成型、施工及结构更新期间保持必要区块加载。占用区块有自然生成保护。

## 从旧版建筑升级

正式 **1.3.9** 径向核心可使用**更新结构**。先检查新版投影与材料，第一次点击进入确认，5 秒内再次点击执行；控制器与任务会随新版位置迁移。

## 控制器配方

<RecipeFor id="molecularmanipulator:omni_computation_controller" fallbackText="当前整合包未提供可用配方，请查看 JEI 和研究条件。" />

需要单方块 CPU 时，参阅[超限算枢](transfinite_compute_nexus.md)。
