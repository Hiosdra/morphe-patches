import org.gradle.api.attributes.Attribute
import org.gradle.api.attributes.Category
import org.gradle.api.attributes.Usage
import org.jetbrains.kotlin.gradle.plugin.KotlinPlatformType

group = "io.github.hiosdra.patches"

patches {
    about {
        name = "Hiosdra Patches"
        description = "Personal, community-maintained patches compatible with Morphe."
        source = "https://github.com/Hiosdra/morphe-patches"
        author = "Hiosdra"
        contact = "https://github.com/Hiosdra"
        website = "https://github.com/Hiosdra/morphe-patches"
        license = "GPLv3"
    }
}

kotlin {
    compilerOptions {
        freeCompilerArgs.add("-Xcontext-parameters")
    }
}

// Separate configuration so gson is available at runtime for the
// generatePatchesList task but never bundled into the APK.
val patchListGeneratorClasspath = configurations.create("patchListGeneratorClasspath")

// Morphe patches compile as JVM code, while Bitmovin publishes Android AAR
// variants. Resolve the Android API graph and expose only its classes to the
// compiler. The host application provides the SDK at runtime.
val bitmovinPlayerClasspath = configurations.create("bitmovinPlayerClasspath") {
    isCanBeConsumed = false
    isCanBeResolved = true

    attributes {
        attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category.LIBRARY))
        attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage.JAVA_API))
        attribute(KotlinPlatformType.attribute, KotlinPlatformType.androidJvm)
    }
}

val bitmovinPlayerClasses = bitmovinPlayerClasspath.incoming.artifactView {
    attributes {
        attribute(Attribute.of("artifactType", String::class.java), "android-classes-jar")
    }
}.files

dependencies {
    compileOnly(libs.gson)
    bitmovinPlayerClasspath(libs.bitmovin.player)
    compileOnly(bitmovinPlayerClasses)
    patchListGeneratorClasspath(libs.gson)
}

tasks {
    register<JavaExec>("generatePatchesList") {
        description = "Build patch with patch list"

        dependsOn(build)

        classpath = sourceSets["main"].runtimeClasspath + patchListGeneratorClasspath
        mainClass.set("util.PatchListGeneratorKt")
    }

    // Used by gradle-semantic-release-plugin.
    publish {
        dependsOn("generatePatchesList")
    }
}
