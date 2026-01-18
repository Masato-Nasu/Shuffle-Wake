// Root build script (Kotlin DSL)
plugins {
    // Use AGP 8.13.x to match Gradle 8.13 (Android Studio error you hit)
    id("com.android.application") version "9.0.0" apply false
    id("org.jetbrains.kotlin.android") version "2.2.10" apply false
}
