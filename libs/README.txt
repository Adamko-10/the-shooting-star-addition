Put The Shooting Star's Fabric jar here, as you download it (for 1.3.1 that is the-shooting-star-demo-1.3.1-fabric.jar).

That file holds The Shooting Star for several Minecraft versions; the build takes the Minecraft 26.3 one out of it
(META-INF/jars/...+mc26.3.jar) into build/shooting-star/. The addon is compiled against that and the dev runs
(gradlew runClient / runServer) load it, but it is never copied into the addon's own jar. The 26.3 jar on its own
works here too.

When The Shooting Star updates, delete the old jar from this folder, drop the new one in, and rebuild - see
README.md for what to check.
