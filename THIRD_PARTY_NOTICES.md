# Third-Party Notices

OmniSequence: Transfinite integrates with the following separately installed
Minecraft mods. Their binaries and source files are not bundled in the produced
mod JAR. Adapted artwork is identified below:

- Applied Energistics 2, 15.4.10.
- AppliedEnhancements, runtime 1.1.2-forge or later; build API 1.1.4-forge.
- ExtendedAE, version 1.20-1.4.20-forge or later.
- Glodium, version 1.20-1.5-forge or later.
- Advanced AE, Forge version, when installed.
- ExtendedAE Plus and JEI, when installed.
- NeoECOAE, Forge 20.4.2 or later, and its LDLib dependency, when installed. Native batch/exact integration uses the 20.5.0+ API.
- Radium, AE2 Lightning Tech and Data Energistics, when installed with supported APIs.

The design was informed by GTLCore's Molecular Assembler Matrix implementation.
No GTLCore source files or assets are included in this project.

ExtendedAE Plus' pattern upload implementation was inspected to implement
compatibility through AE2's public PatternContainer API. No ExtendedAE Plus
source files or assets are included in this project.

Resources distributed under the `molecularmanipulator` namespace are
project-authored assets released under the project license, with these exceptions:

- `textures/item/star_signal_storage_component.png` adapts Applied
  Energistics 2 19.2.17's `assets/ae2/textures/item/cell_component_64k.png`.
  The crystal palette was recolored green and a six-second animation was added;
  the original silhouette and metalwork are retained. Original textures and
  models: (c) 2020 Ridanisaurus Rid, (c) 2013-2020 AlgorithmX2 et al.
  This adapted texture remains under **CC BY-NC-SA 3.0 Unported**, separate from
  the project's MIT license. License text: `licenses/CC-BY-NC-SA-3.0.txt`.
  Source: https://github.com/AppliedEnergistics/Applied-Energistics-2/tree/neoforge/v19.2.17
  License: https://creativecommons.org/licenses/by-nc-sa/3.0/

- `textures/block/sequence_wireless_hub.png` and
  `textures/block/sequence_wireless_hub_housing.png` adapt the
  outer three-pixel frame of ExtendedAE 1.21-2.2.32-neoforge's
  `assets/extendedae/textures/block/wireless_connector/wireless_connect_fluix.png`.
  The connector's frame pixels are retained; the center is replaced with the
  project's bracket-and-dot signal artwork.
  Original textures and models are credited to Sea_Kerman by ExtendedAE.
  These adapted images are distributed under **GNU LGPL version 3**, separate
  from the project's MIT license. The PNG files are the editable artwork.
  License texts: `licenses/LGPL-3.0.txt` and `licenses/GPL-3.0.txt`.
  Source: https://github.com/GlodBlock/ExtendedAE/tree/1.21.1-neoforge
  Original texture: https://github.com/GlodBlock/ExtendedAE/blob/1.21.1-neoforge/src/main/resources/assets/extendedae/textures/block/wireless_connector/wireless_connect_fluix.png

The artwork source versions above identify provenance, not this branch's runtime
dependencies. This branch targets Minecraft 1.20.1 / Forge.

The GUI uses the active Minecraft font. No font files are bundled with the mod.
