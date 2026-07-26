# 万象构序：超限 / OmniSequence: Transfinite

面向 Minecraft 1.21.1 NeoForge 的 AE2 / ExtendedAE 后期附属模组，提供超大规模自动合成、反馈式材料发配、量子 ME 链路和大型多方块系统。

> 为兼容已有世界、配置与整合包脚本，技术命名空间和 Mod ID 仍为 `molecularmanipulator`。

## 版本与兼容

| 项目 | 版本 |
| --- | --- |
| Minecraft | 1.21.1 |
| NeoForge | 21.1.230 或更高 |
| Applied Energistics 2 | 19.2.17 或更高 |
| ExtendedAE | 1.21-2.2.32-neoforge 或更高 |
| Glodium | 1.21-2.2-neoforge |
| 可选兼容 | Advanced AE、ExtendedAE Plus、JEI、AE2WTLib |

当前版本：`1.3.3-hotfix`

## 主要功能

- 将 AE2 单次自动合成下单量扩展至可配置的 `long` 范围。
- 兼容 AE2 创造存储元件和 ExtendedAE 无限存储元件，并将无限数量显示为 `∞`。
- 修复无线终端自动补货覆盖层在超大或无限库存下的整数溢出崩溃。
- 提供分子构序重写阵列、装配矩阵构序重写核心、万物演算核心和构序阵列多方块。
- 支持大型结构投影、一键搭建、一键拆卸、跨区块暂停恢复和动态视觉效果。
- 支持有线 ME 接入及跨维度缠绕态量子链路。
- 提供可由整合包配置的物质分解、序列储存和蓝图复制系统。

## 自动合成与材料发配

万物演算核心使用 `SAFE` 聚合模式加速确定性配方树。替代配方、容器返还、动态输入、循环或未知样板会自动回退到 AE2 原生计算，避免为了速度牺牲正确性。

材料发配采用两条路径：

- 明确支持批处理的机器保留 `long` 逻辑批量直推，仅受任务材料、能源和目标实际接收能力限制。
- 标准 AE2 / ExtendedAE 样板供应器先执行有界探测，再根据主产物返回速度自动采用 `×16`、`×4`、`×2` 或保持当前窗口；机器忙碌、拒收或任务中断时会主动收缩。

多材料样板会在每个发送 Tick 重新为全部材料建立一份配方占位，再按剩余比例公平轮转，避免第一种材料独占输入槽。调度器按虚拟 CPU 公平分配工作，并通过每核心目标耗时、全服紧急耗时和工作单元上限控制服务器 Tick 压力；这些预算限制的是调度工作量，不会截断单个批次的逻辑合成数量。

## 核心设备

### 分子构序重写阵列

- 默认提供 720 个样板槽，可通过配置扩展。
- 支持虚拟高并行和最快 1 Tick 配方处理。
- 中间产物及容器返还通过持久化缓冲安全返回 ME 网络。

### 装配矩阵构序重写核心

- 可嵌入 ExtendedAE 装配矩阵，替代普通合成核心与速度核心。
- 调用真实配方装配与容器返还逻辑，兼容工具耐久和不可消耗输入。
- 输出按 `AEKey` 聚合后批量返回 ME 网络。

### 万物演算核心

- 固定 31×31×39 结构，提供 `Long.MAX_VALUE` 级逻辑合成存储与并行能力。
- 根据运行中的合成请求动态维护虚拟 CPU 通道，并保留空闲通道接收新任务。
- 结构损坏或区块未加载时保存任务、内部材料和进度，恢复后继续运行。
- 支持投影、自动施工、自动拆卸及周边敌对生物生成抑制。

### 构序阵列

- 固定 31×46×31 结构，批量构序并行上限为 `Long.MAX_VALUE`。
- 支持独立 RGB 能量场、内核和星环效果；合成时动画自动加速。
- 不强制加载区块，结构范围未完整加载时会暂停并在恢复后重新校验。

结构的完整材料清单和朝向以游戏内投影及 JEI 信息为准。

## 缠绕态量子链路

大型控制器内置量子端点。将一对缠绕态奇点分别放入远端 AE2 量子环和控制器，即可跨维度接入该 ME 网络。

- 未成型控制器可通过远端网络抽取一键搭建材料。
- 成型后的样板、合成任务、存储、能源和拆卸返还均可走远端网络。
- 链路额外消耗 512 AE/t 和 1 个 AE 频道。
- 远端卸载、断电或频率冲突时自动断开，条件恢复后自动重连。

## 物质构序重写

控制器内置分解标记、蓝图样品和重写产物槽，支持金属、矿物、晶体和有机四类独立构序储量。

规则文件位于：

```text
config/molecularmanipulator/matter_rewrite_rules.json
```

精确物品规则优先于标签规则；携带附魔、命名、耐久、容器内容等自定义数据的物品不会被分解或复制。安装 0～4 张 AE2 加速卡时，每件物品处理周期依次为 20、10、5、2、1 Tick。

## 核心配置

服务端配置文件为 `omnisequence-transfinite-server.toml`，客户端配置文件为 `omnisequence-transfinite-client.toml`。旧版 `molecularmanipulator-*.toml` 会在新文件不存在时自动复制迁移。

| 配置项 | 默认值 | 作用 |
| --- | ---: | --- |
| `pattern_pages` | 20 | 分子构序重写阵列样板页数，每页 36 槽 |
| `build_blocks_per_tick` | 32 | 自动搭建或拆卸每 Tick 处理方块数 |
| `idle_power` | 128 | 分子构序重写阵列待机功耗，单位 AE/t |
| `max_crafting_order_amount` | 1,000,000,000,000 | 单次 AE2 自动合成下单上限 |
| `omni_max_fast_mode` | `SAFE` | 万物演算核心配方树聚合模式 |
| `omni_max_fast_max_nodes` | 8192 | 单次聚合可编译的唯一配方节点上限 |
| `omni_max_fast_compile_budget_ms` | 100 | 聚合图编译超时，超时后回退 AE2 |
| `omni_max_fast_diagnostics` | `false` | 记录聚合耗时和回退原因 |
| `omni_batch_dispatch_enabled` | `true` | 启用兼容供应器的批量材料发配 |
| `omni_batch_allow_substitution_patterns` | `false` | 允许物品替代样板进入批量发配 |
| `omni_provider_max_queued_items` | 65536 | 标准供应器单个自适应推送块的物品总量 |
| `omni_provider_send_operations` | 4096 | 每轮、每种材料的最大传输操作数 |
| `omni_dispatch_target_budget_ms` | 4 | 每核心每 Tick 的目标调度耗时 |
| `omni_dispatch_hard_budget_ms` | 8 | 全服所有万物演算核心共享的紧急耗时上限 |
| `omni_dispatch_max_work_units` | 4096 | 每核心每 Tick 的最大自适应工作单元 |
| `dynamic_effect_level` | 2 | 客户端动态效果：0 关闭、1 精简、2 完整 |

## 安装与构建

将构建好的 JAR 放入服务端和客户端的 `mods` 目录，并安装上表中的必要依赖。

```powershell
./gradlew.bat clean build --no-configuration-cache
```

构建产物：

```text
build/libs/omnisequence-transfinite-1.3.3-hotfix.jar
```

版本变化见 [CHANGELOG.md](CHANGELOG.md)。本项目使用 [MIT License](LICENSE)。