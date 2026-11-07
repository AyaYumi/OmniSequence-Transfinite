---
navigation:
  parent: omnisequence-index.md
  title: "视界奇点天枢"
  icon: molecularmanipulator:event_horizon_singularity_hub
  position: 1100
item_ids:
- molecularmanipulator:event_horizon_singularity_hub
- molecularmanipulator:singularity_base_casing
- molecularmanipulator:gravity_gilded_block
- molecularmanipulator:spacetime_anchor_pillar
- molecularmanipulator:singularity_ring_track
- molecularmanipulator:event_horizon_lens
- molecularmanipulator:event_horizon_flux_pillar
- molecularmanipulator:black_hole_capture_node
- molecularmanipulator:white_hole_resource_core
- molecularmanipulator:event_horizon_stabilizer
- molecularmanipulator:singularity_crystal_tower
- molecularmanipulator:singularity_base_stairs
- molecularmanipulator:singularity_base_slab
---

# 视界奇点天枢

视界奇点天枢用于持续采集资源，并以奇点序质复制物品。资源采集与物质复制共用天枢连接的 ME 网络，可以分别运行。

<ItemGrid>
<ItemIcon id="molecularmanipulator:event_horizon_singularity_hub" />
<ItemIcon id="molecularmanipulator:singularity_ring_track" />
<ItemIcon id="molecularmanipulator:event_horizon_lens" />
<ItemIcon id="molecularmanipulator:black_hole_capture_node" />
<ItemIcon id="molecularmanipulator:white_hole_resource_core" />
<ItemIcon id="molecularmanipulator:singularity_crystal_tower" />
</ItemGrid>

## 搭建与连接

1. 在构筑井完成**三阶：视界奇点**研究，制作天枢部件。主控与白洞资源核心各需要一枚[微型超新星](black_and_white_holes.md)。
2. 按 JEI 结构清单备料，放置控制器并开启**投影**。
3. 清理冲突后点击**一键搭建**。材料优先取玩家背包，再取 ME 库存。
4. 接通 ME 电源与频道，在总览中确认结构和联网状态。
5. 为采集与复制产物准备 ME 物品存储，为奇点序质准备 ME 流体存储。

天枢基壳楼梯和台阶在工作台制作：6 个基壳按楼梯形排列得到 4 个楼梯，3 个基壳横排得到 6 个台阶。

### 完整建筑包

将**空间折叠装配器**接入 ME，可订购**视界奇点天枢建筑包**；每单消耗一个主控和当前悬晶结构的全部标准建材，创造模式也提供同一包裹。JEI 的视界奇点结构页列出完整用料和包裹产物，并支持旋转、平移、缩放与分层查看。

用**星图定标器**选择场地、包裹及朝向，确认后自动展开并检测成型。预留 **97×97、高 129 格**的范围，**主控高度为基面 Y + 65**；中央光井的 566 个留空位置也要清除遮挡。施工未完成时，可在定标面板中点击“中断回收建筑”，待清理完成后使用原包裹重新展开。详细操作见[折叠建筑与整机搬迁](folded_buildings.md)。

## 控制器界面

| 页面 | 功能 |
| --- | --- |
| 总览 | 查看运行状态，控制投影、搭建和整机回收 |
| [资源采集](singularity_resource_collection.md) | 查看资源列表，开始或停止采集 |
| [物质复制](singularity_matter_duplication.md) | 放入样品与微型黑洞，查看序质库存和能耗 |

点击开始采集后，等待天枢启动，产物会送入 ME。停止后建筑归位。微型黑洞生产序质和样品复制无需启动资源采集。

**特效**按钮可为本机选择 0 关闭、1 精简、2 完整，并同步观看者。Shift+点击恢复沿用各客户端全局设置，不改变产量或运动规则。

## 量子连接

总览页的量子槽只接受**缠绕态奇点**。将另一枚配对奇点放入已供电的 AE2 量子环，即可连接远端网络；未成型时也可获取施工材料。链路额外需要 **512 AE/t 和 1 个频道**。

重载后，等待已加载端点完成 AE2 正常初始化即可重连。已销毁、卸载或更换频率的量子环不会再因残留索引被误认作有效端点；真正被占用的配对频率仍拒绝连接。

## 整机回收与搬迁

1. 停止资源采集，完成正在进行的施工或结构升级。完成或取消天枢所在 ME 网络中的外部 CPU 合成任务，等待材料、产物与退款结算。
2. 使用**总览中原有的回收按钮**：首次点击后，间隔 **5～60 tick** 再次点击确认（正常速度下为 0.25～3 秒），将主控和整台机器封存为**一个建筑档案包裹**。
3. 用星图定标器在同一存档的新位置定标展开。部署后采集、运动与回收均保持停止，准备就绪后再手动开始采集。

若仍有运动部件，须先安全归位，再封存整机。保持运动范围的区块加载并清除障碍。等待请求跨重载和发起玩家离线保留，玩家返回且条件满足后可继续；点击**取消**会撤销后续封存，但安全归位继续。

档案保存主控、原蓝图版本、旧版保留的采集节点和光井必需的留空位置（当前悬晶方案为 566 处），并保留原生库存、缠绕态奇点及其频率、样品、微型黑洞、复制进度、待输出、流体与退款、缓冲能量、特效设置、随机器保存的建材和待回收内容。恢复时沿用原结构版本。**单方块奇点天枢不使用此多方块整机回收入口。**

正常拆下控制器会保留量子槽、样品、微型黑洞和待回收内容。持续生产需要区块保持加载，可通过服务器的多方块强加载设置维持建筑所需区块。

该设置只控制本模组的区块加载票据，不是运动许可。关闭后，只要整个运动范围的必需区块由其他方式保持加载，天枢仍能启动、运动和停靠。缺少区块时暂停运动，禁止启动或归位；检查不会同步加载缺失区块。

## 配方

<RecipeFor id="molecularmanipulator:event_horizon_singularity_hub" fallbackText="当前整合包未提供可用配方，请查看 JEI 和研究条件。" />

<RecipeFor id="molecularmanipulator:white_hole_resource_core" fallbackText="当前整合包未提供可用配方，请查看 JEI 和研究条件。" />

<SubPages icons={true} />
