package com.jetbrains.python.envs

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.ArchiveOperations
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileSystemOperations
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
import java.net.URI
import javax.inject.Inject

@CacheableTask
abstract class BootstrapPythonFromZipTask : DefaultTask() {
    @get:Input
    abstract val zipUrl: Property<URI>

    @get:Input
    @get:Optional
    abstract val envType: Property<EnvType>

    @get:Input
    @get:Optional
    abstract val use64Bit: Property<Boolean>

    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val getPipScript: RegularFileProperty

    @get:OutputDirectory
    abstract val envDir: DirectoryProperty

    @get:Input
    @get:Optional
    abstract val packages: ListProperty<String>

    @get:Input
    @get:Optional
    abstract val pipInstallOptions: Property<String>

    @get:Internal
    abstract val zipArchive: RegularFileProperty

    @Inject
    protected abstract fun getExecOperations(): ExecOperations

    @Inject
    protected abstract fun getFsOps(): FileSystemOperations

    @Inject
    protected abstract fun getArchiveOps(): ArchiveOperations


    init {
        zipArchive.set(project.layout.buildDirectory.file(zipUrl.map { uri ->
            val path = uri.path ?: "unknown.zip"
            val archiveName = path.substring(path.lastIndexOf('/') + 1)
            "download-cache/$archiveName"
        }))
    }

    @TaskAction
    fun execute() {
        val targetEnvDir = envDir.get().asFile
        val uri = zipUrl.get()
        val archive = zipArchive.get().asFile
        val type = envType.orNull

        // 1. Clean target dir
        if (targetEnvDir.exists()) getFsOps().delete { delete(targetEnvDir) }
        targetEnvDir.mkdirs()

        // 2. Download zip
        val urlForDownload = uri.toURL()
        logger.quiet("Downloading $urlForDownload to $archive")
        archive.parentFile.mkdirs()
        try {
            project.ant.invokeMethod(
                "get", mapOf(
                    "src" to urlForDownload,
                    "dest" to archive.absolutePath,
                    "verbose" to "false",
                    "usetimestamp" to "true"
                )
            )
        } catch (e: Exception) {
            throw GradleException("Download failed for $uri: ${e.message}", e)
        }

        // 3. Unzip
        logger.quiet("Unzipping $archive to $targetEnvDir")
        try {
            getFsOps().copy {
                from(getArchiveOps().zipTree(archive))
                into(targetEnvDir)
            }
        } catch (e: Exception) {
            // Clean up before throwing
            if (targetEnvDir.exists()) getFsOps().delete { delete(targetEnvDir) }
            archive.delete()
            throw GradleException("Failed to unzip $archive: ${e.message}", e)
        }

        // 4. Handle single top-level directory in archive
        val topLevelFiles = targetEnvDir.listFiles()
        if (topLevelFiles != null && topLevelFiles.size == 1 && topLevelFiles[0].isDirectory) {
            val intermediateDir = topLevelFiles[0]
            logger.quiet("Moving contents from intermediate directory ${intermediateDir.name} to ${targetEnvDir.path}")
            getFsOps().copy {
                from(intermediateDir)
                into(targetEnvDir)
            }
            getFsOps().delete { delete(intermediateDir) }
        }

        // 5. Install/Upgrade Pip and Packages (if type is known)
        if (type != null) {
            val pythonExecProvider =
                PythonEnvsPlugin.getExecutableProvider(project, "python", envType, envDir, use64Bit)
            val pythonExecFile = pythonExecProvider.flatMap { it }.get().asFile
            val pipExecProvider = PythonEnvsPlugin.getExecutableProvider(project, "pip", envType, envDir)
            val pipExecFile = pipExecProvider.flatMap { it }.get().asFile

            var pipFound = pipExecFile.exists()

            if (!pipFound && pythonExecFile.exists()) {
                logger.warn("Pip executable not found in $name. Attempting installation.")
                try {
                    if (type == EnvType.IRONPYTHON) {
                        logger.quiet("Using 'ipy -m ensurepip' for IronPython.")
                        getExecOperations().exec {
                            executable(pythonExecFile.absolutePath)
                            args("-m", "ensurepip")
                        }
                    } else {
                        logger.quiet("Using get-pip.py script.")
                        val getPip = getPipScript.get().asFile
                        if (!getPip.exists()) throw GradleException("get-pip.py script not found at ${getPip.path}")
                        getExecOperations().exec {
                            executable(pythonExecFile.absolutePath)
                            args(getPip.absolutePath)
                        }
                    }
                    pipFound = pipExecProvider.flatMap { it }.get().asFile.exists()
                } catch (e: Exception) {
                    logger.error("Failed to install pip for $name: ${e.message}", e)
                }
            }

            if (pipFound) {
                if (pythonExecFile.exists()) {
                    PythonEnvsPlugin.runPipUpgrade(
                        project, getExecOperations(), pythonExecFile, pipInstallOptions.orNull
                    )
                    PythonEnvsPlugin.runPipInstall(
                        project, getExecOperations(), pipExecFile, pipInstallOptions.orNull, packages.orNull
                    )
                } else {
                    logger.warn("Python executable not found in $name, cannot run pip upgrade/install.")
                }
            } else {
                logger.warn("Pip executable not found or install failed for $name. Skipping package installation.")
            }
        } else {
            logger.info("Environment type not specified for $name, skipping pip/package installation.")
        }

        // 6. Cleanup zip
        logger.quiet("Deleting $archive")
        archive.delete()
    }
}