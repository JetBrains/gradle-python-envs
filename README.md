Gradle Python Envs Plugin
========================

Gradle plugin to create conda envs.

Conda envs are python virtual environments based on [Miniconda](http://conda.pydata.org/miniconda.html).

This plugin is based on [gradle-miniconda-plugin](https://github.com/palantir/gradle-miniconda-plugin),
but in addition to downloading Miniconda it provides:

1. Silent Miniconda installation
2. A convenient DSL to specify target Python environments 
3. Creating Jython environments
4. Working with both 32 and 64 bit versions of Miniconda

Usage
-----
                                                
Apply the plugin to your project following
[`https://plugins.gradle.org/plugin/com.jetbrains.python.envs`](https://plugins.gradle.org/plugin/com.jetbrains.python.envs),
and configure the associated extension:

```gradle
envs {
  bootstrapDirectory = new File(buildDir, 'pythons')
  envsDirectory = new File(buildDir, 'envs')
  minicondaVersion = 'latest'
  packages = ["pip", "setuptools"]

  conda "django19", "2.7", ["django==1.9"], true

  conda "python34_64", "3.4", ["ipython==2.1", "django==1.6", "behave", "jinja2", "tox==2.0"], true

  conda_install "pyqt_env", "2.7", ["pyqt"], false

  jython "jython25", []
}
```

Then invoke the `build_envs` task. 

This will download and install the latest versions of Miniconda both for 32 and 64 bits and Jython to 
`buildDir/pythons`.

Then it will create `django19`, `python34_64` and `pyqt_env` conda envs and `jython25` Jython env in `buildDir/envs`,
installing all the libraries listed correspondingly.

For `conda` libraries will be installed via `pip install` command.  
For `conda_install` command libraries will be installed via `conda install` command. It enables to install, for example,
PyQt in env.

If the last boolean parameter is true, binary will be linked with version name, i.e. "python" will have "python2.7"
link (same for exe file). Used for envs like tox.


License
-------

The code is licensed under the Apache 2.0 License. See the included
[LICENSE](LICENSE) file for details.

