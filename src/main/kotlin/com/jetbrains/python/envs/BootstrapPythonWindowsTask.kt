package com.jetbrains.python.envs

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.process.ExecOperations
import javax.inject.Inject

@CacheableTask
abstract class BootstrapPythonWindowsTask : DefaultTask() {
    @get:Input
    abstract val pythonVersion: Property<String>

    @get:Input
    @get:Optional
    abstract val use64Bit: Property<Boolean>

    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val getPipScript: RegularFileProperty

    @get:OutputDirectory
    abstract val envDir: DirectoryProperty

    // No EnvType needed explicitly? Assumed PYTHON.

    @get:Input
    @get:Optional
    abstract val packages: ListProperty<String>

    @get:Input
    @get:Optional
    abstract val pipInstallOptions: Property<String>

    // Internal property to hold the downloaded installer path
    @get:Internal
    abstract val installerFile: RegularFileProperty

    @Inject
    protected abstract fun getExecOperations(): ExecOperations

    init {
        // Calculate installer file path based on inputs
        installerFile.set(project.layout.buildDirectory.file(pythonVersion.map { version ->
            val archSuffix = if (use64Bit.getOrElse(true)) "-amd64" else "" // Simpler suffix for modern .exe installers
            "python-$version$archSuffix.exe" // Assume modern .exe installer >= 3.5
        }))
    }

    @TaskAction
    fun execute() {
        val targetEnvDir = envDir.get().asFile
        val version = pythonVersion.get()
        val installer = installerFile.get().asFile
        val installerUrl = "https://www.python.org/ftp/python/$version/${installer.name}"

        // Clean existing dir
        if (targetEnvDir.exists()) targetEnvDir.deleteRecursively()
        targetEnvDir.mkdirs()

        // 1. Download installer using ant.get
        logger.quiet("Downloading ${installer.name} from $installerUrl")
        try {
            project.ant.invokeMethod(
                "get", mapOf(
                    "src" to installerUrl,
                    "dest" to installer.absolutePath,
                    "verbose" to "false",
                    "usetimestamp" to "true"
                )
            )
        } catch (e: Exception) {
            throw GradleException("Failed to download Python installer from $installerUrl: ${e.message}", e)
        }

        // 2. Run installer
        logger.quiet("Installing Python $version to $targetEnvDir using ${installer.name}")
        try {
            // Args for modern Python 3.5+ .exe installer (silent)
            getExecOperations().exec {
                commandLine(
                    installer.absolutePath,
                    "/quiet",
                    "InstallAllUsers=0",
                    "Include_launcher=0",
                    "TargetDir=${targetEnvDir.absolutePath}",
                    "PrependPath=0",
                    "Shortcuts=0",
                    "AssociateFiles=0",
                    "Include_doc=0",
                    "Include_pip=1",
                    "Include_tcltk=0",
                    "Include_test=0"
                )
                // isIgnoreExitValue = true // Some installers might return non-zero even on success? Let's not ignore for now.
            }
        } catch (e: Exception) {
            logger.error("Python installer failed for $name: ${e.message}", e)
            if (targetEnvDir.exists()) targetEnvDir.deleteRecursively() // Clean up failed attempt
            throw GradleException("Python installation failed for $name: ${e.message}", e)
        }

        // 3. Verify pip and install/upgrade
        val pythonExecProvider = PythonEnvsPlugin.getExecutableProvider(
            project,
            "python",
            project.provider { EnvType.PYTHON },
            envDir,
            use64Bit
        )
        val pythonExecFile = pythonExecProvider.flatMap { it }.get().asFile
        val pipExecProvider =
            PythonEnvsPlugin.getExecutableProvider(project, "pip", project.provider { EnvType.PYTHON }, envDir)
        val pipExecFile = pipExecProvider.flatMap { it }.get().asFile

        if (!pythonExecFile.exists()) {
            logger.error("Python executable not found after installation in $targetEnvDir")
            throw GradleException("Python installation succeeded but executable not found for $name.")
        }

        if (!pipExecFile.exists()) {
            logger.warn("Pip not found after installation for $name, attempting manual install with get-pip.py")
            try {
                getExecOperations().exec {
                    executable(pythonExecFile.absolutePath)
                    args(getPipScript.get().asFile.absolutePath)
                }
            } catch (e: Exception) {
                logger.error("get-pip.py execution failed for $name: ${e.message}", e)
                // Proceed without pip if get-pip fails? Or throw? Let's throw.
                throw GradleException("Failed to manually install pip for $name: ${e.message}", e)
            }
        }

        // Re-check pip existence after potential manual install
        if (pipExecFile.exists()) {
            PythonEnvsPlugin.runPipUpgrade(project, getExecOperations(), pythonExecFile, pipInstallOptions.orNull)
            PythonEnvsPlugin.runPipInstall(
                project, getExecOperations(), pipExecFile, pipInstallOptions.orNull, packages.orNull
            )
        } else {
            logger.warn("Pip still not found after attempting manual install for $name. Skipping package installation.")
        }

        // Keep the installer file for potential repair/uninstall? Or delete? Let's delete for now.
        // installer.delete()
    }
}