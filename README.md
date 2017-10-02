[![official JetBrains project](http://jb.gg/badges/official-plastic.svg)](https://confluence.jetbrains.com/display/ALL/JetBrains+on+GitHub)

Gradle Python Envs Plugin
========================

Gradle plugin to create Python envs.

This plugin is based on [gradle-miniconda-plugin](https://github.com/palantir/gradle-miniconda-plugin),
but in addition to creating Conda envs it provides:

1. A convenient DSL to specify target Python environments 
2. Creating Python envs for Unix with [python-build](https://github.com/pyenv/pyenv/tree/master/plugins/python-build) for Unix
<br>N.B.: [Common build problems](https://github.com/pyenv/pyenv/wiki/Common-build-problems) article from pyenv
3. Creating Python envs for Windows by installing msi or exe from [python.org](https://www.python.org/). 
<br>N.B.: Windows UAC should be switched off, otherwise - use Python from zip
4. Both Anaconda and Miniconda support (32 and 64 bit versions)
5. Creating Conda envrionments, conda package installation support
6. Creating Jython environments
7. Creating PyPy environments (only Unix is supported, by default pypy2.7-5.8.0 version is used)
8. Creating IronPython environments (only Windows is supported, by default [2.7.7 version](https://github.com/IronLanguages/ironpython2/releases/tag/ipy-2.7.7) is used)
9. Virtualenv creation from any python environment created
10. Python from zip creation: downloading archive from specified url, unpacking and preparing to work with
11. Package installation for any environment or virtualenv 


Usage
-----
                                                
Apply the plugin to your project following
[`https://plugins.gradle.org/plugin/com.jetbrains.python.envs`](https://plugins.gradle.org/plugin/com.jetbrains.python.envs),
and configure the associated extension:

```gradle
envs {
  bootstrapDirectory = new File(buildDir, 'bootstrap')
  envsDirectory = new File(buildDir, 'envs')
  
  // Download python zips when Windows is used from http://repository.net/%archieveName%,
  // where {archieveName} is python-{version}-{architecture}.zip
  zipRepository = new URL("http://repository.net/")
  shouldUseZipsFromRepository = Os.isFamily(Os.FAMILY_WINDOWS)
  
  // by default if architecture isn't specified - 64 bit one is used
  // _64Bits = true
  
  //python "envName", "version", [<packages>]
  python "python35_64", "3.5.3", ["django==1.9"]
  //python "envName", "version", "architecture", [<packages>]
  python "python36_32", "3.6.2", "32", ["django==1.10"]
  //virtualenv "virtualEnvName", "sourceEnvName", [<packages>]
  virtualenv "envPython35", "python35_64", ["pytest"]
  virtualenv "envPython36", "python36_32", ["behave", "requests"]

  //conda "envName", "version", "architecture"
  conda "Miniconda3", "Miniconda3-latest", "64"
  //conda "envName", "version", [<packages>]
  conda "Anaconda2", "Anaconda2-4.4.0", [condaPackage("PyQt")]
  //conda "envName", "version", "architecture", [<packages>]
  conda "Anaconda3", "Anaconda3-4.4.0", "64", ["django==1.8"]
  
  //condaenv "envName", "sourceEnvName", "version", [<packages>]
  condaenv "django19", "Miniconda3", "2.7", ["django==1.9"]
  //condaenv "envName", "version", [<packages>]
  //Here will be created additional "Miniconda2-latest" (or another one specified in condaDefaultVersion value) 
  //conda interpreter to be bootstraped
  condaenv "pyqt_env", "2.7", [condaPackage("pyqt")]
  //condaenv "envName", "sourceEnvName", "version", "architecture", [<packages>]
  condaenv "conda34", "Miniconda3", "3.4", "32", ["ipython==2.1", "django==1.6", "behave", "jinja2", "tox==2.0"]

  if (Os.isFamily(Os.FAMILY_WINDOWS)) {
    // This links are used for envs like tox; *nix envs have such links already
    link "bin/python2.7.exe", "bin/python.exe", new File(envsDirectory, "django19")
    link "bin/python3.4.exe", "bin/python.exe", new File(envsDirectory, "conda34")
  }

  //jython "envName", [<packages>]
  jython "jython"
  virtualenv "envJython", "jython", ["django==1.8"]
  
  if (Os.isFamily(Os.FAMILY_UNIX)) {
    //pypy "envName", [<packages>]
    pypy "pypy2", ["django"]
    virtualenv "envPypy2", "pypy2", ["pytest"]
    //pypy "envName", "version", [<packages>]
    //version should be in accordance with python-build
    pypy "pypy3", "pypy3.5-5.8.0", ["nose"]
    virtualenv "envPypy3", "pypy3", ["django"]    
  }
  
  if (Os.isFamily(Os.FAMILY_WINDOWS)) {
    //ironpython "envName", [<packages>]
    ironpython "ironpython64", ["requests"]
    //ironpython "envName", "architecture", [<packages>]
    ironpython "ironpython32", "32", ["requests"]
    // ironpython doesn't support virtualenvs at all
  }
}
```

Then invoke the `build_envs` task. 

This will download and install specified python's interpreters (python, anaconda and miniconda, jython, pypy, ironpython) to `buildDir/bootstrap`.

Then it will create several conda and virtual envs in `buildDir/envs`.

Libraries listed will be installed correspondingly. Packages in list are installed with `pip install` command. If the function `condaPackage()` was called for package name, it will be installed with `conda install` command. It enables to install, for example, PyQt in env.


License
-------

The code is licensed under the Apache 2.0 License. See the included
[LICENSE](LICENSE) file for details.

