package com.jetbrains.python.envs

/**
 * Project extension to configure Python build environment.
 *
 */
class PythonEnvsExtension {
    File bootstrapDirectory
    File envsDirectory
    File virtualenvsDirectory

    String minicondaVersion = "latest"
    protected File minicondaExecutable32
    protected File minicondaExecutable64
    List<String> condaBasePackages = []

    String pypyDefaultVersion = "pypy2.7-5.8.0"

    List<PythonEnv> pythonEnvs = []
    List<CondaEnv> condaEnvs = []
    List<VirtualEnv> virtualEnvs = []
    List<PythonEnv> envsFromZip = []
    List<CreateFile> files = []
    List<CreateLink> links = []

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
        pythonEnvs << new PythonEnv(envName, envsDirectory, EnvType.PYTHON, version, is64(architecture), packages)
    }

    /**
     * @see #python
     */
    void conda(final String envName,
               final String version,
               final String architecture = null,
               final List<String> packages = null) {
        List<String> pipPackages = packages.findAll { !it.startsWith(CONDA_PREFIX) }
        List<String> condaPackages = packages.findAll { it.startsWith(CONDA_PREFIX) }
                                             .collect { it.substring(CONDA_PREFIX.length()) }
        condaEnvs << new CondaEnv(envName, envsDirectory, version, is64(architecture), pipPackages, condaPackages)
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
                version ?: pypyDefaultVersion,
                null,
                packages
        )
    }

    /**
     * @see #python
     */
    void ironpython(final String envName,
                    final List<String> packages = null,
                    final String architecture = null,
                    final URL urlToArchive = null) {
        URL urlToIronPythonZip = new URL("https://github.com/IronLanguages/main/releases/download/ipy-2.7.7/IronPython-2.7.7-win.zip")
        envsFromZip << new PythonEnv(
                envName,
                envsDirectory,
                EnvType.IRONPYTHON,
                null,
                is64(architecture),
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
            println("Specified environment '$sourceEnvName' for virtualenv '$envName' isn't found")
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

    void textfile(final String path, final String content) {
        files << new CreateFile(path, content)
    }

    void link(final String link, final String source) {
        links << new CreateLink(link, source)
    }

    private List<PythonEnv> allEnvs() {
        return pythonEnvs + condaEnvs + envsFromZip
    }

    String condaPackage(final String packageName) {
        return CONDA_PREFIX + packageName
    }

    private Boolean is64(final String architecture) {
        return (architecture == null) ? _64Bits : !(architecture == "32")
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

    CondaEnv(String name,
             File dir,
             String version,
             Boolean is64,
             List<String> packages,
             List<String> condaPackages) {
        super(name, dir, EnvType.CONDA, version, is64, packages)
        this.condaPackages = condaPackages
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


class CreateLink {
    final String link
    final String source

    CreateLink(String link, String source) {
        this.link = link
        this.source = source
    }
}
