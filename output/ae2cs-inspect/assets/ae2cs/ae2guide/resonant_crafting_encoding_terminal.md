---
navigation:
  parent: index.md
  title: Resonant Crafting Encoding Terminal
  icon: ae2cs:resonant_template_coding_terminal_part
  position: 140
item_ids:
  - ae2cs:resonant_template_coding_terminal_part
  - ae2cs:wireless_resonant_terminal
---

# Resonant Crafting Encoding Terminal

<Row gap="16">
  <ItemImage id="ae2cs:resonant_template_coding_terminal_part" scale="2" />
  <ItemImage id="ae2cs:wireless_resonant_terminal" scale="2" />
</Row>

The **Resonant Crafting Encoding Terminal** is an advanced pattern terminal for encoding both standard AE2 patterns and AECS Resonating Patterns. It is available as a wired ME terminal part and as a wireless terminal.

---

## Recipe Modes

The terminal provides dedicated modes for:

- Crafting recipes
- Processing recipes
- Smithing recipes
- Stonecutting recipes
- Anvil recipes

Processing mode supports up to **144 input slots** and **36 output slots**, allowing large recipes to be encoded without reducing them to the capacity of a standard Pattern Encoding Terminal.

---

## Processing Pattern Options

When encoding processing recipes, the terminal can switch between **standard Processing Patterns** and **Resonating Patterns**. Resonating Patterns can later be configured with individual delivery targets for use by a <ItemLink id="ae2cs:resonating_pattern_provider" />.

JEI and EMI recipe transfer supports three ingredient-layout modes:

- **Merge Ingredients:** combines identical ingredients like the standard AE2 terminal.
- **Partial Split:** preserves recipe order while merging identical adjacent ingredients.
- **Full Split:** preserves each transferred ingredient entry separately.

The input display can also switch between virtual encoding slots and real slots. Real-slot mode extracts available ingredients from the ME network when a recipe is transferred.

---

## Wireless Use

The <ItemLink id="ae2cs:wireless_resonant_terminal" /> provides the same encoding interface away from a fixed terminal. It can be charged through compatible energy containers and installed in a Wireless Universal Terminal when AE2WTLib is available.
