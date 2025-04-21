package com.jetbrains.python.envs

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.DirectoryProperty
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
import java.net.URI
import java.nio.file.Files
import java.nio.file.Paths
import javax.inject.Inject

@CacheableTask
abstract class BootstrapPythonUnixTask : DefaultTask() {
    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val pythonBuildDir: DirectoryProperty

    @get:Input
    @get:Optional
    abstract val pythonVersion: Property<String>

    @get:Input
    @get:Optional
    abstract val patchFileUri: Property<String>

    @get:OutputDirectory
    abstract val envDir: DirectoryProperty

    @get:Input
    abstract val envType: Property<EnvType>

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
        val buildExecutable = pythonBuildDir.file("bin/python-build").get().asFile
        val version = pythonVersion.get()
        val type = envType.get()

        if (!buildExecutable.exists()) {
            throw GradleException("python-build executable not found at ${buildExecutable.path}. Ensure install_python_build task ran successfully.")
        }
        if (!buildExecutable.canExecute()) {
            buildExecutable.setExecutable(true)
        }

        // Clean existing dir before build
        if (targetEnvDir.exists()) targetEnvDir.deleteRecursively()
        targetEnvDir.mkdirs()

        logger.quiet("Creating $type '$name' ($version) at $targetEnvDir using python-build")

        try {
            getExecOperations().exec {
                executable(buildExecutable.absolutePath)
                val patchUri = patchFileUri.orNull
                if (patchUri != null) {
                    logger.quiet("Applying patch from $patchUri to $name")
                    val patchInput = try {
                        // Try path first, then URL
                        val path = Paths.get(patchUri)
                        if (Files.isRegularFile(path)) path.toFile().inputStream()
                        else URI(patchUri).toURL().openStream()
                    } catch (e: Exception) {
                        throw GradleException("Patch file URI '$patchUri' is not a valid file path or URL", e)
                    }
                    standardInput = patchInput
                    args("-p", version, targetEnvDir.absolutePath)
                } else {
                    args(version, targetEnvDir.absolutePath)
                }
                // Consider capturing output/error streams if needed for debugging
            }
            logger.quiet("Successfully created environment $name.")

        } catch (e: Exception) {
            // Check if python executable exists even if build failed (e.g., tests failed)
            val pythonExecProvider = PythonEnvsPlugin.getExecutableProvider(project, "python", envType, envDir)
            val pythonExecFile = pythonExecProvider.flatMap { it }.get().asFile

            if (!pythonExecFile.exists()) {
                // If python exec doesn't exist, it's a fatal error
                logger.error("Failed to create Python environment $name: ${e.message}", e)
                if (targetEnvDir.exists()) targetEnvDir.deleteRecursively() // Clean up failed attempt
                throw GradleException("Python environment creation failed for $name: ${e.message}", e)
            } else {
                // Log a warning but proceed if python seems functional
                logger.warn("python-build execution for $name finished with an error, but the resulting environment seems usable. Warning: ${e.message}")
            }
        }

        // Run pip upgrade and install regardless of minor build errors if env looks valid
        val pythonExecProvider = PythonEnvsPlugin.getExecutableProvider(project, "python", envType, envDir)
        val pythonExecFile = pythonExecProvider.flatMap { it }.get().asFile
        val pipExecProvider = PythonEnvsPlugin.getExecutableProvider(project, "pip", envType, envDir)
        val pipExecFile = pipExecProvider.flatMap { it }.get().asFile

        if (pythonExecFile.exists()) {
            PythonEnvsPlugin.runPipUpgrade(project, getExecOperations(), pythonExecFile, pipInstallOptions.orNull)
            if (pipExecFile.exists()) {
                PythonEnvsPlugin.runPipInstall(
                    project, getExecOperations(), pipExecFile, pipInstallOptions.orNull, packages.orNull
                )
            } else {
                logger.warn("Pip executable not found in $name after build, skipping package installation.")
            }
        } else {
            logger.warn("Python executable not found in $name after build, skipping pip upgrade and package installation.")
        }
    }
}