# Local Forge dependencies

Minecraft 1.20.1 / Java 17, OmniSequence 2.0.13-forge requires AppliedEnhancements
1.1.2-forge or later on the client and server. The default build uses the independently
installed `appliedenhancements-1.1.4-forge.jar`; place it in this directory before building.
To compile against another compatible API build, set `-Papplied_enhancements_api_build=<version>`
and supply that matching local JAR. The runtime minimum is a separate metadata property,
`applied_enhancements_version`.

Both sides require the cycle transaction, exact-plan and smart-doubling APIs.
AE2 15.4.10 is resolved from Modrinth Maven. ExtendedAE 1.20-1.4.20-forge is
resolved from CurseMaven (`curse.maven:extendedae-892005:8843577`), because that
release is absent from Modrinth Maven. The artifact is byte-identical to the
[upstream GitHub release](https://github.com/GlodBlock/ExtendedAE/releases/tag/1.20-1.4.20-forge)
and the reference pack's JAR, with SHA-256
`83bcdee0fc7120bb04e020f3ad28bf486ba9124491a717eb8d2d592d946c100f`.
No local ExtendedAE JAR is required. Optional ECO integration compiles against
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

Optional ExtendedAE Plus integration targets the real Forge **1.6.2** JAR
(`extendedae_plus-1.6.2.jar`), supplied separately for addon integration tests.
Native Sequence Array auto upload and one-click pattern migration do not require
this addon. Forge 1.6.2 has no newer named-provider API; no NeoForge 1.6.4.1
renamed-provider automatic-selection compatibility is implied.

原生自动上传和一键样板迁移无需 EAEP。可选兼容使用真实 Forge 1.6.2；
该版本没有新版 named-provider API，不承诺新版的重命名供应器自动选择行为。

可选 CPU 接口声明仅用于编译，不包含在发布 JAR 中；实际模组需单独安装匹配的 Forge 版本。
FTB Teams 可选，未安装时使用玩家 UUID 绑定；旧版无绑定黑白洞需要重新放置。
