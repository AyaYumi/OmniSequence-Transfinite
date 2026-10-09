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

The Pattern Assembly adds ME autocrafting to the [Fabrication Well](matter_fabrication_well.md). Each assembly holds **36 patterns** and uses its well's research unlocks, parallelism and speed bonuses.

<Row>
<BlockImage id="molecularmanipulator:matter_fabrication_pattern_assembly" scale="4" />

<ItemImage id="ae2:pattern_encoding_terminal" scale="4" />

<ItemImage id="ae2:pattern_access_terminal" scale="4" />
</Row>

## Installation and encoding

1. Complete tier 1, fabricate an assembly and install it in a well service position.
2. Choose an unlocked fabrication recipe in JEI. Encode all consumed ingredients and products, with their quantities, in a **processing pattern**.
3. Insert the pattern and check that the well is formed and connected to online ME.
4. Request the product from an ME terminal. The assembly processes delivered ingredients and returns products to ME.

Manage multiple assemblies through a Pattern Access Terminal. Naming them makes them easier to identify. Assemblies accept items, fluids and other AE resources required by recipes.

## Uploading from JEI

Open a fabrication recipe in JEI from a **Pattern Encoding Terminal**, then select **Encode and upload to fabrication well**. This uses one blank pattern from the terminal, encodes the recipe quantities and uploads it to a formed well belonging to your team. It prefers the nearest assembly with space in your dimension.

The destination chunk must be loaded. Missing assemblies, full inventories, duplicate patterns and missing blanks do not consume a blank pattern.

Wells bind to the placer's FTB team when placed, or to the individual without FTB Teams. Re-place the controller to change its team binding. If no upload destination is found, check team ownership, formation and chunk loading.

## Catalysts

Place required catalysts in an assembly's **Catalysts** page. Each assembly has **36 catalyst slots**. Linked, loaded assemblies in the same well share these items.

Catalysts are retained and excluded from processing patterns. For example, encode the water, lightning and other consumed resources for a crystal catalyzing recipe, then store its crystal block in the catalyst page. Ghost Matter used to fabricate Gravity Crystals works the same way. Larger batches do not require more catalysts.

Missing catalysts prevent new work from starting. Accepted work retains materials and progress until the catalyst returns. Re-encode any pattern that lists a catalyst as a consumed ingredient.

## Caches and tasks

| Page | Contents |
| --- | --- |
| Patterns | Processing patterns available to ME |
| Input cache | Accepted materials awaiting processing |
| Output cache | Products waiting for ME capacity |
| Catalysts | Reusable items for recipes in this well |

Neo ECO, Data Energistics Trinity and Lightning Tech CPUs can submit batch work to assemblies. Research, ingredients, power and output capacity determine the actual processing rate.

**Return Unstarted Ingredients** refunds only batches that have not started. Active work is retained. If ME storage is full, products wait in the output cache until space is available.

Normal assembly removal retains patterns, catalysts, tasks and caches. Reinstall it and restore the structure, network and power to continue.

## Recipes

<RecipeFor id="molecularmanipulator:matter_fabrication_pattern_assembly" fallbackText="No recipe is available in this pack. Check JEI and research requirements." />
