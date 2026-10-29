# AppliedEnhancements dependency / 编译依赖

This branch compiles against `appliedenhancements-1.1.4.jar` in this directory.
The minimum supported runtime version remains 1.1.2.
The separate prerequisite must include `AelisBatchExecutionContext` and `AelisExactCraftingPlanApi`.
Its classes are not embedded in OmniSequence. Install the same dependency build on
client and server. JARs under `libs/` are ignored by Git.

本分支使用独立的 AppliedEnhancements 1.1.4 JAR 编译，所需接口见上文；运行兼容下限仍为 1.1.2。
前置单独安装，客户端和服务端使用相同构建，不嵌入本模组产物。

Optional CPU interfaces are compiled against these ignored local files:

| File | Tested API |
| --- | --- |
| `neoecoae-21.2.10.jar` | `ECOFastPathDispatchProvider`, workstation parallel intake and job-directed output APIs; minimum supported ECO 21.2.10 |
| `thunderbolt-batch-api.jar` | `IBatchCraftingProvider` (2.0.0-beta.4 through 2.0.3-beta) |
| `data_energistics-3.3.0-api.jar` | `CountedCraftingProviderAdapter` and its registration methods |

The Data Energistics filename is a local alias for the 3.3.0 snapshot `51b5e56`;
the public 3.3.0 release exposes the same contracts. Copy matching real mod JARs
here before building, or run `python .github/scripts/prepare_cpu_apis.py` to fetch
pinned compatible public releases. These are compile-only dependencies;
they are not embedded in the output and are optional at runtime.

上述 CPU JAR 仅提供编译接口，不嵌入产物，也不成为必需运行依赖。兼容测试使用
真实 JAR；数据能源在隔离测试目录启用，不会更改玩家整合包中禁用的模组。

`neoeco_api_build=21.2.10` in `gradle.properties` selects the ECO compile-only JAR;
`neoeco_version=21.2.10` sets the optional runtime version floor. ECO can be absent; if installed, version 21.2.10 or later is required.
The CI helper pins the real
[21.2.10 release](https://github.com/DancingSnow0517/NeoECOAEExtension/releases/tag/21.2.10)
with SHA-256 `56713bef88cc0ba67a95c3ae98805a6a4a9a8868cbecbdfe177c972228141274`
and checks its version metadata, including for an existing local copy.
ECO 21.2.9 and earlier are not supported by this branch.

使用 ECO 21.2.10 编译、测试，安装 ECO 时最低要求 21.2.10；未安装 ECO 时仍可运行。
本模组 CPU 通过 ECO 原生接口推送大型集成工作站与合成子系统，单次、尾单及阻挡回退
也保留任务 UUID；定向返回的副产物和超产余量保存在 CPU 中，只有等待部分结算合成进度。
仅复制真实发布 JAR 作为编译依赖，不复制 ECO 源码或将其类打包。

Isolated ECO tests require an LDLib2 `all` JAR >= 2.2.40. The CPU init scripts
stage selected real JARs under `build/cpu-compat-runtime/<run-name>/`, replace
the test process's LDLib2 dependency, and never write into the source mods directory.
LDLib2 2.2.40 embeds Taffy, Yoga and Kotlin; ECO embeds Registrate. AE2 supplies
GuideME through its runtime dependency. Explicit additional integration prerequisites
can be selected with `cpuCompatExtraMods`.

OmniSequence uses `ordering="NONE"` for ECO, enforcing its optional 21.2.10
version floor without introducing a reverse loading-order edge.

The isolated Thunderbolt suite uses `thunderbolt-2.0.0-beta.4.jar` separately so
tests match the installed pack while compilation can use the compatible public API.
