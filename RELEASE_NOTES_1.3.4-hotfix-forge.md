# OmniSequence: Transfinite 1.3.4-hotfix-forge 更新说明

这是面向 Minecraft 1.20.1 Forge 的紧急启动兼容性修复版本，建议所有已经安装 `1.3.4-forge` 的用户更新。

## 修复内容

### 修复部分整合包无法启动

- 修复 `ModConfigSpecMixin` 在 Forge 已经加载 `ForgeConfigSpec` 后才尝试注入，导致游戏启动阶段直接崩溃的问题。
- 对应报错通常包含：

```text
MixinTargetAlreadyLoadedException:
target net.minecraftforge.common.ForgeConfigSpec was loaded too early
```

- 移除对 Forge 核心配置类的 Mixin，不再依赖模组加载顺序。
- Forge 会先加载世界服务端配置、再发送 `ServerAboutToStartEvent`。因此 Forge
  版保留经过验证的 `ServerLifecycleHooks.handleServerAboutToStart` 入口注入，
  只负责在 Forge 选择配置文件前迁移和重建旧配置，避免首次启动忽略旧世界数值。

## 兼容性变化

- 提高大型整合包、多配置模组环境及不同 Mixin 加载顺序下的启动兼容性。
- 不影响 AE2、ExtendedAE、万物演算核心、超大合成订单及批量材料发配功能。
- 不修改方块、物品、样板、网络数据或世界存档格式。
- 不新增、删除或重命名配置选项，现有 `1.3.4-forge` 配置可以直接沿用。
- 旧配置文件的备份、迁移与失效选项清理仍由本模组自己的文件级迁移逻辑处理。
- 不再对已可能提前加载的 `ForgeConfigSpec` 注入；保留的生命周期注入目标在
  Forge 服务器启动路径中尚未提前加载，并且仅执行文件级预迁移。

## 适用环境

- Minecraft：`1.20.1`
- Forge：`47.4.10` 或更高
- Applied Energistics 2：`15.4.10`
- ExtendedAE：`1.20-1.4.12-forge`
- Advanced AE、ExtendedAE Plus、JEI、AE2WTLib：可选兼容

本版本仅适用于 Minecraft 1.20.1 Forge，请勿安装到 NeoForge 1.21.1 环境。Forge 版仍不注册独立的“分子构序重写阵列”单方块，相关功能继续由“构序阵列”多方块提供。

## 更新方法

删除旧的：

```text
omnisequence-transfinite-1.3.4-forge.jar
```

只保留：

```text
omnisequence-transfinite-1.3.4-hotfix-forge.jar
```

客户端与服务端需要安装相同版本。请勿同时保留两个 JAR，否则会因重复加载同一 Mod ID 而无法启动。

本次更新不要求删除或重建配置文件，也不要求新建世界。
