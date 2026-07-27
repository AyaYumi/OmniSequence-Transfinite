# OmniSequence: Transfinite 1.3.4-hotfix 更新说明

这是面向 Minecraft 1.21.1 NeoForge 的紧急启动兼容性修复版本，建议所有已经安装 `1.3.4` 的用户立即更新。

## 修复内容

### 修复部分整合包无法启动

- 修复 `ModConfigSpecMixin` 在 NeoForge 已经加载 `ModConfigSpec` 后才尝试注入，导致游戏启动阶段直接崩溃的问题。
- 对应报错通常包含：

```text
MixinTargetAlreadyLoadedException:
target net.neoforged.neoforge.common.ModConfigSpec was loaded too early
```

- 移除对 NeoForge 核心配置类的 Mixin，不再依赖模组加载顺序。
- 将存档配置迁移改为使用 NeoForge 官方的 `ServerAboutToStartEvent`，同时移除对 `ServerLifecycleHooks` 的核心类注入。

## 兼容性变化

- 提高大型整合包、多配置模组环境及不同 Mixin 加载顺序下的启动兼容性。
- 不影响 AE2、ExtendedAE、万物演算核心、超大合成订单及批量材料发配功能。
- 不修改方块、物品、样板、网络数据或世界存档格式。
- 不新增、删除或重命名配置选项，现有 `1.3.4` 配置可以直接沿用。
- 旧配置文件的备份、迁移与失效选项清理仍由本模组自己的文件级迁移逻辑处理。

## 适用环境

- Minecraft：`1.21.1`
- NeoForge：`21.1.230` 或更高
- Applied Energistics 2：`19.2.17` 或更高
- ExtendedAE：`1.21-2.2.32-neoforge` 或更高
- Advanced AE、ExtendedAE Plus、JEI、AE2WTLib：可选兼容

本版本仅适用于 Minecraft 1.21.1 NeoForge，请勿安装到 Forge 1.20.1 整合包。

## 更新方法

删除旧的：

```text
omnisequence-transfinite-1.3.4.jar
```

只保留：

```text
omnisequence-transfinite-1.3.4-hotfix.jar
```

客户端与服务端需要安装相同版本。请勿同时保留两个 JAR，否则会因重复加载同一 Mod ID 而无法启动。

本次更新不要求删除或重建配置文件，也不要求新建世界。

## 文件校验

```text
SHA-256
428FB1002618C0CD4C88D8C612554D7BDAFE0D5FF213BD067B7106F85F67079C
```
