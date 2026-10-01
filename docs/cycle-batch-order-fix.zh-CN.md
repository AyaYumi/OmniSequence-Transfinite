# 循环批量与处理样板投送顺序修复

## 修复内容

Omni 的批量分配以前按合成任务总数扩展输入，而 Applied Enhancements 的
库存保护只包住首份取料。当任务总数大于当前循环步骤的次数时，额外取料绕过
约束，派发校验抛出 `Cyclic dispatch exceeds the current execution step`。

现在首份取料、批量扩展和额外取料回滚共用 `AelisBatchExecutionContext.inventory()`，
批量次数同时受当前步骤、库存、机器容量和待返回输出容量限制。
Mek Energistics 使用的旧 `MolecularBatchCraftingProvider` 接口无需修改；
新的 `OmniBatchCraftingProvider` admission/commit 路径也会推进循环进度。
拒绝派发会恢复步骤和待返回输出账本，旧 pushPattern 钩子不会重复推进。
材料所有权已转移后，清理异常不会回退循环进度。

循环或种子保护期间，不使用绕过这个库存视图的无限整数直接取料及可复用输入
快捷路径，仍可执行受约束的普通批量；其他合成任务保留原有路径。

投送顺序修复包括：

- 普通供应器余料交回 AE2 的原生投送逻辑，原生／扩展供应器不再被公平批量队列接管。
- 显式批量队列按 `sendList` 中材料首次出现的顺序重试，配方数量的持久化也使用有序表。
- 倍率样板保留原样板的投送回调顺序，包括不相邻的重复输入，并分配全部实际取出的材料。

## 配套依赖

本地版本为 Applied Enhancements `1.1.0` 和 OmniSequence
`2.0.7`。新 API 需要两者配套更新。编译依赖位于
`libs/appliedenhancements-1.1.0.jar`，作为独立模组安装，不嵌入 Omni。
两者以 AE2 `19.2.18` 编译，并保留 AE2 `19.2.17+` 的声明兼容范围。

远程 CI 仍固定检出旧 Applied Enhancements 提交。前置源码发布后，需要将
`.github/workflows/build.yml` 的固定提交及复制产物名称一并更新。

## 验证状态

Applied Enhancements 最新源码完成构建，443 项测试通过。新增覆盖包括：当前步骤
小于任务总数、剩余一次、未来步骤阻止派发、共享保护视图、拒绝及异常回滚、
新接口同步返回输出、旧接口去重、所有权转移后的清理异常。

其他修改完成后，按用户后续要求构建 Omni 完整工作区，`gradlew.bat build` 成功，
225 项测试通过。其中包含倍率样板顺序、全部材料数量、有重复输入和大数量的
投送、单类型目标余料重试及模拟恢复后的顺序。4 项实际加载的服务端 GameTest
也通过，包括太虚部件位置同步后的包围盒回归检查。发行 JAR 包含太虚闪烁修复，
不含验证类。尚未在用户原存档中验证 Mek Energistics、机械动力置物台和抽屉。

## 安装记录

2026-09-30 18:54（北京时间）已将配套 JAR 安装到
`E:\MC\PCL\.minecraft\versions\New Age Science and Technology\mods`。
安装后的文件 SHA-256 与构建产物一致，旧版移出 mods 并备份到同一整合包的
`codex-backups\cycle-order-20260930-185449`，其中 `installation.json` 记录原文件、
新文件、校验和及验证结果。存档与配置未修改。
