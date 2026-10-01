---
navigation:
  parent: index.md
  title: Mirror Pattern Provider
  icon: ae2cs:mirror_pattern_provider
  position: 175
item_ids:
  - ae2cs:mirror_pattern_provider
  - ae2cs:mirror_pattern_provider_part
  - ae2cs:mirror_linker
---

# Mirror Pattern Provider

<Row gap="16">
  <BlockImage id="ae2cs:mirror_pattern_provider" scale="2" />
  <ItemImage id="ae2cs:mirror_pattern_provider_part" scale="2" />
  <ItemImage id="ae2cs:mirror_linker" scale="2" />
</Row>

The **Mirror Pattern Provider** copies the available patterns and priority of another Pattern Provider. It has no pattern slots of its own, allowing one configured provider to be reused at multiple processing locations.

---

## Binding a Target

Use the **<ItemLink id="ae2cs:mirror_linker" />** to configure a mirror:

1. Use the binder on the Pattern Provider that should act as the source.
2. Use the bound binder on a Mirror Pattern Provider.
3. The mirror immediately exposes the source provider's patterns to the ME crafting service.

The binder tooltip displays the stored dimension, position, and selected part side. Use the binder in the air to clear its stored target.

A Mirror Pattern Provider cannot use another Mirror Pattern Provider as its source.

---

## Behavior

- The source provider remains responsible for storing and configuring the patterns.
- Crafting requests are dispatched from the mirror's own location while using the source provider's configuration settings.
- The mirror follows the source provider's priority.
- If the source is unavailable or its chunk is not loaded, the mirrored patterns are temporarily unavailable.
- Block and cable-part forms provide the same mirroring function.

Use several mirrors when the same pattern set needs to be available from multiple machine groups without copying pattern items manually.
