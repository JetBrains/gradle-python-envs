package com.jetbrains.python.envs

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileSystemOperations
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.process.ExecOperations
import javax.inject.Inject

@CacheableTask
abstract class CreateVirtualenvTask : DefaultTask() {
    // Inputs describing the source environment
    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val sourceEnvDir: DirectoryProperty

    @get:Input
    abstract val sourceEnvType: Property<EnvType>

    @get:Input
    @get:Optional
    abstract val sourceUse64Bit: Property<Boolean>

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

    @Inject
    protected abstract fun getFsOps(): FileSystemOperations

    @TaskAction
    fun execute() {
        val sourceDir = sourceEnvDir.get().asFile
        val sourceType = sourceEnvType.get()
        val targetEnvDir = envDir.get().asFile

        if (sourceType == EnvType.IRONPYTHON) {
            // This should be caught during configuration, but double-check
            throw GradleException("Cannot create virtualenv from IronPython source environment.")
        }

        // 1. Find source python/pip executables
        val sourcePythonExecProvider =
            PythonEnvsPlugin.getExecutableProvider(project, "python", sourceEnvType, sourceEnvDir, sourceUse64Bit)
        val sourcePythonExecFile = sourcePythonExecProvider.flatMap { it }.get().asFile
        val sourcePipExecProvider = PythonEnvsPlugin.getExecutableProvider(project, "pip", sourceEnvType, sourceEnvDir)
        val sourcePipExecFile = sourcePipExecProvider.flatMap { it }.get().asFile

        if (!sourcePythonExecFile.exists() || !sourcePipExecFile.exists()) {
            throw GradleException("Source python (${sourcePythonExecFile.path}) or pip (${sourcePipExecFile.path}) executable not found in $sourceDir.")
        }

        // 2. Clean target dir
        if (targetEnvDir.exists()) getFsOps().delete { delete(targetEnvDir) }

        // 3. Install/Ensure 'virtualenv' package in source environment
        logger.quiet("Ensuring 'virtualenv' package exists in source environment: $sourceDir")
        try {
            PythonEnvsPlugin.runPipInstall(
                project, getExecOperations(), sourcePipExecFile, pipInstallOptions.orNull, listOf("virtualenv")
            )
        } catch (e: Exception) {
            throw GradleException(
                "Failed to install 'virtualenv' package into source environment $sourceDir: ${e.message}", e
            )
        }

        // 4. Create the virtualenv
        logger.quiet("Creating virtualenv $name from $sourceDir at $targetEnvDir")
        try {
            getExecOperations().exec {
                workingDir(sourceDir) // Working dir shouldn't strictly matter here
                executable(sourcePythonExecFile.absolutePath)
                args("-m", "virtualenv", targetEnvDir.absolutePath, "--always-copy")
            }
        } catch (e: Exception) {
            logger.error("Failed to create virtualenv $name: ${e.message}", e)
            if (targetEnvDir.exists()) getFsOps().delete { delete(targetEnvDir) } // Clean failed attempt
            throw GradleException("Failed to create virtualenv $name: ${e.message}", e)
        }

        // 5. Install packages into the new virtualenv
        val newEnvTypeProvider = project.provider { EnvType.VIRTUALENV }
        val newPythonExecProvider =
            PythonEnvsPlugin.getExecutableProvider(project, "python", newEnvTypeProvider, envDir)
        val newPythonExecFile = newPythonExecProvider.flatMap { it }.get().asFile
        val newPipExecProvider = PythonEnvsPlugin.getExecutableProvider(project, "pip", newEnvTypeProvider, envDir)
        val newPipExecFile = newPipExecProvider.flatMap { it }.get().asFile

        if (newPythonExecFile.exists() && newPipExecFile.exists()) {
            logger.quiet("Upgrading pip and installing packages into new virtualenv $name")
            PythonEnvsPlugin.runPipUpgrade(project, getExecOperations(), newPythonExecFile, pipInstallOptions.orNull)
            PythonEnvsPlugin.runPipInstall(
                project, getExecOperations(), newPipExecFile, pipInstallOptions.orNull, packages.orNull
            )
        } else {
            logger.warn("Python or Pip executable not found in newly created virtualenv $name. Skipping package installation. Python: ${newPythonExecFile.path}, Pip: ${newPipExecFile.path}")
        }
    }
}