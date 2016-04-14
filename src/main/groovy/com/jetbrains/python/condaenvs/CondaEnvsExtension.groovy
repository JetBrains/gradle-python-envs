package com.jetbrains.python.condaenvs

/**
 * Project extension to configure Python build environment.
 *
 */
class CondaEnvsExtension {
    File bootstrapDirectory
    File buildEnvironmentDirectory
    String minicondaVersion
    String minicondaExecutable32
    String minicondaExecutable64
    List<String> packages
}

