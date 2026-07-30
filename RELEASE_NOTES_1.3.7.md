# OmniSequence: Transfinite 1.3.7 Release Notes

Release date: 2026-07-30

Target environment: Minecraft 1.21.1 / NeoForge / Applied Energistics 2 19.2.17+

## Highlights

Version 1.3.7 adds effect-cleared layouts for both fixed multiblock structures
without invalidating their legacy layouts. Their large effects remain anchored
at the same positions, and each controller lets the player decide whether a
complete legacy structure should be updated.

## Multiblock Layout Changes

- The current Sequence Array layout leaves local `(0, 29, 0)` as air and moves
  its physical core to the top anchor at `(0, 36, 0)`.
- The current Omni-Computation layout leaves local `(0, 17, 0)` as air and
  moves its data entangler into the front lower pylon at `(0, 9, -12)`.
- Complete legacy layouts remain strictly recognized, formed, and operational.
  Hybrid arrangements do not count as either supported layout.
- A controller that detects a complete legacy layout displays an optional
  structure-update notice. No blocks are moved until a player accepts it.
- JEI layer previews, material lists, projection, automatic construction,
  dismantling, and GuideME pages describe the current layouts and upgrade flow.

## Existing-World Upgrade

Existing complete structures remain operational and do not need to be rebuilt.
To adopt the effect-cleared layout:

1. Load every chunk covered by the structure.
2. Open its controller and accept the displayed structure update.
3. The controller migrates the legacy center components to the current-layout
   positions.

Closing the controller or declining the update keeps the legacy layout in use.
The Sequence Array update waits before removal if the recovered blocks cannot
be stored. The Omni-Computation update validates both positions and rolls back
if the move cannot complete. No new world or configuration reset is required.

## Autocrafting, Reusable Inputs, and Cancellation

- Crafting patterns with item substitution enabled remain a conservative
  fallback from MAX_FAST planning, but are permanently eligible for compatible
  runtime batch dispatch. The retired
  `omni_batch_allow_substitution_patterns` option is removed from existing
  server configuration files without resetting other custom values; AE2 still
  chooses the actual substituted input.
- The Assembler Matrix Sequence Rewrite Core and Sequence Array own and persist
  reusable-input batches after accepting them. Saving, chunk unloading, and
  server restarts no longer lose or duplicate an in-progress batch.
- Canceling the AE2 crafting job records a persistent cancellation marker,
  stops every remaining provider-side execution, and returns the exact
  unconsumed materials together with the reusable item's current state.
  Already-completed outputs remain valid, and canceled work cannot silently
  resume after the provider reloads.
- Same-key crafting remainders, including items treated as unbreakable by their
  item data, can complete as one reusable batch. Finite-durability tools are
  batched only when every craft deterministically advances damage by exactly
  one. Unbreaking-enchanted or otherwise probabilistic and context-dependent
  tools conservatively fall back instead of being treated as infinite.
- Key-changing remainders such as a water bucket becoming an empty bucket, plus
  unknown or random transitions, retain AE2's original one-craft execution
  path.
- Batch expansion uses AE2's native pattern-power calculation over the actual
  combined input set, preserving AE2's original crafting-energy semantics.

## Controller and Creative-Mode Fixes

- Controller screens retain the vanilla translucent world backdrop during
  responsive scaling, covering the complete viewport.
- Oversized controller screens now scale down only when needed. Visual slots,
  mouse interaction, item and button tooltips, and JEI ghost-ingredient targets
  remain aligned at the scaled size.
- Pattern inventories now provide debounced cross-page input/output search and
  retain AE2's detailed encoded-pattern hover information. Optional Just Enough
  Characters integration enables Pinyin matching using client-localized names.
- Multiblock projections cache reusable 16x16x16 render sections and rebuild
  only changed sections, at most two per frame, reducing repeated model
  tessellation while previewing large structures.
- Sequence Array automatic construction now follows the same Creative-mode
  material rules as Omni-Computation construction. It no longer waits for
  materials in an empty Creative inventory, and failed placement does not
  generate a refunded block.
- English and Chinese static and dynamic translation keys were checked as
  complete pairs. Compact English labels are used where a narrow button could
  otherwise clip its text.
- GuideME now lists the mod under its own top-level `OmniSequence: Transfinite`
  section instead of mixing its pages into AE2's machine category.
- The Sequence Array guide now explains JEI marker selection, ME-fed
  deconstruction, blueprint reproduction, all four sequence values, rewrite
  entropy and cooling, acceleration intervals, and the matter-rule file.
- Matter Sequence item details use a compact Shift-expand prompt by default.
  The client configuration can disable them, keep `HOLD_SHIFT`, or show them
  permanently with `ALWAYS_VISIBLE`.
- Width-aware label/value rows prevent English text from overlapping in both
  controller screens. The two matter-job states and counters fit independently
  inside their 96-pixel columns, and the fixed-capability notice uses
  normal-size wrapped text instead of shrinking the whole sentence.
- Omni compute lanes use compact localized names inside AE2's narrow crafting
  CPU list; hovering a row still shows its complete name.

## Installation

1. Fully stop the client and server, then back up the world.
2. Remove or disable older active `omnisequence-transfinite-*.jar` files.
3. Put `omnisequence-transfinite-1.3.7.jar` in both client and server `mods`
   directories.
4. Keep exactly one active OmniSequence JAR in each `mods` directory.
5. Start the game, verify version `1.3.7`, and decide from each controller
   whether an existing legacy structure should be updated.

SHA-256: `22ECA3C8A925DC6D43E4BC2D18F5A4A1F2027B1DC12974D2E505F1899E987304`

---

# 万象构序：超限 1.3.7 更新说明

发布日期：2026-07-30

适用环境：Minecraft 1.21.1 / NeoForge / Applied Energistics 2 19.2.17+

## 版本亮点

1.3.7 为两套固定多方块增加了清空特效中心的新版布局，同时保留旧版布局兼容。
大型特效仍位于原来的位置，控制器会让玩家自行决定是否更新完整的旧结构。

## 多方块结构调整

- 构序阵列新版布局会让本地坐标 `(0, 29, 0)` 保持为空气，并将实体核心移动到
  顶部锚点 `(0, 36, 0)`。
- 万物演算核心新版布局会让本地坐标 `(0, 17, 0)` 保持为空气，并将数据纠缠节点
  移动到前方下部塔体 `(0, 9, -12)`。
- 完整旧版布局仍会被严格识别、正常成型并继续运行；新旧位置混搭不会被误判为有效布局。
- 控制器检测到完整旧布局时会显示可选的结构更新提示，只有玩家确认后才会移动方块。
- JEI 分层预览、材料清单、结构投影、自动搭建、自动拆除和 GuideME 页面均已同步。

## 旧存档升级

现有完整结构可以继续运行，不要求重建。若要采用清空特效中心的新版布局：

1. 加载结构覆盖的全部区块。
2. 打开控制器并确认其中显示的结构更新。
3. 控制器会将旧版中心组件迁移到当前布局对应的位置。

关闭控制器或不接受更新即可继续使用旧布局。
构序阵列会在回收方块无处存放时暂停于移除前；万物演算核心会先校验两个位置，
若移动无法完成则回滚目标位置。
本次更新不要求新建世界，也不要求重置配置。

## 自动合成、可复用输入与取消

- 开启物品替代的合成样板仍会从 MAX_FAST 规划中保守回退，但永久允许进入兼容的
  运行时批量发配路径。已停用的
  `omni_batch_allow_substitution_patterns` 会从现有服务端配置中移除，
  且不会重置其他自定义配置；实际替代材料仍由 AE2 原生逻辑选择。
- 装配矩阵构序重写核心与构序阵列在接受可复用输入批次后，会自行持有并持久化完整
  执行状态。保存、区块卸载或服务器重启不会再让进行中的批次丢失或重复执行。
- 取消 AE2 合成任务会写入持久取消标记，停止供应器侧所有剩余执行，并精确退回尚未
  消耗的材料及可复用物品的当前状态；已经完成的产物保持有效，供应器重载后也不会
  悄悄恢复已取消任务。
- 同键返还物品（包括被物品数据判定为不可损坏的物品）可以作为一个可复用批次直接
  完成。有限耐久工具只在每次合成都确定增加恰好 1 点损伤时才会批量执行；带耐久附魔
  或其他概率性、依赖上下文的工具会保守回退，不会被误判为无限可复用。
- 水桶变为空桶等换键返还，以及未知或随机的状态转移，仍保留 AE2 原生逐份执行路径。
- 批量扩展会对实际合并后的输入调用 AE2 原生样板能耗计算，保持 AE2 原版合成能耗语义。

## 文档与界面修复

- 两个控制器界面在响应式缩放时会保留原版黑色半透明世界背景，并覆盖整个屏幕。
- 两套多方块结构投影会缓存可复用的 16x16x16 渲染分区，仅重建发生变化的分区，
  每帧最多重建两个，降低大型结构预览期间的重复模型构建开销。
- GuideME 现在将本模组文档独立列在“万象构序：超限”顶级分类中，不再混入
  AE2 的机器分类。
- 构序阵列指南现在会完整说明 JEI 分解标记、从 ME 网络抽取物品、蓝图复制、
  四类序列价值、重写熵与冷却、加速卡处理周期，以及物质规则配置文件。
- 物品提示中的物质构序详情默认折叠为按住 Shift 展开；客户端配置可以永久关闭、
  保持 `HOLD_SHIFT` 或使用 `ALWAYS_VISIBLE` 永久显示。
- 样板库存现在支持带防抖的跨页输入/输出搜索，并保留 AE2 编码样板的完整悬停信息；
  安装可选的“通用拼音搜索”后，会按客户端本地化名称启用拼音匹配。
- 两个控制器会按实际字体宽度排列标签与数值，避免英文文本相互覆盖；构序阵列的
  两条物质作业状态与计数会分别适配各自 96 像素宽的栏位。
- 万物演算核心的固定能力说明改为正常字号换行显示，不再缩小整行文字。
- AE2 合成 CPU 列表会显示紧凑的演算通道名称，避免文字越过条目；悬停时仍会
  显示完整名称。

## 安装

1. 完全关闭客户端和服务端，并备份世界。
2. 删除或停用旧版 `omnisequence-transfinite-*.jar`。
3. 将 `omnisequence-transfinite-1.3.7.jar` 放入客户端和服务端的 `mods` 目录。
4. 确保每个 `mods` 目录中只有一个启用中的万象构序 JAR。
5. 启动游戏并确认版本为 `1.3.7`，再由各控制器决定是否更新已有的旧版结构。

SHA-256：`22ECA3C8A925DC6D43E4BC2D18F5A4A1F2027B1DC12974D2E505F1899E987304`
