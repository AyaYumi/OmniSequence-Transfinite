# 万象构序：超限 / OmniSequence: Transfinite

[English](README.md) · [接口文档](docs/README.md)

面向 AE2 和 ExtendedAE 后期自动化的扩展模组，提供材料研究、多方块加工、大容量样板库、合成 CPU，以及资源采集与物品复制。模组 ID 为 `molecularmanipulator`。

## 版本与依赖

| 组件 | 当前要求 |
| --- | --- |
| 本模组 | 2.0.13-forge，源码分支 `1.20.1-forge` |
| Minecraft / Java | 1.20.1 / Java 17 |
| Forge | 47.4.20 或更高 |
| AppliedEnhancements | 1.1.2-forge 或更高，客户端和服务端都需要；编译 API 为 1.1.4-forge |
| AE2 | 15.4.10 |
| ExtendedAE | 1.20-1.4.20-forge 或更高 |
| NeoECOAE | 可选；最低 20.4.2；原生批量／精确计划兼容要求 20.5.0+ |
| ExtendedAE Plus | 可选；Forge 集成目标为 1.6.2 |
| JEI / AdvancedAE | 可选；AdvancedAE 启用对应研究与兼容 |

将本模组和必需依赖分别安装到客户端与服务端的 `mods/` 文件夹，两端使用相同构建。ExpandedAE 2.1.1 与本模组不兼容。

## 开始使用

1. 用基础 AE2 材料搭建**物质构筑井**，接入 ME 网络并完成一阶研究。
2. 安装**样板总成**，将构筑井配方接入 ME 自动合成。
3. 分别研究**二阶：构序阵列**与**二阶：万物演算**，制作样板存储、合成设备与 CPU。
4. 两条二阶分支各完成一次后，研究**三阶：视界奇点**，搭建天枢进行采集与复制。

**特殊: 异态物质学**在一阶后开放，只研究一轮，用于制作引力水晶与幽灵物质。它是独立的可选研究，不要求完成三阶。

在物品、JEI 条目或相关方块上使用 AE2 指南快捷键（默认 **G**），可打开中英文游戏内指南。配方、研究条件与结构材料以当前整合包的 JEI 和设备界面为准。

## 主要设备

| 设备 | 用途 |
| --- | --- |
| 物质构筑井 | 材料加工、研究与 ME 自动合成 |
| 分子构序重写阵列 | 360 个样板槽，内部批量执行合成、锻造与切石 |
| 装配矩阵构序重写核心 | 为 ExtendedAE 装配矩阵提供批量加工能力 |
| 构序阵列 | 大容量样板库与九个独立自动补货位置 |
| 分子自动合成器 | 根据成品目标和原料保留量维持库存 |
| 万物演算核心 | 同时管理多个 ME 合成请求 |
| 超限算枢 | 单方块合成 CPU，需要一个频道和足够的 ME 电力 |
| 视界奇点天枢 | 同时采集资源，以奇点序质复制样品 |
| 单方块奇点天枢 | 提供天枢功能，从创造模式物品栏或命令获取 |
| 微型黑洞 / 微型白洞 | 将掉落物与非玩家实体传送到同队最近出口 |
| 引力水晶 | 沿墙面与天花板行走，或搭建推离升降通道 |
| 幽灵物质 | 可用水抑制的危险物质，也是引力水晶的保留催化剂 |

## 样板管理与特效

- **原生自动上传：**在构序阵列总览开启「自动上传」，同一 ME 网络终端中新编码的合成、切石和锻造样板即可进入阵列，无需 ExtendedAE Plus 或上传核心。处理样板留在终端；重复样板退回空白样板。没有已开启、就绪且有空位的阵列时，编码样板保留在终端。
- **一次点击迁移：**「一键迁移样板」将同网络公开实体样板槽和在线已挂载存储盘中的受支持样板移入主样板库，保留 NBT 与重复实体，不重新编码、不消耗空白样板。私有槽、只读配方行、存储总线代理及处理样板不参与。满库时剩余样板留在来源；中断时保留已完成的转移。大型迁移在点击后同步执行，并非持续后台收集。
- **构筑井上传：**JEI「编码并上传至构筑井」优先使用终端中的一张空白样板，本地槽为空时从相连 ME 网络提取。目标按已加载、成型的构筑井及队伍归属识别，不依赖总成名称。Forge EAEP 1.6.2 没有新版 named-provider API，不承诺其自动选中重命名总成。
- **独立特效：**控制器界面提供关闭／精简／完整三档，阵列按钮在配色页。独立选择保存在该控制器并同步给所有观看者，不改变生产或其他机器。Shift+点击或 Shift+Enter／空格恢复跟随各客户端全局特效配置。

新阵列默认关闭自动上传。升级时，旧槽内可识别的上传核心通过控制器输出缓存退回，并保留原先开启行为；已明确保存的开关值优先。具体限制见[配置参考](CONFIGURATION.md)。

## 研究、采集与复制

普通研究默认各有九轮。首次完成解锁配方，后续提高所属构筑井配方的并行与速度。普通下单准备下一轮材料，Shift 下单准备所有剩余轮次，并在一次研究后到达满阶。备料期间可取消；下单研究正式开始后不能停止或暂停。

天枢默认每 20 tick 同时生产资源列表中每种物品 1000 个。物质复制页可放入最多 64 个微型黑洞，每个默认每 20 tick 产生 20 mB 奇点序质。每 mB 序质消耗 1000 FE 或 256 AE，每复制一件物品消耗 1000 mB，原样品保留。生产序质需要 ME 流体存储，接收采集与复制产物需要 ME 物品存储。采集页显示服务端实际生效的资源列表、单类产量与间隔，连接独立服务器的客户端也以该数据为准。

黑白洞在放置时绑定 FTB 队伍，未安装 FTB Teams 时绑定个人。换队后重新放置可更新归属。白洞保持所在区块加载，黑洞需要所在区块已加载才能工作。

## 配置与开发文档

全局配置文件为 `config/omnisequence-transfinite-server.toml` 和 `config/omnisequence-transfinite-client.toml`。可调整设备耗电、样板容量、采集列表、产量及区块加载，详见[配置参考](CONFIGURATION.md)。

整合包作者可通过数据包配置构筑井配方与研究。第三方模组的研究、批量供应器与大数量加工接口见[开发文档索引](docs/README.md)。

## 从源码构建

使用 Java 17，将 `appliedenhancements-1.1.4-forge.jar` 放入 `libs/`，然后运行：

```sh
./gradlew build apiJavadoc
```

运行产物为 `build/libs/omnisequence-transfinite-2.0.13-forge.jar`，公开 Java API 文档位于 `build/docs/api/`。依赖安装与本地文件说明见 [libs/README.md](libs/README.md)。

## 运行回归

测试位于 `src/gameTest`，不会进入发布 JAR。每次使用新的 `-PgameTestRunName=<name>`，输出位于 `build/<name>`；独立服务端和客户端默认目录分别为 `build/gametest-<suite>`、`build/gametest-client-<suite>`。

| 入口 | 测试组与源码用例数 |
| --- | --- |
| 独立服务端 | `cpu`（1）、`performance`（9）、`fabrication`（12）：CPU、配方查找与批量执行、自动合成 |
| 独立服务端 | `patterns`（基础 16）：实体样板迁移与原生上传 |
| 独立服务端 | `singularity-quantum`（3）、`singularity-menu`（2）：量子连接、资源列表与菜单同步 |
| 独立服务端 | `fabrication-upload`（4）：网络空白样板、旧建造者归属迁移与队伍保护 |
| 独立服务端 | `research`、`singularity`、`singularity-unload`、`adaptive` |
| 客户端内置服务端 | `eco`（27，NeoECOAE 20.5.0 批量／精确计划）、`eco-legacy`（2，20.4.2 普通派发、归属、取消与重载） |
| 客户端内置服务端 | `essence`、`native-essence`、`machines`；启用集群参数的 `patterns` |

```sh
./gradlew --no-daemon -PgameTestSuite=<suite> -PgameTestRunName=gametest-<suite>-r1 runGameTestServer
./gradlew --no-daemon -I src/gameTest/integrated-client.init.gradle -PgameTestSuite=<suite> -PgameTestRunName=gametest-client-<suite>-r1 runClient
```

客户端入口创建一次性世界并写入 `gametest-result.json`，必需用例失败时任务失败。NeoECOAE 20.5.0 的独立服务端启动引用了 `ClientLevel`，因此需先初始化客户端。`gameTestEcoVersion` 可指定测试运行版本，不改变编译接口版本。

- `adaptive`：添加 `-PgameTestRecipeModsDir=/path/to/reference-pack/mods`，目录内需有 ExtendedAE Plus 1.6.2。
- `essence`／`native-essence`：同一目录参数需提供 Cucumber、Mystical Agriculture、Mystical Agradditions 和 MATC；`native-essence` 不加载 ECO。`machines` 需要 AdvancedAE 和 GeckoLib。版本见 `build.gradle`。
- `patterns`：`-PgameTestPatternCluster` 使用 NeoECOAE 20.5.0 与 LDLib 1.0.52.a 增加 1 个集群用例，需用客户端入口；`-PgameTestPatternStorageJar=/path/to/ae2omnicells-1.20.1-forge-1.1.6.jar` 独立增加 1 个存储盘用例，不引入集群依赖。两参数同时启用共 18 例。

独立的**白洞／透镜 GPU 回归**检查 3 种特效 × 9 种相机状态，共 27 项，只打开标题画面，需要能正常渲染的客户端：

```sh
./gradlew --no-daemon -I src/gameTest/singularity-lens.init.gradle -PgameTestSuite=cpu -PgameTestRunName=gametest-lens-r1 runClient
```

此处 `cpu` 会被 GPU 测试替换。任务要求 `singularity-lens-result.json` 通过，且日志含匹配本次运行的 `LENS_BOBBING_CLIENT_PASS` 标记。运行回归覆盖选定行为，不作为整合包 TPS 性能结论。

使用 [MIT 许可证](LICENSE)。
