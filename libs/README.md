# Forge 1.20.1 development dependencies

The Forge build uses Java 17. Install these runtime mods separately on both client and server; they are not embedded in OmniSequence.

For **AE2 Unofficial Extended Life Modern**, copy its Forge 1.20.1 jar to
build with the published UELM Maven artifact:

```text
gradlew build -Pae2_uelm_version=15.5.4-uelm
```

The UELM profile was checked against UELM `15.5.4-uelm`; the normal profile
remains compatible with AE2 `15.4.x`. Do not install both AE2 jars together.

| Local artifact | Source |
| --- | --- |
| `appliedenhancements-1.0.9-fix-forge.jar` | Build the AppliedEnhancements `1.20.1-forge` branch with `gradlew build`; use the full reobfuscated JAR. |
| `extended-ae-1.20-1.4.19-forge.jar` | Copy the tested pack's `ExtendedAE-1.20-1.4.19-forge.jar` under this development filename. This version was not available in the Modrinth Maven index when the port was validated. |
| `guideme-20.1.15.jar` | GuideME 20.1.15 for Minecraft 1.20.1. Maven is also configured. |


ForgeGradle deobfuscates these released artifacts for development. Keep all local JAR files out of Git. The existing 1.21.1 AppliedEnhancements JAR is not used by this branch.

The dependency checkout must declare `minecraft_version=1.20.1` and its
`mod_version` must match `applied_enhancements_version` in the root
`gradle.properties`; a branch name alone does not establish loader compatibility.
CI checks both values before building and reports an explicit error for an
incompatible source branch or version. Do not substitute a NeoForge dependency JAR
in the Forge build.

The CI compile baseline uses the published ExtendedAE `1.20-1.4.18-forge` artifact. Local compilation against that version was also verified; gameplay validation uses the pack's 1.4.19 build.

The exact crafting API requires protocol 9 from AppliedEnhancements 1.0.9-fix-forge. For AE2 UELM, use the AppliedEnhancements build from commit `715972a7f33d9d6e9fab52e22ae8bf19191f129f` or later.
