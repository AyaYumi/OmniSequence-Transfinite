---
navigation:
  parent: omnisequence-index.md
  title: 超限算枢
  icon: molecularmanipulator:transfinite_compute_nexus
  position: 1025
item_ids:
- molecularmanipulator:transfinite_compute_nexus
---

# 超限算枢

<BlockImage id="molecularmanipulator:transfinite_compute_nexus" scale="8" />

超限算枢是万物演算分支使用的单方块 AE2 合成 CPU。
它不需要多方块结构，也没有独立机器界面；放下后接入 ME 网络，再通过 AE2 终端提交合成任务即可。

## 解锁与配方

超限算枢由“二阶：万物演算”研究解锁，并在[物质构筑井](matter_fabrication_well.md)中加工制作。
整合包可以修改或移除对应研究和配方。

默认构筑井配方耗时 1,200 tick、功耗 4,096 AE/t，需要 1 个万物演算核心、64 个无限并行矩阵、
64 个无限构序存储矩阵、32 个全知演算矩阵、32 个演算能量稳定器和 64 个量子处理器。
整合包修改配方后，以下实时配方面板的内容优先。

<RecipeFor id="molecularmanipulator:transfinite_compute_nexus" fallbackText="研究或配方被禁用时无法制作此方块。" />

## 使用超限算枢

1. 像普通方块一样放置超限算枢；方块朝向只影响正面纹理，可用 AE2 扳手旋转。
2. 接入 ME 线缆。六个面都可以接入网络；网络必须供电并提供 1 个频道。
3. 在 AE2 终端提交合成请求。超限算枢会按任务需求创建相互独立的虚拟 CPU 通道，并保留一个备用通道。

联机后，超限算枢提供与万物演算核心相同的近似无限逻辑合成存储和并行能力。实际吞吐量仍受原料、能源、输出容量、供应器接收能力和服务器 Tick 时间限制；满足正常条件时，也可以使用加速规划器。

待机耗电位于配置 `transfinite_compute_nexus.idle_power`，默认值为 16,384 AE/t。
ME 网络未供电、没有频道，或失去所有外部网络连接时，方块会停止工作。

超限算枢没有量子槽、样板库、多方块施工队列，也不会强加载区块。相邻的超限算枢可以彼此接入同一个 AE 网络，
但每个方块独立管理自己的虚拟 CPU 通道；只有超限算枢而没有线缆或其他外部 ME 设备时，网络不会进入工作状态。

## 持久化

运行中和排队中的任务、CPU 通道状态以及可恢复的合成内容都会随方块保存。
当超限算枢存在可恢复状态时将其拆下，会得到携带这些状态的便携式超限算枢；重新放置并接入在线 ME 网络后即可继续处理。
没有可恢复内容的超限算枢会正常掉落。
