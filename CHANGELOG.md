# OmniSequence: Transfinite 2.0.3-forge

Minecraft 1.20.1 · Forge · Java 17

## English

Changes from **2.0.2-forge**, including the port of NeoForge commit `32bc92c`. The animation and service-bay updates were already present on the Forge branch; this sync adds the remaining 2.0.3 features.

### Transfinite Compute Nexus

- Added the **Transfinite Compute Nexus**, a single-block AE2 crafting CPU that uses the Omni-Computation Core's virtual CPU and batch-dispatch system.
- Connects to ME cables on all six faces and requires a powered network with one available channel. Its default idle cost is **16,384 AE/t**, configurable through `transfinite_compute_nexus.idle_power`.
- Provides `Long.MAX_VALUE` logical crafting storage and creates independent virtual CPU lanes for concurrent jobs, retaining spare capacity for new requests. Actual throughput depends on materials, power, provider capacity and server tick time.
- Adjacent nexuses keep separate CPU clusters and cannot become part of an ordinary AE2 crafting CPU. Added distinct names in AE2's CPU selection list and a connected powered model with emissive details.
- Saved jobs and recoverable CPU contents can travel with the dropped nexus and resume after placement and reconnection. This block has no machine screen, quantum slot, pattern library or chunk-loading footprint.
- Added its Matter Fabrication Well recipe and bilingual GuideME page. The default recipe unlocks through **Stage 2: Omni-Computation** and requires **Advanced AE**.

### Sequence Array pattern storage

- The formed array now distributes its main pattern library across **14 quantum crystals**, while the controller continues to manage the library and expose it to the Pattern Access Terminal.
- Crystals can retain pattern data when recovered. Automatic construction prefers crystals containing patterns before using blank crystals.
- Changed the configurable maximum from **1,000 pages to 300 pages**: **10,800 slots** at 36 slots per page. The default remains **200 pages / 7,200 slots**; distributing patterns across crystals does not add capacity.

### Matter Fabrication Well and visuals

- Added **20 central service positions**, with five on each of the four platform-collar segments. The **24 front service positions** remain available; the nine former outer service positions are no longer accepted. These positions support item/fluid ports and pattern assemblies.
- Added **20 animated textures** for computation components, crystals, controller faces and fabrication modules, using 24 interpolated frames at two ticks per frame: a 2.4-second loop at 20 TPS.
- Preserved gold and fluid-port blue emissive details across animated frames, refined the controller's dark center panel, and added local purple emissive accents to the Molecular Sequence Rewrite Array.
- Fixed connected-face filler sampling to use the interior of the casing texture in Forge texel coordinates.
- Corrected Forge 1.20.1 sprite UV conversion: its 0–16 coordinates must be normalized before splitting emissive faces. Gold/blue well details and purple molecular-array lines now generate full-bright faces without an AE connection; ordinary shell pixels keep normal shading.
- Removed block-light emission from the well's controller, structure parts and service blocks while retaining their local emissive texture details. Controller powered visuals now follow structure/network state and construction operations.

### JEI and crafting compatibility

- Fixed the responsive-screen case where **JEI's hovered item, highlight and tooltip disagreed with the item under the mouse, while clicks still used the correct position**. JEI 15 container-foreground rendering now uses screen coordinates and restores the machine's render state afterwards. The adapter preserves the origin translation removed by JEI 15 itself; newer entry points are optional.
- Avoided publishing invalid zero-sized GUI properties before screen initialization. Refined visible slot lookup, disabled JEI's unscaled vanilla-slot fallback for responsive screens, and prevented external controls from receiving a second mouse-movement conversion.
- Removed this mod's blanket reduction of non-Omni CPUs with more than 256 co-processors to a value of 255, allowing their own co-processor counts to take effect.
- Updated English/Chinese tooltips and GuideME documentation for the new nexus, crystal storage and research unlocks.
- Refreshed the README, API references, release notes and CurseForge description. Removed the one-off texture import script that depended on a personal Downloads folder; retained runtime assets and animation source images.

### Upgrade notes

1. **If your 2.0.2 array uses more than 300 pages, move patterns from pages 301–1,000 elsewhere before upgrading.** Those slots are outside this version's supported inventory. Back up the world before changing the storage format.
2. Keep pattern-bearing quantum crystals with the array when moving or rebuilding it; the formed library is no longer saved entirely in the controller.
3. If your well uses the nine old outer service positions, move those service blocks to the front row or the new central positions and restore the vacated positions with the blocks shown by the projection.
4. Fully stop the game/server, install the same version on both sides, and keep only one enabled OmniSequence JAR per instance. The Mod ID remains `molecularmanipulator`.
5. This is the **Minecraft 1.20.1 Forge** build. AppliedEnhancements 1.0.6-forge remains a separate required dependency. Native Forge interfaces do not require LDLib2. Advanced AE is optional for the base mod and required for the default Omni-Computation branch and nexus recipe.

### Validation and remaining limits

- **152 Forge unit tests passed**, including JEI 15 entry-point/coordinate checks and a real AE2 15 inventory/NBT round trip across crystal shards.
- **Four isolated Forge GameTests passed** for resource delivery/lookup and nexus cable, CPU, save, drop and power recovery. The separate **AdvancedAE integration test passed**, including the nexus recipe and research unlock and the existing 65,536-craft batch case.
- These checks cover code and coordinate behavior; the latest JEI correction has not received an interactive in-game visual check.
- Existing well-recipe limitations remain: overlapping ingredient alternatives with identical outputs may select different time/power during batch splitting, and adding an earlier matching recipe during reload may leave an existing queue waiting.

---

## 中文

相对于 **2.0.2-forge** 的更新，同步 NeoForge 提交 `32bc92c`。Forge 分支已具备动画与服务位更新，本次补齐其余 2.0.3 功能。

### 超限算枢

- 新增 **超限算枢**：采用万物演算核心的虚拟 CPU 与批量派发系统的单方块 AE2 合成 CPU。
- 六个面均可连接 ME 线缆，需要已供电的网络和一个可用频道。默认待机消耗 **16,384 AE/t**，可通过 `transfinite_compute_nexus.idle_power` 配置。
- 提供 `Long.MAX_VALUE` 级逻辑合成存储，按并发任务创建独立虚拟 CPU 通道，并保留空闲通道接收新请求。实际吞吐量受材料、能源、供应器容量和服务器 Tick 时间限制。
- 相邻算枢保持独立 CPU 集群，不会并入普通 AE2 合成 CPU。新增 AE2 CPU 列表中的独立名称，以及带局部自发光细节的联机连接模型。
- 已保存任务和可恢复的 CPU 内容可以随掉落算枢保留，重新放置并接入网络后继续处理。该方块没有独立机器界面、量子槽、样板库或区块强加载范围。
- 新增物质构筑井加工配方及中英文 GuideME 页面。默认配方由 **二阶：万物演算** 研究解锁，需要 **Advanced AE**。

### 构序阵列样板存储

- 成型阵列的大型样板库改由 **14 颗量子水晶** 分摊保存，仍由控制器统一管理，并向样板访问终端提供访问。
- 回收水晶可以保留样板数据；自动施工优先使用带样板的水晶，再使用空白水晶。
- 可配置上限由 **1,000 页调整为 300 页**，每页 36 槽，合计最多 **10,800 槽**。默认仍为 **200 页 / 7,200 槽**；水晶分摊存储不会额外增加容量。

### 物质构筑井与视觉效果

- 新增 **20 个中央服务位置**，中央平台四个环段各五格；保留 **24 个正前方服务位置**，不再接受原先外围分散的九个位置。这些位置可安装物品／流体接口和样板总成。
- 为演算部件、水晶、控制器正面和构筑井模块加入 **20 组动画贴图**，每组 24 帧、每帧两 Tick 并启用插值，在 20 TPS 下循环周期为 2.4 秒。
- 保留动画中的金色、流体接口蓝色局部自发光，细化控制器中央暗色面板，并为分子构序重写阵列加入紫色局部自发光线条。
- 同时修正连接面填充纹理的坐标，使用外壳内部区域取样。
- 修正 Forge 1.20.1 贴图 UV 换算：其 0～16 坐标必须先归一化才能分割发光面。构筑井金／蓝纹路与分子阵列紫色线条无需 AE 连接即可生成全亮面，普通外壳仍保持正常光照。
- 构筑井控制器、结构件和服务方块不再向环境发出方块光照，保留贴图本身的局部自发光细节。控制器亮起状态跟随结构、网络及施工操作更新。

### JEI 与合成兼容

- 修复响应式界面中 **JEI 悬浮物品、高亮和提示框与鼠标指向不一致，但点击位置仍正确** 的问题。JEI 15 容器前景绘制改用屏幕坐标，保留 JEI 自身会移除的原点平移，结束后恢复机器状态；较新入口按可选兼容处理。
- 避免在界面初始化前向 JEI 上报宽高为零的非法区域；细化可见槽位查找，禁用响应式界面的 JEI 原始未缩放槽位回退，并防止外部控件的鼠标移动被二次换算。
- 移除本模组对非 Omni CPU 的统一限制：协处理器超过 256 时，不再强行压为 255，使其自身的协处理器数量生效。
- 更新超限算枢、水晶样板存储及研究解锁相关的中英文提示和 GuideME 文档。
- 同步更新 README、API 文档、版本说明及 CurseForge 介绍；移除依赖个人 Downloads 目录的一次性贴图导入脚本，保留运行时资源与动画生成源图。

### 升级说明

1. **如果 2.0.2 的阵列使用了超过 300 页，请先将第 301～1,000 页的样板迁出，再升级。** 这些槽位不在本版支持的库存范围内。变更存储格式前请备份世界。
2. 搬迁或重建阵列时保留带样板的量子水晶；成型阵列的样板库不再全部保存在控制器中。
3. 若构筑井使用了原先外围九个服务位置，请将服务方块迁到正前方或新增中央位置，并按投影补回原位置所需方块。
4. 完全退出游戏／关闭服务器后替换文件，客户端和服务端使用相同版本，每个实例只保留一个启用的万象构序 JAR。Mod ID 仍为 `molecularmanipulator`。
5. 本包适用于 **Minecraft 1.20.1 Forge**。AppliedEnhancements 1.0.6-forge 仍需单独安装，原生 Forge 界面不需要 LDLib2；Advanced AE 对基础模组为可选前置，对默认万物演算研究分支和超限算枢配方为必要条件。

### 验证与现有限制

- **152 项 Forge 单元测试通过**，包含 JEI 15 入口与坐标检查，以及真实 AE2 15 库存 NBT 跨水晶分片往返恢复。
- **四项 Forge 隔离 GameTest 通过**，覆盖资源投料／查询和算枢接线、CPU、存档、掉落及供能恢复；独立 **AdvancedAE 集成测试通过**，包含算枢配方及研究解锁和原有 65,536 份批量派发场景。
- 上述验证针对代码及坐标行为；最新 JEI 修正尚未进行游戏内交互式画面验收。
- 构筑井仍存在原有配方限制：同产物、可替代原料重叠时，批次拆分可能使用另一条配方的耗时／能耗；重载时加入优先匹配的配方，可能使已有队列持续等待。

---

## English — 2.0.2

| Area | Changes |
| --- | --- |
| Modern UI compatibility | Fixed a crash while rendering console input fields with Modern UI's text renderer. Flush operations now use the active screen's drawing context, including after an input field is rebound to a later frame. |
| AdvancedAE CPU | Ported the NeoForge public batch-provider API integration. Matter Fabrication assemblies can receive an entire order batch, with exact task and waiting-output counts, capacity reservations, safe rejection rollback and accepted-input ownership after provider exceptions. |
| Dispatch budget | AAE dispatch attempts share a 2 ms soft time budget per CPU per game tick. Capacity backpressure survives repeated calls during the same tick and is retried on the next tick. |
| Recipe matching | Single-material requirements use a direct allocation path instead of rebuilding a flow graph. Full reservations, ordered portions and long-sized quantities preserve their behavior. |
| Default capacity | `sequence_array.pattern_pages` is 200, providing 7,200 slots. Existing saved values remain in effect. |
| Forge GUI parity | Includes the native Forge responsive controls, JEI bounds and scaled text clipping. The platform's console UI does not require LDLib2. |

An optional Forge AAE integration suite now exercises real CPU execution, reservations, rejection/exception handling, cancellation, queue persistence, time slicing and output-overflow protection. See [the test guide](tools/gametest/README.md).

Install `omnisequence-transfinite-2.0.2-forge.jar` on client and server. Required dependencies and the Java 17 / AE2 15 baseline are unchanged.

## 中文 — 2.0.2

| 项目 | 更新 |
| --- | --- |
| Modern UI 兼容 | 修复启用 Modern UI 文本渲染器时，控制台输入框渲染崩溃的问题。缓冲区刷新现在转发到当前屏幕的绘制上下文，并正确跟随后续帧重新绑定。 |
| AAE 量子 CPU | 同步 NeoForge 的公开批量供应器 API 接入，物质构筑井样板总成可整批接单；保持任务与待回产物计数、容量预留、拒收回滚和接收后异常时的材料所有权。 |
| 派发预算 | 同一 CPU 在每个游戏 Tick 内共享 2 ms 软时间预算；容量背压不会被同 Tick 的重复调用重置，下一 Tick 再重试。 |
| 配方匹配 | 单材料需求直接分配，避免反复建立流量图，保留完整材料预留、按顺序分批和 long 数量语义。 |
| 默认页数 | `sequence_array.pattern_pages` 为 200，即 7,200 个样板槽位；已有配置值继续生效。 |
| Forge 界面 | 包含原生 Forge 的响应式控件、JEI 边界和缩放文字裁剪修复，控制台界面无需 LDLib2。 |

新增可选的 Forge AAE 集成测试，覆盖实际 CPU 派发、预留、拒收与异常、取消、队列存档、时间预算及待回产物溢出边界。客户端与服务端使用 `omnisequence-transfinite-2.0.2-forge.jar`。

---

## OmniSequence: Transfinite 2.0.1-forge

Minecraft 1.20.1 / Forge / Java 17

## English

This is a cumulative update from **1.3.9-forge**, compared against [CurseForge file 8675216](https://www.curseforge.com/minecraft/mc-mods/omnisequence-transfinite/files/8675216).

### Added

| Feature | Changes |
| --- | --- |
| Molecular Sequence Rewrite Array | Added a standalone machine with its own pattern inventory, paging and search, alongside the existing multiblock controller and assembler-matrix core. |
| Matter Fabrication Well | Added a 41 × 27 × 41 multiblock, its structural blocks, item/fluid input and output ports, and a Pattern Assembly. Dimensions are X × Y × Z. |
| Research progression | Added AE Foundation, Sequence Array and Omni Computation research branches. Built-in research takes 600 ticks per round and supports nine completion levels by default. Deeper research improves the production parallelism and processing time of recipes belonging to that branch. Optional-mod branches and recipes remain conditional. |
| Pattern Assembly | Added 36 processing-pattern slots per assembly, AE2 delivery, persistent input/output/refund buffers and automatic return to ME storage. |
| Generic AEKey inputs | Fabrication recipes can use `ae_inputs` for registered AEKey types. Inputs preserve exact key identity, NBT and long amounts. Existing item/fluid recipe forms remain supported; generic-input recipes are delivered through the Pattern Assembly. |
| Independent automatic crafting | Added nine dedicated automatic-crafting pattern slots to the Sequence Array, with individual enable switches, output stock limits and input reserves. |
| Modpack integration | Added fabrication/research JSON and Java/KubeJS interfaces, research administration commands, and expanded English/Chinese GuideME documentation. |

### Changed

| Area | Changes |
| --- | --- |
| Shared AE2 enhancements | AppliedEnhancements is now a required, separately installed dependency. Shared AE2 crafting, caches, infinite-storage and terminal enhancements are delegated to that mod. Omni computation uses its configured AELIS planning API instead of the former bundled MAX_FAST planner. |
| Recipe progression | All 17 recipe IDs shipped in the 1.3.9 baseline now use research-gated fabrication recipes. The distributed data now contains 69 recipe JSON files: 56 fabrication, 10 shaped crafting and 3 research definitions. Loaded recipe counts depend on installed optional mods. |
| Multiblock designs | Added the feather-style Sequence Array layout (61 × 29 × 61) and the new Omni Computation layout (65 × 35 × 65). Recognition and an explicit structure-update flow remain available for the supported 1.3.9 layouts. |
| Chunk loading and construction | Added persistent controller-owned chunk tickets for formed structures and construction operations. Dismantling follows actual blocks from top to bottom in alternating rows, excludes air from progress, preserves the controller, and resumes saved work. |
| Recipe lookup | Added complete-output indexing, cached research definitions and queue lookup reuse. Reloading recipes rebuilds the relevant index; research progress remains controller-specific. Recipes with different expected outputs and amounts stay separated during batching. |
| Interface and visuals | Reworked machine screens, JEI cards and structure previews with a consistent native Forge console style, clearer control states, adjacent-block output previews and dedicated multiblock effects. |

### Fixed

- Fixed high-GUI-scale layout mismatches between machine panels, JEI and injected controls such as FTB sidebar and Dark Mode buttons; synchronized visible bounds, mouse input, item hit areas and ghost-ingredient targets.
- Prevented the scaled machine pass from drawing a second full-screen background, which previously produced an extra dark rectangle.
- Fixed long button captions such as **Auto Craft** disappearing because their clipping rectangle did not follow the screen transform.
- Added the missing spectral shader `blend` definition so authored alpha participates in additive blending. Original effect opacity and crystal-surface values are retained.
- Corrected Forge 1.20.1 controller loot serialization and added exact AEKey/NBT/long synchronization for fabrication recipes and buffers.

### Upgrade notes

1. Install **AppliedEnhancements 1.0.6-forge** separately on both client and server. This JAR does not bundle it. The declared dependency range is `[1.0.6-forge,1.1)`.
2. Minecraft, Forge and AE2 dependency ranges remain unchanged: Minecraft `[1.20.1,1.21)`, Forge `47.4.10+`, AE2 `[15.4.10,16)`. This build was checked with Forge 47.4.20, AE2 15.4.10, ExtendedAE 1.20-1.4.19-forge, Glodium 1.20-1.5-forge and GuideME 20.1.15.
3. **Migrate 1.3.9 configuration values explicitly.** The old global file is `config/omnisequence-transfinite-common.toml`; this build reads `config/omnisequence-transfinite-server.toml` as a COMMON config. Its migration code does not directly import the 1.3.9 common filename/grouped schema. Several client-config paths also changed. Simply renaming the file is insufficient.
4. Restored the default to **200 pattern pages / 7,200 slots**, matching 1.3.9. Existing explicit values in valid current configurations are preserved: an existing `sequence_array.pattern_pages = 20` remains 20 unless changed manually. Migrate old custom values explicitly. Planner options now belong to AppliedEnhancements.
5. Back up worlds and configuration before upgrading. Existing supported 1.3.9 structures have a compatibility path; changing their architecture is an explicit operation. New machine crafting progression requires the fabrication/research system.

### Validation and known limitations

- Passed all **131 unit tests** and **3 required Forge GameTests** for this source revision. Runtime checks cover registered custom AEKeys, exact serialization, batch delivery, persistence/refunds, output isolation and recipe/research reloads. These checks do not constitute exhaustive testing of every modpack or a whole-server performance benchmark.
- Fabrication alternatives with identical outputs and overlapping inputs can still change the selected recipe after batch splitting. A reload that introduces an earlier overlapping recipe can leave an existing queue waiting. These limitations remain unresolved.
- Quantum links, virtual CPU lanes, source-aware dispatch, reusable-input batches and the public Batch Provider API already existed in 1.3.9; they are retained rather than presented as new features here.

---

## 中文

本日志汇总 **1.3.9-forge → 2.0.1-forge** 的累计变化，基准为 [CurseForge 文件 8675216](https://www.curseforge.com/minecraft/mc-mods/omnisequence-transfinite/files/8675216)。

### 新增

| 功能 | 更新内容 |
| --- | --- |
| 分子构序重写阵列 | 新增独立机器，具有自己的样板库存、分页与搜索，与已有构序阵列多方块及装配矩阵核心共同提供构序能力。 |
| 物质构筑井 | 新增 41 × 27 × 41 多方块、结构件、物品／流体输入输出口及样板总成。尺寸统一按 X × Y × Z 表示。 |
| 研究路线 | 新增 AE 基础、构序阵列、万物演算三条研究分支。内置研究每轮 600 tick，默认共九级；深度研究提高所属配方的生产并行和加工速度。可选模组分支及配方按条件加载。 |
| 样板总成 | 每个总成提供 36 个加工样板槽，支持 AE2 投料、持久化输入／产物／退款缓存，以及自动返还 ME。 |
| 通用 AEKey 输入 | 构筑井配方可用 `ae_inputs` 声明已注册 AEKey 类型，保留精确资源身份、NBT 和 long 数量。原有物品／流体配方格式继续可用，通用输入配方由样板总成投料。 |
| 独立自动合成 | 构序阵列新增九个专用自动合成样板槽，每槽独立设置开关、成品库存上限及原料保留量。 |
| 整合包接入 | 新增构筑／研究 JSON 与 Java/KubeJS 接口、研究管理命令，并扩充中英文 GuideME 指南。 |

### 调整

| 项目 | 更新内容 |
| --- | --- |
| AE2 通用增强 | AppliedEnhancements 改为必须单独安装的前置。通用下单、缓存、无限存储和终端增强交由前置负责；万物演算通过前置配置的 AELIS API 规划，不再内置旧 MAX_FAST 规划器。 |
| 配方进度 | 1.3.9 中的 17 个配方 ID 全部改为需要研究解锁的构筑井配方。当前包内共有 69 份配方 JSON：56 份构筑、10 份有序合成及 3 份研究；实际加载数量受可选模组影响。 |
| 多方块设计 | 构序阵列采用 61 × 29 × 61 羽翼布局，万物演算采用 65 × 35 × 65 新布局；保留受支持的 1.3.9 结构识别及显式更新流程。 |
| 区块加载与施工 | 新增由控制器持有的持久区块票据，覆盖成型结构与施工操作。拆卸按实际方块从上到下、同层蛇形逐行推进，不把空气计入进度，保留控制器并支持恢复已保存的工作。 |
| 配方查询 | 新增完整产物索引、研究定义缓存和队列查询复用。重载配方重建索引，研究进度按控制器实时读取；批量处理时区分不同预期产物及数量。 |
| 界面与特效 | 重做机器界面、JEI 配方卡和结构预览，统一原生 Forge 控制台风格，补充控件状态、相邻方块输出预览及多方块特效。 |

### 修复

- 修复较大 GUI 缩放下机器面板、JEI、FTB 侧栏及 Dark Mode 等注入按钮的布局错位，同步实际边界、鼠标输入、物品命中区域及幽灵物品拖放目标。
- 阻止缩放绘制阶段重复绘制全屏背景，消除额外的黑色矩形。
- 修复 **Auto Craft** 等较长按钮文字因裁剪框未跟随缩放而完全消失的问题。
- 为光谱着色器补齐 `blend` 配置，让既有 Alpha 正确参与叠加混合；保留原始特效不透明度和晶体表面数值。
- 修正 Forge 1.20.1 控制器掉落数据，并补齐构筑配方与缓存的精确 AEKey／NBT／long 同步。

### 升级说明

1. 客户端与服务端均需单独安装 **AppliedEnhancements 1.0.6-forge**，本 JAR 不内置该前置；声明兼容范围为 `[1.0.6-forge,1.1)`。
2. Minecraft、Forge 与 AE2 的声明范围不变：Minecraft `[1.20.1,1.21)`、Forge `47.4.10+`、AE2 `[15.4.10,16)`。本次验证基线为 Forge 47.4.20、AE2 15.4.10、ExtendedAE 1.20-1.4.19-forge、Glodium 1.20-1.5-forge、GuideME 20.1.15。
3. **请显式迁移 1.3.9 配置。** 旧全局文件为 `config/omnisequence-transfinite-common.toml`，当前版本以 COMMON 类型读取 `config/omnisequence-transfinite-server.toml`。现有迁移代码没有直接导入 1.3.9 的 common 文件名及其分组格式，客户端部分字段路径也已改变，仅重命名文件不足以完成迁移。
4. 默认容量恢复为 **200 页／7,200 槽**，与 1.3.9 一致。已存在且有效的新格式配置保留显式值：已经设置 `sequence_array.pattern_pages = 20` 的配置仍为 20，如需增加应手动修改。旧配置中的自定义值仍需显式迁移。规划器选项改由 AppliedEnhancements 管理。
5. 升级前备份世界和配置。受支持的 1.3.9 旧结构有兼容路径，建筑更新需要显式操作；新造机器需遵循构筑井与研究进度。

### 验证与已知限制

- 当前源码通过全部 **131 项单元测试**及 **3 项必需 Forge GameTest**。运行测试覆盖已注册自定义 AEKey、精确序列化、批量投料、存档／退款、产物隔离及配方／研究重载；不代表所有整合包组合均已验证，也不构成整服性能提升的测量结论。
- 产物相同且原料范围重叠的构筑配方，批量拆分后仍可能改选配方；重载时新增更靠前的重叠配方，也可能使已有队列等待。这些限制尚未修复。
- 量子链路、虚拟 CPU、按供应器能力发配、可复用输入批次及 Batch Provider API 在 1.3.9 中已经存在，本日志将其视为延续功能。

---

# 2.0.0-forge — Minecraft 1.20.1

- Make matter deconstruction/reproduction highlights follow their synchronized enable switches; stopped operations use ordinary buttons.
- Port the 2.0.0 feature set to Java 17 / Forge 47 with AppliedEnhancements 1.0.6-forge.
- Adapt networking, AE2 15 inventories and recipes, Forge capabilities and persistent chunk tickets.
- Redesign every machine and JEI page as a light sci-fi console with custom frames, buttons, text fields, slots, toggles and clear selected/disabled states; no LDLib2 or XML overlays.
- Unify JEI recipe cards and interactive structure previews with the light console palette and custom input/output slots.
- Deepen light-console surfaces and slot borders; move JEI structure quantities to contrasting badges below their models.
- Use square corners for console frames, panels, buttons, text fields and JEI cards.
- Match AE2 19.2.17's cool-grey panel materials, white slot borders, dark text fields and cyan highlights using native Forge 1.20.1 drawing; retain readable button text and quantity badges.
- Remove default text shadows from buttons and fields while preserving vanilla caret spacing, selection and editing; use a contrasting light caret on dark text fields.
- Synchronize total occupied pattern slots so paging and filtering cannot change the displayed machine-wide usage.
- Refactor all six machine screens and JEI layouts against the 1.21.1 branch; restore the 332x364 Omni Computation structure/telemetry/quantum layout, horizontal actions and centered inventory.
- Share fixed machine geometry and validate it against AE2 slot definitions; retain search, tab restoration, confirmation behavior and high-GUI-scale input handling.
- Show adjacent-block thumbnails beside all six output directions with neighbor/status tooltips; rebuild cached previews only when block states or direction settings change.
- Adapt vertex submission and render bounds; scale large controllers to fit the display.
- Preserve the Forge CPU readiness safeguard and cached/bounded scheduling behavior.
- Adapt ExtendedAE's expatternprovider namespace and gate unavailable dependency-only materials.
- See docs/FORGE_PORT.md and libs/README.md for compatibility details.

# Changelog

## 1.3.9-forge - 2026-08-18

### Changed

- Increased the default Sequence Array pattern capacity from 20 pages (720
  slots) to 200 pages (7,200 slots). Existing explicit configurations are left
  unchanged.
- Removed global network-storage probing and refresh caches. Only AE2 creative
  cells and ExtendedAE infinity cells now expose `Long.MAX_VALUE` (`9.2E`);
  every other storage source keeps its original advertised amount.

### Fixed

- Prevented malformed third-party pattern candidates with no matching output
  from dividing by zero in AE2's crafting tree, including MAX_FAST native
  compatibility boundaries and full native fallback.
- Restored speculative MAX_FAST tree structure before native fallback so a
  failed optimized attempt cannot leak mutable process state into AE2.
- Prevented Omni crafting lanes with residual inventory from being selected,
  reused, or removed until AE2 has returned their items to network storage.
- Fixed Forge startup failures under Mixin 0.8.5 by moving runtime helper
  types and static utility methods out of transformed Mixin classes.
- Restricted both Molecular Center and Omni-Computation AE2 connectivity to
  their controllers. Structure casings and outer parts no longer expose grid
  nodes, preventing persistent self-connection crashes after controller reloads.

## 1.3.8-forge - 2026-08-11

### Added

- Exposed the Molecular Center's large pattern inventory as segmented logical
  containers in AE2's Pattern Access Terminal.
- Added the public Omni Batch Provider API v1, including atomic two-phase
  admission/commit, explicit ownership, rejection, backpressure, and an Omni CPU
  marker for third-party provider integrations.
- Ported transactional MAX_FAST graph planning for deterministic crafting,
  smithing, stonecutting, and supported AdvancedAE processing patterns, with
  reusable-input boundaries and exact native AE2 fallbacks.
- Added persistent reusable-input batches for deterministic unchanged remainders
  and `+1` durability tools, including mixed damage states, cancellation refunds,
  and NBT-backed dismantling recovery.
- Added configurable Matter Sequence capacity, entropy capacity and cooling, plus
  independent throughput, cycle-time, and cooling multipliers for 0–4
  Acceleration Cards.

### Changed

- Moved server options to the global
  `config/omnisequence-transfinite-common.toml` file and grouped every common and
  client option by subsystem. Existing flat/server values migrate with a backup.
- Matter Sequence capacity now defaults to `Long.MAX_VALUE`; infinite storage
  uses source-agnostic boundary detection and displays the numeric `9.22E` long
  limit instead of an infinity symbol.
- Accepted reusable batches settle their complete long-count aggregate in one
  machine tick. Post-commit failures retain provider ownership so AE2 cannot
  duplicate an already accepted batch.
- Reorganized and centered the Molecular Center matter interface, expanded its
  status information, and hid Build once the structure is formed.

### Fixed

- Added cancellation-aware MAX_FAST queueing and task-local transactional state,
  preventing obsolete calculations and speculative state from surviving a
  canceled request or unsafe fallback.
- Added saturating arithmetic to network storage, matter values, output recovery,
  and AE2 key-counter aggregation without allowing signed wraparound.
- Plans whose required counts, recipe totals, or byte usage exceed the signed-long
  limit are now marked incomplete and cannot be submitted, preventing apparently
  craftable jobs from stalling forever.
- Added precise reusable-batch cancellation lifetimes, safe late-rejection
  fallback, invalid-NBT quarantine, and recovery drops for active batches and
  long-count output buffers.
- Added the production refmap declaration required by Forge's obfuscated runtime,
  fixing the storage-cell tooltip Mixin startup crash.
- Completed and verified matching English and Chinese labels/tooltips for every
  grouped configuration entry.

## 1.3.7-forge-fix - 2026-07-30

### Added

- Added runtime compatibility with AE2-UELM 15.5.0 while retaining official
  Applied Energistics 2 15.4.10 support. UELM uses its native long-amount
  confirmation path and skips the incompatible `int` field Mixin.

### Changed

- Added effect-cleared layouts for both fixed multiblocks while retaining
  strict compatibility with their complete legacy layouts.
- Controllers now identify a complete legacy layout and show an optional
  structure-update notice. Legacy structures remain formed and operational
  until a player explicitly starts the safe relocation.
- Updated projection, automatic construction, dismantling, JEI previews, and
  GuideME documentation for the current layouts and optional upgrade flow.
- Moved all OmniSequence GuideME pages into a dedicated top-level
  `OmniSequence: Transfinite` section while preserving item-page links.
- Controller screens now scale down only when their native size would exceed
  the current GUI area. Slots, buttons, tooltips, and JEI ghost targets use the
  same transformed coordinates.
- Replaced per-frame ghost-block model tessellation with reusable 16x16x16
  section VBOs for both multiblock projections. Changed sections rebuild
  incrementally, at most two per frame, while unchanged geometry is reused.
- Item-substitution crafting patterns are now permanently eligible for compatible
  batch dispatch. The retired `omni_batch_allow_substitution_patterns` option is
  removed from existing TOML files without resetting other custom values.
- Matter Sequence item tooltips now default to a compact Shift-expand prompt.
  A client setting can disable them, keep the `HOLD_SHIFT` behavior, or make
  them `ALWAYS_VISIBLE`.
- The Sequence Array Controller now supports debounced, cross-page
  input/output pattern search while retaining AE2's detailed encoded-pattern
  hover tooltip. Just Enough Characters remains optional and enables Pinyin
  matching when installed.

### Fixed

- Restored the vanilla translucent world backdrop behind both controller
  screens on Forge 1.20.1, drawing it before responsive GUI scaling so it
  covers the complete viewport.
- Sequence Array construction now honors Creative mode like Omni-Computation
  construction: materials are not consumed, and a failed placement cannot
  create a refunded block.
- Added compact English controller labels for narrow buttons and verified that
  every English and Chinese static/dynamic translation key has a matching
  entry.
- Reworked controller labels and values into width-aware columns and wrapped
  rows, preventing English text overlap. The Omni-Computation fixed-capability
  notice now renders at the normal readable font size.
- The AE2 crafting CPU selector now renders a compact localized Omni lane name
  inside its narrow row while retaining the complete name in the tooltip.

## 1.3.6-forge - 2026-07-29

### Added

- Added AE2 GuideME documentation for the Assembler Matrix Sequence Rewrite
  Core, Omni-Computation Core, and Sequence Array Controller. The standalone
  Molecular Sequence Rewrite Array remains absent from the Forge 1.20.1 build.

### Changed

- Reworked the Sequence Array Controller toolbar to widen its pattern page
  buttons and separate Build from Dismantle.
- Added a timed two-step confirmation to Dismantle so rapid double-clicks and
  unrelated clicks cannot accidentally start structure removal.
- Made Shift-moving supported encoded patterns fill the current pattern page
  first and continue into later pages when needed.
- Restricted the Sequence Array Controller pattern inventory and advertised
  recipes to molecular-assembler-compatible AE2 crafting, smithing, and
  stonecutting patterns.
- Kept the Minecraft 1.21.1 NeoForge-only Expanded AE 2.1.1 conflict declaration
  out of Forge metadata because it targets a different loader and AE2 line.

## 1.3.5-fix-forge - 2026-07-28

### Changed

- Replaced the retired fixed 32-call ordinary-provider throttle with an
  `Integer.MAX_VALUE` logical scheduling ceiling and one server-wide,
  load-adaptive time budget. Work rotates between tasks and patterns so a large
  request cannot monopolize a server tick.
- Cached negative explicit-batch-provider topology and classified extracted
  inputs without temporary collection allocation on the ordinary-provider hot
  path.
- Removed the obsolete `omni_unscaled_dispatch_attempts_per_tick` configuration
  option. Existing TOML files remove only that retired key while preserving
  current custom settings.

### Fixed

- Ordinary multi-input processing patterns now dispatch repeated, complete
  original `1x` recipes. Every call keeps all ingredients together, preventing
  different machines from being filled by different ingredient types and
  deadlocking one-to-many processing setups.
- Normalized ExtendedAE Plus planning-time scaled multi-input wrappers before
  dispatch, preventing an already-multiplied recipe from bypassing the atomic
  multi-input guard.
- Kept aggregate dispatch for explicit batch endpoints and adaptive doubling
  for safe single-input patterns, preserving high throughput where the target
  can accept it without breaking compatibility.
- Removed production-unsafe Mixin helper class loading and routed the AE2 long
  amount widget through an application bridge, fixing startup and crafting
  amount screen class-loading crashes.

## 1.3.5-forge - 2026-07-27

### Changed

- Added native six-direction connected rendering for the Computation Core Frame,
  including seamless faces and non-overlapping translucent border corners.
- Updated the Universal Pattern Matrix texture and enabled its translucent render layer.
- Limited each provider/pattern pair to one scaled-dispatch growth step per server
  tick, preventing a single lane from probing `1, 2, 4, ...` in one tick while
  retaining the learned multiplier for the next tick.

### Compatibility

- This release targets Minecraft 1.20.1, Forge 47.4.10 and AE2 15.4.10.
- The standalone Molecular Sequence Rewrite Array remains removed from the
  Forge 1.20.1 build; its functionality remains in the Molecular Center
  multiblock.

## 1.3.4-hotfix-forge - 2026-07-27

### Fixed

- Removed the `ForgeConfigSpec` core-class mixin that could run after Forge had
  already loaded its target and abort startup with `MixinTargetAlreadyLoadedException`.
- Kept the Forge-specific saved-world migration hook at the beginning of
  `ServerLifecycleHooks.handleServerAboutToStart`, before Forge selects and loads
  the world's server config. Forge posts `ServerAboutToStartEvent` only after that
  load, so moving migration to the event would ignore legacy world values on the
  first startup.

## 1.3.4-forge - 2026-07-27

### Changed

- Removed the obsolete `omni_provider_max_queued_items` and
  `omni_provider_send_operations` server options. Adaptive dispatch no longer has a
  configured material-window cap; fair remainder draining uses an internal high-throughput
  limit.
- Converted the complete dispatch work-unit accounting path to `long` and changed
  `omni_dispatch_max_work_units` to a long-valued option with a default of
  `2147483647` and a maximum of `Long.MAX_VALUE`.

### Fixed

- Added runtime-scaled processing patterns. One provider call now carries a
  complete `1, 2, 4, 8, ...` recipe batch, forwards every extracted input and
  lets AE2 account the corresponding scaled expected outputs.
- Added explicit provider feedback for rejected, fully inserted, queued and
  unverified pushes. Probes grow by doubling; after congestion the last
  successful batch remains the next tick's baseline before another doubled
  probe, avoiding a probe-only throughput gap.
- Enabled runtime-scaled patterns for every non-explicit AE crafting provider.
  AE2/ExtendedAE providers retain precise full/queued feedback, while AE2LT,
  AdvancedAE and other third-party providers use their public acceptance and
  busy-state signals. Explicit project batch providers keep the direct
  long-limit path.
- Preserved ExtendedAE Plus scaled-pattern identity, AdvancedAE directional
  input metadata and AE2LT overloaded-provider metadata while scaling, so
  compatible third-party providers receive the multiplied pattern directly
  instead of falling back to one recipe per tick.
- Persisted legitimate scaled remainder queues with a versioned format and kept
  fair multi-ingredient draining across reloads without confusing them with
  legacy unsafe queues.
- Added fair provider selection for duplicate patterns so a full or rejecting
  target cannot starve other available providers.
- Added a sticky single-recipe fallback for providers that accept `1x` but
  reject scaled patterns. AE2 performs a fresh extraction and accounting cycle
  for every call, while the new
  `omni_unscaled_dispatch_attempts_per_tick` server option shares a default
  limit of 32 real calls across all active CPU lanes of one Omni core.
  Demand lanes are prioritized on the next tick and can borrow unused
  reservations from lanes that ran earlier, while reaching the cap stops that
  CPU lane's single-recipe path before it can repeatedly extract and reinject
  the same task; already-scalable and explicit long-batch tasks remain eligible.
- Limited every dispatch batch by the remaining positive `waitingFor` headroom
  for all expected outputs and container items, preventing accumulated
  outstanding results from wrapping past `Long.MAX_VALUE` into negative counts.
- Added scaled AE waiting-for accounting as a secondary consistency check and
  pre-adjusted batch task progress before provider calls, allowing EAP virtual
  crafting to finish the final aggregate batch without leaving phantom outputs.
- Added strict config-schema regeneration. If a TOML contains any option that
  no longer exists in the current server or client schema, the previous file is
  backed up and the complete configuration is atomically rebuilt from current
  defaults. Missing current options and invalid values retain Forge's normal
  targeted correction behavior.

### Compatibility

- Ported the release to Minecraft 1.20.1, Forge 47.4.10 and AE2 15.4.10.
- Existing worlds, patterns and configuration files remain compatible.
- The standalone Molecular Sequence Rewrite Array remains removed from the
  Forge 1.20.1 build and was not restored by this port.
- The stable mod ID remains `molecularmanipulator`.
- Dedicated batch-provider and machine whitelist behavior is unchanged.

## 1.3.3-forge - 2026-07-26

### Fixed

- Replaced output-return-driven adaptive dispatch with complete-input acceptance
  feedback, so slow, high-parallel and output-less processing targets no longer
  stall material delivery while waiting for products.
- Repeated standard provider attempts safely within the same server tick. Every
  attempt remains one complete AE2 recipe with independent extraction and
  accounting; rejection or provider-side queuing stops the current tick and
  contracts the dispatch window.
- Removed the exact `PatternProviderLogic` class requirement from the standard
  adaptive path. Third-party providers that correctly implement AE2 15.4.10's
  `ICraftingProvider` success, rejection and busy-state contract can now use the
  same bounded high-throughput dispatch.
- Added full-amount preflight checks and cache-aware initial windows for standard
  AE2 pattern providers, while preserving the existing direct-batch path for
  explicitly compatible machines.

### Compatibility

- Ported the hotfix to Minecraft 1.20.1, Forge 47.4.10 and AE2 15.4.10.
- Existing worlds, patterns and configuration files remain compatible.
- The standalone Molecular Sequence Rewrite Array remains removed from the
  Forge 1.20.1 build and was not restored by this port.
- The stable mod ID remains `molecularmanipulator`.

### Platform

- Ported the release to the clean Minecraft 1.20.1 Forge 47.4.10 MDK baseline.
- Targeted Applied Energistics 2 15.4.10, ExtendedAE 1.20-1.4.12-forge and Glodium 1.20-1.5-forge.
- Replaced NeoForge metadata, networking, registries, capabilities and data paths with their Forge 1.20.1 equivalents.

### Fixed

- Reacquired render buffers after every render-type switch in the Omni
  Computation Core and separated multiblock projection model/outline passes,
  preventing `BufferBuilder: Not building!` client crashes.
- Made missing-material crafting summaries use the `ICraftingPlan` result from
  the same calculation, preventing unstable missing-item lists when external
  storage providers report different results during confirmation-page probes.
- Preserved extension data attached to AE2 crafting-plan summaries, fixing
  confirmation packet encoding with AE2: Crafting Tree installed.
- Removed completed-plan caching, in-flight calculation sharing and cancellation
  shielding from the Omni Computation Core so every request owns an independent,
  cancellable AE2 calculation.
- Restored AE2's native per-calculation inventory snapshot path instead of
  replacing it with an additional Omni snapshot context.
- Restored AE2's cooperative crafting-calculation pause handshake and added
  pause checkpoints inside Omni MAX_FAST compilation and execution, preventing
  live crafting-provider updates from racing long-running plan calculations.
- Saturated AE2WTLib restock-overlay inventory totals before they overflow and
  displayed the infinite sentinel as ∞, preventing wireless-terminal restock
  overlays from crashing on infinite network inventories.
- Accelerated actual crafting dispatch by extracting one recipe first and then
  deriving the safe batch size directly from available inputs and power, avoiding
  repeated whole-pattern searches when intermediate materials are still missing.
- Added bounded high-throughput dispatch for AE2 and ExtendedAE processing
  providers. A standard provider now starts with one queue-bounded probe and uses
  primary-output timing to select 16x, 4x, 2x or unchanged window growth; congestion,
  rejection and interrupted jobs contract the window. Explicit batch providers retain
  their direct long-limit path, and unsupported recipes retain the original path.
- Persisted each queued batch's one-craft ingredient vector and reseed every
  ingredient before bulk draining on every provider send tick. This prevents
  one-tick machines from leaving the first remaining material free to monopolize
  shared input slots after consuming the previous tick's reservation.
- Preserved single-recipe execution when batch expansion is unavailable or rolls
  back, while keeping the explicit provider whitelist.
- Replaced the Omni-Computation Core's unbounded same-tick dispatch with an
  adaptive work-unit scheduler. Virtual CPU lanes share fair per-tick quotas,
  batch-safe pushes retain unlimited logical craft counts, and all controllers
  share a configurable server-wide emergency deadline.
- Renamed client and server configuration files to the OmniSequence Transfinite
  name, with non-overwriting migration for global, default and per-save configs.

### Changed

- Removed the obsolete material-calculation cache-hit statistic from the Omni
  Computation Core interface.
- Removed the standalone Molecular Sequence Rewrite Array block, its block entity,
  menu, recipe and client resources from the Forge 1.20.1 build. Existing placed
  copies are removed as missing blocks when old worlds are loaded.
- Replaced the removed block in the Assembler Matrix Sequence Rewrite Core recipe
  with an AE2 Molecular Assembler.

## 1.3.2 - 2026-07-25

### Changed

- Replaced Quantum Entangled Singularities in OmniSequence recipes with ME
  Quantum Rings.
- Replaced the Quartz Cluster in the Computation Crystal Pylon recipe with a
  256k ME Storage Component.

### Fixed

- Infinite amounts now use source-specific compatibility for AE2 creative cells
  and ExtendedAE infinity cells.
- Removed network-wide simulated extraction probes so storage buses and external
  storage providers retain their native amounts without inventory refresh stalls.
- Displayed exact `Long.MAX_VALUE` storage amounts as `Infinite` instead of `9.2E`
  in AE2 terminals and storage tooltips.

## 1.3.1 - 2026-07-25

### Fixed

- Fixed an Omni Computation Core formation crash by respecting AE2's 16-thread
  per-block limit while preserving transfinite cluster-level parallelism.
- Displayed transfinite crafting storage and parallelism as `Infinite` in AE2's
  crafting CPU list and tooltip instead of abbreviated integer limits.
- Fixed shutdown stalls while unloading quantum-linked multiblocks by avoiding
  block updates and redundant AE2 grid work after their chunks begin unloading.

## 1.3.0 - 2026-07-25

### Highlights

- Renamed the project to **OmniSequence: Transfinite** while retaining the
  `molecularmanipulator` mod ID for world and configuration compatibility.
- Added the Omni Computation Core multiblock with transfinite crafting storage,
  long-range logical parallelism and controlled AE2 crafting calculation acceleration.
- Added quantum-linked AE networking and pre-formation construction access for both
  major multiblock structures.
- Extended AE2 crafting requests and supported infinite item sources to long values.
- Added safe MAX_FAST demand aggregation, duplicate subtree merging, batch topology
  calculation and compatibility fallbacks for substitution, container and dynamic patterns.
- Fluid-only substitution patterns remain eligible for deterministic MAX_FAST calculation
  and long-value batch dispatch; item substitution retains the conservative safety fallback.
- Added completion-feedback adaptive material dispatch: windows grow after full primary-output return and contract on provider congestion, rejection or interrupted jobs.
- Added original black-purple crystal models, textures, effects and redesigned machine UIs.
- Added one-click multiblock dismantling, player-facing placement and hostile-spawn suppression
  around the Omni Computation Core.

### Compatibility

- Minecraft 1.20.1
- Forge 47.4.10 or later
- Applied Energistics 2 15.4.10
- ExtendedAE 1.20-1.4.12-forge
- A compatible Forge 1.20.1 release of Advanced AE is optional
- ExtendedAE Plus and JEI integrations are optional

### Notes

- Existing worlds continue to use the stable `molecularmanipulator` namespace.
- Extremely large crafting requests remain subject to recipe-specific compatibility fallbacks.
