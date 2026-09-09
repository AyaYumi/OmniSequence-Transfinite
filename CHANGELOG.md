# Changelog

## OmniSequence: Transfinite 2.0.3

Minecraft 1.21.1 / NeoForge / Java 21

### English

#### Added

| Area | Update |
| --- | --- |
| Texture animation | Added a first animated texture pass for the computation matrices, crystal parts, controller faces and matter-fabrication modules. The effects use distinct grouped pulses, crystal refraction, energy breathing and a retained horizontal coil flow instead of repeating one scan pattern. |
| Emissive textures | Animated matter-fabrication textures retain their gold and fluid-port blue emissive regions without lighting the neutral shell. |
| Matter fabrication service positions | Added three service-block positions to the middle of each of the four directional platform-collar segments around the central field. Item/fluid ports and pattern assemblies are accepted there. |

#### Validation

- 20 animated texture strips use 24 frames at 2 ticks per frame, with a 2.4-second loop.
- All 122 unit tests passed.
- A hidden OpenGL probe verified Minecraft's sprite ticker, interpolation, mipmap upload, loop closure and emissive partition for all 20 animated textures.

#### Installation

- Update both client and server to `omnisequence-transfinite-2.0.3.jar`, keeping one enabled OmniSequence JAR per instance.

### 中文

#### 新增

| 项目 | 更新 |
| --- | --- |
| 贴图动画 | 为演算矩阵、晶体部件、控制器正面及物质构筑井模块加入第一版动画。不同方块分别使用分组脉冲、晶内折光、能量呼吸和保留的线圈横向流动，减少重复的循环扫描。 |
| 自发光贴图 | 动画化的物质构筑井贴图继续保留金色和流体接口蓝色自发光区域，不会点亮中性外壳。 |
| 物质构筑井服务位置 | 中央场四个方位的平台环段中间各增加三个服务方块位置，可放置物品、流体端口和样板总成。 |

#### 验证

- 20 组动画贴图使用 24 帧、每帧 2 Tick，循环周期为 2.4 秒。
- 全部 122 项单元测试通过。
- 使用隐藏 OpenGL 检查 Minecraft 原生贴图播放器、插值、MipMap 上传、循环闭合及 20 组贴图的自发光分区。

#### 安装

- 客户端与服务端更新为 `omnisequence-transfinite-2.0.3.jar`，每个实例只保留一个启用的万象构序 JAR。

---

## OmniSequence: Transfinite 2.0.2

Minecraft 1.21.1 / NeoForge / Java 21

### English

#### Fixed

| Area | Fix |
| --- | --- |
| Default capacity | Increased `sequence_array.pattern_pages` from 20 to 200 (7,200 pattern slots). Existing saved configuration values remain in effect. |
| AdvancedAE batch dispatch | AAE quantum CPUs now use the public batch-provider API, allowing matter fabrication assemblies to accept complete batches. Reservations, rejection rollback, accepted ownership after exceptions and waiting-output limits remain exact. Dispatch attempts share a 2 ms soft budget per CPU per game tick. |
| Single-material matching | Single-requirement recipes now allocate materials directly without rebuilding a flow graph; full reservations, ordered portions and long quantities retain the existing semantics. |
| Third-party controls | Machine panels now apply responsive scaling only to their own content. Injected controls such as the FTB sidebar and Dark Mode button retain their screen positions and size. |
| Mouse input and focus | Routed clicks, movement, dragging, release and scrolling through the correct coordinate space. External controls keep normal focus and keyboard behavior; machine controls retain logical menu coordinates. |
| JEI layout | Registered the fitted bounds of responsive machine screens, converted AE2 exclusion zones, and supplied the visible hit areas for normal items. This prevents layout gaps and misplaced ingredient interactions caused by unscaled bounds. |
| LDLib2 exclusion areas | Converted the molecular-center and Omni-computation overlays' cached exclusion rectangles before JEI reads them. The original LDLib2 layout cache stays unchanged, avoiding repeated scaling on later frames. |
| Long button captions | Fixed native AE2 button captions being completely clipped at large GUI scales. Their clipping rectangles now follow the text's render transform. The fix also covers output-direction captions beside neighbor icons and retains AE2's existing button appearance and scrolling behavior. |
| LDLib2 text and pointer handling | Verified that LDLib2's own clipping already follows the pose matrix. Its modular widgets continue receiving raw screen mouse coordinates during rendering, so LDLib2 performs its inverse transform exactly once. |

#### Validation

- Verified actual AAE 1.6.11 CPU execution against a real matter fabrication assembly: 65,536 crafts use one dispatch. Integration checks also cover reservations, rollback, exceptions, cancellation, persistence, backpressure and time slicing.
- All **122 unit tests** passed, including **14 focused UI regression tests**.
- Re-ran those 14 UI tests using the target pack's **JEI 19.39.0.368** and **LDLib2 2.2.29** JARs; all passed.
- Checks cover fitted bounds across machine layouts and viewport sizes, external-control input, long-caption clipping, LDLib2 exclusion areas, and raw-to-local mouse conversion.
- These automated checks exercise code and coordinate behavior without a full interactive modpack session.

#### Installation

- Update both client and server to `omnisequence-transfinite-2.0.2.jar`, keeping one enabled OmniSequence JAR per instance.
- This is the **Minecraft 1.21.1 NeoForge** build. Java 21 and the existing 2.0.1 dependencies, including LDLib2, remain required.
- Build baseline: NeoForge 21.1.220, AE2 19.2.17, JEI API 19.27.0.340 and LDLib2 2.2.18. The target pack currently uses NeoForge 21.1.233.

---

### 中文

#### 修复

| 项目 | 修复内容 |
| --- | --- |
| 默认容量 | `sequence_array.pattern_pages` 默认值由 20 调整为 200，即 7,200 个样板槽位；已有配置中的值继续生效。 |
| AdvancedAE 批量派发 | AAE 量子 CPU 现已接入公开批量供应器 API，物质构筑井样板总成可整批接单；保留容量预留、拒收回滚、接收后异常的材料所有权及待回产物计数边界。每个 CPU 同一游戏 Tick 内的派发尝试共享 2 ms 软时间预算。 |
| 单材料匹配 | 单需求配方直接分配材料，不再重复建立流量图；保留完整预留、顺序分批和 long 数量语义。 |
| 第三方控件 | 响应式缩放仅作用于机器自身内容。FTB 侧栏、Dark Mode 等注入控件保持屏幕位置和大小，避免被机器面板一起缩小或移动。 |
| 鼠标输入与焦点 | 按正确坐标系处理点击、移动、拖动、松开及滚轮；外部控件保留正常焦点和键盘行为，机器控件继续使用菜单逻辑坐标。 |
| JEI 布局 | 为响应式机器界面注册实际显示边界，转换 AE2 避让区域，并提供普通物品的实际命中区域，修复未缩放边界造成的异常留白和物品交互错位。 |
| LDLib2 避让区域 | 构序阵列和万物演算界面向 JEI 上报前，转换 LDLib2 缓存的避让矩形；保留原始布局缓存，避免后续帧反复缩放。 |
| 长按钮文字 | 修复大 GUI 缩放下原生 AE2 按钮长文字被完全裁掉的问题，裁剪框现已跟随文字绘制矩阵。相邻方块图标旁的输出方向文字也一并修复，保留 AE2 原有按钮样式及滚动行为。 |
| LDLib2 文字与指针 | 已验证 LDLib2 自身裁剪会正确跟随矩阵；模块化控件绘制时继续接收原始屏幕鼠标坐标，由 LDLib2 恰好执行一次逆变换。 |

#### 验证

- 实际加载 AAE 1.6.11 验证 CPU 向真实构筑井样板总成派发：65,536 份配方只需一次操作；另覆盖预留、回滚、异常、取消、存档、背压和时间预算。
- 全部 **122 项单元测试**通过，其中包含 **14 项界面专项回归测试**。
- 使用目标整合包的 **JEI 19.39.0.368** 和 **LDLib2 2.2.29** JAR 重新运行这 14 项界面测试，全部通过。
- 覆盖各机器布局与视口尺寸下的边界、外部控件输入、长文字裁剪、LDLib2 避让区域及鼠标坐标换算。
- 自动化验证针对代码和坐标行为，未进行完整整合包内的交互式画面验收。

#### 安装

- 客户端与服务端更新为 `omnisequence-transfinite-2.0.2.jar`，每个实例只保留一个启用的万象构序 JAR。
- 本包用于 **Minecraft 1.21.1 NeoForge**，仍需 Java 21 和 2.0.1 原有前置，包括 LDLib2。
- 构建基线：NeoForge 21.1.220、AE2 19.2.17、JEI API 19.27.0.340、LDLib2 2.2.18；目标整合包当前使用 NeoForge 21.1.233。

## 2.0.1 - 2026-09-08

### English

#### Changes

| Area | Update |
| --- | --- |
| AEKey inputs | Matter Fabrication Pattern Assemblies now accept all registered AEKey input types, including resources provided by compatible AE2 addons. Custom well recipes can declare exact resources and positive long amounts through the optional `ae_inputs` field. Existing item/fluid recipes remain compatible. |
| Recipe lookup | Added an index keyed by complete recipe outputs and quantities. Pattern delivery now searches matching output candidates instead of repeatedly scanning every well recipe. Candidate order and full input validation are preserved. |
| Research checks | Cached research definitions and recipe ownership. Completion counts remain specific to each controller and are read live, so unlocks, revocations and loaded progress take effect immediately. Recipe snapshot replacement rebuilds the index. |
| Queue overhead | Reused candidate lists while merging queued deliveries and removed unnecessary active-input snapshot copies from capacity checks. |
| Interfaces and documentation | Added generic input display to JEI and GuideME recipes. Updated the English/Chinese assembly guides, recipe/API documentation and release references. |
| Repository cleanup | Consolidated the duplicate MIT license into `LICENSE`, retaining the contributor notice. Runtime textures, models and GuideME pages remain referenced; local diagnostics and build outputs stay outside Git. |

#### Performance and validation

- Local lookup benchmark: 128 well recipes, 32 research definitions, 2,000 warm-up lookups, followed by three rounds of 2,000 lookups. Median time decreased from **493.08 ms to 6.21 ms**, approximately **98.7% less time**. This measures recipe lookup only, not whole-server TPS or tick performance.
- Validated third-party AEKey delivery, long batches, queued/active/refund persistence, ME refunds, live research changes and recipe reloads.
- Verified that patterns producing different outputs remain separate even when their inputs are identical or proportional. For `10A + 10B -> C` and `20A + 20B -> D`, ordering two C crafts produces `2C`. `10A + 10B -> C` and `10A + 10B -> D` also follow the ordered pattern's output. Single deliveries, API batches, interleaved queues and save/reload were checked.

#### Upgrade notes

- Update OmniSequence to **2.0.1 on both client and server**. Keep only one enabled OmniSequence JAR in each `mods` directory.
- Requirements are unchanged from 2.0.0: Minecraft 1.21.1, Java 21, NeoForge 21.1.220+, AE2 19.2.17+, ExtendedAE 1.21-2.2.32-neoforge+, Glodium 1.21-2.2-neoforge, LDLib2 2.2.18+, and **AppliedEnhancements 1.0.6+** installed separately on both sides.
- The technical Mod ID remains `molecularmanipulator`. Generic inputs must be declared in a well recipe; enabling an AEKey type alone does not add production recipes.

#### Known limitations

- Overlapping ingredient alternatives that produce the **same output** can still select a different recipe when queued work is split into smaller batches, changing processing time and power. Adding a higher-priority overlapping recipe during reload can also leave existing queued work waiting. These previously identified cases are not fixed in 2.0.1.

### 中文

#### 更新内容

| 项目 | 更新说明 |
| --- | --- |
| AEKey 输入 | 物质构筑井样板总成现在支持所有已注册的 AEKey 输入类型，包括 AE2 兼容附属提供的资源。自定义构筑井配方可通过可选的 `ae_inputs` 字段声明精确资源和正 long 数量，现有物品、流体配方保持兼容。 |
| 配方查询 | 按完整产物及数量建立索引，投料时直接查找对应候选，减少反复扫描全部构筑井配方的开销；保留候选顺序与完整输入校验。 |
| 研究检查 | 缓存研究定义及配方归属；完成次数仍按控制器实时读取，解锁、撤销和存档恢复立即生效。配方快照替换后自动重建索引。 |
| 队列开销 | 合并待加工投料时复用候选列表，容量检查不再复制整份加工中原料快照。 |
| 界面与文档 | JEI 和 GuideME 配方显示通用输入；更新中英文样板总成指南、配方与 API 文档及当前版本信息。 |
| 仓库清理 | 将重复的 MIT 许可文件合并为 `LICENSE`，保留贡献者版权声明；现有运行时纹理、模型与 GuideME 页面均有引用，临时诊断和构建产物不纳入 Git。 |

#### 性能与验证

- 本地配方查询基准：128 个构筑井配方、32 个研究定义，先预热 2,000 次，再测试三轮、每轮 2,000 次。中位耗时从 **493.08ms 降至 6.21ms**，减少约 **98.7%**。此结果只代表配方查询环节，不代表整个服务器的 TPS 或 Tick 提升比例。
- 验证了第三方 AEKey 投料、long 批次、排队／加工中／退款数据持久化、原料返回 ME、研究状态即时变更及配方重载。
- 验证了原料相同或数量成比例、但产物不同的配方隔离：`10A + 10B -> C` 与 `20A + 20B -> D` 同时存在时，下单两份 C 仍产出 `2C`；`10A + 10B -> C` 与 `10A + 10B -> D` 也按所下单样板的产物执行。覆盖单份投料、批量 API、交错排队和存档重载。

#### 升级说明

- **客户端和服务端同时更新至 2.0.1**，各自 `mods` 文件夹内仅保留一个启用的 OmniSequence JAR。
- 运行要求与 2.0.0 相同：Minecraft 1.21.1、Java 21、NeoForge 21.1.220+、AE2 19.2.17+、ExtendedAE 1.21-2.2.32-neoforge+、Glodium 1.21-2.2-neoforge、LDLib2 2.2.18+；两端仍需单独安装 **AppliedEnhancements 1.0.6+**。
- 技术 Mod ID 保持 `molecularmanipulator`。通用输入需要在构筑井配方中声明，接入 AEKey 类型本身不会自动添加加工配方。

#### 已知限制

- **产物相同**、可替代原料范围重叠的配方，在队列拆分成较小批次时，仍可能改选另一条配方并改变加工耗时和能耗；重载时新增更高优先级的重叠配方，也可能使已有排队任务保持等待。这两类已确认的问题尚未在 2.0.1 中修复。

## 2.0.0 - 2026-09-08

Release date: 2026-09-08

## English

### Requirements

Minecraft 1.21.1, Java 21, NeoForge 21.1.220+, AE2 19.2.17+, ExtendedAE 1.21-2.2.32-neoforge+, Glodium 1.21-2.2-neoforge, and LDLib2 2.2.18+.
**AppliedEnhancements 1.0.6 or later is required on both the client and server.** Install it as a separate mod; it is not bundled inside OmniSequence.

### Changes

| Area | Update |
| --- | --- |
| Matter Fabrication Well | Added the 41×41×27 Pearl Genesis Chamber with item/fluid processing, research progression, native 16×16 textures and production/research effects. |
| Research | Added AE material, Sequence Array and Omni-Computation branches, repeatable deep research and branch-specific production bonuses. All three built-in stages take 30 seconds per round; data packs and KubeJS can customize research. The Omni branch requires AdvancedAE. |
| Service modules | Added item/fluid input and output ports, dedicated port interfaces, and held-item placement previews for valid well positions. |
| Pattern assemblies | Added 36 processing-pattern slots per assembly, rename support, item/fluid input and output buffers, queued-ingredient refunds and persistent tasks. Contents survive saves and normal block removal. |
| Current structures | The Sequence Array uses the 61×61×29 Frost Feather Crown; the Omni-Computation Core uses a 65×65×35 floating crown. Projection, JEI views, animated effects and controller interfaces follow the current layouts. |
| Legacy structures | Retained only the official 1.3.9 Sequence Array and Omni-Computation blueprints, labeled Legacy 1.3.9. Removed other historical and experimental layouts. The well retains only its current blueprint. |
| Structure updates | Both supported legacy machines show a projection warning. Click Update Structure, wait at least 0.5 seconds, then click again within 5 seconds to confirm. Rapid double-clicks do not trigger an update. Fixed legacy version/size labels and saved-operation compatibility. |
| Dismantling and storage | Dismantling follows actual matching blocks from top to bottom in serpentine rows. Pauses and reloads retain progress; capacity limits pause recovery. Machine/module contents, fluids, patterns and pending returns remain persistent. |
| Sequence Array automation | Added nine independent passive crafting slots with per-input ME reserves and primary-output stock limits. Outputs and remainders return to ME; reusable inputs and deterministic tool pools retain cancellation/refund state. |
| AE2 integration | Shared ordering, pattern caching, material summaries, infinite-cell handling and general terminal enhancements now use AppliedEnhancements. An active formed Omni core invokes its public AELIS planner API; existing prerequisite configuration is respected. |
| Guides and recipes | Added English/Chinese GuideME pages for the well, research, ports and assemblies. Fixed embedded well recipes and updated ingredient quantities, fluids, time, power, research unlocks and machine behavior descriptions. |
| World operation and APIs | Required multiblock chunks stay loaded while the structures or their operations need them. Retained the batch-provider API and added documented matter-research integration. |

### Upgrade notes

- Install both new JARs on the client and server, replacing the corresponding older enabled files.
- Back up your world before structural upgrades. Open the current projection to check the footprint, materials and controller destination.
- Official 1.3.9 upgrades preserve controller contents and support save/reload. The Sequence Array controller moves 3 blocks down and 15 behind its old position; the Omni controller moves 15 blocks up and 5 behind. New materials and recovery space are required.
- Other historical or experimental structures are no longer automatically recognized or migrated. Saved work targeting removed layouts stops instead of continuing against a different blueprint.
- The technical Mod ID remains `molecularmanipulator`. Shared AE2 enhancement settings belong to `appliedenhancements-common.toml`.

## 中文

### 运行要求

Minecraft 1.21.1、Java 21、NeoForge 21.1.220+、AE2 19.2.17+、ExtendedAE 1.21-2.2.32-neoforge+、Glodium 1.21-2.2-neoforge、LDLib2 2.2.18+。
**客户端和服务端均必须安装 AppliedEnhancements 1.0.6 或更高版本。** 前置以独立模组安装，不嵌入本模组安装包。

### 更新内容

| 项目 | 更新说明 |
| --- | --- |
| 物质构筑井 | 新增 41×41×27 白金创生舱，支持物品和流体加工、研究进度，配套原生 16×16 材质及生产、研究动态效果。 |
| 研究系统 | 新增 AE 材料、构序阵列和万物演算三个研究分支，支持重复深度研究及分支生产加成。三个内置阶段每轮均为 30 秒；支持数据包和 KubeJS 自定义。万物演算分支需要 AdvancedAE。 |
| 输入输出口 | 新增物品、流体输入输出口及独立界面；手持对应模块时显示构筑井的合法安装位置。 |
| 样板总成 | 每个总成提供 36 个处理样板槽，支持重命名、物品和流体输入输出缓存、待处理原料退回及任务持久化；保存和正常拆装保留内容。 |
| 当前建筑 | 构序阵列采用 61×61×29 霜晶羽冠，万物演算核心采用 65×65×35 悬浮星冠；投影、JEI、动态效果和控制器界面适配当前布局。 |
| 旧版蓝图 | 仅保留正式 1.3.9 的构序阵列、万物演算核心蓝图，明确标记“旧版 1.3.9”；删除其他历史和试验版布局。物质构筑井只保留当前蓝图。 |
| 结构更新 | 两台受支持的旧版机器均提示“当前版本建筑变化较大，请打开建筑投影确认”。首次点击更新后，至少等待 0.5 秒，再于 5 秒内点击确认；快速双击不会直接执行。修正旧版标记、尺寸和施工存档兼容。 |
| 拆除与内容保存 | 只拆除实际匹配的结构方块，按从上到下、同层蛇形顺序进行；暂停或重载保留进度，回收空间不足时暂停。机器、接口中的物品、流体、样板及待返还内容持久保存。 |
| 构序阵列自动合成 | 新增九个独立被动合成槽，可设置 ME 原料保护量和主产物库存上限；产物及余料返回 ME，可复用材料与确定性工具池支持取消退款。 |
| AE2 前置接入 | 通用下单、样板缓存、材料汇总、无限磁盘及终端增强交由 AppliedEnhancements；在线成型的万物演算核心通过公开 API 调用 AELIS，并遵从前置已有配置。 |
| 指南与配方 | 新增构筑井、研究、接口和样板总成的中英文 GuideME；修复内嵌构筑井配方报错，更新材料数量、流体、耗时、功耗、研究解锁及机器说明。 |
| 世界运行与 API | 多方块及其施工需要时保持相关区块加载；保留批量投料 API，提供已文档化的物质研究扩展接口。 |

### 升级说明

- 客户端和服务端都安装本次两个 JAR，并替换对应的旧版启用文件。
- 更新结构前备份世界，打开当前建筑投影确认占地、材料和控制器目标位置。
- 正式 1.3.9 更新保留控制器内容并支持保存重载。构序阵列控制器移至原位置下方 3 格、背后 15 格；万物演算核心控制器移至上方 15 格、背后 5 格。需要新版材料和回收空间。
- 其他历史或试验版不再自动识别、迁移；指向已删除蓝图的存档施工会停止，避免按照另一套蓝图继续执行。
- 技术 Mod ID 保持 `molecularmanipulator`；通用 AE2 增强配置位于 `appliedenhancements-common.toml`。

## 1.3.9-hotfix - 2026-08-11

### Added

- Large pattern inventories in both Molecular Manipulator single-block
  machines and the Sequence Array multiblock are exposed as multiple logical
  containers in the AE2 Pattern Access Terminal.
- Matter Sequence capacity, rewrite entropy capacity, base cooling, and every
  0–4 Acceleration Card tier's parallelism, batch time, and cooling multiplier
  are configurable. The controller displays per-item entropy, effective cooling,
  and estimated cooldown time.
- Matter rewrite JSON documentation now records the entropy formulas and exact
  categorized TOML paths without replacing configured item and tag rules.

### Changed

- Matter Sequence storage defaults to `Long.MAX_VALUE`; rewrite entropy defaults
  to 1,000,000 and uses saturating `long` arithmetic throughout.
- Server and client options are grouped by subsystem. Existing flat paths are
  migrated with their values preserved and a `.toml.bak` backup.
- Removed the crafting-confirmation path label and its dedicated planning-progress
  network synchronization.

### Fixed

- Completed Molecular Center and Omni Computation structures no longer keep
  showing their construction-progress indicator. The indicator remains visible
  while the structure is incomplete, being built, or being dismantled.
- Infinite storage cells are now detected by simulated over-extraction at the
  AE2 network-storage boundary instead of hard-coding ExtendedAE and AE2
  inventory implementations. Detection is cached per mounted cell and key,
  periodically rechecked, and saturates network and tooltip amounts without
  changing the cell's own advertised contents.
- Bulk `KeyCounter` merges now use the same saturating addition as individual
  updates. Combining an infinite cell with an existing finite stack can no
  longer wrap `Long.MAX_VALUE` into a negative amount and disappear from the
  terminal, regardless of storage mount order.
- Deterministic AdvancedAE `AdvProcessingPattern` recipes now pass through the
  same exact-input, exact-output, remainder, overflow, and runtime-template
  verification as native AE2 processing patterns. This removes the full native
  fallback that made large AdvancedAE processing requests expand one craft at a
  time, while unknown AdvancedAE implementations and subclasses remain on AE2.
- When the real attempt for a deterministic multi-candidate graph cannot use
  its first candidate, AE2 still receives that attempt to preserve later-choice
  priority. If every choice fails, the following missing-material simulation
  now reuses the verified transactional graph and aggregates the first
  candidate's shortages instead of traversing it natively a second time.
- Background and interactive calculations waiting for an Omni execution slot
  now honor `Future.cancel(true)`. Cancelled requester/menu jobs leave the queue
  immediately instead of eventually acquiring a slot and performing obsolete
  work.
- MAX_FAST now recognizes the `fuzzy_crafted_input` state produced when AE2 has
  already selected a valid substitute damage state for a container-returning
  pattern. Deterministic `+1` durability recipes such as platinum dust with a
  substitutable ore hammer can therefore use the verified bulk tool-capacity
  path instead of returning the complete request to AE2's per-craft recursion.
- The selected substitute is revalidated with the original pattern input before
  aggregation. The fast boundary still requires one exact-output pattern,
  deterministic and mutually compatible remainder behavior, a unit tool slot,
  and no self-reference; every unproven case continues through AE2 unchanged.
- Diagnostic mode now records successful reusable-boundary aggregation with the
  request count, calculated pattern count, barrier type, and pattern identity.
  General MAX_FAST decisions also include the requested key and whether the
  attempt was a real craft or the final missing-material simulation.

## 1.3.9 - 2026-08-04

### Added

- Added the public Omni Batch Provider API v1 for pattern-holding AE2 machines.
  Third-party `ICraftingProvider` implementations can advertise an atomic batch
  admission, receive exact multi-craft material deliveries, and explicitly
  commit or reject input ownership without any Mod-specific adapter.
- Added a read-only Omni CPU marker so provider mods with their own AE2 CPU
  batching hook can skip it while an Omni CPU owns material allocation.
- Added an internal reusable-tool pool for all three molecular crafting
  machines. One deterministic `+1` durability input can reserve multiple tools,
  including different damage states, execute them as one persistent batch, and
  refund every exact remaining tool state when the AE2 job is canceled.

### Changed

- Lowered the minimum NeoForge requirement from 21.1.230 to 21.1.220 while
  retaining compatibility with newer NeoForge 21.1 patch releases.
- Lowered the minimum LDLib2 requirement from 2.2.29 to 2.2.18 while retaining
  compatibility with newer LDLib2 releases.
- Replaced the sole MixinExtras Expressions injection with a standard Sponge
  Mixin redirect compatible with the MixinExtras 0.5.0 bundled by NeoForge
  21.1.220.
- Removed the project-added `LD²` badges from the LDLib2 interfaces.
- Restored AE2's numeric formatting for `Long.MAX_VALUE` network-storage
  amounts instead of replacing them with an infinity symbol.
- Made accepted machine batches retain input ownership even when a post-commit
  save, event, or wake hook throws, preventing the same AE2 work from being
  scheduled twice.
- Reusable-tool candidates that fail a machine's full recipe-state validation
  now fall back to AE2's original one-recipe dispatch for that provider instead
  of rebuilding the same rejected aggregate indefinitely.
- Removed the 65,536-crafts-per-tick execution slice from accepted reusable-tool
  batches. All three molecular crafting machines now settle the complete
  long-count aggregate in one machine tick.
- Multi-candidate AE2 plans now transactionally aggregate the first
  deterministic candidate in AE2 priority order instead of expanding it one
  craft at a time, with an exact fallback whenever the full candidate cannot
  satisfy the request.
- Recursively nested crafting patterns that return an unchanged catalyst now
  reuse only the physically required catalyst count while preserving AE2 input
  order, network extraction peaks, crafting counts, and byte accounting.
  Substitute-enabled catalyst slots are supported when the actually selected
  stack has a stable identical remainder; non-deterministic durability, random,
  or dynamic remainder transitions still fall back to AE2's original planner.
- Directly requested recipes with one deterministic `+1` durability tool input
  can now bulk-plan missing fresh tools. Existing tool capacity is consumed
  first, then the remaining tools and their ingredients are requested in one
  recursive batch instead of forcing the entire order through AE2's one-craft
  container loop.
- Deterministic `+1` durability recipes can now stay on the MAX_FAST path when
  nested inside a larger recipe graph. Rejected nested boundaries abort the
  speculative child transaction before AE2 reruns the complete native tree.
- Failed or interrupted speculative boundaries now restore both AE2's
  missing-item accounting and mutable pattern-candidate availability, avoiding
  duplicate missing entries or leaked candidate state so a native retry or a
  later calculation starts from clean state.
- Exact terminal shortages now fail the real MAX_FAST attempt immediately and
  are staged into the simulated missing-material plan in one aggregated pass.
  Merged terminal nodes are accepted only when every recursion context confirms
  that the input is genuinely uncraftable.
- MAX_FAST now validates merged recipe occurrences to a fixed point, including
  occurrences discovered after their shared graph node was first compiled.
  Contexts that disagree about terminal status, candidate priority, or child
  recipe structure—including occurrences with different request-unit
  amounts—are never executed through the context-insensitive aggregate path,
  preventing a craftable intermediate from being reported as a generic
  terminal shortage.
- Recursion-context conflicts now trigger a bounded recompilation that isolates
  only the affected recipe occurrences instead of immediately abandoning the
  complete graph. Context-sensitive graphs retain AE2's depth-first input order
  so parent output surplus cannot satisfy its own still-unplanned descendants;
  graphs with substitutions, reusable inputs, or unsafe boundaries continue to
  use the native planner.
- Exact AE2 smithing-table and stonecutting patterns now participate in
  deterministic MAX_FAST graph compilation instead of forcing the complete
  crafting order back to the native planner. Patterns with real input
  substitutions still retain the existing safe local fallback.
- Stateful molecular crafting machines now drop one NBT-backed recovery block
  when broken with an active batch, quarantined batch, or long-count output
  buffer. Replacing it restores the pending state without spawning an unsafe
  number of item entities.

## 1.3.8 - 2026-07-31

### Changed

- Added LDLib2 2.2.29 as a required dependency and migrated the Omni
  Computation controller's status and action interface to a modular LDLib2 UI
  while retaining the existing AE2 menu, slot, and synchronization behavior.
- Added an animated live-telemetry drawer, smoothly interpolated structure and
  operation progress bars, and status-change pulse animations to demonstrate
  LDLib2's component and animation systems.
- Migrated the Molecular Center controller's four-tab action layer to LDLib2,
  including animated tab selection and interpolated deconstruction, rewrite,
  and entropy meters, while preserving AE2 slots, pattern search, text input,
  color controls, and destructive-action confirmation behavior.
- Moved both LDLib2 controller layouts and their base styling into declarative
  XML/LSS resources; Java now handles AE2 state binding, interaction callbacks,
  dynamic visibility, textures, and animations.
- Refactored the Molecular Sequence Rewrite Array screen with an XML-backed
  LDLib2 navigation and status layer, animated pattern-capacity feedback, and
  a shared dark transfinite visual theme while retaining native AE2 slots and
  the existing Java search field.
- Rebalanced all Sequence Array component recipes around ExtendedAE's
  higher-tier progression. High-volume parts now use Assembler Matrix blocks,
  Entro blocks, and concurrent processors, while the unique core and
  controller require matrix cores and a wireless hub.

### Fixed

- Gave the Omni-Computation XML value column explicit widths so dynamically
  populated network, storage, parallelism, job, lane, calculation, and quantum
  states remain visible after the initial empty-label layout pass.
- Restored the Omni-Computation Core's last validated formed state during chunk
  loading and synchronized its powered model before AE2 cluster restoration can
  return early, preventing an online core from retaining its inactive texture
  after joining a world.
- Kept LDLib2 hover and click hit-testing aligned with responsively scaled
  controller screens by supplying raw screen mouse coordinates to its
  pose-aware render pass instead of transforming the pointer twice.
- Sequence Array formation now validates physical multiblock parts only. The
  rendered center at local `(0, 29, 0)` may be occupied without unforming the
  structure, and automatic repair, dismantling, or ghost previews leave that
  position untouched.
- Blocking AE2 and ExtendedAE pattern providers now retain serial dispatch
  semantics when connected to OmniSequence batch-crafting providers.

## 1.3.7 - 2026-07-30

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
- Item-substitution crafting patterns are now permanently eligible for
  compatible runtime batch dispatch. AE2 still selects the actual substituted
  input, and the retired `omni_batch_allow_substitution_patterns` option is
  removed from existing TOML files without resetting other custom values.
- The Assembler Matrix Sequence Rewrite Core and Sequence Array now own and
  persist accepted reusable-input batches across saves, chunk unloads, and
  server restarts.
- Same-key and unbreakable remainders can run as one reusable batch.
  Finite-durability tools batch only when each craft deterministically adds
  exactly one damage; probabilistic, contextual, and key-changing transitions
  retain AE2's original one-craft path.
- Reusable batch expansion now uses AE2's native pattern-power calculation
  over the actual combined input set.
- Matter Sequence item tooltips now default to a compact Shift-expand prompt.
  The client setting supports `DISABLED`, `HOLD_SHIFT`, and `ALWAYS_VISIBLE`.
- Pattern inventories now have debounced, cross-page input/output search while
  retaining AE2's detailed encoded-pattern hover tooltip. Search uses
  client-localized names and optionally delegates matching to Just Enough
  Characters, enabling Pinyin queries without making JEC a required mod.

### Fixed

- Canceling an AE2 job now persistently stops every remaining provider-side
  reusable execution and refunds the exact unused materials together with the
  reusable item's current state. Completed outputs remain valid, and canceled
  work cannot resume after a reload.
- Kept the vanilla translucent world backdrop behind controller screens while
  responsive scaling is active, covering the complete viewport.
- Sequence Array construction now honors Creative mode like Omni-Computation
  construction: materials are not consumed, and a failed placement cannot
  create a refunded block.
- Added compact English controller labels for narrow buttons and verified that
  every English and Chinese static/dynamic translation key has a matching
  entry.
- Reworked controller labels and values into width-aware columns and wrapped
  rows, preventing English text overlap. The Sequence Array's two matter-job
  states and counters fit independently inside their 96-pixel columns, and the
  Omni-Computation fixed-capability notice renders at normal readable size.
- The AE2 crafting CPU selector now renders a compact localized Omni lane name
  inside its narrow row while retaining the complete name in the tooltip.

## 1.3.6 - 2026-07-29

### Added

- Added AE2 GuideME documentation for the Molecular Sequence Rewrite Array,
  Assembler Matrix Sequence Rewrite Core, Omni-Computation Core, and Sequence
  Array Controller.

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
- Marked only Expanded AE 2.1.1 as incompatible in NeoForge metadata. AppliedFlux,
  ExtendedAE, and later NeoForge 21.1 patch releases are not conflict entries.

## 1.3.5-fix - 2026-07-28

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

## 1.3.5 - 2026-07-27

### Changed

- Added native six-direction connected rendering for the Computation Core Frame,
  including seamless faces and non-overlapping translucent border corners.
- Updated the Universal Pattern Matrix texture and enabled its translucent render layer.
- Limited each provider/pattern pair to one scaled-dispatch growth step per server
  tick, preventing a single lane from probing `1, 2, 4, ...` in one tick while
  retaining the learned multiplier for the next tick.
- Replaced the fixed 32-call compatibility throttle with an
  `Integer.MAX_VALUE` hard ceiling plus one strict server-wide, load-aware time
  slice. Active Omni cores share up to 20 ms while average MSPT is low and
  automatically contract toward a rotating single-lane progress token as MSPT
  approaches 45. Task iteration also rotates and gives each compatibility
  pattern a short sub-slice so one huge recipe cannot starve later recipes.
- Cached negative explicit-batch-provider topology once per job pattern and
  server tick. Ordinary multi-input compatibility pushes now skip repeated
  waiting-for analysis, pattern validation and provider pre-scans while retaining
  the original complete-recipe `pushPattern` call and shared time budget.
- Removed the obsolete `omni_unscaled_dispatch_attempts_per_tick` option from
  the server config and configuration screen. Existing TOML files migrate by
  deleting only that retired key, preserving every current custom value.

### Fixed

- Routed non-explicit multi-input providers through AE2-style repeated complete
  `1x` recipe calls, bypassing the runtime-scaled-pattern context entirely. This
  prevents one-to-many targets from routing large waves of the first, second and
  later ingredients into different machines and deadlocking their input slots.
  The `Integer.MAX_VALUE` parallelism remains a logical scheduling window; real
  one-recipe calls are time-sliced by the controller-wide per-tick allowance,
  while single-input patterns and explicit batch providers retain adaptive
  scaling.
- Removed helper and anonymous classes from the Mixin package so transformed
  AE2 crafting CPU targets no longer trigger Mixin `IllegalClassLoadError`
  during startup or adaptive provider iteration.
- Normalized ExtendedAE Plus planning-time scaled multi-input tasks before an
  Omni CPU executes them, so an EAP `1x` wrapper cannot conceal an already
  multiplied ingredient batch from the atomic-dispatch guard.
- Replaced the production-unsafe direct reference to the client accessor mixin
  with an application bridge implemented on AE2's number entry widget. Opening
  the long crafting amount screen no longer fails with
  `NoClassDefFoundError: NumberEntryWidgetAccessor`.
## 1.3.4-hotfix - 2026-07-27

### Fixed

- Removed the `ModConfigSpec` core-class mixin that could run after NeoForge had
  already loaded its target and abort startup with `MixinTargetAlreadyLoadedException`.
- Moved saved-world configuration migration to NeoForge's supported
  `ServerAboutToStartEvent`, eliminating the remaining lifecycle core-class mixin.

## 1.3.4 - 2026-07-27

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
  defaults. Missing current options and invalid values retain NeoForge's normal
  targeted correction behavior.

### Compatibility

- Existing worlds, patterns and configuration files remain compatible.
- The stable mod ID remains `molecularmanipulator`.
- Dedicated batch-provider and machine whitelist behavior is unchanged.

## 1.3.3-hotfix - 2026-07-26

### Fixed

- Reacquired render buffers after every render-type switch in the Omni
  Computation Core and separated multiblock projection model/outline passes,
  preventing `BufferBuilder: Not building!` client crashes.
- Replaced output-return-driven adaptive dispatch with complete-input acceptance
  feedback, so slow, high-parallel and output-less processing targets no longer
  stall material delivery while waiting for products.
- Repeated standard provider attempts safely within the same server tick. Every
  attempt remains one complete AE2 recipe with independent extraction and
  accounting; rejection or provider-side queuing stops the current tick and
  contracts the dispatch window.
- Removed the exact `PatternProviderLogic` class requirement from the standard
  adaptive path. Third-party providers that correctly implement AE2's
  `ICraftingProvider` success, rejection and busy-state contract can now use the
  same bounded high-throughput dispatch.
- Added full-amount preflight checks and cache-aware initial windows for standard
  AE2 pattern providers, while preserving the existing direct-batch path for
  explicitly compatible machines.

### Compatibility

- Existing worlds, patterns and configuration files remain compatible.
- The stable mod ID remains `molecularmanipulator`.
- Dedicated batch-provider and machine whitelist behavior is unchanged.

## 1.3.3 - 2026-07-25

### Fixed

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

- Minecraft 1.21.1
- NeoForge 21.1.230 or later
- Applied Energistics 2 19.2.17 or later
- ExtendedAE 1.21-2.2.32-neoforge or later
- Advanced AE 1.6.11 or later is optional
- ExtendedAE Plus and JEI integrations are optional

### Notes

- Existing worlds continue to use the stable `molecularmanipulator` namespace.
- Extremely large crafting requests remain subject to recipe-specific compatibility fallbacks.
