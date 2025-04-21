package com.jetbrains.python.envs

import org.apache.tools.ant.taskdefs.condition.Os
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileSystemOperations
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import org.gradle.process.ExecOperations
import java.net.URL
import javax.inject.Inject

@CacheableTask
abstract class BootstrapCondaTask : DefaultTask() {
    @get:Input
    abstract val condaVersion: Property<String>

    @get:OutputDirectory
    abstract val envDir: DirectoryProperty

    @get:Input
    @get:Optional
    abstract val pipPackages: ListProperty<String>

    @get:Input
    @get:Optional
    abstract val condaPackages: ListProperty<String>

    @get:Input
    @get:Optional
    abstract val pipInstallOptions: Property<String>

    // Internal properties for download logic
    @get:Internal
    val installerUrl: Provider<URL> = PythonEnvsPlugin.getUrlToDownloadConda(condaVersion)

    @get:Internal
    val installerFile: RegularFileProperty = project.objects.fileProperty().convention(
        project.layout.buildDirectory.file(installerUrl.map { url ->
            val installerName = url.path.substring(url.path.lastIndexOf('/') + 1)
            "download-cache/$installerName"
        })
    )

    @Inject
    protected abstract fun getExecOperations(): ExecOperations

    @Inject
    protected abstract fun getFsOps(): FileSystemOperations

    @TaskAction
    fun execute() {
        val targetEnvDir = envDir.get().asFile
        val installer = installerFile.get().asFile
        val url = installerUrl.get()

        // 1. Clean target dir
        if (targetEnvDir.exists()) getFsOps().delete { delete(targetEnvDir) }

        // 2. Download installer
        logger.quiet("Downloading ${installer.name}")
        installer.parentFile.mkdirs()
        try {
            project.ant.invokeMethod(
                "get", mapOf(
                    "src" to url, "dest" to installer.absolutePath, "verbose" to "false", "usetimestamp" to "true"
                )
            )
        } catch (e: Exception) {
            throw GradleException("Failed to download pyenv zip: ${e.message}", e)
        }

        // 3. Run installer
        logger.quiet("Bootstrapping Conda ($condaVersion) to $targetEnvDir using ${installer.name}")
        try {
            if (Os.isFamily(Os.FAMILY_WINDOWS)) {
                getExecOperations().exec {
                    // Windows Miniconda/Anaconda installer args for silent install
                    commandLine(
                        installer.absolutePath,
                        "/InstallationType=JustMe",
                        "/AddToPath=0",
                        "/RegisterPython=0",
                        "/S",
                        "/D=${targetEnvDir.absolutePath}"
                    )
                }
            } else {
                getExecOperations().exec {
                    // Unix Miniconda/Anaconda installer args for silent install
                    commandLine("bash", installer.absolutePath, "-b", "-p", targetEnvDir.absolutePath)
                }
            }
        } catch (e: Exception) {
            logger.error("Conda installation failed for $name: ${e.message}", e)
            // Clean up potentially partially created env dir
            if (targetEnvDir.exists()) getFsOps().delete { delete(targetEnvDir) }
            throw GradleException("Conda installation failed for $name: ${e.message}", e)
        }

        // 4. Install packages (pip and conda)
        // Base conda env is CONDA type
        val condaEnvTypeProvider = project.provider { EnvType.CONDA }
        val pythonExecProvider = PythonEnvsPlugin.getExecutableProvider(project, "python", condaEnvTypeProvider, envDir)
        val pythonExecFile = pythonExecProvider.flatMap { it }.get().asFile
        val pipExecProvider = PythonEnvsPlugin.getExecutableProvider(project, "pip", condaEnvTypeProvider, envDir)
        val pipExecFile = pipExecProvider.flatMap { it }.get().asFile
        val condaExecProvider = PythonEnvsPlugin.getExecutableProvider(project, "conda", condaEnvTypeProvider, envDir)
        val condaExecFile = condaExecProvider.flatMap { it }.get().asFile

        if (pythonExecFile.exists()) {
            PythonEnvsPlugin.runPipUpgrade(project, getExecOperations(), pythonExecFile, pipInstallOptions.orNull)
            if (pipExecFile.exists()) {
                PythonEnvsPlugin.runPipInstall(
                    project, getExecOperations(), pipExecFile, pipInstallOptions.orNull, pipPackages.orNull
                )
            } else {
                logger.warn("Pip executable not found in base Conda env $name after install, skipping pip package installation.")
            }
        } else {
            logger.warn("Python executable not found in base Conda env $name after install, skipping pip upgrade/package installation.")
        }

        if (condaExecFile.exists()) {
            PythonEnvsPlugin.runCondaInstall(
                project, getExecOperations(), condaExecFile, targetEnvDir, condaPackages.orNull
            )
        } else {
            logger.warn("Conda executable not found in base Conda env $name after install, skipping conda package installation.")
        }

        // 5. Cleanup installer
        logger.quiet("Deleting Conda installer $installer")
        installer.delete()
    }
}