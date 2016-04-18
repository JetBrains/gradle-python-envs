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
    List<JythonEnv> jythonEnvs = []
    
    void jython(final String envName, final List<String> packages) {
        jythonEnvs << new JythonEnv(envName, packages)
    } 
}

class JythonEnv {
    String name
    List<String> packages
    
    JythonEnv(String name, List<String> packages) {
        this.name = name
        this.packages = packages;
    }
}



