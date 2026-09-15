---
navigation:
  parent: omnisequence-index.md
  title: 万物演算核心
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

<BlockImage id="molecularmanipulator:omni_computation_controller" scale="8" />

万物演算核心是 65×65×35 的后期[合成 CPU](ae2:items-blocks-machines/crafting_cpu_multiblock.md)。结构完整并联机后，它会提供近似无限的逻辑合成存储与并行能力。

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

> 这是万物演算分支中的多方块设备；同一分支的单方块 CPU 请参阅[超限算枢](transfinite_compute_nexus.md)。

## 概览

| 项目 | 数值 |
| --- | --- |
| 结构 | 65 × 65 × 35 |
| 所需空间 | 控制器下方 17 格、上方 17 格；左右各 32 格，前方 22 格、后方 42 格 |
| 解锁方式 | 二阶：万物演算首次完成 |
| 研究前置 | 一阶完成一次；二阶每轮 30 秒 |

控制器及部件在[物质构筑井](matter_fabrication_well.md)中加工制作。

## 搭建结构

1. 将控制器正面朝向结构外侧放置。
2. 右键控制器并开启“投影”。半透明方块表示缺失位置，红色边框表示方块冲突。
3. 在 JEI 结构分类中查看全部层级和完整材料清单。
4. 清理冲突方块后点击“一键搭建”。系统优先从玩家背包取材，随后从已连接的 ME 网络抽取。
5. 接入并供应 ME 能量；若结构没有立即成型，可点击“检测”重新校验。

### 结构形状

当前布局为放大的悬浮星冕仪，分为清楚的三层：

| 层 | 半径 |
| --- | --- |
| 最外侧一整圈水平圆环 | 中心线 31 格 |
| 内部两条完整轨道，各倾斜约 45° 并互相交错 | 23.5 格 |
| 中央的镂空球笼 | 骨架 10.5 格 |

环与环、环与球体之间保留空隙。六个水晶弧托嵌在外环上，前方没有独立托盘或断开的环段。

控制器仍位于球心前方 10 格的赤道位置，嵌入球笼正面，保留操作窗口。星核及内层陀螺光环随主体放大；外环与两条内轨的光效分别沿真实方块轨道运行。客户端接收服务器确认的结构版本后选择对应特效。

### 旧版 1.3.9

旧版兼容仅保留正式 1.3.9 的 31×31×39 径向核心，标记为“旧版 1.3.9”；其他历史与开发试验版蓝图不再自动识别或迁移。

> 当前版本建筑变化较大，请先在提示区打开建筑投影确认。第一次点击“更新结构”只进入确认状态，等待片刻后再次点击才会执行，5 秒未确认则取消。

更新会回收旧结构并搭建新版，需要新版材料和回收空间。控制器移动到旧位置上方 15 格、背后 5 格处，内部任务与量子槽内容保留。

使用 AE2 扳手可旋转控制器并重新计算结构朝向。

## 自动合成

核心会随合成请求动态创建虚拟 CPU 通道，并保留空闲通道。结构成型并接入在线 AE 网络后，本网络的合成请求可以使用加速规划器。

> 循环合成仍需要可启动该循环的种子材料和其余耗材；规划成功并不代表原料或能量可以省略。

材料派发根据机器接收能力与服务器负载调整，实际吞吐量仍受原料、能源、背压与服务器 Tick 时间限制。

## 持久化与远程连接

* 核心在成型以及搭建、拆除、结构更新期间自动强加载所需区块。
* 正常拆下控制器时，存储内容、运行任务与量子槽内容会随掉落方块保存，重新安装后仍需恢复有效结构和网络。
* 结构损坏或部分区块卸载时，运行中的任务、内部材料和进度都会保留；结构重新完整加载并通过校验后会继续运行。

### 量子链路

量子槽可放入一对缠绕态奇点中的一个；将另一个放入已供电的 AE2 量子环，即可跨维度连接核心。

| 项目 | 数值 |
| --- | --- |
| 额外耗电 | 512 AE/t |
| 频道 | 1 个 |
| 冲突 | 有线连接与冲突的远端网络不能同时使用 |

### 拆除

“一键拆除”会分批移除结构并保留控制器。回收方块优先送入 ME 网络，其次进入玩家背包；两处均满时会安全暂停。

队列只包含所选结构实际存在的方块，按世界高度由高到低、同层逐行蛇形拆除，完成一层才进入下一层。空气和已换成其他类型的目标不占拆除数量；暂停和存档重载后继续原队列，不重新扫描历代结构范围。

## 自然生成保护

成型及施工、拆卸或结构更新期间，本多方块占用区块的整个高度禁止自然生成生物，包括怪物、动物、水生生物和蝙蝠，同时拦截巡逻和增援生成。

刷怪笼、刷怪蛋、繁殖、指令及已有生物不受影响。禁刷本身无需 AE 供电；当没有结构或作业占用该区块时恢复正常生成。

## 配方

<RecipeFor id="molecularmanipulator:omni_computation_controller" fallbackText="研究或配方被禁用时无法制作此方块。" />
