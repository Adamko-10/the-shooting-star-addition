Put The Shooting Star's jars here, as they are: one for every version the addon should work with, for example
the-shooting-star-demo-1.3.3-forge.jar, -1.3.4-, -1.4.0- and -1.4.1-forge.jar.

The addon is compiled against the newest one, and the dev runs load it. The release jar is made to work with every
one of them (gradle/tss.gradle), but none of them is ever copied into it. When The Shooting Star updates, add the new
jar here (keep the old ones) and rebuild - see README.md for what to check.

The Shooting Star 1.4.0+ is obfuscated. The build needs a mapping for each such jar in libs/mappings/, made on your
own machine with ./gradlew tssMappings (see README.md, "When The Shooting Star updates"); it is never committed. A
readable The Shooting Star (1.3.x) needs none.
