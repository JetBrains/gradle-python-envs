package com.jetbrains.python.envs

import org.apache.tools.ant.taskdefs.condition.Os
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.artifacts.Configuration
import org.gradle.api.tasks.StopExecutionException
import org.gradle.util.VersionNumber

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

class PythonEnvsPlugin implements Plugin<Project> {
    String os = System.getProperty('os.name').replaceAll(' ', '')

    private void resolveJython(Project project) {
        project.dependencies {
            jython group: 'org.python', name: 'jython-installer', version: '2.7.1'
        }
    }

    private void resolveMiniconda(Project project, boolean is64, PythonEnvsExtension myExt) {
        def myExtension = "sh"
        if (os.contains("Windows")) {
            os = "Windows"
            myExtension = "exe"
        }
        def myName = "Miniconda2"
        // versions <= 3.16 were named "Miniconda-${version}"
        // But latest in special case: it should always use Miniconda2, even VersionNumber.parse parses latest as 0.0.0
        if (myExt.minicondaVersion != "latest" && VersionNumber.parse(myExt.minicondaVersion) <= VersionNumber.parse("3.16")) {
            myName = "Miniconda"
        }
        project.dependencies {
            if (is64) {
                minicondaInstaller64(group: "miniconda", name: myName, version: myExt.minicondaVersion) {
                    artifact {
                        name = myName
                        type = myExtension
                        classifier = "$os-x86_64"
                        extension = myExtension
                    }
                }
            } else {
                minicondaInstaller32(group: "miniconda", name: myName, version: myExt.minicondaVersion) {
                    artifact {
                        name = myName
                        type = myExtension
                        classifier = "$os-x86"
                        extension = myExtension
                    }
                }
            }
        }
    }

    private void createBootstrapConda(Project project, PythonEnvsExtension envs, boolean is64, String minicondaVersion) {
        project.tasks.create("bootstrap_conda_" + (is64 ? "64" : "32")) {
            def conf = is64 ? project.configurations.minicondaInstaller64 : project.configurations.minicondaInstaller32

            File installDir = new File(envs.bootstrapDirectory, minicondaVersion.concat(is64 ? '_64' : '_32'))
            File condaExecutable = findCondaExecutable(installDir)

            if (is64) {
                envs.minicondaExecutable64 = condaExecutable
            } else {
                envs.minicondaExecutable32 = condaExecutable
            }

            outputs.dir(installDir)
            onlyIf {
                !(installDir.exists())
            }

            doLast {
                project.exec {
                    if (os.contains("Windows")) {
                        commandLine conf.singleFile, "/InstallationType=JustMe", "/AddToPath=0", "/RegisterPython=0", "/S", "/D=$installDir"
                    } else {
                        commandLine "bash", conf.singleFile, "-b", "-p", installDir
                    }
                }

                condaInstall(project, condaExecutable, envs.condaBasePackages)
            }
        }
    }

    private void installPythonBuild(Project project, File installDir) {
        project.tasks.create(name: 'install_python_build') {
            outputs.dir(installDir)
            onlyIf {
                !installDir.exists()
            }

            doLast {
                File pythonBuildZip = new File(project.buildDir, "python-build.zip")
                getClass().getClassLoader().getResource('python-build.zip').withInputStream { is ->
                    pythonBuildZip.withOutputStream { os ->
                        os << is
                    }
                }

                File temporaryUnzipFolder = new File(project.buildDir, "python-build-tmp")
                project.ant.unzip(src: pythonBuildZip, dest: temporaryUnzipFolder)
                project.exec {
                    commandLine "bash", new File(temporaryUnzipFolder, "install.sh")
                    environment PREFIX: installDir
                }

                temporaryUnzipFolder.deleteDir()
                pythonBuildZip.delete()
            }
        }
    }

    private static File findCondaExecutable(File condaDir) {
        return new File(condaDir, Os.isFamily(Os.FAMILY_WINDOWS) ? "Scripts/conda.exe" : "bin/conda")
    }

    private Task createPythonEnvUnix(Project project, PythonEnv env, File envDir) {
        return project.tasks.create(name: "Create $env.type interpreter '$env.name'") {
            onlyIf {
                !envDir.exists()
            }

            dependsOn "install_python_build"

            doLast {
                try {
                    project.exec {
                        executable new File(project.buildDir, "python-build/bin/python-build")
                        args env.version, envDir
                    }
                }
                catch (Exception e) {
                    printRedText(e.message)
                    throw new StopExecutionException()
                }

                pipInstall(project, envDir, env.packages)
            }
        }
    }

    private Task createPythonEnvWindows(Project project, PythonEnv env, File envDir){
        return project.tasks.create(name: "Create $env.type interpreter '$env.name'") {
            onlyIf {
                !envDir.exists()
            }

            doLast {
                try {
                    String urlToMsi = "https://www.python.org/ftp/python/$version/python-$version.msi"
                    project.ant.get(dest: new File(project.buildDir, "python-$version.msi")) {
                        url(url: urlToMsi)
                    }
                }
                catch (Exception e) {
                    printRedText(e.message)
                    throw new StopExecutionException()
                }

                // TODO msi install script

                pipInstall(project, envDir, env.packages)
            }
        }
    }

    private Task createJythonEnv(Project project, PythonEnv env, File envDir) {
        return project.tasks.create(name: "Create jython interpreter '$env.name'") {
            onlyIf {
                !envDir.exists()
            }

            doLast {
                project.javaexec {
                    main = '-jar'
                    args project.configurations.jython.singleFile, '-s', '-d', envDir, '-t', 'standard'
                }

                pipInstall(project, envDir, env.packages)
            }
        }
    }

    private Closure printRedText = { String base ->
        println("\u001B[31m $base \u001B[0m")
    }

    @Override
    void apply(Project project) {
        project.mkdir("build")
        PythonEnvsExtension envs = project.extensions.create("envs", PythonEnvsExtension.class)

        project.repositories {
            ivy {
                url "http://repo.continuum.io"
                layout "pattern", {
                    artifact "[organisation]/[module]-[revision]-[classifier].[ext]"
                }
            }
            mavenCentral()
        }

        project.configurations {
            minicondaInstaller32
            minicondaInstaller64
            jython
        }

        project.afterEvaluate {
            Configuration conf32 = project.configurations.minicondaInstaller32
            conf32.incoming.beforeResolve {
                resolveMiniconda(project, false, envs)
            }

            Configuration conf64 = project.configurations.minicondaInstaller64
            conf64.incoming.beforeResolve {
                resolveMiniconda(project, true, envs)
            }

            Configuration jython = project.configurations.jython
            jython.incoming.beforeResolve {
                resolveJython(project)
            }

            createBootstrapConda(project, envs, true, envs.minicondaVersion)
            createBootstrapConda(project, envs, false, envs.minicondaVersion)
            installPythonBuild(project, new File(project.buildDir, "python-build"))

            Task python_envs_task = project.tasks.create(name: 'build_python_envs') {
                onlyIf { !envs.pythonEnvs.empty }

                envs.pythonEnvs.each { env ->
                    File envDir = new File(envs.envsDirectory, env.name)

                    if (env.type == "jython") {
                        dependsOn createJythonEnv(project, env, envDir)
                    }
                    else if (Os.isFamily(Os.FAMILY_UNIX)) {
                        dependsOn createPythonEnvUnix(project, env, envDir)
                    }
                    else if (Os.isFamily(Os.FAMILY_WINDOWS)) {
                        dependsOn createPythonEnvWindows(project, env, envDir)
                    }
                }
            }

            Task virtualenvs_task = project.tasks.create(name: 'build_virtualenvs') {
                onlyIf { !envs.virtualEnvs.empty }

                envs.virtualEnvs.each { env ->
                    dependsOn project.tasks.create("Create virtualenv '$env.name'") {
                        File virtualEnvDir = new File(envs.virtualenvsDirectory ?: envs.envsDirectory, env.name)

                        onlyIf {
                            !virtualEnvDir.exists()
                        }

                        doLast {
                            File sourceEnvDir = new File(envs.envsDirectory, env.sourceEnv.name)

                            if (env.sourceEnv.type == "conda") {
                                condaInstall(project, findCondaExecutable(sourceEnvDir), ["virtualenv"], sourceEnvDir)
                            } else {
                                pipInstall(project, sourceEnvDir, ["virtualenv"])
                            }

                            project.exec {
                                executable new File(sourceEnvDir, "bin/virtualenv")
                                args virtualEnvDir
                            }

                            pipInstall(project, virtualEnvDir, env.packages)
                        }
                    }
                }
            }

            Task conda_envs_task = project.tasks.create(name: 'build_conda_envs') {
                onlyIf { !envs.condaEnvs.empty }

                envs.condaEnvs.each { env ->
                    dependsOn project.tasks.create("Create conda env '$env.name'") {
                        File envDir = new File(envs.envsDirectory, env.name)
                        boolean is64 = !env.name.endsWith("_32")

                        if (is64) {
                            dependsOn "bootstrap_conda_64"
                        } else {
                            dependsOn "bootstrap_conda_32"
                        }

                        outputs.dir(envDir)
                        onlyIf {
                            !envDir.exists()
                        }

                        File condaExecutable = is64 ? envs.minicondaExecutable64 : envs.minicondaExecutable32

                        doLast {
                            project.exec {
                                executable condaExecutable
                                args "create", "-p", envDir, "-y", "python=$env.version"
                                args env.condaPackages
                            }

                            pipInstall(project, envDir, env.packages)

                            if (env.linkWithVersion && Os.isFamily(Os.FAMILY_WINDOWS)) {
                                // *nix envs have such links already
                                final Path source = Paths.get(envDir.toString(), "python.exe")
                                final Path dest = Paths.get(envDir.toString(), "python" + env.version.toString() + ".exe")

                                Files.createLink(dest, source)
                            }
                        }
                    }
                }
            }

            Task create_files_task = project.tasks.create(name: 'create_files') {
                onlyIf { !envs.files.empty }

                envs.files.each { e ->
                    File f = new File(envs.envsDirectory, e.path)

                    doLast {
                        f.write(e.content)
                    }
                }
            }

            project.tasks.create(name: 'build_envs') {
                dependsOn python_envs_task, conda_envs_task, virtualenvs_task, create_files_task
            }

        }
    }

    private void condaInstall(Project project, File condaExecutable, List<String> packages, File envDir = null) {
        packages.collect { e ->
            [condaExecutable, "install", "-y"] + (envDir != null ? ["-p", envDir] : []) + e
        }.each {
            cmd ->
                project.exec {
                    commandLine cmd.flatten()
                }
        }
    }

    private void pipInstall(Project project, File envDir, List<String> packages) {
        File pipExecutable = new File(envDir, Os.isFamily(Os.FAMILY_WINDOWS) ? "Scripts/pip.exe" : "bin/pip")
        packages.collect { e ->
            [pipExecutable, "install"] + e
        }.each {
            cmd ->
                project.exec {
                    commandLine cmd.flatten()
                }
        }
    }
}
