Put The Shooting Star's Forge jar here, as it is (for example the-shooting-star-demo-1.3.4-forge.jar, 1.3.3 or newer; it
carries the Forge 1.20.1 build this branch is made for).

The addon is compiled against it and the dev runs (gradlew runServer / runServerTest) load it, remapped to Mojang
names, but it is never copied into the addon's own jar. When The Shooting Star updates, delete the old jar from this
folder, drop the new one in, and rebuild - see README.md for what to check.
