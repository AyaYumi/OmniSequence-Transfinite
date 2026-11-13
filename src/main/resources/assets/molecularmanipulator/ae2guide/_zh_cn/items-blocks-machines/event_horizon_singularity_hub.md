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

视界奇点天枢为 ME 网络持续供货。资源采集同时产出列表中的物品，物质复制则消耗奇点序质来复制样品。两项独立运行。

<ItemGrid>
<ItemIcon id="molecularmanipulator:event_horizon_singularity_hub" />
<ItemIcon id="molecularmanipulator:singularity_ring_track" />
<ItemIcon id="molecularmanipulator:event_horizon_lens" />
<ItemIcon id="molecularmanipulator:black_hole_capture_node" />
<ItemIcon id="molecularmanipulator:white_hole_resource_core" />
<ItemIcon id="molecularmanipulator:singularity_crystal_tower" />
</ItemGrid>

上图依次是主控、轨道、透镜、捕获节点、资源核心和晶塔。右键主控打开操作界面，产物直接存入 ME。

## 准备什么

- 在构筑井完成Ⅳ阶：视界物质工程制作结构件，再完成Ⅴ阶：恒星核心工程解锁主控和白洞资源核心。后两者各需一枚[微型超新星](black_and_white_holes.md)。完整用料见 JEI 天枢结构页。
- 静态建筑占地 **97×97、高 129 格**，主控高度为**基面 Y + 65**。基面是最低实体方块的高度。中央光井要留空，周围也要为运动部件留出空间。
- 准备 ME 供电、可用频道和足够的物品存储。产序质与复制另需流体存储，见[物质复制](singularity_matter_duplication.md)。

## 推荐做法：用建筑包展开

<Row>
<BlockImage id="molecularmanipulator:folding_assembler" scale="4" />

<ItemImage id="molecularmanipulator:folded_building_package" scale="4" />

<ItemImage id="molecularmanipulator:star_chart_calibrator" scale="4" />
</Row>

建筑包包含主控与全部标准建材，用星图定标器选址展开。

1. 将空间折叠装配器接到能自动合成的 ME 网络，订购天枢建筑包。订单会消耗主控和完整建材。
2. 把包裹留在背包，手持**星图定标器右键场地**，选择包裹、调整位置和朝向。
3. 查看浅蓝投影和金色边界；清除红色冲突，包括光井内的障碍，再确认展开。
4. 等待建筑展开完毕，接好 ME，在主控的**总览**确认“结构已成型”和网络在线。定标器可重复使用。

选址和中断施工的详细操作见[折叠建筑与整机搬迁](folded_buildings.md)。JEI 结构预览支持旋转、平移、缩放与分层查看。

### 已有主控，也可以自动搭建

放置主控并打开**结构投影**，按 JEI 清单准备建材，然后点击**自动搭建**；先取背包材料，再取 ME 材料。基壳楼梯与台阶在工作台制作：6 个基壳做 4 个楼梯，3 个横排做 6 个台阶。

## 开始生产

总览显示**结构已成型、网络在线**后，按目标打开对应页面：

| 想做什么 | 操作与成功标志 |
| --- | --- |
| 采集粗矿、原木等 | [资源采集](singularity_resource_collection.md) → 开始采集；启动后，ME 中各类资源增加。 |
| 生产奇点序质流体 | [物质复制](singularity_matter_duplication.md) → 放入微型黑洞并供能；无样品时可直接观察 ME 流体库存增加。 |
| 复制指定物品 | [物质复制](singularity_matter_duplication.md) → 放入样品并备好序质；ME 出现复制品，样品保留。 |

“停止采集”会让运动部件归位，但不停止产液或复制。取出黑洞可停止产液，取出样品可停止复制。总览中的特效设置只改变画面，不改变产量。

### 远端 ME 连接

在右侧栏用“远程连接”绑定构序无线枢纽，连接条件见[无线枢纽设置](sequence_array_controller.md)。

也可在总览的量子槽放一枚缠绕态奇点，配对另一枚放入已供电、已加载的 AE2 量子环。量子连接在未成型时也能取得施工材料。

每次只使用一种连接方式。使用量子槽前，先断开无线枢纽并移除外部 ME 电缆。量子连接额外耗电 **512 AE/t**，仍需提供频道，配对不能被其他端点占用。重启后稍等网络重新连接。

## 整机搬迁

1. 停止采集，结束施工或升级。完成或取消所在 ME 网络的外部 CPU 合成任务，等材料、产物和退款结清。
2. 在总览点击“回收包裹”，按提示再次确认。正常速度下，两次点击间隔约 **0.25～3 秒**。
3. 等运动部件安全归位，领取一个封存包裹。整机、物品、流体、量子奇点、样品、黑洞、加工进度和待交付内容一同保留，也会保留布局、设置和无线绑定。
4. 用定标器在同一存档的新位置或其他维度展开。恢复所选 ME 连接并检查存储，再手动开始采集。如果无线枢纽被移动或替换，需要重新绑定天枢。

等待归位时保持运动范围加载并清除障碍，让发起玩家返回操作。重启或玩家暂时离线不会丢掉回收请求。点击“取消”可撤销封存，部件仍会安全归位。背包满时腾出一格，再在空中使用定标器领取包裹。

**封存包裹需要原存档中的建筑档案。** 不要只复制物品到另一个存档；备份或转移世界时保留完整世界文件夹。更多说明见[折叠建筑与整机搬迁](folded_buildings.md)。[单方块天枢](compact_singularity_hub.md)不使用这套整机回收操作。

## 不工作时先看这里

| 现象 | 怎么处理 |
| --- | --- |
| 不能成型或展开 | 检查高度、红色冲突、缺料和中央光井留空要求。 |
| 成型了却没产物 | 还要联网、点击开始采集，并为全部资源留出 ME 物品空间。复制另查样品与序质。 |
| 运动或归位停住 | 清除运动路径障碍，加载**整个运动范围**的区块，不只是主控区块。 |
| 回收一直等候 | 确认已停采集、任务已结清、发起玩家在场，运动部件能够归位。 |

服务器的多方块强加载开关控制本模组是否保持区块加载。关闭后，只要所需区块已由其他方式加载，仍可启动、运动和归位；缺少区块时机器等待。

## 配方查询

<RecipeFor id="molecularmanipulator:event_horizon_singularity_hub" fallbackText="在 JEI 中查看构筑井配方与研究要求。" />

<RecipeFor id="molecularmanipulator:white_hole_resource_core" fallbackText="在 JEI 中查看构筑井配方与研究要求。" />

<SubPages icons={true} />
