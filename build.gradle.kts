plugins {
    java
    application
    id("org.javamodularity.moduleplugin") version "2.0.1"
    id("org.openjfx.javafxplugin") version "0.1.0"
    id("org.beryx.jlink") version "4.1.1"
}

group = "ant"
version = "1.0"

repositories {
    mavenCentral()
}

val junitVersion = "5.10.2"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
}

application {
    mainModule.set("ant.gasmeter")
    mainClass.set("ant.gasmeter.Application")
}

javafx {
    version = "25"
    modules = listOf("javafx.controls", "javafx.fxml", "javafx.web", "javafx.swing")
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

dependencies {
    implementation("org.controlsfx:controlsfx:11.2.1")
    implementation("org.kordamp.bootstrapfx:bootstrapfx-core:0.4.0")
    implementation("eu.hansolo:tilesfx:21.0.3") {
        exclude(group = "org.openjfx")
    }
    implementation("com.github.hypfvieh:dbus-java-transport-native-unixsocket:5.1.1")
    implementation("com.github.hypfvieh:bluez-dbus:0.3.2")
    testImplementation("org.junit.jupiter:junit-jupiter-api:${junitVersion}")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:${junitVersion}")
}

tasks.withType<Test> {
    useJUnitPlatform()
}

jlink {
    imageZip.set(layout.buildDirectory.file("${layout.buildDirectory}/distributions/app-${javafx.platform.classifier}.zip"))
    options.set(listOf("--strip-debug", "--compress", "2", "--no-header-files", "--no-man-pages"))
    launcher {
        name = "Gas Meter"
    }
    targetPlatform("linux", System.getenv("JDK_LINUX_HOME"))
    targetPlatform("linux-arm64", "/home/main/Documents/coding/zulu25.28.85-ca-fx-jdk25.0.0-linux_aarch64")
}


