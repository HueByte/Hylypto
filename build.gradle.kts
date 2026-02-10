plugins {
    java
}

group = "com.hylypto"
version = "0.1.0-SNAPSHOT"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(25))
    }
}

repositories {
    mavenCentral()
    maven {
        name = "hytale"
        url = uri("https://maven.hytale.com/release")
    }
}

dependencies {
    compileOnly("com.hypixel.hytale:Server:2026.02.06-aa1b071c2")
}

tasks.jar {
    archiveBaseName.set("Hylypto")
}
