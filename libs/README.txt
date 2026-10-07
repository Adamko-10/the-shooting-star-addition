Put The Shooting Star's Fabric 26.3 jar here, as it is (the file you get from its CurseForge page, for example
the-shooting-star-1.3.1-fabric-26.3.jar).

The addon is compiled against it and the dev runs (gradlew runClient / runServer) load it, but it is never copied
into the addon's own jar. When The Shooting Star updates, delete the old jar from this folder, drop the new one
in, and rebuild - see README.md for what to check.
