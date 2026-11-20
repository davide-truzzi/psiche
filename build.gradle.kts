plugins {
    application
    id("org.gradlex.extra-java-module-info") version "1.14.2"
}

group = "it.infog"
version = "0.9.3-beta"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

// JavaFX publishes one jar per platform under a classifier; pick the one for the build machine.
val javafxPlatform: String = run {
    val os = System.getProperty("os.name").lowercase()
    val base = when {
        os.contains("win") -> "win"
        os.contains("mac") -> "mac"
        else -> "linux"
    }
    if (System.getProperty("os.arch") == "aarch64") "$base-aarch64" else base
}

repositories {
    mavenCentral()
}

dependencies {
    listOf(libs.javafx.base, libs.javafx.graphics, libs.javafx.controls, libs.javafx.media, libs.javafx.web).forEach {
        implementation(variantOf(it) { classifier(javafxPlatform) })
    }
    implementation(libs.jgit)
    implementation(libs.commonmark)
    implementation(libs.commonmark.strikethrough)
    implementation(libs.commonmark.footnotes)
    implementation(libs.commonmark.tables)
    implementation(libs.openhtmltopdf.pdfbox)
    implementation(libs.slf4j.api)
    // The editor's text, which, unlike JavaFX's TextArea, can show parts of it in another colour, such as comments.
    implementation(libs.richtextfx)
    // The spell checker: Lucene's Hunspell, in pure Java, which reads LibreOffice's dictionaries, bundled in the app.
    implementation(libs.lucene.core)
    implementation(libs.lucene.analysis.common)
    // Reads WebP images, which neither JavaFX nor the JDK can, so a note shows a picture of them; it adds itself to
    // ImageIO, so no code names it.
    runtimeOnly(libs.imageio.webp)
    runtimeOnly(libs.slf4j.simple)

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.assertj.core)
    // The W3C's EPUB validator, the one stores run: every EPUB the tests export must pass it. Two of its dependencies
    // predate Java 9 and repeat packages the JDK has (xml-apis: javax.xml, org.w3c.dom, org.xml.sax; xercesImpl:
    // org.w3c.dom.html), which a modular project may not read from two modules, so EPUBCheck uses the JDK's parsers.
    testImplementation(libs.epubcheck) {
        exclude(group = "xml-apis", module = "xml-apis")
        exclude(group = "xerces", module = "xercesImpl")
    }
    testRuntimeOnly(libs.junit.platform.launcher)
}

// OpenHTMLtoPDF's jars, and ReactFX's and WellBehavedFX's, which RichTextFX needs, name no module, so Gradle would
// leave them on the classpath, out of reach of the app's module; give them module names: Java's own derivation from
// the file names for OpenHTMLtoPDF, and their packages' for the other two. Every other jar is used as it is.
extraJavaModuleInfo {
    failOnMissingModuleInfo.set(false)
    automaticModule("io.github.openhtmltopdf:openhtmltopdf-core", "openhtmltopdf.core")
    automaticModule("io.github.openhtmltopdf:openhtmltopdf-pdfbox", "openhtmltopdf.pdfbox")
    automaticModule("org.reactfx:reactfx", "org.reactfx")
    automaticModule("org.fxmisc.wellbehaved:wellbehavedfx", "org.fxmisc.wellbehaved")
}

application {
    // The command that starts the app, as the packages will be named too: psiche, psiche.AppImage, psiche.exe.
    applicationName = "psiche"
    mainModule = "it.infog.psiche"
    mainClass = "it.infog.psiche.ui.WriterApp"
    applicationDefaultJvmArgs = listOf(
        // JGit is an automatic module, so the modules it needs (JavaEWAH and commons-codec for pack bitmaps and
        // commit-graph filters, java.sql) are not resolved unless added explicitly.
        "--add-modules=ALL-MODULE-PATH,java.sql",
        // JavaFX loads its native libraries itself, which JDK 24+ warns about unless native access is granted; the
        // preview's WebView brings javafx.media along, for the audio and video a page may hold.
        "--enable-native-access=javafx.graphics,javafx.web,javafx.media",
        // The preview paces the web engine's timer through the engine's go-between, an internal class of JavaFX.
        "--add-exports=javafx.web/com.sun.webkit=it.infog.psiche",
    )
}

// The app's version goes into its module, where the About dialog reads it, so it is written down only above.
tasks.compileJava {
    options.javaModuleVersion = provider { version.toString() }
    options.compilerArgs.add("--add-exports=javafx.web/com.sun.webkit=it.infog.psiche")
}

// The licence the app is distributed under goes inside it too, for the About dialog to show without a network.
tasks.processResources {
    from("LICENSE") {
        into("it/infog/psiche/ui/preferences")
    }
}

tasks.test {
    useJUnitPlatform()
    // Keep JGit away from the developer's ~/.gitconfig and ~/.netrc so tests see a clean Git setup.
    val testHome = layout.buildDirectory.dir("test-home").get().asFile
    doFirst { testHome.mkdirs() }
    systemProperty("user.home", testHome.absolutePath)
    environment("XDG_CONFIG_HOME", testHome.absolutePath)
    environment("GIT_CONFIG_NOSYSTEM", "1")
}

// Packaging: Psiche Writer as a standalone app that brings its own Java runtime, so nothing needs installing.
// jlink makes a runtime of the JDK modules the app needs; jpackage puts it next to the app's own jars, which stay on
// the module path, since several are automatic modules jlink cannot link. jpackage only packages for the system it
// runs on, so the AppImage is made on Linux and the Windows zip on Windows.
val packageDir = layout.buildDirectory.dir("package")
val osName = System.getProperty("os.name").lowercase()
val isWindows = osName.contains("win")
val isLinux = !isWindows && !osName.contains("mac")
val toolchainBin = javaToolchains.launcherFor(java.toolchain).map { it.metadata.installationPath.dir("bin") }

fun jdkTool(name: String): String = toolchainBin.get().file(if (isWindows) "$name.exe" else name).asFile.path

val runtimeModules = listOf(
    "java.base", "java.desktop", "java.logging", "java.management", "java.naming", "java.net.http",
    "java.scripting", "java.security.jgss", "java.sql", "java.xml", "jdk.charsets", "jdk.crypto.ec", "jdk.jfr",
    "jdk.unsupported", "jdk.xml.dom",
    // JavaFX's web module needs it. Maven's jdk-jsobject jar is empty: the JDK has the real module.
    "jdk.jsobject",
    // Every language but English: the languages a book can be written in, and their names.
    "jdk.localedata",
)

val jlinkRuntime by tasks.registering(Exec::class) {
    description = "Makes the Java runtime the packaged app brings, of the JDK modules it needs."
    val runtime = packageDir.map { it.dir("runtime") }
    // Made again for another module list or another JDK: without inputs, Gradle would keep the first one made.
    inputs.property("modules", runtimeModules)
    inputs.property("jdk", javaToolchains.launcherFor(java.toolchain).map { it.metadata.javaRuntimeVersion })
    outputs.dir(runtime)
    doFirst { delete(runtime) }
    executable(jdkTool("jlink"))
    args("--add-modules", runtimeModules.joinToString(","), "--strip-debug", "--no-header-files", "--no-man-pages",
        "--output", runtime.get().asFile.path)
}

val jpackageImage by tasks.registering(Exec::class) {
    description = "Makes the app image: the psiche launcher, the runtime and the app's jars, in build/package/image."
    dependsOn(tasks.installDist, jlinkRuntime)
    val image = packageDir.map { it.dir("image") }
    val icon = file("src/package/icons/psiche." + if (isWindows) "ico" else "png")
    inputs.dir(layout.buildDirectory.dir("install/psiche/lib"))
    inputs.file(icon)
    inputs.file("LICENSE")
    inputs.dir("src/package/windows")
    outputs.dir(image)
    doFirst { delete(image) }
    // The licence goes next to the launcher too, where whoever unpacks the app finds it: in the Windows zip beside
    // psiche.exe, and in the AppImage beside the launcher it starts. The app also carries it, for About to show.
    // On Windows, psiche.cmd and its instructions join them: Smart App Control blocks the unsigned psiche.exe, and
    // psiche.cmd starts the app through the runtime's signed javaw.exe instead.
    doLast {
        copy {
            from("LICENSE")
            if (isWindows)
                from("src/package/windows")
            into(image.map { it.dir("psiche") })
        }
    }
    executable(jdkTool("jpackage"))
    // Windows packages take only numbers as the app's version, so a mark such as "-alpha" stays out of it.
    args("--type", "app-image", "--name", "psiche", "--app-version", version.toString().substringBefore("-"),
        "--description", "Psiche Writer", "--icon", icon.path,
        "--runtime-image", packageDir.get().dir("runtime").asFile.path,
        "--module-path", layout.buildDirectory.dir("install/psiche/lib").get().asFile.path,
        "--module", "it.infog.psiche/it.infog.psiche.ui.WriterApp",
        "--java-options", "--add-modules=ALL-MODULE-PATH,java.sql",
        "--java-options", "--enable-native-access=javafx.graphics,javafx.web,javafx.media",
        "--java-options", "--add-exports=javafx.web/com.sun.webkit=it.infog.psiche",
        "--dest", image.get().asFile.path)
}

val appImage by tasks.registering(Exec::class) {
    group = "distribution"
    description = "Makes build/distributions/psiche.AppImage, one file that runs on Linux. Needs appimagetool: on the " +
        "PATH, or named with -Pappimagetool=/path/to/appimagetool."
    onlyIf("AppImages are made on Linux") { isLinux }
    dependsOn(jpackageImage)
    val appDir = packageDir.map { it.dir("AppDir") }
    val appImageFile = layout.buildDirectory.file("distributions/psiche.AppImage")
    // What the AppImage is made of: without inputs, Gradle would take an AppImage already there as up to date, and
    // never make it again with new code.
    inputs.dir(packageDir.map { it.dir("image/psiche") })
    inputs.dir("src/package/linux")
    inputs.file("src/package/icons/psiche.png")
    outputs.file(appImageFile)
    doFirst {
        delete(appDir)
        copy {
            from(packageDir.map { it.dir("image/psiche") })
            into(appDir.map { it.dir("usr/lib/psiche") })
        }
        copy {
            from("src/package/linux")
            into(appDir)
        }
        copy {
            from("src/package/icons/psiche.png")
            into(appDir)
        }
        copy {
            from("src/package/icons/psiche.png")
            into(appDir)
            rename { ".DirIcon" }
        }
        appImageFile.get().asFile.parentFile.mkdirs()
    }
    executable(providers.gradleProperty("appimagetool").getOrElse("appimagetool"))
    environment("ARCH", if (System.getProperty("os.arch") == "aarch64") "aarch64" else "x86_64")
    // appimagetool is itself an AppImage; this lets it run where FUSE is missing, as on many build machines.
    environment("APPIMAGE_EXTRACT_AND_RUN", "1")
    args(appDir.get().asFile.path, appImageFile.get().asFile.path)
}

val portableZip by tasks.registering(Zip::class) {
    group = "distribution"
    description = "Zips the app image into build/distributions/psiche-<system>.zip: on Windows, psiche/psiche.exe and " +
        "its runtime, which run once unzipped anywhere."
    dependsOn(jpackageImage)
    from(packageDir.map { it.dir("image") })
    // A zip keeps no file permissions unless told: the launcher and the runtime's helpers must stay executable.
    eachFile {
        if (file.canExecute())
            permissions { unix("rwxr-xr-x") }
    }
    archiveFileName.set("psiche-" + (if (isWindows) "windows" else if (isLinux) "linux" else "macos") + ".zip")
    destinationDirectory.set(layout.buildDirectory.dir("distributions"))
}
