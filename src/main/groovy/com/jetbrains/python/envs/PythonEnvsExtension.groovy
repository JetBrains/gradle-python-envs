package com.jetbrains.python.envs


/**
 * Project extension to configure Python build environment.
 *
 */
class PythonEnvsExtension {
    File bootstrapDirectory
    File envsDirectory
    String minicondaVersion
    String minicondaExecutable32
    String minicondaExecutable64
    List<String> packages
    List<PythonEnv> jythonEnvs = []
    List<PythonEnv> condaEnvs = []
    List<PythonEnv> virtualEnvs = []

    List<CreateFile> files = []
    Boolean _64Bits = false
    String CONDA_PREFIX = "CONDA_"

    /**
     * @param envName name of environment like "env_for_django"
     * @param version py version like "3.4"
     * @param packages collection of py packages to install
     * @param linkWithVersion if true, binary will be linked with version name.
     * I.e. "python" will be have "python2.7" link (same for exe file). Used for envs line tox.
     */
    void conda(final String envName, final String version, final List<String> packages, final boolean linkWithVersion) {
        def pipPackages = packages.findAll{!it.startsWith(CONDA_PREFIX)}
        def condaPackages = packages.findAll{it.startsWith(CONDA_PREFIX)}.collect{it.substring(CONDA_PREFIX.length())}
        condaEnvs << new VersionedPythonEnv(envName, version, pipPackages, condaPackages, linkWithVersion)
    }

    void virtualenv(final String envName, final List<String> packages) {
        virtualEnvs << new PythonEnv(envName, packages)
    }

    String install(final String packageName) {
        return CONDA_PREFIX + packageName
    }
    
    void jython(final String envName, final List<String> packages) {
        jythonEnvs << new PythonEnv(envName, packages)
    } 
    
    void textfile(final String path, String content) {
        files << new CreateFile(path, content)
    }
}

class PythonEnv {
    String name
    List<String> packages

    PythonEnv(String name, List<String> packages) {
        this.name = name
        this.packages = packages;
    }
}

class VersionedPythonEnv extends PythonEnv {
    String version
    List<String> condaPackages
    boolean linkWithVersion

    VersionedPythonEnv(String name, String version, List<String> packages, List<String> condaPackages,
                       boolean linkWithVersion) {
        super(name, packages)
        this.version = version
        this.condaPackages = condaPackages
        this.linkWithVersion = linkWithVersion
    }
}

class CreateFile {
    String path;
    String content;

    CreateFile(String path, String content) {
        this.path = path
        this.content = content
    }
}



