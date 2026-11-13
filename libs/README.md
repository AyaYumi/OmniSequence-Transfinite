# Local Forge dependencies

Minecraft 1.20.1 / Java 17, OmniSequence 20.3.0.1 requires AppliedEnhancements
1.1.2-forge or later on the client and server. The default build uses the independently
installed `appliedenhancements-1.1.4-forge.jar`; place it in this directory before building.
To compile against another compatible API build, set `-Papplied_enhancements_api_build=<version>`
and supply that matching local JAR. The runtime minimum is a separate metadata property,
`applied_enhancements_version`.

Both sides require the cycle transaction, exact-plan and smart-doubling APIs.
AE2 15.4.10 is resolved from Modrinth Maven. ExtendedAE 1.20-1.4.20-forge is
resolved from CurseMaven (`curse.maven:extendedae-892005:8843577`); this release
is absent from Modrinth Maven. No local ExtendedAE JAR is required. See the
[upstream GitHub release](https://github.com/GlodBlock/ExtendedAE/releases/tag/1.20-1.4.20-forge).
Optional ECO integration compiles against
the actual NeoECOAE 20.5.0 Forge artifact (`neoeco_api_build`), not a local stub.
The separate optional runtime minimum (`neoeco_version`) is 20.4.2. Older runtimes
use ordinary AE dispatch; native batch/exact integration remains gated to 20.5.0+.
The `eco-legacy` test suite compiles its old CPU fixture against the real 20.4.2 API
and runs that version independently of the production compile-only API.

Thunderbolt and Data Energistics adapters compile against Java 17 ABI declarations
under `src/optionalCpuApi/java`. Those declarations are excluded from runtime classpaths
and release JARs. Compatible Forge implementations must be installed separately;
older builds without the referenced provider API use their ordinary dispatch path.
Never install NeoForge JARs on Forge.

Optional ExtendedAE Plus automatic destination-selection and well-upload integration
targets Forge **1.6.4**, including renamed assemblies. Native Sequence Array auto upload,
one-click pattern migration and well uploads do not require it. The separate `adaptive`
game-test fixture uses Forge **1.6.2** (`extendedae_plus-1.6.2.jar`).

原生自动上传、一键样板迁移和构筑井上传无需 EAEP。可选的自动目标选择与上传联动
对应 Forge 1.6.4，可识别重命名总成；`adaptive` 游戏测试使用的旧夹具仍为 Forge 1.6.2。

可选 CPU 接口声明仅用于编译，不包含在发布 JAR 中；实际模组需单独安装匹配的 Forge 版本。
FTB Teams 可选，未安装时使用玩家 UUID 绑定；旧版无绑定黑白洞需要重新放置。
