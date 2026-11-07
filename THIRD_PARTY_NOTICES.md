# Third-Party Notices

OmniSequence: Transfinite integrates with the following separately installed
Minecraft mods. Their binaries and source files are not bundled in the produced
mod JAR. Adapted artwork is identified below:

- Applied Energistics 2, version 19.2.18.
- AppliedEnhancements, version 1.1.2 or later (compile API 1.1.4).
- ExtendedAE, version 1.21-2.2.32-neoforge or later.
- Glodium, version 1.21-2.2-neoforge or later.
- LowDragLib2, version 2.2.18 or later.
- Advanced AE, version 1.6.11 or later, when installed.
- ExtendedAE Plus and JEI, when installed. Additional provider-selection/upload
  hooks target ExtendedAE Plus 1.6.4.1 only.
- Neo ECO AE Extension, version 21.2.10 or later, when installed.
- Data Energistics, when installed; the depot capacity-simulation fix targets
  versions 3.3.0 and 3.3.5 only.

The design was informed by GTLCore's Molecular Assembler Matrix implementation.
No GTLCore source files or assets are included in this project.

ExtendedAE Plus' pattern upload implementation was inspected to implement
compatibility through AE2's public PatternContainer API. No ExtendedAE Plus
source files or assets are included in this project.

Resources distributed under the `molecularmanipulator` namespace are
project-authored assets released under the project license, with this exception:

- `textures/item/star_signal_storage_component.png` adapts Applied
  Energistics 2 19.2.17's `assets/ae2/textures/item/cell_component_64k.png`.
  The crystal palette was recolored green and a six-second animation was added;
  the original silhouette and metalwork are retained. Original textures and
  models: (c) 2020 Ridanisaurus Rid, (c) 2013-2020 AlgorithmX2 et al.
  This adapted texture remains under **CC BY-NC-SA 3.0 Unported**, separate from
  the project's MIT license. License text: `licenses/CC-BY-NC-SA-3.0.txt`.
  Source: https://github.com/AppliedEnergistics/Applied-Energistics-2/tree/neoforge/v19.2.17
  License: https://creativecommons.org/licenses/by-nc-sa/3.0/

The GUI uses bundled Inter, Inter Tight, JetBrains Mono, and Source Han Sans SC
font files. Their SIL Open Font License texts are included in
`licenses/` and in the mod JAR.

## NeoForged MDK template

The Gradle wrapper, `build.gradle`, `settings.gradle` and `gradle.properties`
derive from the NeoForged Mod Development Kit (MDK) template.
Copyright (c) 2023 NeoForged project. These template-derived files are licensed
under the [MIT License](LICENSE), whose full permission and warranty text is
bundled alongside this notice.
