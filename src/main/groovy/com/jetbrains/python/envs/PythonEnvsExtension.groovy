package com.jetbrains.python.envs


/**
 * Project extension to configure Python build environment.
 *
 */
class PythonEnvsExtension {
    File bootstrapDirectory
    File buildEnvironmentDirectory
    String minicondaVersion
    String minicondaExecutable32
    String minicondaExecutable64
    List<String> packages
    List<JythonEnv> jythonEnvs = []
    
    void jython(final String envName) {
        jythonEnvs << new JythonEnv(envName)
    } 
    
}

class JythonEnv {
    JythonEnv(String name) {
        this.name = name
    }
    String name
    
}



