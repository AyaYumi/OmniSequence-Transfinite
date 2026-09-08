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

万物演算核心是 65×65×35 的后期[合成 CPU](ae2:items-blocks-machines/crafting_cpu_multiblock.md)。
结构完整并联机后，它会提供近似无限的逻辑合成存储与并行能力。

本机器需要安装 AdvancedAE。控制器及部件在[物质构筑井](matter_fabrication_well.md)完成“二阶：万物演算”首次研究后，由构筑井加工制作。
默认前置为一阶研究完成一次，二阶每轮研究耗时 30 秒。未安装 AdvancedAE 时该研究分支及配方不可用。

## 搭建结构

1. 将控制器正面朝向结构外侧放置。完整结构占地 65×65、高 35 格，需要控制器下方 17 格、上方 17 格的可建造空间；左右各延伸 32 格，前方 22 格、后方 42 格。
2. 右键控制器并开启“投影”。半透明方块表示缺失位置，红色边框表示方块冲突。
3. 在 JEI 结构分类中查看全部层级和完整材料清单。
4. 清理冲突方块后点击“一键搭建”。系统优先从玩家背包取材，随后从已连接的 ME 网络抽取。
5. 接入并供应 ME 能量；若结构没有立即成型，可点击“检测”重新校验。

当前布局为放大的悬浮星冕仪，分为清楚的三层：最外侧是一整圈水平圆环；内部两条完整轨道分别倾斜约 45°，
互相交错；中央为直径约 23 格的镂空球笼。外环中心线半径 31 格，内轨半径 23.5 格，球笼骨架半径 10.5 格，
环与环、环与球体之间保留空隙。六个水晶弧托嵌在外环上，前方没有独立托盘或断开的环段。

控制器仍位于球心前方 10 格的赤道位置，嵌入球笼正面，保留操作窗口。星核及内层陀螺光环随主体放大；
外环与两条内轨的光效分别沿真实方块轨道运行。客户端接收服务器确认的结构版本后选择对应特效。

旧版兼容仅保留正式 1.3.9 的 31×31×39 径向核心，标记为“旧版 1.3.9”；其他历史与开发试验版蓝图不再自动识别或迁移。
当前版本建筑变化较大，请先在提示区打开建筑投影确认。第一次点击“更新结构”只进入确认状态，等待片刻后再次点击才会执行，5 秒未确认则取消。
更新会回收旧结构并搭建新版，需要新版材料和回收空间。控制器移动到旧位置上方 15 格、背后 5 格处，内部任务与量子槽内容保留。

使用 AE2 扳手可旋转控制器并重新计算结构朝向。

## 自动合成

核心会随合成请求动态创建虚拟 CPU 通道，并保留空闲通道。结构成型并接入在线 AE 网络后，本网络的合成请求可以使用 AppliedEnhancements 提供的 AELIS 规划器。
循环合成仍需要可启动该循环的种子材料和其余耗材；规划成功并不代表原料或能量可以省略。

材料派发根据机器接收能力与服务器负载调整，实际吞吐量仍受原料、能源、背压与服务器 Tick 时间限制。

## 持久化与远程连接

核心在成型以及搭建、拆除、结构更新期间自动强加载所需区块。正常拆下控制器时，存储内容、运行任务与量子槽内容会随掉落方块保存，重新安装后仍需恢复有效结构和网络。

结构损坏或部分区块卸载时，运行中的任务、内部材料和进度都会保留。结构重新完整加载并通过校验后会继续运行。

量子槽可放入一对缠绕态奇点中的一个；将另一个放入已供电的 AE2 量子环，即可跨维度连接核心。
量子链路额外消耗 512 AE/t 和 1 个频道。有线连接与冲突的远端网络不能同时使用。

“一键拆除”会分批移除结构并保留控制器。回收方块优先送入 ME 网络，其次进入玩家背包；两处均满时会安全暂停。
队列只包含所选结构实际存在的方块，按世界高度由高到低、同层逐行蛇形拆除，完成一层才进入下一层。空气和已换成其他类型的目标不占拆除数量；暂停和存档重载后继续原队列，不重新扫描历代结构范围。

## 配方

<RecipeFor id="molecularmanipulator:omni_computation_controller" fallbackText="此配方需要 AdvancedAE；未安装前置或整合包移除配方时不可用。" />
