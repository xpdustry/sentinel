import com.xpdustry.ksr.kotlinRelocate
import com.xpdustry.toxopid.extension.anukeXpdustry
import com.xpdustry.toxopid.spec.ModMetadata
import com.xpdustry.toxopid.spec.ModPlatform
import com.xpdustry.toxopid.task.GithubAssetDownload

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.spotless)
    alias(libs.plugins.indra.common)
    alias(libs.plugins.indra.git)
    alias(libs.plugins.indra.publishing)
    alias(libs.plugins.shadow)
    alias(libs.plugins.ksr)
    alias(libs.plugins.toxopid)
}

val metadata = ModMetadata.fromJson(file("plugin.json"))
group = "com.xpdustry"
if (indraGit.headTag() == null) metadata.version += "-SNAPSHOT"
version = metadata.version
description = metadata.description

toxopid {
    compileVersion = "v${metadata.minGameVersion}"
    platforms = setOf(ModPlatform.SERVER)
}

repositories {
    mavenCentral()
    anukeXpdustry()
    maven("https://maven.xpdustry.com/releases") {
        name = "xpdustry-releases"
        mavenContent { releasesOnly() }
    }
}

dependencies {
    compileOnly(kotlin("stdlib-jdk8"))
    compileOnly(kotlin("reflect"))
    compileOnly(libs.kotlinx.serialization.json)
    compileOnly(libs.kotlinx.coroutines.core)
    compileOnly(toxopid.dependencies.arcCore)
    compileOnly(toxopid.dependencies.mindustryCore)
    compileOnly(libs.distributor.api)
    implementation(libs.distributor.command.cloud)
    implementation(libs.guava)
    implementation(libs.jsoup)
    implementation(libs.hoplite.core)
    implementation(libs.hoplite.yaml)
    testImplementation(libs.junit.api)
    testRuntimeOnly(libs.junit.engine)
}

configurations.runtimeClasspath {
    exclude("org.jetbrains.kotlin")
    exclude("org.jetbrains.kotlinx")
}

indra {
    javaVersions {
        target(17)
        minimumToolchain(17)
    }

    publishSnapshotsTo("xpdustry", "https://maven.xpdustry.com/snapshots")
    publishReleasesTo("xpdustry", "https://maven.xpdustry.com/releases")

    mitLicense()

    if (metadata.repository.isNotBlank()) {
        val repo = metadata.repository.split("/")
        github(repo[0], repo[1]) {
            ci(true)
            issues(true)
            scm(true)
        }
    }

    configurePublications {
        pom {
            organization {
                name = "xpdustry"
                url = "https://www.xpdustry.com"
            }

            developers {
                developer {
                    id = "Phinner"
                    timezone = "Europe/Brussels"
                }
            }
        }
    }
}

signing {
    val signingKey: String? by project
    val signingPassword: String? by project
    useInMemoryPgpKeys(signingKey, signingPassword)
}

spotless {
    kotlin {
        ktfmt().dropboxStyle()
        licenseHeaderFile(rootProject.file("HEADER.txt"))
    }
    kotlinGradle {
        ktlint()
    }
}

kotlin {
    explicitApi()
}

val generateResources by tasks.registering {
    outputs.files(fileTree(temporaryDir))
    doLast {
        temporaryDir.resolve("plugin.json").writeText(ModMetadata.toJson(metadata))
    }
}

data class Relocation(val from: String, val to: String, val kotlin: Boolean = false)

tasks.shadowJar {
    archiveFileName = "${metadata.name}.jar"
    archiveClassifier = "plugin"
    from(generateResources)
    from(rootProject.file("LICENSE.md")) { into("META-INF") }
    minimize()
    mergeServiceFiles()

    sequenceOf(
        Relocation("com.xpdustry.distributor.api.command.cloud", "cloud"),
        Relocation("org.incendo.cloud", "cloud"),
        Relocation("io.leangen.geantyref", "geantyref"),
        Relocation("com.google.common", "guava"),
        Relocation("org.jsoup", "jsoup"),
        Relocation("com.sksamuel.hoplite", "hoplite", true),
    ).forEach {
        if (it.kotlin) {
            kotlinRelocate(it.from, "com.xpdustry.sentinel.shadow.${it.to}")
        } else {
            relocate(it.from, "com.xpdustry.sentinel.shadow.${it.to}")
        }
    }
}

tasks.register<Copy>("release") {
    dependsOn(tasks.build)
    from(tasks.shadowJar)
    destinationDir = temporaryDir
}

val downloadSlf4md by tasks.registering(GithubAssetDownload::class) {
    owner = "xpdustry"
    repo = "slf4md"
    asset = "slf4md-simple.jar"
    version = "v${libs.versions.slf4md.get()}"
}

val downloadDistributorCommon by tasks.registering(GithubAssetDownload::class) {
    owner = "xpdustry"
    repo = "distributor"
    asset = "distributor-common.jar"
    version = "v${libs.versions.distributor.get()}"
}

val downloadKotlinRuntime by tasks.registering(GithubAssetDownload::class) {
    owner = "xpdustry"
    repo = "kotlin-runtime"
    asset = "kotlin-runtime.jar"
    version = "v${libs.versions.kotlin.runtime.get()}-k.${libs.versions.kotlin.core.get()}"
}

tasks.runMindustryServer {
    mods.from(downloadSlf4md, downloadDistributorCommon, downloadKotlinRuntime)
}
