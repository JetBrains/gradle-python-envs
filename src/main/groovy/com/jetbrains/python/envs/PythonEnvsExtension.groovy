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
    protected File minicondaExecutable32
    protected File minicondaExecutable64
    List<String> condaBasePackages = []

    List<PythonEnv> pythonEnvs = []
    List<CondaEnv> condaEnvs = []
    List<VirtualEnv> virtualEnvs = []
    List<PythonEnv> envsFromZip = []
    List<CreateFile> files = []

    Boolean _64Bits = true  // By default 64 bit envs should be installed
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
        Boolean is64 = (architecture == null) ? _64Bits : !(architecture == "32")
        pythonEnvs << new PythonEnv(envName, envsDirectory, EnvType.PYTHON, version, is64, packages)
    }

    /**
     * @see #python
     * @param linkWithVersion if true, binary will be linked with version name.
     * I.e. "python" will be have "python2.7" link (same for exe file). Used for envs line tox.
     */
    void conda(final String envName,
               final String version,
               final String architecture = null,
               final List<String> packages = null,
               final boolean linkWithVersion = false) {
        Boolean is64 = (architecture == null) ? _64Bits : !(architecture == "32")
        List<String> pipPackages = packages.findAll { !it.startsWith(CONDA_PREFIX) }
        List<String> condaPackages = packages.findAll { it.startsWith(CONDA_PREFIX) }
                                             .collect { it.substring(CONDA_PREFIX.length()) }
        condaEnvs << new CondaEnv(envName, envsDirectory, version, is64, pipPackages, condaPackages, linkWithVersion)
    }

    /**
     * @see #python
     */
    void jython(final String envName, final List<String> packages = null) {
        pythonEnvs << new PythonEnv(envName, envsDirectory, EnvType.JYTHON, null, null, packages)
    }

    /**
     * @see #python
     */
    void pypy(final String envName, final List<String> packages = null, final String version = null) {
        pythonEnvs << new PythonEnv(
                envName,
                envsDirectory,
                EnvType.PYPY,
                version ?: "pypy2.7-5.8.0",
                null,
                packages
        )
    }

    /**
     * @see #python
     */
    void ironpython(final String envName, final List<String> packages = null, final URL urlToArchive = null) {
        URL urlToIronPythonZip = new URL("https://github.com/IronLanguages/main/releases/download/ipy-2.7.7/IronPython-2.7.7-win.zip")
        envsFromZip << new PythonEnv(
                envName,
                envsDirectory,
                EnvType.IRONPYTHON,
                null,
                null,
                packages,
                urlToArchive ?: urlToIronPythonZip
        )
    }

    /**
     * @see #python
     * @param sourceEnvName name of inherited environment like "env_for_django"
     */
    void virtualenv(final String envName, final String sourceEnvName, final List<String> packages = null) {
        PythonEnv pythonEnv = allEnvs().find { it.name == sourceEnvName }
        if (pythonEnv != null) {
            virtualEnvs << new VirtualEnv(envName, virtualenvsDirectory ?: envsDirectory, pythonEnv, packages)
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
                    final String type = null,
                    final List<String> packages = null) {
        envsFromZip << new PythonEnv(
                envName,
                envsDirectory,
                EnvType.fromString(type),
                null,
                null,
                packages,
                urlToArchive
        )
    }

    private List<PythonEnv> allEnvs() {
        return pythonEnvs + condaEnvs + envsFromZip
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
    IRONPYTHON,
    VIRTUALENV
    // TODO non-python virtuaenv?

    static fromString(String type) {
        return type == null ? null : valueOf(type.toUpperCase())
    }
}


class PythonEnv {
    final String name
    final File envDir
    final EnvType type
    final String version
    final Boolean is64
    final List<String> packages
    final URL url

    PythonEnv(String name,
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


class CondaEnv extends PythonEnv {
    final List<String> condaPackages
    final boolean linkWithVersion

    CondaEnv(String name,
             File dir,
             String version,
             Boolean is64,
             List<String> packages,
             List<String> condaPackages,
             boolean linkWithVersion) {
        super(name, dir, EnvType.CONDA, version, is64, packages)
        this.condaPackages = condaPackages
        this.linkWithVersion = linkWithVersion
    }
}


class VirtualEnv extends PythonEnv {
    final PythonEnv sourceEnv

    VirtualEnv(String name, File dir, PythonEnv sourceEnv, List<String> packages) {
        super(name, dir, EnvType.VIRTUALENV, null, null, packages)
        this.sourceEnv = sourceEnv
    }
}


class CreateFile {
    final String path
    final String content

    CreateFile(String path, String content) {
        this.path = path
        this.content = content
    }
}
