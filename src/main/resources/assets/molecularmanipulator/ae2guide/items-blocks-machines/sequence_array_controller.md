---
navigation:
  parent: omnisequence-index.md
  title: "Sequence Array Controller"
  icon: molecularmanipulator:molecular_center_controller
  position: 1030
item_ids:
- molecularmanipulator:molecular_center_controller
- molecularmanipulator:molecular_center_casing
- molecularmanipulator:molecular_center_glass
- molecularmanipulator:molecular_center_coil
- molecularmanipulator:molecular_center_stabilizer
- molecularmanipulator:molecular_center_core
---

# Sequence Array Controller

## Purpose: organize recipes, then craft to order or restock automatically

The Sequence Array is a large **pattern library**. Patterns are recipe cards made in an encoding terminal, not ingredients or products. By default, the array holds **7200 patterns**, with built-in recipe execution and **nine independent restocking slots**.

<Row>
<BlockImage id="molecularmanipulator:molecular_center_controller" scale="4" />
<ItemImage id="ae2:crafting_pattern" scale="4" />
<ItemImage id="ae2:smithing_table_pattern" scale="4" />
<ItemImage id="ae2:stonecutting_pattern" scale="4" />
</Row>

The controller handles the three recipe types shown: crafting, smithing and stonecutting. The main library tells terminals what can be made, the array's built-in crafting makes it, and ME storage holds the actual ingredients and products.

| Your goal | Where the pattern belongs |
| --- | --- |
| Request a specific quantity from a terminal | Main pattern library. The network also needs a **crafting CPU**, the device that accepts and arranges orders. |
| Automatically replenish everyday products | The nine slots on the Stocking page. The array uses ingredients already in ME. |
| Run a furnace or another external machine | Put a processing pattern in a Pattern Provider connected to that machine, not in the array. |

## Prepare

- Complete one round of **Tier III: Sequence Engineering** in the [Fabrication Well](matter_fabrication_research.md), then fabricate the controller and structure parts. This route uses ExtendedAE materials.
- Clear a **61 × 61-block area, 29 blocks tall**. The controller is horizontally central, not on the bottom: leave **6 blocks below** and **22 above** it, and check the projection before committing to the site. No solid foundation is required.
- ME power, item storage and free **channels**, the network connection allowances for devices. Terminal orders also need an available crafting CPU.
- The complete material list from JEI's structure page. The items below are array structure parts, not another item-storage system.

<ItemGrid>
<ItemIcon id="molecularmanipulator:molecular_center_controller" />
<ItemIcon id="molecularmanipulator:molecular_center_casing" />
<ItemIcon id="molecularmanipulator:molecular_center_glass" />
<ItemIcon id="molecularmanipulator:molecular_center_coil" />
<ItemIcon id="molecularmanipulator:molecular_center_stabilizer" />
<ItemIcon id="molecularmanipulator:molecular_center_core" />
</ItemGrid>

<RecipeFor id="molecularmanipulator:molecular_center_controller" fallbackText="Check JEI for the controller's Fabrication Well recipe and the structure material list." />

## Steps: build it, then test one pattern

1. Place the controller, open its interface and enable **Block Projection** on the **Control** page. Check that the structure will not intersect terrain or existing machines.
2. Gather the listed parts. Connect an ME network for construction supplies, or carry them in your inventory. Select **Build**; construction uses your inventory before ME stock.
3. Wait for construction to finish. Check that the machine reports both **formed** and **online**. Fix missing blocks, power or channels before adding patterns.
4. Insert an “oak log → oak planks” crafting pattern into the main library. Store at least **4 logs** in ME.
5. Request **16 planks** from a terminal on the same network and confirm with an available crafting CPU. The array performs the recipe internally and returns the products to ME.

The main library defaults to **200 pages of 36 slots**. Servers can change the page count, up to 300. Use pages and search in the controller, or organize it through a Pattern Access Terminal on the same network. The structure's **14 quantum crystals** hold the patterns; finish construction or crystal recovery before rearranging them.

### Prefer new patterns to go straight into the library?

Enable **Auto upload** on Control. Crafting, smithing and stonecutting patterns newly encoded in native AE2 Pattern Encoding Terminals on the same network then go directly into the main library. **New arrays have this off by default.** Duplicate recipes return a blank pattern. If the library is full or the array is unavailable, the encoded pattern stays in the terminal. Processing patterns are not uploaded.

Use **Migrate patterns** to collect existing patterns from accessible pattern slots and mounted storage cells on the same network. This runs only when clicked. Processing patterns stay in place; duplicate physical patterns remain separate items, and anything that does not fit stays at its source.

### Need automatic restocking?

Open **Stocking**, put a pattern in one of the nine dedicated slots and select that slot. Set the product target and ingredient reserves, click **Set**, then start the slot. Try “64 planks, reserve 8 logs” first; **a product target of 0 means unlimited**. See [Molecular Auto Crafter](molecular_auto_crafter.md) for the full workflow. Array restocking delivers to ME.

## What success looks like

- The controller reports formed and online, and the Pattern Access Terminal can find patterns in its main library.
- The 16-plank test order finishes. Products appear in ME, not in pattern slots.
- Restocking runs below the target, pauses at the target and resumes when you remove products.

## Troubleshooting

| What you see | What to do |
| --- | --- |
| Construction stops, or the structure is incomplete | Follow the message: add materials, clear projection conflicts and keep construction chunks loaded. Supply parts from your inventory or the construction network. |
| Formed but offline | Check ME power and channels. Default base idle consumption is **128 AE/t**. |
| A pattern is inserted, but the terminal cannot order it | Check that it is in the main library, not a restocking slot, and that the network has an idle crafting CPU. |
| Newly encoded patterns do not upload | Enable Auto upload. Use a native AE2 encoding terminal on the same network, with room in the library and the array ready. |
| A Storage Bus cannot reach restocking slots | Storage Buses access the main library only. Manage restocking and quantum slots in the controller. |
| An order produces nothing | Read missing ingredients in Crafting Status, and leave ME space for products and returned items. |

## Next steps

For a remote connection, use paired **Quantum Entangled Singularities**: one in the controller's quantum slot and its partner in a powered AE2 quantum bridge. The link needs an additional **512 AE/t and one channel**. It can also supply construction materials before formation.

Before relocating, settle external crafting orders, then use **Reclaim Package** to pack the whole machine. Click once, then confirm within **three seconds**. The package includes the pattern library and internal contents; see [Folded Buildings and Relocation](folded_buildings.md) for placement.

Colors and local effects settings do not change production. For more order-handling capacity, continue to the [Omni Computation Core](omni_computation_core.md). For compact restocking, use a [Molecular Auto Crafter](molecular_auto_crafter.md).
