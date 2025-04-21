package com.jetbrains.python.envs

import org.apache.tools.ant.taskdefs.condition.Os
import org.gradle.api.GradleException
import org.gradle.api.InvalidUserDataException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.file.Directory
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.TaskProvider
import org.gradle.kotlin.dsl.assign
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.named
import org.gradle.kotlin.dsl.register
import org.gradle.kotlin.dsl.repositories
import org.gradle.process.ExecOperations
import java.io.File
import java.net.URI
import java.net.URL


class PythonEnvsPlugin : Plugin<Project> {

    companion object {
        private val osName: String = System.getProperty("os.name").replace(" ", "").let {
            if (it.contains("Windows", ignoreCase = true)) "Windows" else it
        }

        private val isWindows: Boolean = Os.isFamily(Os.FAMILY_WINDOWS)
        private val isUnix: Boolean = Os.isFamily(Os.FAMILY_UNIX)
        private val isMacOsX: Boolean = Os.isFamily(Os.FAMILY_MAC)

        fun getUrlToDownloadConda(condaVersionProvider: Provider<String>): Provider<URL> {
            return condaVersionProvider.map { condaVersion ->
                val repository = if (condaVersion.contains("miniconda", ignoreCase = true)) "miniconda" else "archive"
                val arch = getArch()
                val ext = if (isWindows) "exe" else "sh"
                URI("https://repo.continuum.io/$repository/${condaVersion}-$osName-$arch.$ext").toURL()
            }
        }

        private fun getArch(): String {
            var arch = System.getProperty("os.arch")
            arch = when {
                arch.matches(Regex("x86|i386|ia-32|i686")) -> "x86"
                arch.matches(Regex("x86_64|amd64|x64|x86-64")) -> "x86_64"
                arch.matches(Regex("arm|arm-v7|armv7|arm32")) -> "armv7l"
                arch.matches(Regex("aarch64|arm64|arm-v8")) -> if (isMacOsX) "arm64" else "aarch64"
                else -> arch // Keep original if no match
            }
            return arch
        }

        internal fun getExecutableProvider(
            project: Project,
            executable: String,
            envTypeProvider: Provider<EnvType>,
            envDirProvider: Provider<out Directory>,
            use64BitProvider: Provider<Boolean>? = null
        ): Provider<RegularFileProperty> {
            return project.provider {
                val envType = envTypeProvider.get()
                val envDir = envDirProvider.get().asFile
                val use64Bit = use64BitProvider?.orNull

                val pathString = when (envType) {
                    EnvType.PYTHON, EnvType.CONDA -> when (executable) {
                        "pip", "virtualenv", "conda" -> if (isWindows) "Scripts/${executable}.exe" else "bin/${executable}"
                        else -> if (executable.startsWith("python")) {
                            if (isWindows) "${executable}.exe" else "bin/${executable}"
                        } else {
                            // Consider returning null or failing later if not found
                            throw RuntimeException("$executable is not supported for $envType yet")
                        }
                    }

                    EnvType.JYTHON, EnvType.PYPY -> {
                        val execName = if (envType == EnvType.JYTHON && executable == "python") "jython" else executable
                        "bin/$execName${if (isWindows) ".bat" else ""}"
                    }

                    EnvType.IRONPYTHON -> when (executable) {
                        "ipy", "python" -> "net45/${if (use64Bit == true) "ipy.exe" else "ipy32.exe"}"
                        else -> "Scripts/${executable}.exe"
                    }

                    EnvType.VIRTUALENV -> if (isWindows) "Scripts/${executable}.exe" else "bin/${executable}"
                }
                project.objects.fileProperty().fileValue(File(envDir, pathString))
            }
        }


        // Replace old getPipFile with registration using DownloadTask
        private fun registerGetPipTask(project: Project, bootstrapDir: DirectoryProperty): TaskProvider<DownloadTask> {
            return project.tasks.register<DownloadTask>("prepareGetPipScript") {
                description = "Downloads the get-pip.py bootstrap script."
                group = "Build Environment Setup"
                url.set("https://bootstrap.pypa.io/get-pip.py")
                destination.set(bootstrapDir.file("get-pip.py"))
            }
        }

        internal fun runPipUpgrade(
            project: Project, execOps: ExecOperations, pythonExecutable: File, pipOptions: String?
        ) {
            if (!pythonExecutable.exists()) {
                project.logger.warn("Cannot upgrade pip/setuptools: Python executable not found at ${pythonExecutable.path}")
                return
            }
            project.logger.quiet("Force upgrading pip and setuptools using ${pythonExecutable.path}")
            val command = mutableListOf(
                pythonExecutable.absolutePath, "-m", "pip", "install", "--upgrade", "--force-reinstall"
            )
            pipOptions?.split(" ")?.filter { it.isNotBlank() }?.let { command.addAll(it) }
            command.addAll(listOf("pip", "setuptools"))

            project.logger.debug("Executing pip upgrade: ${command.joinToString(" ")}")
            val result = execOps.exec {
                commandLine = command
                isIgnoreExitValue = true
            }
            if (result.exitValue != 0) {
                project.logger.warn("Pip/setuptools upgrade command exited with code ${result.exitValue}. Check logs.")
            }
        }

        internal fun runPipInstall(
            project: Project, execOps: ExecOperations, pipExecutable: File, pipOptions: String?, packages: List<String>?
        ) {
            if (packages.isNullOrEmpty()) return
            if (!pipExecutable.exists()) {
                throw GradleException("Cannot install packages: pip executable not found at ${pipExecutable.path}")
            }

            project.logger.quiet("Installing packages via pip using ${pipExecutable.path}: $packages")
            val command = mutableListOf(pipExecutable.absolutePath, "install")
            pipOptions?.split(" ")?.filter { it.isNotBlank() }?.let { command.addAll(it) }
            command.addAll(packages)

            project.logger.debug("Executing pip install: ${command.joinToString(" ")}")
            val result = execOps.exec {
                commandLine = command
            }
            if (result.exitValue != 0) {
                throw GradleException("pip install failed with packages $packages. Exit code: ${result.exitValue}")
            }
        }

        internal fun runCondaInstall(
            project: Project,
            execOps: ExecOperations,
            condaExecutable: File,
            targetEnvDir: File,
            packages: List<String>?
        ) {
            if (packages.isNullOrEmpty()) return
            if (!condaExecutable.exists()) {
                throw GradleException("Cannot install conda packages: conda executable not found at ${condaExecutable.path}")
            }

            project.logger.quiet("Installing packages via conda using ${condaExecutable.path} into ${targetEnvDir.path}: $packages")
            val command = mutableListOf(
                condaExecutable.absolutePath, "install", "-y", "-p", targetEnvDir.absolutePath
            )
            command.addAll(packages)

            project.logger.debug("Executing conda install: ${command.joinToString(" ")}")
            val result = execOps.exec {
                commandLine = command
            }
            if (result.exitValue != 0) {
                throw GradleException("conda install failed with packages $packages. Exit code: ${result.exitValue}")
            }
        }

    }

    override fun apply(project: Project) {
        val envs = project.extensions.create(
            "envs", PythonEnvsExtension::class.java, project.objects, project.layout
        )

        project.repositories {
            mavenCentral()
        }

        // Master tasks for aggregation
        val buildPythons = project.tasks.register("build_pythons") {
            group = "Build Environment"
            description = "Builds configured Python/Jython/PyPy environments."
        }
        val buildPythonsFromZip = project.tasks.register("build_pythons_from_zip") {
            group = "Build Environment"
            description = "Builds Python/IronPython environments from pre-built zip archives."
        }
        val buildVirtualenvs = project.tasks.register("build_virtual_envs") {
            group = "Build Environment"
            description = "Creates virtual environments based on existing Python environments."
        }
        val buildCondas = project.tasks.register("build_condas") {
            group = "Build Environment"
            description = "Bootstraps base Conda (Miniconda/Anaconda) environments."
        }
        val buildCondaEnvs = project.tasks.register("build_conda_envs") {
            group = "Build Environment"
            description = "Creates Conda environments based on existing Conda installations."
        }

        // --- Initial Configuration Phase ---
        // Register tasks that don't depend on potentially implicit configurations

        val getPipTask = registerGetPipTask(project, envs.bootstrapDirectory)
        val installPythonBuildTaskProvider =
            if (isUnix) registerInstallPythonBuildTask(project, envs.bootstrapDirectory.dir("python-build")) else null
        val jythonConfiguration = project.configurations.create("jython")

        envs.pythonsFromZip.all {
            configurePythonFromZipTask(project, this, envs, getPipTask, buildPythonsFromZip)
        }

        project.afterEvaluate {
            // Step 1: Validate configurations
            if (envs.zipRepository.isPresent && envs.shouldUseZipsFromRepository.getOrElse(false)) {
                envs.pythons.forEach { pythonEnv ->
                    if (pythonEnv.patchFileUri.isPresent) {
                        throw InvalidUserDataException("A patch is defined for a pre-built Python")
                    }
                    val newEnv = objects.newInstance(Python::class.java, name).apply {
                        this.version.set(pythonEnv.version)
                        this.url.set(
                            getUrlFromRepository(
                                envs.zipRepository.get(), "python", pythonEnv.version.get(), pythonEnv.use64Bit.get()
                            )
                        )
                        this.packages.set(pythonEnv.packages)
                    }
                    configurePythonFromZipTask(project, newEnv, envs, getPipTask, buildPythonsFromZip)
                }
            } else {
                envs.pythons.all {
                    configurePythonTask(
                        project,
                        this,
                        envs,
                        installPythonBuildTaskProvider,
                        jythonConfiguration,
                        getPipTask,
                        buildPythons
                    )
                }
            }

            // Step 2: Ensure Jython dependency is added if needed (needs to be in afterEvaluate)
            if (envs.pythons.any { it.type.getOrElse(EnvType.PYTHON) == EnvType.JYTHON }) {
                project.dependencies { add("jython", "org.python:jython-installer:2.7.1") }
            }

            // Step 3: Ensure all required Conda configurations exist
            val requiredSourceCondaNames = envs.condaEnvs.map { it.sourceEnvName.get() }.toSet()
            val missingSourceCondaNames = requiredSourceCondaNames.filter { sourceName ->
                envs.condas.findByName(sourceName) == null
            }
            missingSourceCondaNames.forEach { sourceName ->
                project.logger.info("Defining implicit source conda environment '$sourceName' with version '\${PythonEnvsExtension.CONDA_DEFAULT_VERSION}'.")
                envs.condas.maybeCreate(sourceName).apply {
                    this.version.set(PythonEnvsExtension.CONDA_DEFAULT_VERSION)
                }
            }

            // Step 4: Register tasks for ALL Conda configurations (explicit and implicit)
            envs.condas.forEach { condaConfig ->
                val taskName = "Bootstrap_${condaConfig.name}"
                if (project.tasks.findByName(taskName) == null) {
                    project.logger.debug("Registering task '$taskName' for conda config '${condaConfig.name}'")
                    val provider = registerCondaTask(project, condaConfig, envs)
                    buildCondas.configure { dependsOn(provider) }
                } else {
                    project.logger.debug("Task '$taskName' already registered for conda config '${condaConfig.name}'")
                }
            }

            // Step 5: Configure dependent tasks (Virtualenv)
            envs.virtualEnvs.forEach { virtualEnvConfig ->
                configureVirtualenvTask(project, virtualEnvConfig, envs, buildVirtualenvs)
            }

            // Step 6: Configure dependent tasks (CondaEnv)
            envs.condaEnvs.forEach { condaEnvConfig ->
                configureCondaEnvTask(project, condaEnvConfig, envs, buildCondaEnvs)
            }
        }

        // Configure master build task dependencies
        project.tasks.register("build_envs") {
            group = "Build Environment"
            description = "Builds all configured Python environments (Python, Conda, Virtualenv, etc.)."
            dependsOn(buildPythons, buildPythonsFromZip, buildVirtualenvs, buildCondas, buildCondaEnvs)
        }
    }

    // --- Helper methods to encapsulate configuration logic ---

    private fun getUrlFromRepository(zipRepository: URL, type: String, version: String, use64Bit: Boolean = true) =
        zipRepository.toURI().resolve("$type-$version-${if (use64Bit) "64" else "32"}.zip")

    private fun configurePythonTask(
        project: Project,
        env: Python,
        envs: PythonEnvsExtension,
        installPythonBuildTaskProvider: TaskProvider<InstallPythonBuildTask>?,
        jythonConfiguration: org.gradle.api.artifacts.Configuration,
        getPipTask: TaskProvider<DownloadTask>,
        buildPythons: TaskProvider<Task>
    ) {
        val bootstrapTask = when (val resolvedType = env.type.getOrElse(EnvType.PYTHON)) {
            EnvType.PYTHON, EnvType.PYPY -> {
                if (isUnix) {
                    if (installPythonBuildTaskProvider == null) {
                        project.logger.warn("Unix environment requested but installPythonBuildTask is not available. Skipping ${env.name}")
                        null
                    } else {
                        registerPythonUnixTask(project, env, installPythonBuildTaskProvider, envs.pipInstallOptions)
                    }
                } else if (isWindows) {
                    registerPythonWindowsTask(project, env, getPipTask, envs.pipInstallOptions)
                } else {
                    project.logger.warn("Unsupported OS for Python/PyPy build via source/installer: $osName for env ${env.name}")
                    null
                }
            }

            EnvType.JYTHON -> registerJythonTask(project, env, jythonConfiguration, envs.pipInstallOptions)
            else -> {
                project.logger.error("$resolvedType is not supported in the 'pythons' block for env ${env.name}.")
                null
            }
        }
        bootstrapTask?.let { buildPythons.configure { dependsOn(it) } }
    }

    private fun configurePythonFromZipTask(
        project: Project,
        env: Python,
        envs: PythonEnvsExtension,
        getPipTask: TaskProvider<DownloadTask>,
        buildPythonsFromZip: TaskProvider<Task>
    ) {
        val bootstrapTask = registerPythonFromZipTask(project, env, getPipTask, envs)
        buildPythonsFromZip.configure { dependsOn(bootstrapTask) }
    }

    private fun configureVirtualenvTask(
        project: Project, env: VirtualEnv, envs: PythonEnvsExtension, buildVirtualenvs: TaskProvider<Task>
    ) {
        val sourceTaskProvider: Provider<TaskProvider<out Task>> = project.provider {
            val sourceName = env.sourceEnvName.get()
            val sourcePython = envs.pythons.findByName(sourceName)
            val sourcePythonZip = envs.pythonsFromZip.findByName(sourceName)

            if (sourcePython != null) {
                if (sourcePython.type.getOrElse(EnvType.PYTHON) == EnvType.IRONPYTHON) {
                    throw GradleException("Cannot create virtualenv '${env.name}' from IronPython source '$sourceName'")
                }
                project.tasks.named("Bootstrap_${sourceName}")
            } else if (sourcePythonZip != null) {
                if (sourcePythonZip.type.getOrElse(EnvType.PYTHON) == EnvType.IRONPYTHON) {
                    throw GradleException("Cannot create virtualenv '${env.name}' from IronPython source '$sourceName'")
                }
                project.tasks.named("Bootstrap_${sourceName}_from_archive")
            } else {
                // This should ideally not happen if source validation is done earlier, but keep as fallback
                throw GradleException("Cannot find source environment '$sourceName' for virtualenv '${env.name}' during task configuration.")
            }
        }
        val virtualenvTask = registerVirtualenvTask(project, env, envs, sourceTaskProvider)
        buildVirtualenvs.configure { dependsOn(virtualenvTask) }
    }

    private fun configureCondaEnvTask(
        project: Project, env: CondaEnv, envs: PythonEnvsExtension, buildCondaEnvs: TaskProvider<Task>
    ) {
        val sourceName = env.sourceEnvName.get()
        val taskName = "Bootstrap_${sourceName}"

        // Source task is now guaranteed to be registered (either explicitly or implicitly in afterEvaluate)
        val sourceTaskProvider: Provider<TaskProvider<out Task>> = project.provider {
            project.tasks.named<Task>(taskName)
        }

        val condaEnvTask = registerCondaEnvTask(project, env, envs, sourceTaskProvider)
        buildCondaEnvs.configure { dependsOn(condaEnvTask) }
    }

    private fun registerInstallPythonBuildTask(
        project: Project, installDir: Provider<Directory?>
    ): TaskProvider<InstallPythonBuildTask> {
        return project.tasks.register<InstallPythonBuildTask>("install_python_build") {
            description = "Downloads and installs python-build (from pyenv) if needed on Unix systems."
            group = "Build Environment Setup"
            this.installDir.set(installDir.get())
        }
    }

    private fun registerPythonUnixTask(
        project: Project,
        env: Python,
        installPythonBuildTask: TaskProvider<InstallPythonBuildTask>,
        pipOptions: Property<String>
    ): TaskProvider<BootstrapPythonUnixTask> {
        return project.tasks.register<BootstrapPythonUnixTask>("Bootstrap_${env.name}") {
            description = "Bootstraps Python/PyPy environment '${env.name}' using python-build."
            group = "Build Environment"
            dependsOn(installPythonBuildTask)

            // Configure inputs/outputs from the extension object (env)
            pythonBuildDir.set(installPythonBuildTask.flatMap { it.installDir })
            pythonVersion.set(env.version)
            patchFileUri.set(env.patchFileUri)
            envDir.set(env.envDir)
            envType.set(env.type)
            packages.set(env.packages)
            pipInstallOptions.set(pipOptions)

            // Up-to-date check based on python exec existence might be useful
            // outputs.upToDateWhen { envDir.file("bin/python").get().asFile.exists() } // Example check
        }
    }

    private fun registerPythonWindowsTask(
        project: Project, env: Python, getPipTask: TaskProvider<DownloadTask>, pipOptions: Property<String>
    ): TaskProvider<BootstrapPythonWindowsTask> {
        return project.tasks.register<BootstrapPythonWindowsTask>("Bootstrap_${env.name}") {
            description = "Bootstraps Python environment '${env.name}' using the official Windows installer."
            group = "Build Environment"
            dependsOn(getPipTask)

            // Configure inputs/outputs
            pythonVersion.set(env.version)
            use64Bit.set(env.use64Bit)
            getPipScript.set(getPipTask.flatMap { it.destination })
            envDir.set(env.envDir)
            packages.set(env.packages)
            pipInstallOptions.set(pipOptions)

            onlyIf { isWindows }
        }
    }

    private fun registerJythonTask(
        project: Project,
        env: Python,
        jythonConfiguration: org.gradle.api.artifacts.Configuration,
        pipOptions: Property<String>
    ): TaskProvider<BootstrapJythonTask> {
        return project.tasks.register<BootstrapJythonTask>("Bootstrap_${env.name}") {
            description = "Bootstraps Jython environment '${env.name}' using the installer JAR."
            group = "Build Environment"

            // Configure inputs/outputs
            jythonInstallerFiles = jythonConfiguration
            envDir.set(env.envDir)
            packages.set(env.packages)
            pipInstallOptions.set(pipOptions)
        }
    }

    private fun registerPythonFromZipTask(
        project: Project, env: Python, getPipTask: TaskProvider<DownloadTask>, envs: PythonEnvsExtension
    ): TaskProvider<BootstrapPythonFromZipTask> {
        return project.tasks.register<BootstrapPythonFromZipTask>("Bootstrap_${env.name}_from_archive") {
            description = "Bootstraps Python environment '${env.name}' from a Zip archive."
            group = "Build Environment"
            dependsOn(getPipTask)

            // Configure inputs/outputs from extension
            zipUrl.set(env.url)
            envType.set(env.type)
            use64Bit.set(env.use64Bit)
            getPipScript.set(getPipTask.flatMap { it.destination })
            envDir.set(env.envDir)
            packages.set(env.packages)
            pipInstallOptions.set(envs.pipInstallOptions)

            // Ensure URL is provided
            onlyIf { env.url.isPresent }
        }
    }

    // Updated registration function for Virtualenv
    private fun registerVirtualenvTask(
        project: Project,
        env: VirtualEnv,
        envs: PythonEnvsExtension,
        sourceTaskProvider: Provider<TaskProvider<out Task>>
    ): TaskProvider<CreateVirtualenvTask> {

        // Need Providers for source env details (dir, type, use64Bit)
        // These depend on the *type* of the source environment config (Python or PythonFromZip)
        val sourceEnvDetailsProvider: Provider<Triple<DirectoryProperty, Property<EnvType>, Property<Boolean>>> =
            project.provider {
                val sourceName = env.sourceEnvName.get()
                val sourcePython = envs.pythons.findByName(sourceName)
                val sourcePythonZip = envs.pythonsFromZip.findByName(sourceName)
                when {
                    sourcePython != null -> Triple(sourcePython.envDir, sourcePython.type, sourcePython.use64Bit)
                    sourcePythonZip != null -> Triple(
                        sourcePythonZip.envDir, sourcePythonZip.type, sourcePythonZip.use64Bit
                    )
                    // Should have been caught earlier, but defensive check
                    else -> throw GradleException("Logic error: Source env '$sourceName' not found for virtualenv '${env.name}' during provider resolution.")
                }
            }

        return project.tasks.register<CreateVirtualenvTask>("Create_virtualenv_${env.name}") {
            description =
                "Creates virtual environment '${env.name}' from source '${env.sourceEnvName.getOrElse("unknown")}'."
            group = "Build Environment"
            dependsOn(sourceTaskProvider)

            // Configure inputs from source env providers and virtualenv config
            sourceEnvDir.set(sourceEnvDetailsProvider.flatMap { it.first })
            sourceEnvType.set(sourceEnvDetailsProvider.flatMap { it.second })
            sourceUse64Bit.set(sourceEnvDetailsProvider.flatMap { it.third })

            envDir.set(env.envDir)
            packages.set(env.packages)
            pipInstallOptions.set(envs.pipInstallOptions)
        }
    }

    private fun registerCondaTask(
        project: Project, env: Conda, envs: PythonEnvsExtension
    ): TaskProvider<BootstrapCondaTask> {
        return project.tasks.register<BootstrapCondaTask>("Bootstrap_${env.name}") {
            description = "Bootstraps base Conda environment '${env.name}'."
            group = "Build Environment"
            // Configure inputs from Conda object and extension
            condaVersion.set(env.version)
            envDir.set(env.envDir)
            pipPackages.set(env.packages)
            condaPackages.set(env.condaPackages)
            pipInstallOptions.set(envs.pipInstallOptions)
        }
    }

    private fun registerCondaEnvTask(
        project: Project, env: CondaEnv, envs: PythonEnvsExtension, sourceTaskProvider: Provider<TaskProvider<out Task>>
    ): TaskProvider<CreateCondaEnvTask> {
        val sourceEnvDirProvider: Provider<DirectoryProperty> = project.provider {
            val sourceName = env.sourceEnvName.get()
            val sourceConda = envs.condas.findByName(sourceName)
                ?: throw GradleException("Logic error: Source conda env '$sourceName' not found for conda env '${env.name}' during provider resolution.")
            sourceConda.envDir
        }

        return project.tasks.register<CreateCondaEnvTask>("Create_conda_env_${env.name}") {
            description =
                "Creates Conda environment '${env.name}' from base '${env.sourceEnvName.getOrElse("unknown")}'"
            group = "Build Environment"
            dependsOn(sourceTaskProvider)

            // Configure inputs
            sourceEnvDir.set(sourceEnvDirProvider.flatMap { it })
            pythonVersion.set(env.version)
            envDir.set(env.envDir)
            pipPackages.set(env.packages)
            condaPackages.set(env.condaPackages)
            pipInstallOptions.set(envs.pipInstallOptions)

            onlyIf { env.sourceEnvName.isPresent && env.version.isPresent }
        }
    }
}
