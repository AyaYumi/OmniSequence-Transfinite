# OmniSequence: Transfinite

OmniSequence: Transfinite is an endgame Applied Energistics 2 and ExtendedAE addon for
Minecraft 1.20.1 on Forge. It adds molecular rewriting, transfinite crafting storage,
large-scale parallel processing, quantum-linked multiblocks and accelerated crafting-plan
calculation designed for very large automation networks.

## Main Features

- Sequence Array multiblock with 720 default pattern slots, configurable matter rewriting
  and persistent output routing.
- Omni Computation Core with effectively unlimited logical crafting storage and parallelism.
- Long-value AE2 crafting orders and long-value display for supported infinite item sources.
- Entangled quantum links for remote AE network access.
- Construction-only AE access before multiblock formation and full functionality after formation.
- Safe demand aggregation and duplicate subtree merging with automatic AE2 fallback for unsafe recipes.
- Optional Advanced AE visibility and compatibility without making Advanced AE a required dependency.
- Original black-purple crystal models, textures, animated effects and redesigned machine interfaces.

## Requirements

- Minecraft 1.20.1
- Forge 47.4.10 or later
- Applied Energistics 2 15.4.10
- ExtendedAE 1.20-1.4.12-forge
- Dependencies required by ExtendedAE, including Glodium

## Optional Integrations

- A compatible Forge 1.20.1 release of Advanced AE
- ExtendedAE Plus
- JEI

## Installation

Install the required dependencies, then place the OmniSequence: Transfinite JAR in the
`mods` directory on both the client and server. Client and server versions must match.

The standalone Molecular Sequence Rewrite Array block is not included in the Forge 1.20.1
build. Existing copies of that block are removed as missing blocks when an old world is loaded;
use the Sequence Array multiblock for pattern and matter-rewriting functionality.

---

# 万象构序：超限

《万象构序：超限》是面向 Minecraft 1.20.1 Forge 的 AE2 / ExtendedAE 后期附属模组，
提供分子重写、超限合成存储、大规模并行构序、缠绕态量子链路和大型配方计算优化。

安装 AE2、ExtendedAE 及其必要前置后，将本模组 JAR 同时放入客户端与服务端的 `mods`
目录即可。Advanced AE、ExtendedAE Plus 与 JEI 均为可选兼容项。

Forge 1.20.1 版不再包含独立的 `分子构序重写阵列` 单方块；旧世界中的该方块会作为缺失方块移除，
样板与物质重写功能由 `构序阵列` 多方块提供。
