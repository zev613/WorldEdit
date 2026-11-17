import buildlogic.addEngineHubRepository
import buildlogic.internalVersion
import buildlogic.withCuiProtocolDependsOnCommonRule
import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import net.neoforged.gradle.dsl.common.runs.run.Run

plugins {
    alias(libs.plugins.neogradle.userdev)
    id("buildlogic.platform")
}

platform {
    kind = buildlogic.WorldEditKind.Mod
}

val minecraftVersion = libs.versions.neoforge.minecraft.get()
val nextMajorMinecraftVersion: String = minecraftVersion.split('.').let { (useless, major) ->
    "$useless.${major.toInt() + 1}"
}

val apiClasspath = configurations.resolvable("apiClasspath") {
    extendsFrom(configurations.api.get())
    // In dev runs the common CUI protocol comes from inside the (shadowed) NeoForge protocol jar, see below
    exclude(group = libs.cuiProtocol.common.get().group, module = libs.cuiProtocol.common.get().name)
}

jarJar.disableDefaultSources()

repositories {
    addEngineHubRepository()
    maven {
        name = "FabricMC"
        url = uri("https://maven.fabricmc.net/")
    }
    mavenCentral()
}

configurations {
    val coreResourcesScope = dependencyScope("coreResourcesScope")
    resolvable("coreResourcesResolvable") {
        extendsFrom(coreResourcesScope.get())
        attributes {
            // Attributes are related to how `expose-resources` works, see that plugin file for details.
            attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category::class, Category.VERIFICATION))
            attribute(Bundling.BUNDLING_ATTRIBUTE, objects.named(Bundling::class, Bundling.EXTERNAL))
            attribute(VerificationType.VERIFICATION_TYPE_ATTRIBUTE, objects.named(VerificationType::class, "resources"))
        }
    }
}

withCuiProtocolDependsOnCommonRule(libs.cuiProtocol.neoforge.get().module)

dependencies {
    api(project(":worldedit-core"))
    api(project(":worldedit-core-mc"))

    implementation(libs.neoforge)
    // Use the shadowed protocol jar (the one shipped in our jar-in-jar), which contains the common protocol
    // classes. The "dev" variant doesn't, and the separate common jar would end up on the plain classpath in dev
    // runs, where it can't see the game's classes and mod loading fails with a LinkageError.
    implementation(libs.cuiProtocol.neoforge) {
        attributes {
            attribute(Bundling.BUNDLING_ATTRIBUTE, objects.named(Bundling::class, Bundling.SHADOWED))
        }
    }
    jarJar(libs.cuiProtocol.neoforge) {
        attributes {
            attribute(Bundling.BUNDLING_ATTRIBUTE, objects.named(Bundling::class, Bundling.SHADOWED))
        }
    }

    "coreResourcesScope"(project(":worldedit-core"))
    "coreResourcesScope"(project(":worldedit-core-mc"))
}

// worldedit-core-mc uses Minecraft classes, so in dev runs it has to be loaded by the game's class loader as
// part of the worldedit mod. If it is loaded from the plain classpath instead, mod loading fails with a
// LinkageError (two different ServerPlayer classes).
evaluationDependsOn(":worldedit-core-mc")
val coreMcMain: SourceSet = project(":worldedit-core-mc").the<SourceSetContainer>()["main"]
val coreMcOutputs = coreMcMain.output

configurations.runtimeClasspath {
    exclude(group = libs.cuiProtocol.common.get().group, module = libs.cuiProtocol.common.get().name)
}

runs {
    val runConfig = Action<Run> {
        workingDirectory(project.file("run").canonicalPath)
        modSources {
            add("worldedit", sourceSets["main"])
            add("worldedit", coreMcMain)
        }
        dependencies {
            runtime(apiClasspath.get().filter { file ->
                !file.absolutePath.contains("${File.separator}worldedit-core-mc${File.separator}")
            })
        }
    }
    register("client").configure(runConfig)
    register("server").configure(runConfig)
    configureEach {
        if (name == "gameTestServer") {
            runConfig.execute(this)
        }
    }
}

// NeoForge 26.3.0.83's early loading screen can crash the client on start-up with
// "IllegalStateException: Missing uniform tex (should be COMBINED_IMAGE_SAMPLER)", so dev runs turn it off
// (the game's own loading screen still shows). Pass -PearlyLoadingScreen=true to keep it.
val configureEarlyLoadingScreen by tasks.registering {
    description = "Sets earlyWindowControl in the dev run's fml.toml"
    val fmlConfig = project.file("run/config/fml.toml")
    val enabled = providers.gradleProperty("earlyLoadingScreen").map { it.toBoolean() }.orElse(false)
    inputs.property("enabled", enabled)
    outputs.upToDateWhen { false }
    doLast {
        val line = "earlyWindowControl = ${enabled.get()}"
        val existing = if (fmlConfig.exists()) fmlConfig.readText() else ""
        val setting = Regex("(?m)^\\s*earlyWindowControl\\s*=.*$")
        val updated = if (setting.containsMatchIn(existing)) {
            existing.replace(setting, line)
        } else {
            existing + (if (existing.isEmpty() || existing.endsWith("\n")) "" else "\n") + line + "\n"
        }
        if (updated != existing) {
            fmlConfig.parentFile.mkdirs()
            fmlConfig.writeText(updated)
        }
    }
}
tasks.matching { it.name == "runClient" }.configureEach {
    dependsOn(configureEarlyLoadingScreen)
}

configure<BasePluginExtension> {
    archivesName.set("${archivesName.get()}-mc$minecraftVersion")
}

configure<PublishingExtension> {
    publications.named<MavenPublication>("maven") {
        artifactId = the<BasePluginExtension>().archivesName.get()
        from(components["java"])
    }
}

tasks.named<Copy>("processResources") {
    // this will ensure that this task is redone when the versions change.
    val properties = mapOf(
        "version" to internalVersion,
        "neoVersion" to libs.neoforge.get().version,
        "minecraftVersion" to minecraftVersion,
        "nextMajorMinecraftVersion" to nextMajorMinecraftVersion
    )
    properties.forEach { (key, value) ->
        inputs.property(key, value)
    }

    filesMatching("META-INF/neoforge.mods.toml") {
        expand(properties.mapValues {
            when (val v = it.value) {
                is Provider<*> -> v.get()
                else -> v
            }
        })
    }

    // copy from -core resources as well
    from(configurations.named("coreResourcesResolvable"))
}

tasks.named<ShadowJar>("shadowJar") {
    archiveClassifier = "dist-slim"
    relocate("org.antlr.v4", "com.sk89q.worldedit.antlr4")
    relocate("net.royawesome.jlibnoise", "com.sk89q.worldedit.jlibnoise")
    dependencies {
        include(dependency("org.antlr:antlr4-runtime"))
        include(dependency("org.mozilla:rhino-runtime"))
        include(dependency("com.sk89q.lib:jlibnoise"))
    }
    minimize {
        exclude(dependency("org.mozilla:rhino-runtime"))
    }
}

tasks.jarJar {
    archiveClassifier = "dist"
    val shadowJar = tasks.shadowJar.get()
    dependsOn(shadowJar)
    manifest.inheritFrom(shadowJar.manifest)
    from(project.zipTree(shadowJar.archiveFile).matching {
        exclude("META-INF/MANIFEST.MF")
    })
}
