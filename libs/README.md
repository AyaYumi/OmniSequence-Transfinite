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
| `neoecoae-21.2.10.jar` | `ECOFastPathDispatchProvider`, workstation parallel intake and job-directed output APIs; minimum supported ECO 21.2.9 |
| `thunderbolt-batch-api.jar` | `IBatchCraftingProvider` (2.0.0-beta.4 through 2.0.3-beta) |
| `data_energistics-3.3.0-api.jar` | `CountedCraftingProviderAdapter` and its registration methods |

The Data Energistics filename is a local alias for the 3.3.0 snapshot `51b5e56`;
the public 3.3.0 release exposes the same contracts. Copy matching real mod JARs
here before building, or run `python .github/scripts/prepare_cpu_apis.py` to fetch
pinned compatible public releases. These are compile-only dependencies;
they are not embedded in the output and are optional at runtime.

上述 CPU JAR 仅提供编译接口，不嵌入产物，也不成为必需运行依赖。

`neoeco_api_build=21.2.10` in `gradle.properties` selects the ECO compile-only JAR;
`neoeco_version=21.2.9` remains the optional runtime version floor. ECO can be absent.
The CI helper pins the real
[21.2.10 release](https://github.com/DancingSnow0517/NeoECOAEExtension/releases/tag/21.2.10)
with SHA-256 `56713bef88cc0ba67a95c3ae98805a6a4a9a8868cbecbdfe177c972228141274`
and checks its version metadata, including for an existing local copy.
Use `-Pneoeco_api_build=21.2.9` with the corresponding real JAR to check the older baseline.
Older ECO JARs may remain here but are not selected by the default compilation.

默认使用 ECO 21.2.10 编译、测试，兼容下限仍为 21.2.9，未安装 ECO 时仍可运行。
本模组 CPU 通过 ECO 原生接口推送大型集成工作站与合成子系统，单次、尾单及阻挡回退
也保留任务 UUID；定向返回的副产物和超产余量保存在 CPU 中，只有等待部分结算合成进度。
仅复制真实发布 JAR 作为编译依赖，不复制 ECO 源码或将其类打包。

The unmodified ECO 21.2.9 release declares `AFTER molecularmanipulator`.
OmniSequence uses `ordering="NONE"` for ECO, preserving its optional version
floor without introducing a reverse loading-order edge.

Fabrication wells use their native durable-queue FastPath before ECO's generic
AE2 processing-pattern scaling ramp. This preserves full long-sized intake;
ECO still owns material/energy accounting, exact dispatch and blocking checks.

构筑井优先使用自己的持久化队列 FastPath，避免先被 ECO 通用加工倍率爬坡截成小批次；
材料、能量、精确数量及阻挡校验仍由 ECO 事务控制。
