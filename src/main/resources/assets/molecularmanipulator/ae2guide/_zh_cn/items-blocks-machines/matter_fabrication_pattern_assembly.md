---
navigation:
  parent: items-blocks-machines/matter_fabrication_well.md
  title: "物质构筑井样板总成"
  icon: molecularmanipulator:matter_fabrication_pattern_assembly
  position: 2
item_ids:
- molecularmanipulator:matter_fabrication_pattern_assembly
---

# 物质构筑井样板总成

装上样板总成后，你可以直接在 ME 终端请求构筑井产物，不必每次自己向接口投料。

<Row>
<BlockImage id="molecularmanipulator:matter_fabrication_pattern_assembly" scale="4" />

<ItemImage id="ae2:pattern_encoding_terminal" scale="4" />

<ItemImage id="ae2:pattern_access_terminal" scale="4" />
</Row>

从左到右：样板总成、用来编码样板的终端、用来管理已安装样板的终端。

## 先准备什么

- 在[这座井的研究页](matter_fabrication_research.md)完成**Ⅰ阶：AE 基础构筑**。
- 先用[物品接口](matter_fabrication_ports.md)按下方配方做出第一个总成。
- 准备有电、有存储的 ME 网络、合成 CPU、样板编码终端和空白样板。
- 选择一条已经解锁的**构筑井加工配方**。处理样板要按配方数量记录消耗的原料和产物。

<RecipeFor id="molecularmanipulator:matter_fabrication_pattern_assembly" />

## 一步步完成第一单

1. **安装总成。** 手持总成查看高亮位置，替换其中一个外壳。构筑井要保持成型、在线。
2. **编码处理样板。** 在样板编码终端中打开需要的构筑井 JEI 配方，点击**编码并上传至构筑井**，即可编码并送入总成。
3. **查看样板页。** 上传后，样板应出现在总成的**样板**页。每个总成有 **36 个样板槽**；也可以手动编码匹配的处理样板，再自己放入。
4. **单独放好可复用工具。** 如果 JEI 列出催化剂，按下节方法放进**催化剂**页，不要把它写成样板中需要消耗的原料。
5. **在 ME 中请求产物。** 合成 CPU 会把材料交给总成，构筑井负责加工，再把产物送回 ME。

总成支持物品、流体，以及星讯流等配方资源。 由总成接单的加工不需要另外安装输入输出口。

### JEI 会把样板上传到哪里？

上传消耗**一张空白样板**，先用终端本地槽，再从相连 ME 库存取用。 目标必须是同队已成型构筑井内、区块已加载的总成， 优先选择同维度最近且有空位的总成。 找不到目标、总成已满或样板重复时，不消耗空白样板。

安装 FTB Teams 时，控制器归放置者当时的队伍；未安装时归放置者个人。 换队后需要更新归属，可重新放置控制器。总成名称不影响上传； 起好名字后，在样板终端中更容易管理。

ExtendedAE Plus 的自动选择上传目标功能支持 **1.6.4.1**。

## 可复用的催化剂

**催化剂**就是配方需要、但做完不会消耗的工具。每个总成有 **36 个催化剂槽**，同一座已成型构筑井内、已加载的总成共享使用。

<Row>
<ItemImage id="molecularmanipulator:ghost_matter" scale="4" />

<BlockImage id="molecularmanipulator:flawless_star_marrow_parent_rock" scale="4" />
</Row>

例如：幽灵物质用来[生产引力水晶](nomai_materials.md)，无瑕星髓母岩用来进行[星讯诱导结晶](star_marrow_crystals.md)。

1. 按配方所列数量，把催化剂放进同井任意总成的**催化剂**页。
2. 处理样板只填写**消耗的原料和产物**。
3. 将催化剂留在槽中。增加加工数量，不会增加催化剂需求。

取走催化剂后，任务会保留材料和进度等待，放回即可继续。

## 成功时看哪里

产物在 ME 中显示为可合成，总成显示**AE 网络已连接**，下单后能在 ME 中收到成品。

| 总成页面 | 查看内容 |
| --- | --- |
| 样板 | 已安装的处理样板 |
| 输入 | 已经送来、等待加工的材料 |
| 输出 | 等待 ME 接收的产物 |
| 催化剂 | 本井共享的可复用工具 |

**退回待加工原料**只退回还没开始的任务，已开始的任务保留材料。ME 已满时，产物和待退原料会继续保存在总成中，腾出空间后再返回。 正常拆下总成会保留样板、催化剂、任务和库存。

## 不工作时先查这些

| 问题 | 处理方法 |
| --- | --- |
| 上传找不到总成 | 检查队伍归属、结构成型、区块加载，以及是否有空样板槽。 |
| ME 无法规划合成 | 检查样板、原料和合成 CPU；构筑井需要处理样板，不是普通合成样板。 |
| 收到材料后一直等 | 检查这座井的研究、供电和催化剂；误把催化剂写成消耗材料时，重新编码。 |
| 做完却没收到成品 | 给 ME 腾出空间，并查看总成的**输出**页。 |
| 生产太慢 | [深度研究](matter_fabrication_research.md)增加一次能做的份数；控制器所在 ME 网络上的[星算引擎](star_computation_engine.md)提供生产速度。 |

兼容的 Neo ECO、数据能源三位一体和闪电科技 CPU 可以提交批量任务。[超限算枢](transfinite_compute_nexus.md)能一次送入当前可用的大批原料；加工按运行进度耗电，不要求预付整批电量。

## 接下来做什么

试做[星髓加工材料](star_marrow_crystals.md)或[引力水晶](nomai_materials.md)，再用[深度研究](matter_fabrication_research.md)提高每批产量。
