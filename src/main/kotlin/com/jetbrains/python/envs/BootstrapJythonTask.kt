package com.jetbrains.python.envs

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.InvalidUserDataException
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import org.gradle.process.ExecOperations
import javax.inject.Inject

@CacheableTask
abstract class BootstrapJythonTask : DefaultTask() {
    @get:Classpath
    abstract val jythonInstallerFiles: ConfigurableFileCollection

    @get:OutputDirectory
    abstract val envDir: DirectoryProperty

    @get:Input
    @get:Optional
    abstract val packages: ListProperty<String>

    @get:Input
    @get:Optional
    abstract val pipInstallOptions: Property<String>

    @Inject
    protected abstract fun getExecOperations(): ExecOperations

    @TaskAction
    fun execute() {
        val targetEnvDir = envDir.get().asFile
        val installerJar = jythonInstallerFiles.singleFile

        if (!installerJar.exists()) {
            throw InvalidUserDataException("Jython installer JAR not found (looked in ${jythonInstallerFiles.files}). Ensure the 'jython' configuration is populated.")
        }

        // Clean existing dir
        if (targetEnvDir.exists()) targetEnvDir.deleteRecursively()
        // Installer creates the dir

        logger.quiet("Creating Jython environment '$name' at $targetEnvDir using ${installerJar.name}")

        try {
            // Use the injected ExecOperations for javaexec
            getExecOperations().javaexec {
                mainClass.set("-jar")
                args = listOf(
                    installerJar.absolutePath, "-s", "-d", targetEnvDir.absolutePath, "-t", "standard"
                )
                classpath = jythonInstallerFiles
            }
        } catch (e: Exception) {
            logger.error("Jython installation failed for $name: ${e.message}", e)
            if (targetEnvDir.exists()) targetEnvDir.deleteRecursively()
            throw GradleException("Jython installation failed for $name: ${e.message}", e)
        }

        // Install packages using pip (assuming Jython standard install includes pip)
        // Jython executable might be 'jython' or 'bin/jython'
        val pythonExecProvider =
            PythonEnvsPlugin.getExecutableProvider(project, "python", project.provider { EnvType.JYTHON }, envDir)
        val pythonExecFile = pythonExecProvider.flatMap { it }.get().asFile
        val pipExecProvider =
            PythonEnvsPlugin.getExecutableProvider(project, "pip", project.provider { EnvType.JYTHON }, envDir)
        val pipExecFile = pipExecProvider.flatMap { it }.get().asFile

        if (pythonExecFile.exists()) {
            PythonEnvsPlugin.runPipUpgrade(project, getExecOperations(), pythonExecFile, pipInstallOptions.orNull)
            if (pipExecFile.exists()) {
                PythonEnvsPlugin.runPipInstall(
                    project, getExecOperations(), pipExecFile, pipInstallOptions.orNull, packages.orNull
                )
            } else {
                logger.warn("Pip executable not found in Jython env $name after install, skipping package installation.")
            }
        } else {
            logger.warn("Jython executable not found in $name after install, skipping pip upgrade and package installation.")
        }
    }
}