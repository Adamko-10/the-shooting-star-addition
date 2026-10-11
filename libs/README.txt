Put The Shooting Star's Fabric jar here, as you download it (1.4.0: the-shooting-star-demo-1.4.0-fabric.jar).

That file holds The Shooting Star for several Minecraft versions; the build takes the Minecraft 1.21.11 one out of it
(META-INF/jars/...+mc1.21.11.jar) into build/shooting-star/. The addon is compiled against that and the dev runs
(gradlew runClient / runServer) load it, but it is never copied into the addon's own jar. The 1.21.11 jar on its own
works here too.

When The Shooting Star updates, delete the old jar from this folder, drop the new one in, and rebuild - see
README.md for what to check.

The Shooting Star 1.4.0 is obfuscated. The build also needs a mapping for it in libs/mappings/, made on your own
machine with ./gradlew tssMappings (see README.md, "When The Shooting Star updates"); it is never committed. Without
one, the build uses the jar as it is, which only works for a readable The Shooting Star (1.3.x).
