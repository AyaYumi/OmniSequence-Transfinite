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
- molecularmanipulator:sequence_wireless_hub
---

# Sequence Array Controller

The Sequence Array stores up to **7200 patterns** by default and performs their recipes internally. Make patterns in a Pattern Encoding Terminal. Its **nine independent restocking slots** can also keep products stocked without terminal orders.

<Row>
<BlockImage id="molecularmanipulator:molecular_center_controller" scale="4" />
<ItemImage id="ae2:crafting_pattern" scale="4" />
<ItemImage id="ae2:smithing_table_pattern" scale="4" />
<ItemImage id="ae2:stonecutting_pattern" scale="4" />
</Row>

The array accepts crafting, smithing and stonecutting patterns. Its main library makes those recipes available in terminals, and the array performs the work. Store ingredients and products in ME.

| Your goal | Where the pattern belongs |
| --- | --- |
| Request a specific quantity from a terminal | Main pattern library. The network also needs a **crafting CPU**, the device that accepts and arranges orders. |
| Automatically replenish everyday products | The nine slots on the Stocking page. The array uses ingredients already in ME. |
| Run a furnace or another external machine | Put a processing pattern in a Pattern Provider connected to that machine, not in the array. |

## Prepare

- Complete one round of **Tier III: Sequence Engineering** in the [Fabrication Well](matter_fabrication_research.md), then fabricate the controller and structure parts. This route uses ExtendedAE materials.
- Clear a **61 × 61-block area, 29 blocks tall**. The controller sits at the horizontal center, **6 blocks above** the bottom, with **22 blocks above** it. Check the projection before choosing the site. No solid foundation is required.
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

## Build and try a pattern

1. Place the controller, open its interface and enable **Block Projection** on the **Control** page. Check that the structure will not intersect terrain or existing machines.
2. Gather the listed parts. Connect an ME network for construction supplies, or carry them in your inventory. Select **Build**; construction uses your inventory before ME stock.
3. Wait for construction to finish. Check that the machine reports both **formed** and **online**. Fix missing blocks, power or channels before adding patterns.
4. Insert an "oak log → oak planks" crafting pattern into the main library. Store at least **4 logs** in ME.
5. Request **16 planks** from a terminal on the same network and confirm with an available crafting CPU. The array performs the recipe internally and returns the products to ME.

The main library defaults to **200 pages of 36 slots**. Servers can change the page count, up to 300. Use pages and search in the controller, or organize it through a Pattern Access Terminal on the same network. The structure's **14 quantum crystals** hold the patterns; finish construction or crystal recovery before rearranging them.

### Upload new patterns automatically

Enable **Auto upload** on Control. Crafting, smithing and stonecutting patterns newly encoded in native AE2 Pattern Encoding Terminals on the same network then go directly into the main library. **New arrays have this off by default.** Duplicate recipes return a blank pattern. If the library is full or the array is unavailable, the encoded pattern stays in the terminal. Processing patterns are not uploaded.

Use **Migrate patterns** to collect existing patterns from accessible pattern slots and mounted storage cells on the same network. This runs only when clicked. Processing patterns stay in place; duplicate physical patterns remain separate items, and anything that does not fit stays at its source.

### Automatic restocking

Open Stocking, put a pattern in one of the nine dedicated slots and select it. Set the product target and ingredient reserves, click Set, then start the slot. Try "64 planks, reserve 8 logs" first. A product target of **0 means unlimited**. See [Molecular Auto Crafter](molecular_auto_crafter.md) for the full workflow. Array restocking delivers to ME.

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

## Sequence Wireless Hub: one wired block, many remote machines

The Sequence Wireless Hub connects to your main ME network by cable. It provides native AE2 links to multiple remote machines, across dimensions and without a distance limit. Bind each receiver through its right sidebar. You do not need a card or an occupied machine slot.

<BlockImage id="molecularmanipulator:sequence_wireless_hub" scale="4" />

Supported receivers are the Matter Fabrication Well, Sequence Array, Molecular Sequence Rewrite Array, Molecular Auto Crafter, Star Computation Engine, multiblock Omni Computation Core, Event Horizon Singularity Hub and Compact Singularity Hub. The Transfinite Compute Nexus is not supported. The connection button is available on their tabs and autocrafting configuration pages. On fabrication ports and Pattern Assemblies, it manages the well controller's binding. Construction, formation, research and channel requirements still apply.

With FTB Teams, the list shows only loaded hubs owned by the receiver's placement team. Members of that team and administrators can manage the connection. Opening an interface does not change ownership, and cross-team bindings cannot connect. Without FTB Teams, ownership is per player. Normal receiver access restrictions still apply.

1. Remove the receiver's external ME cables. If it has a quantum slot, remove its singularity too. These connection methods cannot operate together.
2. Click **Remote connection** in the machine interface's right sidebar.
3. Select a same-team loaded hub. Check its name, dimension, coordinates and online text, then click **Connect selected hub**. Selecting a row alone does not change the binding.

The upper section shows the current binding and actual link status. Click Disconnect to unbind. Each page shows three hubs. Browse with the page buttons or mouse wheel. Tab and arrow keys focus controls, Enter activates them and Esc closes the panel. Purple active 3D thumbnails are decorative; the online text tells you whether the connection is working.

An AE2 Memory Card is an optional backup way to bind. Sneak-right-click the hub with the card, then right-click the machine. To unbind, sneak-right-click the machine with a selected card. You do not need to keep the card afterward, and it cannot bypass team checks.

Unlock the hub with Tier III: Sequence Engineering research. Its Matter Fabrication Well recipe uses **8 AE2 Quantum Rings + 1 Quantum Link Chamber + 4 Star Chart Processors**, taking **400 ticks at 512 AE/t**.

<RecipeFor id="molecularmanipulator:sequence_wireless_hub" fallbackText="Check JEI for the Sequence Wireless Hub's Fabrication Well recipe, unlocked by Tier III: Sequence Engineering." />

The hub is a native AE2 dense node with **32 channels by default**, following AE2 configuration. That bandwidth does not guarantee connections for 32 devices, because other nodes inside a machine may also use channels.

Budget **64 AE/t** for hub idle power, plus **512 AE/t per established wireless link** and the machines' own power costs.

Keep both the hub and receiver chunks loaded. Wireless does not load chunks. Existing multiblock chunk-loading settings still apply.

Normal machine saves, removal recovery and building packaging retain receiver bindings. The hub keeps its identity across world restarts. Breaking, replacing or moving the hub gives it a new identity, so select it again and rebind each receiver.

Right-click the hub with an empty hand to see status, link count and usage. Its signal markings are black offline and purple when powered, wired and online. It emits **0 world light** and does not light its surroundings.

## Next steps

You can also use paired Quantum Entangled Singularities for a remote connection while the wireless hub is disconnected. Put one in the controller's quantum slot and its partner in a powered AE2 quantum bridge. This link needs an additional **512 AE/t and one channel** and can supply construction materials before formation.

Before relocating, settle external crafting orders, then use Reclaim Package to pack the whole machine. Click once, then confirm within **three seconds**. The package retains the pattern library, internal contents and wireless binding. See [Folded Buildings and Relocation](folded_buildings.md) for placement. If you also move or replace the wireless hub, rebind the array afterward.

Colors and local effects settings do not change production. For more order-handling capacity, continue to the [Omni Computation Core](omni_computation_core.md). For compact restocking, use a [Molecular Auto Crafter](molecular_auto_crafter.md).
