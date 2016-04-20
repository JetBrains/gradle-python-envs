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
    
    void conda(final String envName, final String version, final List<String> packages) {
        condaEnvs << new VersionedPythonEnv(envName, version, packages)
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

    VersionedPythonEnv(String name, String version, List<String> packages) {
        super(name, packages)
        this.version = version
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



