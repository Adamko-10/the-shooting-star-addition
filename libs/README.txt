Put The Shooting Star's Forge jar here, as it is (the-shooting-star-demo-1.4.0-forge.jar; it
carries the Forge 1.20.1 build this branch is made for).

The addon is compiled against it and the dev runs (gradlew runServer / runServerTest) load it, remapped to Mojang
names, but it is never copied into the addon's own jar. When The Shooting Star updates, delete the old jar from this
folder, drop the new one in, and rebuild - see README.md for what to check.

The Shooting Star 1.4.0 is obfuscated. The build also needs a mapping for it in libs/mappings/, made on your own
machine with ./gradlew tssMappings (see README.md, "When The Shooting Star updates"); it is never committed. Without
one, the build uses the jar as it is, which only works for a readable The Shooting Star (1.3.x).
