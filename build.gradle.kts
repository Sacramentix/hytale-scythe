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

tasks.register("generateHytaleVersionFile") {
    group = "build"
    description = "Generates a file containing the Hytale server version."
    
    val outputDir = layout.buildDirectory.dir("generated")
    val versionFile = outputDir.map { it.file("hytaleServer.version") }
    outputs.file(versionFile)

    doLast {
        val configuration = configurations.named("compileClasspath").get()
        
        if (!configuration.isCanBeResolved) {
            throw GradleException("compileClasspath cannot be resolved")
        }

        val hytaleServerArtifact = configuration.resolvedConfiguration.resolvedArtifacts
            .find { it.moduleVersion.id.group == "com.hypixel.hytale" && it.moduleVersion.id.name == "Server" }

        val hytaleServerVersion = hytaleServerArtifact?.moduleVersion?.id?.version
            ?: throw GradleException("Could not find com.hypixel.hytale:Server artifact in compileClasspath")

        val file = versionFile.get().asFile
        file.parentFile.mkdirs()
        file.writeText(hytaleServerVersion)
        println("Generated hytaleServer.version: $hytaleServerVersion at ${file.absolutePath}")
    }
}

tasks.named("build") {
    dependsOn("generateHytaleVersionFile")
}
