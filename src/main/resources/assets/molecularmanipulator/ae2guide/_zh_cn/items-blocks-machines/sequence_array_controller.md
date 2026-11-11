---
navigation:
  parent: omnisequence-index.md
  title: "构序阵列控制器"
  icon: molecularmanipulator:molecular_center_controller
  position: 1030
item_ids:
- molecularmanipulator:molecular_center_controller
- molecularmanipulator:molecular_center_casing
- molecularmanipulator:molecular_center_glass
- molecularmanipulator:molecular_center_coil
- molecularmanipulator:molecular_center_stabilizer
- molecularmanipulator:molecular_center_core
---

# 构序阵列控制器

## 用途：集中存配方，并选择下单制作或自动补货

构序阵列是一座大型**样板库**。样板是在编码终端写好配方的卡片，不是原料或成品。阵列默认可存 **7200 张样板**，还内置实际制作功能和 **9 个独立补货槽**。

<Row>
<BlockImage id="molecularmanipulator:molecular_center_controller" scale="4" />
<ItemImage id="ae2:crafting_pattern" scale="4" />
<ItemImage id="ae2:smithing_table_pattern" scale="4" />
<ItemImage id="ae2:stonecutting_pattern" scale="4" />
</Row>

控制器管理上图三类配方：合成、锻造、切石。主样板库让终端知道“能做什么”，阵列内置的加工功能负责“实际制作”，ME 存储才是放原料和成品的仓库。

| 想做什么 | 样板放哪里 |
| --- | --- |
| 从终端下单，指定这次要多少 | 主样板库；网络还需**合成 CPU**，也就是接收和安排订单的设备。 |
| 常用成品少了就自动补满 | “自动合成”页的九个补货槽；由阵列自行取 ME 现有原料制作。 |
| 让熔炉等外部机器加工 | 将处理样板放到样板供应器，连接对应机器，不放进阵列。 |

## 准备

- 在[构筑井](matter_fabrication_research.md)完成一轮**Ⅲ阶：构序工程**，制作控制器和结构部件。这条路线使用 ExtendedAE 材料。
- 留出 **61 × 61 格、高 29 格**的建筑空间。控制器位于水平中央，不在建筑底面；相对控制器向下留 **6 格**、向上留 **22 格**，并先用投影检查场地。建筑不需要实体底座。
- 准备 ME 供电、物品存储和空闲**频道**（网络给联网设备的连接名额）。终端下单还要准备空闲合成 CPU。
- 在 JEI 的结构页查看完整材料清单；下面这些是阵列自身的结构部件，不是另一套物品存储。

<ItemGrid>
<ItemIcon id="molecularmanipulator:molecular_center_controller" />
<ItemIcon id="molecularmanipulator:molecular_center_casing" />
<ItemIcon id="molecularmanipulator:molecular_center_glass" />
<ItemIcon id="molecularmanipulator:molecular_center_coil" />
<ItemIcon id="molecularmanipulator:molecular_center_stabilizer" />
<ItemIcon id="molecularmanipulator:molecular_center_core" />
</ItemGrid>

<RecipeFor id="molecularmanipulator:molecular_center_controller" fallbackText="在 JEI 中查看控制器的构筑井配方与结构材料清单。" />

## 步骤：搭好后先试一张样板

1. 放置控制器，打开界面，在**装配总控**页开启**方块投影**。确认建筑不会穿进山体或已有机器。
2. 按结构清单准备材料，接入施工可用的 ME 网络，或把材料带在背包里。点击**一键搭建**；施工先使用背包，再使用 ME 库存。
3. 等待施工结束，检查界面同时显示**结构完整**和**在线**。补齐缺件、供电和频道后再放样板。
4. 在主样板库放入一张“橡木原木 → 橡木木板”的合成样板，向 ME 存入至少 **4 个原木**。
5. 从同网络终端请求 **16 个木板**，用空闲合成 CPU 确认订单。阵列会在内部制作，成品送回 ME。

主样板库默认 **200 页，每页 36 槽**；服务器可调整页数，最多 300 页。可在控制器翻页、搜索，也可用同网络的样板管理终端整理。样板存于结构中的 **14 颗量子水晶**，施工或回收水晶期间先等操作完成，再整理样板。

### 不想每次手动搬入新样板？

在总控页开启**自动上传**，之后在同网络的原生 AE2 样板编码终端编码合成、锻造或切石样板，新样板会直接进入主库。**新阵列默认关闭此功能**；重复配方会返还空白样板，满库或阵列未就绪时，编码样板留在终端。处理样板不上传。

已有样板可用**一键迁移样板**集中收取：从同网络可取出的样板槽和在线存储盘搬入主库， 仅点击时执行。处理样板不搬； 重复的实体样板分别保留，满库后未搬入的留在原处。

### 需要自动补货？

切到**自动合成**页，将样板放进九个专用槽之一，选中该槽，填写成品目标和原料保留量，点击**设定**后再启动。先用“木板目标 64、原木保留 8”试运行；**成品目标 0 代表不限量**。完整操作见[分子自动合成器](molecular_auto_crafter.md)。阵列的补货产物送回 ME。

## 成功标志

- 控制器显示结构完整且在线，样板终端能找到主库里的样板。
- 16 个木板的测试订单完成，成品出现在 ME，而不是占用样板槽。
- 开启补货后，目标未满时制作，达到目标时暂停，取走成品后继续。

## 排错

| 看到的情况 | 怎么处理 |
| --- | --- |
| 搭建停住或结构不完整 | 看提示补材料、清理投影冲突，并保持施工区域区块加载；所需材料可放背包或施工网络。 |
| 结构完整但离线 | 检查 ME 电力和频道；基础待机耗电默认为 **128 AE/t**。 |
| 放了样板却不能从终端下单 | 检查是否放在主库而不是补货槽，再检查同网络的空闲合成 CPU。 |
| 编码后的样板没有自动入库 | 开启自动上传，确认使用同网络原生 AE2 编码终端、主库有空位，且阵列已就绪。 |
| 不能用存储总线整理补货槽 | 存储总线只访问主样板库；补货槽和量子槽请在控制器中操作。 |
| 有订单却不出货 | 看合成状态中的缺料，并为 ME 成品和返还物腾出空间。 |

## 下一步

远端接线可使用一对**缠绕态奇点**：一枚放控制器的量子槽，配对另一枚放进已供电的 AE2 量子环。该连接额外需要 **512 AE/t 和 1 个频道**，也可在建筑成型前为施工取料。

搬家前先结清外部合成订单，再用**回收包裹**整机打包；首次点击后在 **3 秒内**再次确认。样板库和机内内容一同封存，具体放置方法见[折叠建筑与整机搬迁](folded_buildings.md)。

配色页可调整颜色和本机特效档位，特效不改变产量。需要扩大接单能力时，继续阅读[万物演算核心](omni_computation_core.md)；只是需要小型补货设备时，使用[分子自动合成器](molecular_auto_crafter.md)。
