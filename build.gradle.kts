plugins {
    // AGP + Kotlin for all modules (no need to apply here)
    id("com.android.application") version "8.12.3" apply false
    id("org.jetbrains.kotlin.android") version "2.0.21" apply false

    // Hilt Gradle plugin (this is what was “not found”)
    id("com.google.dagger.hilt.android") version "2.52" apply false

    // (Optional) KSP if you plan to use it; safe to leave even if unused
    id("com.google.devtools.ksp") version "2.0.21-1.0.25" apply false

    // Add this line for the Compose Compiler
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.21" apply false
}