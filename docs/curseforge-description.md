# OmniSequence: Transfinite

**Large-scale AE2 automation, research-driven fabrication, and quantum-linked multiblocks.**

OmniSequence: Transfinite expands Applied Energistics 2 and ExtendedAE with machines built for demanding endgame factories. Manage thousands of patterns, run concurrent crafting jobs, build a Matter Fabrication Well, and develop your production network through research.

This description covers **version 2.0.3 for Minecraft 1.21.1 / NeoForge / Java 21**. For other downloads, use the requirements and changelog attached to that file.

## Machines for your endgame factory

### Matter Fabrication Well

A pearl-white, silver and champagne-gold chamber with a **41 × 41 footprint and 27-block height** combines material processing with a research progression system.

- Use item and fluid ports for direct processing, or install **Pattern Assemblies with 36 processing-pattern slots each** to receive jobs from AE2 autocrafting.
- Install service blocks along the 24 front positions or the 20 central platform positions.
- Process compatible addon resources through custom recipes using registered AEKey inputs. These recipes are delivered through pattern assemblies; manual ports handle items and fluids.
- Return finished products and refunds to ME through persistent buffers. Recipe lookup checks the expected outputs and quantities as well as the ingredients.

Start with **AE Foundation**, then unlock the **Sequence Array** and, with Advanced AE installed, **Omni-Computation** branches. Built-in research has nine completion levels and takes **30 seconds per round at 20 TPS**. Deeper research increases the well's production parallelism and processing speed for the relevant recipes. Progress belongs to each well controller, and modpacks can change the costs, duration, prerequisites and unlocks.

### Molecular Sequence Rewrite Array

A compact crafting machine with **360 pattern slots across ten pages**. It batches supported recipes, can process them in as little as one tick, and returns outputs and container remainders through a persistent ME buffer.

### Assembler Matrix Sequence Rewrite Core

Install this core inside an **ExtendedAE Assembler Matrix** in place of ordinary crafting and speed cores. It runs recipe assembly and remainder handling, batches supported reusable inputs, and returns aggregated outputs to ME.

### Sequence Array

Build a floating **Frost Feather Crown** with a **61 × 61 footprint and 29-block height**. Its central controller brings together:

- **7,200 pattern slots by default**, configurable from one to 300 pages for a maximum of **10,800 slots**. The formed library is distributed across 14 quantum crystals and managed through the controller.
- Support for encoded AE2 crafting, smithing-table and stonecutting patterns.
- **Nine separate passive autocrafting slots**, each with its own enable switch, ingredient reserves and output-stock target. Keep essentials stocked directly from ME without repeatedly placing orders.
- **Matter sequence rewriting**: deconstruct eligible materials into metal, mineral, crystal and organic sequences, then reproduce items from a blueprint sample. Entropy, cooling and configurable Acceleration Card tiers regulate this process.
- Independent RGB effects, structure projection, automatic construction and controlled dismantling.

### Omni-Computation Core

A floating celestial crown with a **65 × 65 footprint and 35-block height** serves as a large crafting CPU. It provides `Long.MAX_VALUE`-scale logical crafting storage, creates virtual CPU lanes for active requests, and retains spare capacity for new jobs. Saved tasks and internal materials survive temporary structure damage or unavailable chunks.

### Transfinite Compute Nexus — new in 2.0.3

Bring the Omni virtual CPU system into **one block**. Connect a nexus to an ME cable on any face and submit crafting requests through your AE2 terminal. Each nexus manages its own CPU lanes, even when several are placed together.

The nexus requires a powered network and one channel, with a configurable default idle cost of **16,384 AE/t**. Recoverable jobs and CPU contents can remain with the block when it is removed and placed again. It has no machine screen, quantum link, pattern inventory or automatic chunk loading.

The default Omni-Computation Core and nexus progression requires **Advanced AE**. The nexus is manufactured in the well after unlocking the Omni-Computation research branch.

## Planning and material dispatch

**AppliedEnhancements is a required companion mod.** It supplies AELIS planning and shared AE2 enhancements such as large crafting orders, material summaries, pattern caching and infinite-cell handling. OmniSequence connects an active Omni core or nexus to its public planner API and manages its own machines, batching and virtual CPUs. Shared enhancement settings belong to AppliedEnhancements.

Material delivery adapts to the target machine:

- Providers that explicitly support atomic batching can accept complete batches with 64-bit quantities.
- Safe single-ingredient recipes can increase their batch size as the provider accepts more work.
- Other recipes are delivered as complete individual recipes, preserving material grouping.
- Shared dispatch budgets and rotating work order distribute time across large jobs.

**Logical capacity is not physical throughput.** Materials, power, output space, provider acceptance and server tick time still determine how much work can finish.

The three molecular crafting machines support persistent reusable-input batches and deterministic durability-tool pools. Canceling a job returns unconsumed inputs and the tools' current states. Random durability changes and unsupported remainder behavior use the native single-craft path.

## Build, connect and recover

Use in-game projections and JEI's interactive structure previews to inspect layers, orientation and materials before building. Large multiblocks support automatic construction and dismantling; dismantling processes matching blocks from top to bottom and retains its progress across saves.

The large multiblocks automatically keep their required chunks loaded while formed or performing construction, dismantling or structure updates. Structural damage pauses affected work while preserving its state. The single-block nexus does not load chunks for you.

Those occupied chunks also block natural spawning at every height, including monsters, animals, aquatic mobs and bats. This includes natural world-generation, patrol and reinforcement spawns. Spawners, spawn eggs, commands and existing mobs remain unaffected. Protection follows the chunk footprint and ends when no multiblock owns it; the single-block nexus has no such area.

Large controllers also support **Entangled Quantum Links**. Place matching Entangled Singularities in the controller and a powered remote AE2 Quantum Ring for cross-dimensional ME access. The link costs an additional **512 AE/t and one channel**, and can supply construction materials before the multiblock is complete.

When supported molecular machines hold active batches or large output buffers, their recovery data stays with the dropped machine instead of becoming a large pile of loose items. For the formed Sequence Array's main pattern library, retain its pattern-bearing crystals when moving or rebuilding the structure.

## Guides and modpack support

- Hover supported machine items in an inventory or JEI and press **G** to open English or Chinese AE2 GuideME documentation.
- View the well's live processing recipes, quantities and research unlocks in JEI and GuideME.
- Responsive machine interfaces fit smaller viewports; 2.0.3 corrects JEI hover/highlight coordinates during scaled rendering.
- Define fabrication and research recipes through datapacks or KubeJS. Customize matter rewriting with `config/molecularmanipulator/matter_rewrite_rules.json`.
- Integrate other pattern-holding machines through the public **Omni Batch Provider API v1**, and manage research through the documented research API.

## Requirements and installation

Install the matching **Minecraft 1.21.1 NeoForge** files on both client and server:

| Component | Requirement |
| --- | --- |
| Java | 21 |
| NeoForge | 21.1.220 or later for Minecraft 1.21.1 |
| Applied Energistics 2 | 19.2.17+ |
| AppliedEnhancements | 1.0.6+; install separately |
| ExtendedAE | 1.21-2.2.32-neoforge+ |
| Glodium | Compatible Minecraft 1.21.1 NeoForge build; project baseline is 1.21-2.2-neoforge |
| LDLib2 | 2.2.18+ |

**Advanced AE 1.6.11+** is optional for the base mod and required for the default Omni-Computation research branch and nexus recipe. Optional integrations also include JEI, ExtendedAE Plus and AE2WTLib-compatible terminals.

Stop the client/server before replacing the JAR, install the same OmniSequence version on both sides, and keep one enabled OmniSequence JAR per instance. When upgrading from a configuration above 300 pattern pages, move patterns beyond page 300 before installing 2.0.3. Use the current projection to relocate service blocks from the well's former outer service positions.

**Known incompatibility:** Expanded AE **2.1.1** includes a conflicting pattern-provider mixin and is blocked. ExtendedAE and AppliedFlux themselves are not the mod named in that conflict.

The Mod ID remains **`molecularmanipulator`** for existing world and script references. This project uses the **MIT License**.

## Links

- [Source code](https://github.com/AyaYumi/OmniSequence-Transfinite)
- [Issues](https://github.com/AyaYumi/OmniSequence-Transfinite/issues)
- [NeoForge changelog](https://github.com/AyaYumi/OmniSequence-Transfinite/blob/1.21.1-neoforge/CHANGELOG.md)
- [Integration APIs and recipe limitations](https://github.com/AyaYumi/OmniSequence-Transfinite/blob/1.21.1-neoforge/docs/README.md)
- [AppliedEnhancements](https://github.com/AyaYumi/AppliedEnhancements)

---

# 万象构序：超限

**面向大型 AE2 自动化网络的研究、构筑与跨维度多方块系统。**

万象构序：超限是 Applied Energistics 2 与 ExtendedAE 的后期扩展。管理数千份样板、同时处理多个合成任务、搭建物质构筑井，并通过研究逐步扩展工厂的生产能力。

以下介绍对应 **2.0.3，Minecraft 1.21.1 / NeoForge / Java 21**。其他下载文件的版本、前置和功能请以各自说明为准。

## 后期工厂的核心设备

### 物质构筑井

采用珍珠白、银色与香槟金配色的构筑腔体，**占地 41 × 41、高 27 格**，将物质加工与研究成长结合在一起。

- 通过物品、流体接口直接加工，或安装**每台拥有 36 个处理样板槽的样板总成**，承接 AE2 自动合成任务。
- 正前方 24 个位置和中央平台 20 个位置可安装服务方块。
- 自定义配方可使用已注册的 AEKey 输入，接入兼容附属的资源。这类配方通过样板总成投料，手动接口处理物品和流体。
- 产物和退款通过持久缓冲返回 ME；配方查询同时考虑材料与预期产物及其数量。

先完成**一阶：AE 基础**，再进入**二阶：构序阵列**；安装 Advanced AE 后还可进入**二阶：万物演算**。内置研究共有九级进度，在 20 TPS 下每轮需 **30 秒**。深度研究提升构筑井对应配方的并行上限和加工速度。研究进度属于各自的控制器，整合包可以调整费用、耗时、前置条件和解锁内容。

### 分子构序重写阵列

拥有 **360 个样板槽、共十页**的单方块合成设备。对支持的配方批量执行，最快一 Tick 完成加工，并通过持久 ME 缓冲返回产物与容器余物。

### 装配矩阵构序重写核心

安装在 **ExtendedAE 装配矩阵**内部，替代普通合成核心和速度核心。执行真实配方装配及容器返还逻辑，批量使用受支持的可复用输入，并将聚合产物送回 ME。

### 构序阵列

搭建**占地 61 × 61、高 29 格**的悬浮「霜晶羽冠」，通过中央控制器管理：

- 默认 **7,200 个样板槽**，可配置一至 300 页，最多 **10,800 槽**。成型后由 14 颗量子水晶分摊保存样板库，仍由控制器统一管理。
- 支持 AE2 编码合成、锻造和切石样板。
- **九个独立被动自动合成槽**，分别设置启停、原料保护量和成品库存目标，直接从 ME 补充常用物资。
- **物质构序重写**：将符合规则的物品分解为金属、矿物、晶体和有机四类构序，再根据蓝图样品复制物品。熵值、散热与可配置的加速卡档位共同调节加工过程。
- 独立 RGB 特效、结构投影、自动施工和受控拆卸。

### 万物演算核心

**占地 65 × 65、高 35 格**的悬浮星冕式合成 CPU，提供 `Long.MAX_VALUE` 级逻辑合成存储。根据当前任务维护虚拟 CPU 通道，并预留空闲容量接收新请求；结构临时损坏或区块不可用时保留已保存任务和内部材料。

### 超限算枢——2.0.3 新增

将万物演算的虚拟 CPU 系统集成到**一个方块**中。任意一面接入 ME 线缆，即可通过 AE2 终端提交合成任务；多个算枢相邻放置时仍独立管理各自的 CPU 通道。

算枢需要已供电的网络和一个频道，默认待机消耗 **16,384 AE/t**，可以配置。可恢复任务和 CPU 内容可随方块拆下、重新放置后继续处理。它没有独立机器界面、量子链路、样板库存或自动区块加载功能。

默认的万物演算核心和超限算枢成长路线需要 **Advanced AE**。超限算枢在完成万物演算分支解锁后，由构筑井加工制作。

## 合成规划与材料派发

**AppliedEnhancements 是必需的独立前置。** 它负责 AELIS 规划及大额下单、材料统计、样板缓存、无限存储元件处理等通用 AE2 增强。万象构序通过在线万物演算核心或超限算枢接入其公开规划接口，并负责自身机器的批量执行与虚拟 CPU 管理；通用增强参数在 AppliedEnhancements 中配置。

材料派发根据接收设备的能力工作：

- 明确支持原子批处理的供应器可以接收完整的 64 位数量批次。
- 安全的单原料配方根据供应器的接收情况逐步提高批量。
- 其他配方逐份派发完整材料，维持每份配方的材料组合。
- 共享派发预算与轮换调度，为不同大任务分配执行时间。

**逻辑容量不等于实际吞吐量。** 原料、能源、输出空间、供应器接收能力和服务器 Tick 时间仍决定实际产能。

三种构序合成设备支持持久化可复用输入批次和确定性的多工具耐久池。取消任务时退回未消耗材料及工具当前状态；随机耐久变化和不支持的返还行为沿用原生逐份合成路径。

## 搭建、联网与恢复

通过游戏内投影和 JEI 可交互结构预览，查看层级、朝向及完整材料。大型多方块支持自动搭建和拆卸；拆卸按高度从上到下处理匹配方块，进度可以跨存档恢复。

大型多方块在成型及施工、拆卸、结构更新期间自动强加载所需区块。结构损坏会暂停相关工作并保留状态。单方块超限算枢不提供区块加载。

这些占地区块的整个高度同时禁止自然生成生物，包括怪物、动物、水生生物和蝙蝠，涵盖自然生成、世界生成、巡逻和增援生成。刷怪笼、刷怪蛋、指令和已有生物不受影响。禁刷随区块范围生效，最后一台多方块释放范围后解除；单方块超限算枢不提供该保护。

大型控制器还支持**缠绕态量子链路**：将一对缠绕态奇点分别放入控制器与已供电的远端 AE2 量子环，即可跨维度接入 ME。链路额外消耗 **512 AE/t 和一个频道**，多方块尚未完成时也能通过远端网络获取施工材料。

受支持的构序机器持有批次或大额输出缓存时，恢复数据随掉落机器保留，避免将巨量材料变成散落物品。搬迁或重建成型构序阵列时，请同时保留携带大型样板库数据的量子水晶。

## 游戏内文档与整合包支持

- 在物品栏或 JEI 中指向受支持的机器物品，按 **G** 打开中文或英文 AE2 GuideME 指南。
- 在 JEI 和 GuideME 中查看构筑井实时配方、数量与研究解锁要求。
- 响应式机器界面适应较小视口；2.0.3 修正缩放绘制时 JEI 悬浮与高亮坐标。
- 使用数据包或 KubeJS 定义加工和研究配方，通过 `config/molecularmanipulator/matter_rewrite_rules.json` 定制物质构序规则。
- 第三方样板机器可接入公开的 **Omni Batch Provider API v1**；研究管理另有文档化接口。

## 前置与安装

客户端和服务端均需安装匹配的 **Minecraft 1.21.1 NeoForge** 文件：

| 组件 | 要求 |
| --- | --- |
| Java | 21 |
| NeoForge | Minecraft 1.21.1 对应的 21.1.220 或更高版本 |
| Applied Energistics 2 | 19.2.17+ |
| AppliedEnhancements | 1.0.6+，需单独安装 |
| ExtendedAE | 1.21-2.2.32-neoforge+ |
| Glodium | 兼容的 Minecraft 1.21.1 NeoForge 版本；项目基线为 1.21-2.2-neoforge |
| LDLib2 | 2.2.18+ |

**Advanced AE 1.6.11+** 对基础模组为可选，对默认万物演算研究分支和超限算枢配方为必要条件。其他可选兼容包括 JEI、ExtendedAE Plus 和兼容 AE2WTLib 的终端。

替换 JAR 前完全关闭客户端／服务器，双端使用相同版本，每个实例保留一个启用的万象构序 JAR。如果原配置超过 300 页样板，请先迁出第 300 页之后的样板，再安装 2.0.3；原先位于构筑井外围服务位置的方块，应按当前投影迁移。

**已知不兼容：** Expanded AE **2.1.1** 包含冲突的样板供应器 Mixin，已被阻止加载。该冲突并非针对 ExtendedAE 或 AppliedFlux 本体。

Mod ID 保持为 **`molecularmanipulator`**，延续现有世界和脚本引用。项目采用 **MIT 开源协议**。

## 项目链接

- [源码](https://github.com/AyaYumi/OmniSequence-Transfinite)
- [问题反馈](https://github.com/AyaYumi/OmniSequence-Transfinite/issues)
- [NeoForge 更新日志](https://github.com/AyaYumi/OmniSequence-Transfinite/blob/1.21.1-neoforge/CHANGELOG.md)
- [集成 API 与配方限制](https://github.com/AyaYumi/OmniSequence-Transfinite/blob/1.21.1-neoforge/docs/README.md)
- [AppliedEnhancements](https://github.com/AyaYumi/AppliedEnhancements)
