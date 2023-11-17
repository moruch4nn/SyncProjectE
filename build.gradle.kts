import org.jetbrains.kotlin.gradle.plugin.mpp.pm20.util.archivesName

plugins {
    kotlin("jvm") version "1.9.20"
    id("net.minecraftforge.gradle") version "5.1.+"
    eclipse
}

tasks.withType<JavaCompile> {
    sourceCompatibility = "1.8"
    targetCompatibility = "1.8"
    options.encoding = "UTF-8"
}

minecraft {
    mappings("official", "1.16.5")
}

archivesName.set("syncprojecte")
group = "dev.mr3n.syncprojecte"
version = "1.0"
java.toolchain.languageVersion.set(JavaLanguageVersion.of("8"))

repositories {
    mavenCentral()
    maven("https://cursemaven.com")
}

val shadow: Configuration by configurations.creating

val NamedDomainObjectContainer<Configuration>.shadow: NamedDomainObjectProvider<Configuration>
    get() = named<Configuration>("shadow")

dependencies {
    minecraft("net.minecraftforge:forge:1.16.5-36.2.39")
    compileOnly(("curse.maven:projecte-226410:3736621"))
    shadow(kotlin("stdlib"))
}

tasks.jar {
    manifest {
        attributes(mapOf(
            "FMLCorePlugin" to "dev.mr3n.syncprojecte.SyncProjectE",
            "FMLCorePluginContainsFMLMod" to "true",
            "ForceLoadAsMod" to "true",
        ))
    }
}

tasks.jar {
    from(configurations.shadow.map { configuration ->
        configuration.asFileTree.fold(files().asFileTree) { collection, file ->
            if(file.isDirectory) collection else collection.plus(zipTree(file))
        }
    })
    // exclude("META-INF", "META-INF/**")
}

tasks.jar {
    finalizedBy("reobfJar")
}

kotlin { jvmToolchain(8) }