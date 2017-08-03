Gradle Python Envs Plugin
========================

Gradle plugin to create Python envs.

This plugin is based on [gradle-miniconda-plugin](https://github.com/palantir/gradle-miniconda-plugin),
but in addition to creating Conda envs it provides:

1. Silent Miniconda installation
2. A convenient DSL to specify target Python environments 
3. Working with both 32 and 64 bit versions of Miniconda
4. Creating Python envs for Unix with [python-build](https://github.com/pyenv/pyenv/tree/master/plugins/python-build) for Unix
<br>N.B.: [Common build problems](https://github.com/pyenv/pyenv/wiki/Common-build-problems) article from pyenv
5. Creating Python envs for Windows by installing msi or exe from [python.org](https://www.python.org/). 
<br>N.B.: Windows UAC should be switched off, otherwise - use Env from zip
6. Creating Jython environments
7. Creating PyPy environments (only Unix is supported, by default pypy2.7-5.8.0 version is used)
8. Creating IronPython environments (only Windows is supported, by default [2.7.7 version](https://github.com/IronLanguages/ironpython2/releases/tag/ipy-2.7.7) is used)
9. Virtualenv creation from any environment created
10. Env from zip creation: downloading archive from specified url, unpacking and preparing to work with
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
  // if virtualenvsDirectory is not specified - envsDirectory is used
  virtualenvsDirectory = new File(buildDir, 'virtualenvs')  
  
  // List of packages to install in bootstrapped miniconda's environments
  condaBasePackages = ["requests"]
  
  // by default if architecture isn't specified - 64 bit one is used
  // _64Bits = true
  
  //python "envName", "version", "architecture", [<packages>]
  python "python35_64", "3.5.3", "64", ["django==1.9"]
  python "python36_32", "3.6.2", "32", ["django==1.10"]
  //virtualenv "virtualEnvName", "sourceEnvName", [<packages>]
  virtualenv "envPython35", "python35_64", ["pytest"]
  virtualenv "envPython36", "python36_32", ["behave", "requests"]

  //conda "envName", "version", "architecture", [<packages>]
  conda "django19", "2.7", null, ["django==1.9"]
  conda "conda34", "3.4", "64", ["ipython==2.1", "django==1.6", "behave", "jinja2", "tox==2.0"]
  conda "pyqt_env", "2.7", null, [condaPackage("pyqt")]
  virtualenv "envConda34", "conda34", ["django==1.9"]

  if (Os.isFamily(Os.FAMILY_WINDOWS)) {
    // This links are used for envs like tox; *nix envs have such links already
    link "django19/bin/python2.7.exe", "django19/bin/python.exe"
    link "conda34/bin/python3.4.exe", "conda34/bin/python.exe"
  }

  //jython "envName", [<packages>]
  jython "jython"
  virtualenv "envJython", "jython", ["django==1.8"]
  
  if (Os.isFamily(Os.FAMILY_UNIX)) {
    //pypy "envName", [<packages>], "version"
    //version should be in accordance with python-build
    
    pypy "pypy2", ["django"]
    virtualenv "envPypy2", "pypy2", ["pytest"]
    pypy "pypy3", ["nose"], "pypy3.5-5.8.0"
    virtualenv "envPypy3", "pypy3", ["django"]    
  }
  
  if (Os.isFamily(Os.FAMILY_WINDOWS)) {
    ironpython "ironpython", ["requests"], "32"
    // ironpython doesn't support virtualenvs at all
  }
}
```

Then invoke the `build_envs` task. 

This will download and install the latest versions of Miniconda both for 32 and 64 bits to `buildDir/bootstrap`.

Then it will create several envs in `buildDir/envs` and virtualenvs in `buildDir/virtualenvs`, installing all the libraries listed correspondingly. Packages in list are installed with `pip install` command. If the function `condaPackage()` was called for package name, it will be installed with `conda install` command. It enables to install, for example, PyQt in env.


License
-------

The code is licensed under the Apache 2.0 License. See the included
[LICENSE](LICENSE) file for details.

