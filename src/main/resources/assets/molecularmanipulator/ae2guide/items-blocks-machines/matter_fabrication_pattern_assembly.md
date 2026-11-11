---
navigation:
  parent: items-blocks-machines/matter_fabrication_well.md
  title: "Fabrication Pattern Assembly"
  icon: molecularmanipulator:matter_fabrication_pattern_assembly
  position: 2
item_ids:
- molecularmanipulator:matter_fabrication_pattern_assembly
---

# Fabrication Pattern Assembly

A Pattern Assembly lets you request Fabrication Well products from an ME terminal instead of loading ingredients into ports yourself.

<Row>
<BlockImage id="molecularmanipulator:matter_fabrication_pattern_assembly" scale="4" />

<ItemImage id="ae2:pattern_encoding_terminal" scale="4" />

<ItemImage id="ae2:pattern_access_terminal" scale="4" />
</Row>

From left to right: the assembly, a terminal for encoding patterns, and a terminal for managing installed patterns.

## Before you start

- Complete **Tier I: AE Foundation** in this [well's research page](matter_fabrication_research.md).
- Use the well's [item ports](matter_fabrication_ports.md) to make your first assembly with the recipe below.
- Have a powered ME network with storage, a crafting CPU, a Pattern Encoding Terminal and Blank Patterns.
- Choose a **Fabrication Well recipe** you have unlocked. A processing pattern must describe that recipe's consumed ingredients and products, with the correct quantities.

<RecipeFor id="molecularmanipulator:matter_fabrication_pattern_assembly" />

## Set up your first order

1. **Install the assembly.** Hold it to highlight valid positions, then replace one of the well's highlighted casing blocks. The well must remain complete and online.
2. **Encode a processing pattern.** In the Pattern Encoding Terminal, open the desired Fabrication Well recipe in JEI. Select **Encode and upload to fabrication well** to encode it and send it to an assembly.
3. **Check the Pattern page.** The uploaded pattern should appear there. Each assembly holds **36 patterns**. You can also encode a matching processing pattern manually and insert it yourself.
4. **Supply reusable tools separately.** If JEI lists a catalyst, put it in the **Catalyst** page as described below. Do not add it to the pattern's consumed inputs.
5. **Request the product in ME.** The crafting CPU delivers ingredients to the assembly; the well processes them and sends products back to ME.

Assemblies support items, fluids and recipe resources such as Star Signal Flow. You do not need input/output ports for an assembly-driven job.

### Where does JEI upload send the pattern?

Upload uses **one Blank Pattern**, first from the terminal's local slot, then from connected ME storage. It looks for a loaded assembly in a complete well belonging to your team, preferring the nearest with space in your dimension. Missing destinations, full assemblies and duplicate patterns do not spend a blank.

With FTB Teams, the controller belongs to the team of the player who placed it; without FTB Teams, it belongs to that player. Re-place the controller after changing teams if you need to update this ownership. Assembly names do not affect upload; naming them does help when managing them through a Pattern Access Terminal.

ExtendedAE Plus automatic destination selection is supported for **1.6.4.1**.

## Reusable catalysts

A **catalyst** is a tool the recipe needs nearby but does not consume. Each assembly has **36 catalyst slots**, shared with the loaded assemblies in the same complete well.

<Row>
<ItemImage id="molecularmanipulator:ghost_matter" scale="4" />

<BlockImage id="molecularmanipulator:flawless_star_marrow_parent_rock" scale="4" />
</Row>

Examples: Ghost Matter enables [Gravity Crystal production](nomai_materials.md); a Flawless Star Marrow Parent Rock enables [Signal-Induced Crystallization](star_marrow_crystals.md).

1. Put the catalyst quantity shown by the recipe into any assembly's **Catalyst** page in this well.
2. Encode **only consumed materials and products** in the processing pattern.
3. Leave the catalyst installed. Making a larger batch does not multiply its requirement.

If a catalyst is removed, work waits with its materials and progress until you put it back.

## What success looks like

The product is craftable in ME, the assembly shows **Connected to AE**, and a requested job returns its output to storage.

| Assembly page | What to look for |
| --- | --- |
| Pattern | Installed processing patterns |
| Input | Ingredients already delivered for work |
| Output | Products waiting for ME to accept them |
| Catalyst | Reusable tools shared by this well |

**Return inputs** sends back only work that has not started. Active work keeps its materials. Full ME storage leaves products and refunds safely buffered until space is available. Normal assembly removal retains patterns, catalysts, work and stored materials.

## If it does not work

| Problem | Fix |
| --- | --- |
| Upload cannot find an assembly | Check team ownership, building formation, loaded chunks and a free pattern slot. |
| ME cannot plan the product | Check the pattern, ingredients and crafting CPU. Use a processing pattern for the well, not an ordinary crafting pattern. |
| Delivered ingredients are waiting | Check this well's research, power and catalysts. Re-encode patterns that incorrectly consume a catalyst. |
| Job finishes but ME does not receive it | Free ME storage; inspect the assembly's **Output** page. |
| Production is slow | [Deeper well research](matter_fabrication_research.md) raises the number processed together; a [Star Computation Engine](star_computation_engine.md) on the controller's ME network supplies production speed. |

Compatible Neo ECO, Data Energistics Trinity and Lightning Tech CPUs can submit batch work. The [Transfinite Compute Nexus](transfinite_compute_nexus.md) can deliver a large available ingredient batch at once; processing draws power as it runs, not as a full-batch advance payment.

## Next steps

Try [Star Marrow material processing](star_marrow_crystals.md) or [Gravity Crystals](nomai_materials.md), then use [deep research](matter_fabrication_research.md) to increase batch sizes.
