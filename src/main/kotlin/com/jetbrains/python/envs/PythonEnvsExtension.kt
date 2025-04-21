package com.jetbrains.python.envs

import org.gradle.api.Named
import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.ProjectLayout
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.Optional
import java.net.URI
import java.net.URL
import javax.inject.Inject

abstract class PythonEnvsExtension @Inject constructor(objects: ObjectFactory, projectLayout: ProjectLayout) {
    companion object {
        const val CONDA_DEFAULT_VERSION = "Miniconda2-latest"
        const val PYPY_DEFAULT_VERSION = "pypy2.7-5.8.0"
        const val IRONPYTHON_URL =
            "https://github.com/IronLanguages/ironpython2/releases/download/ipy-2.7.9/IronPython.2.7.9.zip"
    }

    // Internal helper to avoid repetition
    private fun configurePython(name: String, configure: Python.() -> Unit) {
        pythons.create(name, configure)
    }

    private fun configurePythonFromZip(name: String, configure: Python.() -> Unit) {
        pythonsFromZip.create(name, configure)
    }

    private fun configureVirtualEnv(name: String, configure: VirtualEnv.() -> Unit) {
        virtualEnvs.create(name, configure)
    }

    private fun configureConda(name: String, configure: Conda.() -> Unit) {
        condas.create(name, configure)
    }

    private fun configureCondaEnv(name: String, configure: CondaEnv.() -> Unit) {
        condaEnvs.create(name, configure)
    }

    @get:InputDirectory
    val bootstrapDirectory: DirectoryProperty =
        objects.directoryProperty().convention(projectLayout.buildDirectory.dir("python-envs/bootstrap"))

    @get:InputDirectory
    val envsDirectory: DirectoryProperty =
        objects.directoryProperty().convention(projectLayout.projectDirectory.dir("python-envs/envs"))

    @get:Input
    val pipInstallOptions: Property<String> = objects.property(String::class.java).convention("")

    @get:Input
    @get:Optional
    val zipRepository: Property<URL> = objects.property(URL::class.java)

    @get:Input
    @get:Optional
    val shouldUseZipsFromRepository: Property<Boolean> = objects.property(Boolean::class.java).convention(false)

    val pythons: NamedDomainObjectContainer<Python> = objects.domainObjectContainer(Python::class.java) { name ->
        objects.newInstance(Python::class.java, name).apply {
            configureDefaultEnvDir(envsDirectory, objects)
            type.convention(EnvType.PYTHON)
            use64Bit.convention(true)
        }
    }
    val pythonsFromZip: NamedDomainObjectContainer<Python> = objects.domainObjectContainer(Python::class.java) { name ->
        objects.newInstance(Python::class.java, name).apply {
            configureDefaultEnvDir(envsDirectory, objects)
            use64Bit.convention(true)
        }
    }
    val virtualEnvs: NamedDomainObjectContainer<VirtualEnv> =
        objects.domainObjectContainer(VirtualEnv::class.java) { name ->
            objects.newInstance(VirtualEnv::class.java, name).apply {
                configureDefaultEnvDir(envsDirectory, objects)
                type.convention(EnvType.VIRTUALENV)
            }
        }
    val condas: NamedDomainObjectContainer<Conda> = objects.domainObjectContainer(Conda::class.java) { name ->
        objects.newInstance(Conda::class.java, name).apply {
            configureDefaultEnvDir(envsDirectory, objects)
            type.convention(EnvType.CONDA)
        }
    }
    val condaEnvs: NamedDomainObjectContainer<CondaEnv> = objects.domainObjectContainer(CondaEnv::class.java) { name ->
        objects.newInstance(CondaEnv::class.java, name).apply {
            configureDefaultEnvDir(envsDirectory, objects)
            type.convention(EnvType.CONDA)
            sourceEnvName.convention(CONDA_DEFAULT_VERSION)
        }
    }

    // --- Groovy DSL Methods ---

    @JvmOverloads
    fun python(
        name: String,
        pythonVersion: String,
        bits: String = "64",
        packages: List<String> = emptyList(),
        patchUri: String? = null
    ) {
        configurePython(name) {
            this.type.set(EnvType.PYTHON)
            this.version.set(pythonVersion)
            this.use64Bit.set(bits != "32")
            this.packages.set(packages)
            if (patchUri != null) this.patchFileUri.set(patchUri)
        }
    }

    fun python(name: String, pythonVersion: String, packages: List<String>) {
        python(name, pythonVersion, "64", packages, null)
    }

    @JvmOverloads
    fun virtualenv(name: String, sourceEnvName: String, packages: List<String> = emptyList()) {
        configureVirtualEnv(name) {
            this.type.set(EnvType.VIRTUALENV)
            this.sourceEnvName.set(sourceEnvName)
            this.packages.set(packages)
        }
    }

    @JvmOverloads
    fun conda(
        name: String,
        pythonVersion: String? = null,
        architecture: String? = "64",
        packagesList: List<Any> = emptyList()
    ) {
        configureConda(name) {
            val pipPackages = packagesList.filterIsInstance<String>()
            val condaPackages = packagesList.filterIsInstance<CondaPackageMarker>().map { it.name }
            this.type.set(EnvType.CONDA)
            this.use64Bit.set(architecture != "32")
            this.version.set(pythonVersion ?: CONDA_DEFAULT_VERSION)
            this.packages.set(pipPackages)
            this.condaPackages.set(condaPackages)
        }
    }

    fun conda(name: String, pythonVersion: String, packagesList: List<Any>) {
        conda(name, pythonVersion, null, packagesList)
    }

    @JvmOverloads
    fun condaenv(
        name: String,
        pythonVersion: String,
        sourceEnvName: String? = null,
        packagesList: List<Any> = emptyList()
    ) {
        configureCondaEnv(name) {
            val pipPackages = packagesList.filterIsInstance<String>()
            val condaPackages = packagesList.filterIsInstance<CondaPackageMarker>().map { it.name }
            this.type.set(EnvType.CONDA)
            this.version.set(pythonVersion)
            this.condaPackages.set(condaPackages)
            this.packages.set(pipPackages)
            if (sourceEnvName != null) this.sourceEnvName.set(sourceEnvName)
        }
    }

    fun condaenv(name: String, pythonVersion: String, packagesList: List<Any>) {
        condaenv(name, pythonVersion, null, packagesList)
    }

    fun jython(name: String) {
        configurePython(name) {
            this.type.set(EnvType.JYTHON)
            // Version is usually fixed by the jython-installer dependency, not set here.
        }
    }

    @JvmOverloads
    fun pypy(name: String, version: String? = null, packages: List<String> = emptyList()) {
        configurePython(name) {
            this.type.set(EnvType.PYPY)
            this.version.set(version ?: PYPY_DEFAULT_VERSION)
            this.packages.set(packages)
        }
    }

    fun pypy(name: String, packages: List<String>) {
        pypy(name, null, packages)
    }

    @JvmOverloads
    fun ironpython(
        name: String,
        architecture: String = "64",
        packages: List<String> = emptyList(),
        url: String = IRONPYTHON_URL
    ) {
        // IronPython is typically fetched via zip
        configurePythonFromZip(name) {
            this.type.set(EnvType.IRONPYTHON)
            this.use64Bit.set(architecture != "32")
            this.packages.set(packages)
            this.url.set(URI(url))
        }
    }

    fun condaPackage(packageName: String): CondaPackageMarker {
        return CondaPackageMarker(packageName)
    }
}

enum class EnvType { PYTHON, JYTHON, PYPY, IRONPYTHON, CONDA, VIRTUALENV }

interface PythonEnv : Named {
    val envDir: DirectoryProperty

    @get:Optional
    val packages: ListProperty<String>

    val type: Property<EnvType>

    fun configureDefaultEnvDir(baseEnvsDir: DirectoryProperty, objects: ObjectFactory) {
        envDir.convention(baseEnvsDir.dir(name))
    }
}

interface Python : PythonEnv {
    @get:Optional
    val version: Property<String>

    @get:Optional
    val use64Bit: Property<Boolean>

    @get:Optional
    val patchFileUri: Property<String>

    @get:Optional
    val url: Property<URI>
}

interface Conda : PythonEnv {
    @get:Optional
    val version: Property<String>

    @get:Optional
    val use64Bit: Property<Boolean>

    @get:Optional
    val condaPackages: ListProperty<String>
}

interface VirtualEnv : PythonEnv {
    val sourceEnvName: Property<String>
}

interface CondaEnv : PythonEnv {
    val sourceEnvName: Property<String>

    val version: Property<String>

    @get:Optional
    val condaPackages: ListProperty<String>
}

data class CondaPackageMarker(val name: String)
