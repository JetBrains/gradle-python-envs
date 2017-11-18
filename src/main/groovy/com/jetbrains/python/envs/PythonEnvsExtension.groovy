package com.jetbrains.python.envs

import java.nio.file.Path
import java.nio.file.Paths

/**
 * Project extension to configure Python build environment.
 *
 */
class PythonEnvsExtension {
    File bootstrapDirectory
    File envsDirectory

    URL zipRepository
    Boolean shouldUseZipsFromRepository = false

    Boolean _64Bits = true  // By default 64 bit envs should be installed
    String condaDefaultVersion = "Miniconda2-latest"
    String pypyDefaultVersion = "pypy2.7-5.8.0"
    String pipInstallOptions = "--trusted-host pypi.python.org"

    List<Python> pythons = []
    List<Conda> condas = []
    List<CondaEnv> condaEnvs = []
    List<VirtualEnv> virtualEnvs = []
    List<Python> pythonsFromZip = []
    List<CreateFile> files = []
    List<CreateLink> links = []

    String CONDA_PREFIX = "CONDA_"

    /**
     * @param envName name of environment like "env_for_django"
     * @param version py version like "3.4"
     * @param packages collection of py packages to install
     */
    void python(final String envName,
                final String version,
                final String architecture = null,
                final List<String> packages = null) {
        if (zipRepository && shouldUseZipsFromRepository) {
            pythonFromZip envName, getUrlFromRepository("python", version, architecture), "python", packages
        } else {
            pythons << new Python(envName, bootstrapDirectory, EnvType.PYTHON, version, is64(architecture), packages)
        }
    }

    void python(final String envName, final String version, final List<String> packages) {
        python(envName, version, null, packages)
    }

    /**
     * @see #python
     * @param urlToArchive URL link to archive with environment
     */
    void pythonFromZip(final String envName,
                       final URL urlToArchive,
                       final String type = null,
                       final List<String> packages = null) {
        pythonsFromZip << new Python(
                envName,
                bootstrapDirectory,
                EnvType.fromString(type),
                null,
                null,
                packages,
                urlToArchive
        )
    }

    /**
     * @see #python
     * @param sourceEnvName name of inherited environment like "env_for_django"
     */
    void virtualenv(final String envName, final String sourceEnvName, final List<String> packages = null) {
        Python pythonEnv = (pythons + pythonsFromZip).find { it.name == sourceEnvName }
        if (pythonEnv != null) {
            virtualEnvs << new VirtualEnv(envName, envsDirectory, pythonEnv, packages)
        } else {
            println("Specified environment '$sourceEnvName' for virtualenv '$envName' isn't found")
        }
    }

    /**
     * @see #python
     */
    void conda(final String envName,
               final String version,
               final String architecture,
               final List<String> packages = null) {
        List<String> pipPackages = packages.findAll { !it.startsWith(CONDA_PREFIX) }
        List<String> condaPackages = packages.findAll { it.startsWith(CONDA_PREFIX) }
                                             .collect { it.substring(CONDA_PREFIX.length()) }
        condas << new Conda(envName, bootstrapDirectory, version, is64(architecture), pipPackages, condaPackages)
    }

    void conda(final String envName,
               final String version,
               final List<String> packages = null) {
        conda(envName, version, null, packages)
    }

    void conda(final String envName,
               final List<String> packages = null) {
        conda(envName, condaDefaultVersion, null, packages)
    }

    /**
     * @see #python
     * @param sourceEnvName name of inherited environment like "env_for_django"
     */
    void condaenv(final String envName,
                  final String version,
                  final String sourceEnvName = null,
                  final List<String> packages = null) {
        List<String> pipPackages = packages.findAll { !it.startsWith(CONDA_PREFIX) }
        List<String> condaPackages = packages.findAll { it.startsWith(CONDA_PREFIX) }
                                             .collect { it.substring(CONDA_PREFIX.length()) }
        if (sourceEnvName == null) {
            conda condaDefaultVersion
        }
        Conda condaEnv = condas.find { it.name == sourceEnvName ?: condaDefaultVersion }

        if (condaEnv != null) {
            condaEnvs << new CondaEnv(envName, envsDirectory, condaEnv, version, pipPackages, condaPackages)
        } else {
            println("Specified environment '$sourceEnvName' for condaenv '$envName' isn't found")
        }
    }

    void condaenv(final String envName,
                  final String version,
                  final List<String> packages) {
        condaenv(envName, version, null, packages)
    }

    /**
     * @see #python
     */
    void jython(final String envName, final List<String> packages = null) {
        pythons << new Python(envName, bootstrapDirectory, EnvType.JYTHON, null, null, packages)
    }

    /**
     * @see #python
     */
    void pypy(final String envName, final String version = null, final List<String> packages = null) {
        pythons << new Python(
                envName,
                bootstrapDirectory,
                EnvType.PYPY,
                version ?: pypyDefaultVersion,
                null,
                packages
        )
    }

    void pypy(final String envName, final List<String> packages) {
        pypy(envName, null, packages)
    }

    /**
     * @see #python
     */
    void ironpython(final String envName,
                    final String architecture = null,
                    final List<String> packages = null,
                    final URL urlToArchive = null) {
        URL urlToIronPythonZip = new URL("https://github.com/IronLanguages/main/releases/download/ipy-2.7.7/IronPython-2.7.7-win.zip")
        pythonsFromZip << new Python(
                envName,
                bootstrapDirectory,
                EnvType.IRONPYTHON,
                null,
                is64(architecture),
                packages,
                urlToArchive ?: urlToIronPythonZip
        )
    }

    void ironpython(final String envName,
                    final List<String> packages,
                    final URL urlToArchive = null) {
        ironpython(envName, null, packages, urlToArchive)
    }

    void textfile(final String path, final String content) {
        files << new CreateFile(new File(path), content)
    }

    void link(final String linkString, final String sourceString, final File baseDir = null) {
        Path linkPath = Paths.get(baseDir ? baseDir.toString() : '', linkString)
        Path sourcePath = Paths.get(baseDir ? baseDir.toString() : '', sourceString)
        links << new CreateLink(linkPath, sourcePath)
    }

    String condaPackage(final String packageName) {
        return CONDA_PREFIX + packageName
    }

    private Boolean is64(final String architecture) {
        return (architecture == null) ? _64Bits : !(architecture == "32")
    }

    private URL getUrlFromRepository(final String type, final String version, final String architecture = null) {
        if (!zipRepository) return null
        return zipRepository.toURI().resolve("$type-$version-${architecture ?: _64Bits ? "64" : "32"}.zip").toURL()
    }
}


enum EnvType {
    PYTHON,
    CONDA,
    JYTHON,
    PYPY,
    IRONPYTHON,
    VIRTUALENV
    // TODO non-python virtualenv?

    static fromString(String type) {
        return type == null ? null : valueOf(type.toUpperCase())
    }
}


class Python {
    final String name
    final File envDir
    final EnvType type
    final String version
    final Boolean is64
    final List<String> packages
    final URL url

    Python(String name,
           File dir,
           EnvType type = null,
           String version = null,
           Boolean is64 = true,
           List<String> packages = null,
           URL url = null) {
        this.name = name
        this.envDir = new File(dir, name)
        this.type = type
        this.version = version
        this.is64 = is64
        this.packages = packages
        this.url = url
    }
}


class VirtualEnv extends Python {
    final Python sourceEnv

    VirtualEnv(String name, File dir, Python sourceEnv, List<String> packages) {
        super(name, dir, EnvType.VIRTUALENV, sourceEnv.version, sourceEnv.is64, packages)
        this.sourceEnv = sourceEnv
    }
}


class Conda extends Python {
    final List<String> condaPackages

    Conda(String name,
          File dir,
          String version,
          Boolean is64,
          List<String> pipPackages,
          List<String> condaPackages) {
        super(name, dir, EnvType.CONDA, version, is64, pipPackages)
        this.condaPackages = condaPackages
    }
}


class CondaEnv extends Conda {
    final Conda sourceEnv

    CondaEnv(String name,
             File dir,
             Conda sourceEnv,
             String version,
             List<String> pipPackages,
             List<String> condaPackages) {
        super(name, dir, version, sourceEnv.is64, pipPackages, condaPackages)
        this.sourceEnv = sourceEnv
    }
}


class CreateFile {
    final File file
    final String content

    CreateFile(File file, String content) {
        this.file = file
        this.content = content
    }
}


class CreateLink {
    final Path link
    final Path source

    CreateLink(Path link, Path source) {
        this.link = link
        this.source = source
    }
}
