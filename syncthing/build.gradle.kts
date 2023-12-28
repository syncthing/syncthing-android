import ru.vyarus.gradle.plugin.python.task.PythonTask

plugins {
    id("ru.vyarus.use-python") version "3.0.0"
}

tasks.register<PythonTask>("buildNative") {
    val ndkVersionShared = rootProject.extra.get("ndkVersionShared")
    environment("NDK_VERSION", "$ndkVersionShared")
    inputs.dir("$projectDir/src/")
    outputs.dir("$projectDir/../app/src/main/jniLibs/")
    command = "-u ./build-syncthing.py"
}

/**
 * Use separate task instead of standard clean(), so these folders aren't deleted by `gradle clean`.
 */
tasks.register<Delete>("cleanNative") {
    delete("$projectDir/../app/src/main/jniLibs/")
    delete("gobuild")
}
