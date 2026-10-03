---
navigation:
  parent: omnisequence-index.md
  title: "超限算枢"
  icon: molecularmanipulator:transfinite_compute_nexus
  position: 1025
item_ids:
- molecularmanipulator:transfinite_compute_nexus
---

# 超限算枢

一块方块即可提供万物演算分支的 ME 合成 CPU 能力，适合紧凑的合成网络。

<Row>
<BlockImage id="molecularmanipulator:transfinite_compute_nexus" scale="4" />

<ItemImage id="ae2:fluix_glass_cable" scale="4" />

<ItemImage id="ae2:crafting_terminal" scale="4" />
</Row>

## 放置、连接、请求

1. 在构筑井完成**二阶：万物演算**研究并制作算枢。
2. 放置算枢，六个面均可连接 ME 线缆。
3. 给网络供电并分配 **1 个频道**。
4. 在 ME 终端发起合成，算枢自动创建独立虚拟 CPU 通道并保留备用通道。

| 项目 | 说明 |
| --- | --- |
| 结构需求 | 单方块 |
| 基础耗电 | 默认 16,384 AE/t，可配置 |
| 查看任务 | 使用 AE2 合成状态界面 |
| 网络要求 | 有真实的外部 ME 连接 |

仅有互相相邻的算枢、没有线缆或其他外部 ME 设备时，不会进入工作状态。

## 工作与搬迁

逻辑存储和并行能力与万物演算核心相同。实际吞吐量取决于材料、供电、输出空间、供应器和服务器运行情况。

正常拆下带任务的算枢会保存可恢复状态；重新放置并接入在线 ME 网络后继续。算枢不会强加载区块，需要持续工作时请保持所在区块加载。

## 配方

<RecipeFor id="molecularmanipulator:transfinite_compute_nexus" fallbackText="当前整合包未提供可用配方，请查看 JEI 和研究条件。" />
