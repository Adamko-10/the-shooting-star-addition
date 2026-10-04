Put The Shooting Star's jar here, as it is (for example the-shooting-star-demo-1.2.2-neoforge.jar).

The addon is compiled against it and the dev runs (gradlew runClient / runServer) load it, but it is never copied
into the addon's own jar. When The Shooting Star updates, delete the old jar from this folder, drop the new one
in, and rebuild - see README.md for what to check.
