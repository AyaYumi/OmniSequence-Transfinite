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

The Sequence Array stores up to 7200 patterns by default and performs crafting, smithing and stonecutting internally. Insert patterns from a Pattern Encoding Terminal, then request products through ME. Its nine restocking slots can also keep products stocked automatically.

<Row>
<BlockImage id="molecularmanipulator:molecular_center_controller" scale="4" />
<ItemImage id="ae2:crafting_pattern" scale="4" />
<ItemImage id="ae2:smithing_table_pattern" scale="4" />
<ItemImage id="ae2:stonecutting_pattern" scale="4" />
</Row>

Keep ingredients and products in ME. Choose the pattern inventory for the work you want:

| Your goal | Where the pattern belongs |
| --- | --- |
| Request a specific quantity from a terminal | Main pattern library. The network also needs a crafting CPU to manage orders. |
| Automatically replenish products | The nine slots on Stocking. The array uses ingredients already in ME. |
| Run a furnace or another external machine | A Pattern Provider connected to that machine. The array cannot perform these processing patterns. |

## Requirements

- Complete one round of Tier III: Sequence Engineering in the [Fabrication Well](matter_fabrication_research.md), then fabricate the controller and structure parts. This route uses ExtendedAE materials.
- Clear a 61 × 61-block area, 29 blocks tall. The controller sits at the horizontal center, 6 blocks above the bottom, with 22 blocks above it. Check the projection before choosing the site. No solid foundation is required.
- Prepare ME power, item storage and free channels. Terminal orders also need an available crafting CPU. The array's default idle draw is 128 AE/t.
- Gather the complete material list from JEI's structure page. The parts below form the array; they do not provide ME item storage.

<ItemGrid>
<ItemIcon id="molecularmanipulator:molecular_center_controller" />
<ItemIcon id="molecularmanipulator:molecular_center_casing" />
<ItemIcon id="molecularmanipulator:molecular_center_glass" />
<ItemIcon id="molecularmanipulator:molecular_center_coil" />
<ItemIcon id="molecularmanipulator:molecular_center_stabilizer" />
<ItemIcon id="molecularmanipulator:molecular_center_core" />
</ItemGrid>

<RecipeFor id="molecularmanipulator:molecular_center_controller" fallbackText="Check JEI for the controller's Fabrication Well recipe and the structure material list." />

## Build the array and request products

1. Place the controller and enable Block Projection on Control. Check for conflicts with terrain or existing machines.
2. Gather the listed parts in your inventory or an ME network connected to the controller. Select Build. Construction uses your inventory first, then ME.
3. Wait for construction to finish and check that the array reports formed and online. Fix missing blocks, power or channels before adding patterns.
4. Insert an oak log to oak planks crafting pattern into the main library. Store at least 4 logs in ME.
5. Request 16 planks from a terminal on the same network and confirm with an available crafting CPU. The array turns the 4 logs into 16 planks and returns them to ME.

## Managing patterns

The main library defaults to 200 pages of 36 slots. Servers can change the page count, up to 300. Use pages and search in the controller, or a Pattern Access Terminal on the same network. The structure's 14 quantum crystals hold the patterns; finish construction or crystal recovery before rearranging them.

### Upload newly encoded patterns

Enable Auto upload on Control. New arrays have this off by default. Crafting, smithing and stonecutting patterns newly encoded in native AE2 Pattern Encoding Terminals on the same network then go directly into the main library.

Duplicate recipes return a blank pattern. If the library is full or the array is unavailable, the encoded pattern stays in the terminal. Processing patterns stay in the terminal too.

### Move existing patterns into the array

Click Migrate patterns to collect existing patterns from accessible pattern slots and mounted storage cells on the same network. Migration runs only when clicked. Processing patterns stay in place, duplicate physical patterns remain separate items, and anything that does not fit stays at its source.

### Automatic restocking

Open Stocking, insert a pattern in one of the nine dedicated slots and select it. Set the product target and ingredient reserves, click Set, then start the slot. For example, set a target of 64 planks and a reserve of 8 logs. The slot crafts below the target, pauses when it is reached and resumes as you use the products.

A product target of 0 allows unlimited production. Restocking delivers to ME. See [Sequence Auto Crafter](molecular_auto_crafter.md) for the full workflow.

## Troubleshooting

| What you see | What to do |
| --- | --- |
| Construction stops, or the structure is incomplete | Add missing materials, clear projection conflicts and keep construction chunks loaded. Supply parts from your inventory or the construction network. |
| Formed but offline | Check ME power and channels. The base idle draw is 128 AE/t by default. |
| A pattern is inserted, but the terminal cannot order it | Put it in the main library, not a restocking slot, and connect an idle crafting CPU. |
| Newly encoded patterns do not upload | Enable Auto upload. Use a native AE2 encoding terminal on the same network, with room in the library and the array ready. |
| A Storage Bus cannot reach restocking slots | Storage Buses access the main library only. Manage restocking and quantum slots in the controller. |
| An order produces nothing | Read missing ingredients in Crafting Status, and leave ME space for products and returned items. |

## Sequence Wireless Hub

Cable a Sequence Wireless Hub to your main ME network to connect several remote machines. Those machines can be any distance away, even in other dimensions.

<BlockImage id="molecularmanipulator:sequence_wireless_hub" scale="4" />

Craft one hub with **1 AE2 Quantum Link Chamber** in the center of a crafting table and **8 Quantum Rings** around it. You can make it before the Star Orrery without research.

<RecipeFor id="molecularmanipulator:sequence_wireless_hub" fallbackText="Crafting table: put 1 AE2 Quantum Link Chamber in the center and 8 Quantum Rings around it. Available before the Star Orrery, with no research unlock." />

### Supported machines

The hub supports the Matter Fabrication Well, Sequence Array, Molecular Sequence Rewrite Array, Sequence Auto Crafter, Star Computation Engine, multiblock Omni Computation Core, Event Horizon Singularity Hub and Compact Singularity Hub. Use a cable or another external ME device for the Transfinite Compute Nexus. The well's ports and Pattern Assemblies share its controller's binding. Each machine still has its own building, research and channel requirements.

### Bind a receiver

1. Disconnect external ME cables from the receiver and empty its quantum slot if it has one.
2. Open **Remote connection** in the right sidebar of a machine tab or its autocrafting settings.
3. Select a hub. Check its name, dimension, coordinates and online status, then click **Connect selected hub** to bind it.

With FTB Teams, the list shows loaded hubs owned by the team that placed the receiver. That team's members and administrators can manage the binding. Without FTB Teams, the hub and receiver must belong to the same player. Opening the panel leaves ownership as it was, and the machine's access rules still apply.

Use the text at the top of the panel to check the current binding and link status. Click **Disconnect** to clear the binding. The list shows three hubs per page. Use the page buttons or scroll to browse. Use Tab and the arrow keys to focus controls, Enter to activate them and Esc to close the panel.

The sidebar leaves machine slots free and needs no card. To use an optional AE2 Memory Card, sneak-right-click the hub with it, then right-click the machine. To unbind, sneak-right-click the machine with the card after selecting a hub. You can put the card away afterward. The same ownership and access rules apply.

### Power, channels and loaded chunks

The hub can carry **32 channels** with default AE2 settings. Some machines use more than one. Supply **64 AE/t** for the hub at idle and **512 AE/t** for each established wireless link, on top of the machines' own power costs. Keep both ends loaded using your usual chunk-loading setup, including the machine's multiblock option where available.

Bindings survive saves, normal machine recovery and building packaging. If you break, move or replace the hub itself, select it again and rebind each machine. Right-click the hub with an empty hand to see its status, connection count and usage. The hub emits **0 block light**.

## Quantum connection and relocation

You can also use paired Quantum Entangled Singularities while the wireless hub is disconnected. Put one in the controller's quantum slot and its partner in a powered AE2 quantum bridge. This link needs an additional **512 AE/t and one channel**. It can supply construction materials before formation.

Before relocating, settle external crafting orders. Click Reclaim Package and confirm within **three seconds**. The package keeps the pattern library, internal contents and wireless binding. Follow [Folded Buildings and Relocation](folded_buildings.md) to place it.

For more order-handling capacity, see the [Omni Computation Core](omni_computation_core.md). For restocking in one block, use a [Sequence Auto Crafter](molecular_auto_crafter.md).
