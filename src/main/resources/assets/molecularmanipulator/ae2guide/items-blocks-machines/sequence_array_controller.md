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

A product target of 0 allows unlimited production. Restocking delivers to ME. See [Molecular Auto Crafter](molecular_auto_crafter.md) for the full workflow.

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

Connect a Sequence Wireless Hub to your main ME network by cable, then bind remote machines through their right sidebar. The hub provides AE2 connections across dimensions without a distance limit. No card or machine slot is required.

<BlockImage id="molecularmanipulator:sequence_wireless_hub" scale="4" />

Craft one hub at a crafting table with 1 AE2 Quantum Link Chamber in the center and 8 Quantum Rings around it. It needs no research unlock and is available before the Star Orrery.

<RecipeFor id="molecularmanipulator:sequence_wireless_hub" fallbackText="Crafting table: put 1 AE2 Quantum Link Chamber in the center and 8 Quantum Rings around it. Available before the Star Orrery, with no research unlock." />

### Supported machines

You can bind the Matter Fabrication Well, Sequence Array, Molecular Sequence Rewrite Array, Molecular Auto Crafter, Star Computation Engine, multiblock Omni Computation Core, Event Horizon Singularity Hub and Compact Singularity Hub. The Transfinite Compute Nexus does not support this connection.

Remote connection is available on machine tabs and autocrafting configuration pages. On fabrication ports and Pattern Assemblies, it manages the well controller's binding. Construction, formation, research and channel requirements still apply.

### Bind a receiver

1. Remove the receiver's external ME cables. If it has a quantum slot, remove its singularity too. These connection methods cannot operate together.
2. Click Remote connection in the right sidebar.
3. Select a loaded hub owned by the same team as the receiver. Check its name, dimension, coordinates and online text, then click Connect selected hub. Selecting the row alone does not bind it.

With FTB Teams, ownership comes from the team that placed the receiver. Its team members and administrators can manage the connection. The list shows only loaded hubs owned by that team, and cross-team bindings cannot connect. Without FTB Teams, ownership is per player. Opening a panel leaves ownership unchanged, and normal receiver access restrictions still apply.

The panel's upper section shows the current binding and actual link status. Use the online text to check the connection; purple active 3D thumbnails are decorative. Click Disconnect to unbind. Each page lists three hubs. Browse with the page buttons or mouse wheel. Tab and arrow keys focus controls, Enter activates them and Esc closes the panel.

An AE2 Memory Card is an optional way to bind. Sneak-right-click the hub with the card, then right-click the machine. To unbind, sneak-right-click the machine with a selected card. You can put the card away afterward. It follows the same team checks.

### Power, channels and loaded chunks

The hub is an AE2 dense node with 32 channels by default, subject to AE2 configuration. A machine may use channels for more than one internal device, so 32 channels do not guarantee 32 receivers.

Supply 64 AE/t for the hub, plus 512 AE/t per established wireless link and the machines' own power costs. Keep both hub and receiver chunks loaded. Wireless does not load chunks; existing multiblock chunk-loading settings still apply.

Receiver bindings survive normal saves, removal recovery and building packaging. The hub keeps its identity across world restarts. Breaking, replacing or moving it gives it a new identity, requiring each receiver to be rebound.

Empty-hand right-click the hub to see status, link count and usage. Its markings are black offline and purple when powered, wired and online. It emits 0 world light.

## Quantum connection and relocation

You can also use paired Quantum Entangled Singularities while the wireless hub is disconnected. Put one in the controller's quantum slot and its partner in a powered AE2 quantum bridge. This link needs an additional 512 AE/t and one channel, and can supply construction materials before formation.

Before relocating, settle external crafting orders, then use Reclaim Package to pack the whole machine. Click once, then confirm within three seconds. The package retains the pattern library, internal contents and wireless binding. See [Folded Buildings and Relocation](folded_buildings.md) for placement. If you also move or replace the wireless hub, rebind the array afterward.

Colors and local effects settings affect only visuals. For more order-handling capacity, see the [Omni Computation Core](omni_computation_core.md). For restocking in one block, use a [Molecular Auto Crafter](molecular_auto_crafter.md).
