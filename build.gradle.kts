plugins {
    java
}

group = "com.betterrtp"
version = "1.0.0"

java {
    toolchain { languageVersion.set(JavaLanguageVersion.of(25)) }
}

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://repo.opencollab.dev/main/")
}

dependencies {
    // Server API: provided by Paper/Purpur at runtime, never shaded.
    compileOnly("io.papermc.paper:paper-api:26.2.build.+")
    // Optional Bedrock integration: provided by the Floodgate plugin at runtime, never shaded.
    // (Cumulus, used for SimpleForm, comes transitively from the Floodgate API.)
    compileOnly("org.geysermc.floodgate:api:2.2.4-SNAPSHOT")

    testImplementation("io.papermc.paper:paper-api:26.2.build.+")
    testImplementation(platform("org.junit:junit-bom:5.11.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks {
    withType<JavaCompile>().configureEach {
        options.encoding = "UTF-8"
        options.release.set(25)
        options.compilerArgs.add("-Xlint:all,-processing")
    }
    processResources {
        filesMatching("plugin.yml") { expand("version" to project.version) }
    }
    test {
        useJUnitPlatform()
    }
    jar {
        archiveBaseName.set("BetterRTP")
    }
}
