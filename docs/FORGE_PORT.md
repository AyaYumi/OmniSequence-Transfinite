# Forge 1.20.1 port / 移植说明

The `1.20.1-forge` branch ports OmniSequence 2.0.1 to **2.0.1-forge**, together with AppliedEnhancements **1.0.6-forge**. It targets Java 17 bytecode, Forge 47.4.10 or newer, and AE2 15.4.10. The local integration baseline is Forge 47.4.20.

| Boundary | Forge implementation |
| --- | --- |
| Networking | Direction-bound SimpleChannel messages; server validation, main-thread handling, bounded search-index decoding and generation/revision checks remain in place. |
| Item/fluid persistence | 1.20.1 NBT APIs replace data components and registry-aware serializers. Portable contents retain their decompression budget. |
| 2.0.1 input/lookup parity | Generic AEKey recipes use a JSON-to-NBT bridge for AE2 15. A RecipeManager accessor observes the replaced byName snapshot so the output/research index survives repeated lookups and invalidates on data reload. |
| Mixin mappings | The accessor's SRG reference map is declared in the mixin config, tracked as compiler output and copied into runtime resources. Incremental builds retain it; development runs remap it to official names. |
| Recipe API | Recipes carry their own `id()` / `getId()` and use Forge `fromJson`, `fromNetwork`, and `toNetwork`. `value()` remains available on the custom recipe records. Data directories use `recipes/`, `loot_tables/`, and plural tag directories. |
| External item IDs | ExtendedAE uses the `expatternprovider` namespace and mod ID in this version. AdvancedAE remains `advanced_ae`. |
| Optional recipes | Forge `conditions` and `forge:item_exists` prevent absent dependency-only items from breaking data reload. |
| UI | All six machine screens and JEI pages follow the 1.21.1 branch's layouts using the native Forge console theme. Minecraft/AE2 retain container events and synchronization. Enabled operations control their button highlights; output directions show cached neighbor thumbnails. XML overlays and LDLib2 are removed; paging, search, research, draft preservation and high-GUI-scale input handling remain available. |
| Rendering | Vertices are explicitly completed, baked-model data uses Forge builders, and full multiblock bounds are exposed through block entities for frustum culling. Large effects use a 384-block view range. |
| Capabilities | Item/fluid ports cache LazyOptional capabilities and invalidate/recreate them with the block-entity lifecycle. |
| Chunk tickets | ForgeChunkManager restores controller-owned ticking tickets and releases only that controller's tickets on actual removal. |
| Virtual CPUs | The earlier Forge readiness safeguard is retained: a CPU must be live, idle and have an empty return inventory before reuse or removal. |

## Materials / 材料差异

Some ExtendedAE 1.21.1 materials do not exist in its Forge release. The port keeps every OmniSequence machine and all three research definitions, using available Forge materials for their costs:

| 1.21.1 material | Forge cost equivalent |
| --- | --- |
| Entro crystal / dust / block | AE2 fluix crystal / dust / block |
| Concurrent processor | AE2 engineering processor |
| Machine frame | ExtendedAE assembler matrix frame |
| Entro ingot / infused-entro ingot tag | Netherite ingot / Forge netherite-ingot tag, consistent with the older Forge machines' endgame material tier |

Fabrication recipes whose output is itself a missing ExtendedAE-only item stay conditional; they do not create substitute high-value resources. KubeJS can replace the port's recipe costs through `ServerEvents.recipes`.

1.20.1 配方使用 `nbt` 表达物品附加数据，不能直接照搬 1.21.1 的数据组件。研究字段的大型数量应继续使用十进制字符串，避免 JavaScript 精度损失。研究配方保留原 ID、轮次、时长、并行度与解锁关系；不存在的前置专属产物由条件判断跳过，不会生成替代的高价值物品。

See [dependency setup](../libs/README.md) for local development artifacts. Required mods are installed separately. LDLib2 is not used by the console UI. See [UI design](UI_DESIGN.md).

## Validation / 验证

Run `gradlew test build` with Java 17. Before upgrading a pack, back up its old JARs and configuration, then keep one enabled version of each mod.

Use a separate creative world for runtime checks: open each machine menu at the pack's normal GUI scale, check pattern paging/search and terminal filters, submit an ordinary crafting order, exercise item/fluid ports, build the three multiblocks, and save/reopen the world. Test AELIS with its option enabled, then restore the pack's original settings. Check exact input/output quantities and server logs as well as screenshots. An idle tick-time sample is not a sustained production-load benchmark.

The Forge controller loot table uses `minecraft:copy_nbt` and `BlockEntityTag`; the NeoForge `copy_custom_data` function is not valid on 1.20.1. Runtime tests are described in [the regression guide](../tools/gametest/README.md).
