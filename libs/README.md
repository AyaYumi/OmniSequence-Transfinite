# Forge 1.20.1 development dependencies

The Forge build uses Java 17. Install these runtime mods separately on both client and server; they are not embedded in OmniSequence.

| Local artifact | Source |
| --- | --- |
| `appliedenhancements-1.0.6-forge.jar` | Build the AppliedEnhancements `1.20.1-forge` branch with `gradlew build`; use the full reobfuscated JAR. |
| `extended-ae-1.20-1.4.19-forge.jar` | Copy the tested pack's `ExtendedAE-1.20-1.4.19-forge.jar` under this development filename. This version was not available in the Modrinth Maven index when the port was validated. |
| `guideme-20.1.15.jar` | GuideME 20.1.15 for Minecraft 1.20.1. Maven is also configured. |


ForgeGradle deobfuscates these released artifacts for development. Keep all local JAR files out of Git. The existing 1.21.1 AppliedEnhancements JAR is not used by this branch.

The dependency checkout must declare `minecraft_version=1.20.1` and produce
`appliedenhancements-1.0.6-forge.jar`; a branch name alone does not establish loader
compatibility. CI checks the dependency's Minecraft target before building it and
reports an explicit error for an incompatible source branch. Do not substitute a
NeoForge dependency JAR in the Forge build.

The CI compile baseline uses the published ExtendedAE `1.20-1.4.18-forge` artifact. Local compilation against that version was also verified; gameplay validation uses the pack's 1.4.19 build.
