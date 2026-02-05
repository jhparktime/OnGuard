// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    id("com.android.application") version "8.13.2" apply false
    id("com.android.library") version "8.13.2" apply false
    id("org.jetbrains.kotlin.android") version "2.0.21" apply false
    id("com.google.dagger.hilt.android") version "2.51.1" apply false
    id("com.google.devtools.ksp") version "2.0.21-1.0.27" apply false
}

val submoduleDir = layout.projectDirectory.dir("app/java-llama.cpp")
val submoduleCmake = submoduleDir.file("CMakeLists.txt").asFile

val ensureJavaLlamaSubmodule by tasks.registering(Exec::class) {
    description = "Initialize java-llama.cpp submodule"
    group = "build setup"
    workingDir = rootDir
    onlyIf { !submoduleCmake.exists() }
    commandLine("git", "submodule", "update", "--init")
}

val applyJavaLlamaAndroidPatch by tasks.registering(Exec::class) {
    description = "Apply Android NDK patch to java-llama.cpp"
    group = "build setup"
    dependsOn(ensureJavaLlamaSubmodule)
    workingDir = rootDir
    val isWindows = System.getProperty("os.name").lowercase().contains("win")
    if (isWindows) {
        commandLine("cmd", "/c", "scripts\\apply-java-llama-android-patch.bat")
    } else {
        commandLine("bash", "scripts/apply-java-llama-android-patch.sh")
    }
}

// CMake configure가 patch보다 먼저 돌면 FindJava 등으로 실패할 수 있어 의존성 연결
gradle.projectsLoaded {
    rootProject.subprojects.find { it.name == "app" }?.let { appProject ->
        appProject.afterEvaluate {
            appProject.tasks.matching { it.name.lowercase().startsWith("configurecmake") }.configureEach {
                dependsOn(applyJavaLlamaAndroidPatch)
            }
        }
    }
}

tasks.register("clean", Delete::class) {
    delete(rootProject.layout.buildDirectory)
}
