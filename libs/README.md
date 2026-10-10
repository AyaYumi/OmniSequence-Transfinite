# Local Forge dependencies

Minecraft 1.20.1 / Java 17, OmniSequence 2.0.8-forge requires the independently installed
`appliedenhancements-1.1.0-forge.jar`. Place it in this directory before building.
Both sides require the matching Forge mod with the cycle transaction, exact-plan and smart-doubling APIs.

Optional CPU adapters compile against Java 17 ABI declarations under `src/optionalCpuApi/java`.
Those declarations are excluded from runtime classpaths and release JARs. Compatible ECO,
Thunderbolt and Data Energistics implementations must be installed separately; older builds
without the referenced provider API use their ordinary dispatch path. Never install NeoForge JARs on Forge.

可选 CPU 接口声明仅用于编译，不包含在发布 JAR 中；实际模组需单独安装匹配的 Forge 版本。
FTB Teams 可选，未安装时使用玩家 UUID 绑定；旧版无绑定黑白洞需要重新放置。
