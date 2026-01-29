// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    // Atualizado para 8.2.2 para suporte total ao Java 17 e Firebase 34
    id("com.android.application") version "8.2.2" apply false
    // Atualizado para 1.9.24 para resolver o erro de incompatibilidade de Metadata do Kotlin
    id("org.jetbrains.kotlin.android") version "2.0.0" apply false
    id("com.google.gms.google-services") version "4.4.1" apply false
}
