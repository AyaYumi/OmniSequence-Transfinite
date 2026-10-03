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

Connect ME crafting requests to the well. Unlock it through tier 1 research, then install it in a valid service position.

<Row>
<BlockImage id="molecularmanipulator:matter_fabrication_pattern_assembly" scale="4" />

<ItemImage id="ae2:pattern_encoding_terminal" scale="4" />

<ItemImage id="ae2:pattern_access_terminal" scale="4" />
</Row>

## From pattern to product

1. Choose an unlocked fabrication recipe in JEI.
2. Encode a **processing pattern** with exactly matching resources and amounts.
3. Insert the pattern and connect the formed well to an online ME network.
4. Request the product from an ME terminal. The assembly queues supplied ingredients, processes them, and returns the output.

| Pattern setting | Detail |
| --- | --- |
| Capacity | 36 pattern slots |
| Pattern type | Processing patterns matching well recipes |
| Research bonuses | Controller's branch permissions, parallelism, and speed |
| Multiple assemblies | Rename them to distinguish them in the Pattern Access Terminal |

## Three pages

| Page | Contents |
| --- | --- |
| Patterns | Processing recipes advertised to ME |
| Input cache | Accepted ingredients awaiting processing |
| Output cache | Products waiting for ME storage |

Caches support items, fluids, and other registered AE resource types. Matching resources and components are counted together using long integer quantities. Materials, power, and output space still limit actual batches.

**Return Unstarted Ingredients** refunds batches that have not begun. Active work is retained, and blocked products stay in the output cache.

## Relocate and resume

Patterns, jobs, ingredients, outputs, and pending refunds persist and travel with the normally removed assembly. Restore the structure, network, and power to continue.

## Recipe

<RecipeFor id="molecularmanipulator:matter_fabrication_pattern_assembly" fallbackText="No recipe is available in this pack. Check JEI and research requirements." />
