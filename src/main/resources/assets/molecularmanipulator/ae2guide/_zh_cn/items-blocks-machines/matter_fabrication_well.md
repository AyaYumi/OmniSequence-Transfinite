---
navigation:
  parent: omnisequence-index.md
  title: 物质构筑井
  icon: molecularmanipulator:matter_fabrication_controller
  position: 900
item_ids:
- molecularmanipulator:matter_fabrication_controller
- molecularmanipulator:matter_fabrication_casing
- molecularmanipulator:matter_fabrication_glass
- molecularmanipulator:matter_fabrication_coil
- molecularmanipulator:matter_fabrication_stabilizer
- molecularmanipulator:matter_fabrication_core
---

# 物质构筑井

<BlockImage id="molecularmanipulator:matter_fabrication_controller" scale="8" />

物质构筑井是一座集材料加工、AE 自动合成和研究于一体的多方块机器，也是本模组的研究起点。
先用 AE2 原版材料制作控制器、结构部件和输入输出口，再通过研究解锁后续机器与加工配方。
[样板总成](matter_fabrication_pattern_assembly.md)需要完成一阶研究后，在构筑井内制作。

## 搭建与连接

1. 当前结构为占地 41×41、高 27 格的珍珠白构筑舱。控制器正面朝向操作侧，预留完整施工空间。
2. 打开控制器，使用“投影”检查缺失位置和冲突方块；JEI 的物质构筑井结构页可查看各层和材料清单。
3. 清理冲突后点击“一键搭建”。施工优先取用玩家背包材料，再从连接的 ME 网络取材。
4. 将[输入输出口](matter_fabrication_ports.md)或样板总成放入允许的接口位置。手持这些方块时，附近构筑井会显示安装位置，包含前方平台的可用接口排。
5. 结构成型后给控制器连接并供应 ME 能量。材料加工通过接口或样板总成进行，研究从控制器网络取材。

控制器的量子槽可以放入配对的缠绕态奇点，将另一枚放在已供电的 AE2 量子环内。
未成型时量子链路可用于取用远端施工材料；成型后可连接工作网络。量子连接额外消耗 512 AE/t 和一个频道。
链路状态显示在控制页面，连接不同网络产生冲突时需要先处理原有连接。

## 加工与研究

新控制器为 0 阶。[一阶研究](matter_fabrication_research.md)首次完成后开放 AE 材料加工、后续研究所需的材料加工和样板总成配方。
二阶分成构序阵列与万物演算两个分支，默认只要求一阶完成一次，可同时研究。

普通加工把物品、流体送入对应输入口，并为产物准备输出口。使用 AE 自动合成时，在样板总成内放入与构筑井配方匹配的处理样板。
JEI 和本指南内的加工配方会标明对应研究阶段；研究只决定本控制器的配方权限与加工加成。

## 运行与保存

合成中会显示旋转星环、材料汇聚光流和制造扫描层；研究星图在上方独立显示，支持多个分支同时研究。
暂停、前置条件不满足、网络或供能不足时，研究光脉冲会停止。第三方研究会自动选取星图样式。

物质构筑井只保留当前蓝图，不再识别开发过程中的试验版建筑。成型、搭建或拆除期间，控制器自动强加载所需区块；停止需要强加载的工作并失去有效结构后会释放相应区块。
强加载不会提供额外材料或能量。

“一键拆除”按层从上到下回收结构并保留控制器，优先返还 ME 网络，其次进入玩家背包；空间不足时暂停并保存进度。
正常拆下有存储内容的本模组方块时，内容会随掉落方块保存。控制器保存研究进度与已接收任务，接口保存各自缓存，样板总成保存样板及其批次。
重新放置后仍需恢复有效结构、网络和供能才能继续。

## 控制器配方

<RecipeFor id="molecularmanipulator:matter_fabrication_controller" fallbackText="当前整合包没有此物品的可用配方，请查看 JEI 或整合包说明。" />

## 详细说明

<SubPages />
