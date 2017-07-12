package com.jetbrains.python.envs

/**
 * Project extension to configure Python build environment.
 *
 */
class PythonEnvsExtension {
    File bootstrapDirectory
    File envsDirectory
    File virtualenvsDirectory
    String minicondaVersion // TODO latest by default
    File minicondaExecutable32
    File minicondaExecutable64
    List<String> condaBasePackages = []

    List<PythonEnv> pythonEnvs = []
    List<CondaEnv> condaEnvs = []
    List<VirtualEnv> virtualEnvs = []
    List<PythonEnv> envsFromZip = []
    List<CreateFile> files = []

    String CONDA_PREFIX = "CONDA_"

    // TODO 64 bits stuff

    /**
     * @param envName name of environment like "env_for_django"
     * @param version py version like "3.4"
     * @param packages collection of py packages to install
     */
    void python(final String envName, final String version, final List<String> packages = null) {
        pythonEnvs << new PythonEnv(envName, EnvType.PYTHON, version, packages)
    }

    /**
     * @see #python
     * @param linkWithVersion if true, binary will be linked with version name.
     * I.e. "python" will be have "python2.7" link (same for exe file). Used for envs line tox.
     */
    void conda(final String envName,
               final String version,
               final List<String> packages = null,
               final boolean linkWithVersion = false) {
        List<String> pipPackages = packages.findAll { !it.startsWith(CONDA_PREFIX) }
        List<String> condaPackages = packages.findAll { it.startsWith(CONDA_PREFIX) }
                                             .collect { it.substring(CONDA_PREFIX.length()) }
        condaEnvs << new CondaEnv(envName, version, pipPackages, condaPackages, linkWithVersion)
    }

    /**
     * @see #python
     */
    void jython(final String envName, final List<String> packages = null) {
        pythonEnvs << new PythonEnv(envName, EnvType.JYTHON, null, packages)
    }

    // TODO pypy
    // TODO ironpython

    /**
     * @see #python
     * @param sourceEnvName name of inherited environment like "env_for_django"
     */
    void virtualenv(final String envName, final String sourceEnvName, final List<String> packages = null) {
        PythonEnv pythonEnv = (pythonEnvs + condaEnvs + envsFromZip).find { it.name == sourceEnvName }
        if (pythonEnv != null) {
            virtualEnvs << new VirtualEnv(envName, pythonEnv, packages)
        } else {
            println("\u001B[31mSpecified environment '$sourceEnvName' for virtualenv '$envName' isn't found\u001B[0m")
        }
    }

    /**
     * @see #python
     * @param urlToArchive URL link to archive with environment
     */
    void envFromZip(final String envName,
                    final URL urlToArchive,
                    final EnvType type = null,
                    final List<String> packages = null) {
        envsFromZip << new PythonEnv(envName, type, null, packages, urlToArchive)
    }

    String condaPackage(final String packageName) {
        return CONDA_PREFIX + packageName
    }

    void textfile(final String path, final String content) {
        files << new CreateFile(path, content)
    }
}


enum EnvType {
    PYTHON,
    CONDA,
    JYTHON,
    PYPY,
    IRONPYTHON
}


class PythonEnv {
    String name
    EnvType type
    String version
    List<String> packages
    URL url

    PythonEnv(String name, EnvType type = null, String version = null, List<String> packages = null, URL url = null) {
        this.name = name
        this.type = type ?: EnvType.PYTHON
        this.version = version
        this.packages = packages
        this.url = url
    }
}


class CondaEnv extends PythonEnv {
    List<String> condaPackages
    boolean linkWithVersion

    CondaEnv(String name, String version, List<String> packages, List<String> condaPackages, boolean linkWithVersion) {
        super(name, EnvType.CONDA, version, packages)
        this.condaPackages = condaPackages
        this.linkWithVersion = linkWithVersion
    }
}


class VirtualEnv {
    String name
    PythonEnv sourceEnv
    List<String> packages

    VirtualEnv(String name, PythonEnv sourceEnv, packages) {
        this.name = name
        this.sourceEnv = sourceEnv
        this.packages = packages
    }
}


class CreateFile {
    String path
    String content

    CreateFile(String path, String content) {
        this.path = path
        this.content = content
    }
}
