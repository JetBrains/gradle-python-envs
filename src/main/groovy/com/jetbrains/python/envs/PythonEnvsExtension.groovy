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
    List<CreateFile> files = []
    Boolean _64Bits = false

    /**
     * @param envName name of environment like "env_for_django"
     * @param version py version like "3.4"
     * @param packages collection of py packages to install
     * @param linkWithVersion if true, binary will be linked with version name.
     * I.e. "python" will be have "python2.7" link (same for exe file). Used for envs line tox.
     */
    void conda(final String envName, final String version, final List<String> packages, final boolean linkWithVersion) {
        condaEnvs << new VersionedPythonEnv(envName, version, packages, linkWithVersion, false)
    }

    void conda_install(final String envName, final String version, final List<String> packages,
                       final boolean linkWithVersion) {
        condaEnvs << new VersionedPythonEnv(envName, version, packages, linkWithVersion, true)
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
    boolean linkWithVersion
    boolean useCondaInstall

    VersionedPythonEnv(String name, String version, List<String> packages, boolean linkWithVersion,
                       boolean useCondaInstall) {
        super(name, packages)
        this.version = version
        this.linkWithVersion = linkWithVersion
        this.useCondaInstall = useCondaInstall
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



