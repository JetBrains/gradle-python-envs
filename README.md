Gradle Conda Envs Plugin 
========================

Gradle plugin to create conda envs.

Conda envs are python virtual environments based on [Miniconda](http://conda.pydata.org/miniconda.html).

This plugin is an extended and modified fork of [gradle-miniconda-plugin](https://github.com/palantir/gradle-miniconda-plugin)

Usage
-----
                                                
Apply the plugin to your project following
[`https://plugins.gradle.org/plugin/com.palantir.python.miniconda`](https://plugins.gradle.org/plugin/com.palantir.python.miniconda),
and configure the associated extension:

```gradle
miniconda {
    bootstrapDirectoryPrefix = new File(System.getProperty('user.home'), '.miniconda')
    buildEnvironmentDirectory = new File(buildDir, 'python')
    minicondaVersion = '3.10.1'
    packages = ['ipython-notebook']
}
```

Then invoke the `setupPython` task and use the resulting installation directory from `Exec` tasks:

```gradle
task launchNotebook(type: Exec) {
    dependsOn 'setupPython'
    executable "${miniconda.buildEnvironmentDirectory}/bin/ipython"
    args 'notebook'
}
```

License
-------

The code is licensed under the Apache 2.0 License. See the included
[LICENSE](LICENSE) file for details.

