package com.jetbrains.python.envs

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.ArchiveOperations
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileSystemOperations
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import org.gradle.process.ExecOperations
import javax.inject.Inject

@CacheableTask
abstract class InstallPythonBuildTask : DefaultTask() {

    companion object {
        const val PYENV_URL = "https://github.com/pyenv/pyenv/archive/master.zip"
        const val PATH_TO_PYTHON_BUILD = "pyenv-master/plugins/python-build"
    }

    @get:OutputDirectory
    abstract val installDir: DirectoryProperty

    @Inject
    protected abstract fun getExecOperations(): ExecOperations

    @Inject
    protected abstract fun getFsOps(): FileSystemOperations

    @Inject
    protected abstract fun getArchiveOps(): ArchiveOperations

    @TaskAction
    fun execute() {
        val targetInstallDir = installDir.get().asFile
        val buildDir = project.layout.buildDirectory.get().asFile
        val pyenvZip = buildDir.resolve("pyenv-download.zip")
        val unzipTempDir = buildDir.resolve("python-build-tmp-unzip")

        if (targetInstallDir.exists() && targetInstallDir.listFiles()?.isNotEmpty() == true) {
            logger.info("python-build install directory already exists and is not empty, skipping: $targetInstallDir")
            didWork = false
            return
        }

        buildDir.mkdirs()
        targetInstallDir.mkdirs()

        // Clean potential leftovers from previous failed runs
        unzipTempDir.deleteRecursively()
        unzipTempDir.mkdirs()

        try {
            // 1. Download pyenv zip using ant.get
            logger.quiet("Downloading latest pyenv from github")
            try {
                project.ant.invokeMethod(
                    "get", mapOf(
                        "src" to PYENV_URL,
                        "dest" to pyenvZip.absolutePath,
                        "verbose" to "false",
                        "usetimestamp" to "true"
                    )
                )
            } catch (e: Exception) {
                throw GradleException("Failed to download pyenv zip: ${e.message}", e)
            }

            // 2. Unzip python-build plugin from the archive
            logger.quiet("Unzipping python-build to $unzipTempDir")
            getFsOps().copy {
                from(getArchiveOps().zipTree(pyenvZip))
                into(unzipTempDir)
                include("$PATH_TO_PYTHON_BUILD/**")
                eachFile {
                    path = path.replaceFirst(PATH_TO_PYTHON_BUILD, "")
                }
            }

            // 3. Run install.sh
            val installScript = unzipTempDir.resolve("install.sh")
            if (!installScript.exists()) {
                throw GradleException("install.sh not found in extracted python-build at $unzipTempDir")
            }

            logger.quiet("Installing python-build via bash to $targetInstallDir")
            getExecOperations().exec {
                environment("PREFIX", targetInstallDir.absolutePath)
                commandLine("bash", installScript.absolutePath)
            }

            logger.quiet("Successfully installed python-build to $targetInstallDir")
        } finally {
            // 4. Cleanup
            logger.quiet("Removing temporary files")
            unzipTempDir.deleteRecursively()
            pyenvZip.delete()
        }
    }
}