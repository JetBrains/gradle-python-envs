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
import org.gradle.process.ExecSpec
import javax.inject.Inject

@CacheableTask
abstract class CreateCondaEnvTask : DefaultTask() {
    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val sourceEnvDir: DirectoryProperty

    @get:Input
    abstract val pythonVersion: Property<String>

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

    @Inject
    protected abstract fun getExecOperations(): ExecOperations

    @Inject
    protected abstract fun getFsOps(): FileSystemOperations

    @TaskAction
    fun execute() {
        val sourceDir = sourceEnvDir.get().asFile
        val targetEnvDir = envDir.get().asFile
        val pyVersion = pythonVersion.get()
        val requestedCondaPackages = condaPackages.getOrElse(emptyList())

        // 1. Find source conda executable
        val sourceCondaExecProvider =
            PythonEnvsPlugin.getExecutableProvider(project, "conda", project.provider { EnvType.CONDA }, sourceEnvDir)
        val sourceCondaExecFile = sourceCondaExecProvider.flatMap { it }.get().asFile

        if (!sourceCondaExecFile.exists()) {
            throw GradleException("Source conda executable not found in ${sourceDir.path}. Ensure the base Conda environment task ran successfully.")
        }

        // 2. Clean target dir
        if (targetEnvDir.exists()) getFsOps().delete { delete(targetEnvDir) }

        // 3. Create conda environment
        logger.quiet("Creating conda env '$name' with Python $pyVersion at $targetEnvDir")
        try {
            val createCommand = mutableListOf(
                "create", "-p", targetEnvDir.absolutePath, "-y", // Specify path and auto-confirm
                "python=$pyVersion" // Specify python version
            )
            createCommand.addAll(requestedCondaPackages) // Add user-specified conda packages

            getExecOperations().exec {
                executable(sourceCondaExecFile.absolutePath)
                args = createCommand
            }
        } catch (e: Exception) {
            logger.error("Failed to create conda environment $name: ${e.message}", e)
            if (targetEnvDir.exists()) getFsOps().delete { delete(targetEnvDir) }
            throw GradleException("Failed to create conda environment $name: ${e.message}", e)
        }

        // 4. Install pip packages into the new environment
        val newEnvTypeProvider = project.provider { EnvType.CONDA }
        val newPythonExecProvider =
            PythonEnvsPlugin.getExecutableProvider(project, "python", newEnvTypeProvider, envDir)
        val newPythonExecFile = newPythonExecProvider.flatMap { it }.get().asFile
        val newPipExecProvider = PythonEnvsPlugin.getExecutableProvider(project, "pip", newEnvTypeProvider, envDir)
        val newPipExecFile = newPipExecProvider.flatMap { it }.get().asFile

        if (newPythonExecFile.exists() && newPipExecFile.exists()) {
            logger.quiet("Upgrading pip and installing packages into new conda env $name")
            PythonEnvsPlugin.runPipUpgrade(project, getExecOperations(), newPythonExecFile, pipInstallOptions.orNull)
            PythonEnvsPlugin.runPipInstall(
                project, getExecOperations(), newPipExecFile, pipInstallOptions.orNull, pipPackages.orNull
            )
        } else {
            logger.warn("Python or Pip executable not found in newly created conda env $name. Skipping package installation. Python: ${newPythonExecFile.path}, Pip: ${newPipExecFile.path}")
        }
    }
}