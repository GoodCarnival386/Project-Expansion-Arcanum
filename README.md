# Project Expansion: Arcanum

Independent maintenance fork of [Project Expansion](https://github.com/DonovanDMC/ProjectExpansion) by Donovan_DMC. Maintained by JDJades. Not officially supported by ProjectE.

## Environment
- Minecraft 1.21.1
- NeoForge 21.1.219 or compatible newer 21.1 build
- Java 21
- ProjectE PE1.1.0

The runtime namespace remains `projectexa`. Porting features does **not** certify cross-version world migration. Do not open an irreplaceable 1.20.1 world in this development build.

## Development
`gradlew.bat compileJava` compiles Java sources. `gradlew.bat runClient` and `gradlew.bat build` invoke the asset generator through Node.js on all platforms. Install Node.js and npm and add them to PATH; Bash is not required on Windows. The generator installs dependencies from the lockfile on first use and when the dependency manifests change, then reuses its local dependency cache.

Optional integrations must remain optional. This migration covers the stone table, Final Star, JEI transfer, personal EMC warehouse, projectiles and living-entity repair. Optional Ars Nouveau 5.13.3, Create 6.0.10 and Goety 3.2.1 material-supply integrations are now implemented. See [the API audit](docs/INTEGRATIONS_1_21_1.md) for version requirements and release-package caveats; game runtime acceptance is still pending.

The Arcane Transmutation Tablet includes smithing, stonecutting, and anvil panels unlocked by learning the corresponding workstation. Carrying a tablet also enables EMC purchases of learned items through JEI and survival pick block (Shift picks a stack or tops up the held stack). Server options `enableJeiEmcRetrieval` and `enableWorldEmcPick` control these inputs. JEI retrieval targets 19.57.0.451 and 19.70.0.469, with a minimum of 19.57.0.451. Both sides must use the updated mod protocol.

See [LICENSE](LICENSE) and [NOTICE.md](NOTICE.md) for original attribution.
