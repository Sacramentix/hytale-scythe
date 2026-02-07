plugins {
    java
    eclipse
    idea
}

/**
 * NOTE: This is entirely optional and basics can be done in `settings.gradle.kts`
 */

repositories {
    // Any external repositories besides: MavenLocal, MavenCentral, HytaleMaven, and CurseMaven
}

dependencies {
    // Any external dependency you also want to include
}

eclipse {
    classpath {
        defaultOutputDir = file("bin/main")
    }
}
