# AppliedEnhancements dependency / 编译依赖

This branch compiles against `appliedenhancements-1.1.0.jar` in this directory.
The separate prerequisite must include `AelisBatchExecutionContext` and `AelisExactCraftingPlanApi`.
Its classes are not embedded in OmniSequence. Install the same dependency build on
client and server. JARs under `libs/` are ignored by Git.

本分支使用独立的 AppliedEnhancements 1.1.0 JAR 编译，所需接口见上文。
前置单独安装，客户端和服务端使用相同构建，不嵌入本模组产物。
