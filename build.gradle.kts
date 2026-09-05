plugins {
    id("com.android.application") version "9.4.0" apply false
    kotlin("jvm") version "2.4.10" apply false
    kotlin("plugin.compose") version "2.4.10" apply false
}

val testCatalogUpdater = tasks.register<Exec>("testCatalogUpdater") {
    group = "verification"
    description = "Runs the development-only card catalog updater tests."
    environment("PYTHONDONTWRITEBYTECODE", "1")
    commandLine(
        "python3",
        "-m",
        "unittest",
        "discover",
        "-s",
        "tools/update-card-catalog/tests",
        "-p",
        "test_*.py",
    )
}

tasks.register("test") {
    group = "verification"
    description = "Runs all JVM and catalog updater tests."
    dependsOn(":cycle-core:test", ":app:testDebugUnitTest", testCatalogUpdater)
}
