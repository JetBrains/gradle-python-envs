package com.jetbrains.python.envs

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import org.gradle.process.ExecOperations
import javax.inject.Inject

@CacheableTask
abstract class DownloadTask : DefaultTask() {
    @get:Input
    abstract val url: Property<String>

    @get:OutputFile
    abstract val destination: RegularFileProperty

    @Inject
    protected abstract fun getExecOperations(): ExecOperations

    @TaskAction
    fun execute() {
        val targetFile = destination.get().asFile
        val sourceUrl = url.get()
        targetFile.parentFile.mkdirs()

        try {
            logger.info("Downloading $sourceUrl to $targetFile")
            project.ant.invokeMethod("get", mapOf(
                "src" to sourceUrl,
                "dest" to targetFile.absolutePath,
                "verbose" to "false",
                "usetimestamp" to "true"
            ))
            logger.quiet("Downloaded $sourceUrl to ${targetFile.path}")
        } catch (e: Exception) {
            logger.error("Failed to download $sourceUrl", e)
            targetFile.delete() // Clean up partial download
            throw GradleException("Download failed for $sourceUrl: ${e.message}", e)
        }
    }
}
