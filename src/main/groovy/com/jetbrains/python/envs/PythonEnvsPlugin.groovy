package com.jetbrains.python.envs

import org.apache.tools.ant.taskdefs.condition.Os
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.util.VersionNumber
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

class PythonEnvsPlugin implements Plugin<Project> {
    def os = System.getProperty('os.name').replaceAll(' ', '')

    def resolveJython(project) {
        project.dependencies {
            jython group: 'org.python', name: 'jython-installer', version: '2.7.1b3'
        }
    }

    def resolveMiniconda(project, is64, myExt) {
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


    def createBootstrapPython(project, envs, is64, minicondaBootstrapVersionDir) {
        project.task("bootstrapPython" + (is64 ? "64" : "32")) {
            def conf = is64 ? project.configurations.minicondaInstaller64 : project.configurations.minicondaInstaller32

            def installDir = "$minicondaBootstrapVersionDir${is64 ? '_64' : '_32'}"

            def condaExecutable = findCondaExecutable(installDir)

            if (is64) {
                envs.minicondaExecutable64 = condaExecutable
            } else {
                envs.minicondaExecutable32 = condaExecutable
            }


            outputs.dir(installDir)
            onlyIf {
                !(new File(installDir).exists())
            }

            doLast {
                project.exec {
                    if (os.contains("Windows")) {
                        commandLine conf.singleFile, "/InstallationType=JustMe", "/AddToPath=0", "/RegisterPython=0", "/S", "/D=$installDir"
                    } else {
                        commandLine "bash", conf.singleFile, "-b", "-p", installDir
                    }
                }

                condaInstall(project, null, condaExecutable, envs.basePackages)
//            doFirst {
//                if (!myExt.bootstrapDirectory.exists()) {
//                    myExt.bootstrapDirectory.mkdir()
//                }
//            }
            }
        }
    }

    def createBootstrapJython(project, jythonBootstrapDir) {
        project.tasks.create(name: 'bootstrapJython') {
            def conf = project.configurations.jython

            outputs.dir(jythonBootstrapDir)

            onlyIf {
                !jythonBootstrapDir.exists()
            }

            doLast {
                project.javaexec {
                    main = '-jar'
                    args conf.singleFile, '-s', '-d', jythonBootstrapDir, '-t', 'standard'
                }

                project.exec {
                    executable new File(jythonBootstrapDir, "bin/pip")
                    args "install", "virtualenv"
                }
            }
        }
    }

    def findCondaExecutable(root) {
        if (root.endsWith("/")) {
            root = root.substring(0, root.length() - 1)
        }
        return new File("$root/${Os.isFamily(Os.FAMILY_WINDOWS) ? 'Scripts/conda.exe' : 'bin/conda'}").absolutePath
    }

    @Override
    void apply(Project project) {
        def envs = project.extensions.create("envs", PythonEnvsExtension.class)


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
            def conf32 = project.configurations.minicondaInstaller32
            conf32.incoming.beforeResolve {
                resolveMiniconda(project, false, envs)
            }

            def conf64 = project.configurations.minicondaInstaller64
            conf64.incoming.beforeResolve {
                resolveMiniconda(project, true, envs)
            }

            def jython = project.configurations.jython

            jython.incoming.beforeResolve {
                resolveJython(project)
            }

            def minicondaBootstrapVersionDir = new File(envs.bootstrapDirectory, envs.minicondaVersion)

            createBootstrapPython(project, envs, true, minicondaBootstrapVersionDir)
            createBootstrapPython(project, envs, false, minicondaBootstrapVersionDir)

            createBootstrapJython(project, new File(envs.bootstrapDirectory, "jython"))


            def jython_envs_task = project.tasks.create(name: 'build_jython_envs') {
                onlyIf { !envs.jythonEnvs.empty }

                envs.jythonEnvs.each { e ->
                    dependsOn project.tasks.create(name: "Create Jython virtualenv '$name'", dependsOn: 'bootstrapJython') {
                        doLast {
                            project.exec {
                                executable new File(envs.bootstrapDirectory, "jython/bin/virtualenv")
                                args new File(envs.envsDirectory, e.name)
                            }

                            pipInstall(project, new File(envs.bootstrapDirectory, "jython").getPath(), e.packages)
                        }
                    }
                }
            }

            def virtualenvs_task = project.tasks.create(name: 'build_virtualenvs') {
                onlyIf { !envs.virtualEnvs.empty }

                envs.virtualEnvs.each { e ->
                    def name = e.name

                    dependsOn project.tasks.create("Create virtualenv '$name'") {
                        def env = project.file("$envs.envsDirectory/$name")
                        def is64 = name.endsWith("_64") || envs._64Bits

                        if (is64) {
                            dependsOn "bootstrapPython64"
                        } else {
                            dependsOn "bootstrapPython32"
                        }

                        onlyIf {
                            !env.exists()
                        }

                        def conda_executable = is64 ? envs.minicondaExecutable64 : envs.minicondaExecutable32

                        doLast {
                            condaInstall(project, null, conda_executable, ["virtualenv"])

                            project.exec {
                                executable new File(new File(conda_executable).parent, "virtualenv")
                                args "$envs.envsDirectory/$name"
                            }
                        }
                    }
                }

            }

            def conda_envs_task = project.tasks.create(name: 'build_conda_envs') {
                onlyIf { !envs.condaEnvs.empty }

                envs.condaEnvs.each { e ->

                    def name = e.name

                    dependsOn project.tasks.create("Create conda env '$name'") {
                        def env = project.file("$envs.envsDirectory/$name")
                        def is64 = name.endsWith("_64") || envs._64Bits

                        if (is64) {
                            dependsOn "bootstrapPython64"
                        } else {
                            dependsOn "bootstrapPython32"
                        }

                        inputs.property("packages", e.packages)
                        outputs.dir(env)

                        onlyIf {
                            !env.exists()
                        }

                        def conda_executable = is64 ? envs.minicondaExecutable64 : envs.minicondaExecutable32

                        doLast {
                            project.exec {
                                executable conda_executable
                                args "create", "-p", env, "-y", "python=$e.version"
                                args envs.packages
                            }

                            def envPath = "$envs.envsDirectory/$name"
                            pipInstall(project, envPath, e.packages)
                            condaInstall(project, envPath, conda_executable, e.condaPackages)

                            if (e.linkWithVersion && Os.isFamily(Os.FAMILY_WINDOWS)) {
                                // *nix envs have such links already
                                final Path source = Paths.get(envPath, "python.exe");
                                final Path dest = Paths.get(envPath, "python" + e.version.toString() + ".exe");

                                Files.createLink(dest, source);
                            }

                        }
                    }
                }
            }

            def create_files_task = project.tasks.create(name: 'create_files') {
                onlyIf { !envs.files.empty }

                envs.files.each { e ->
                    def f = new File(envs.envsDirectory, e.path)

                    doLast {
                        f.write(e.content)
                    }
                }
            }

            project.tasks.create(name: 'build_envs') {
                dependsOn conda_envs_task, jython_envs_task, virtualenvs_task, create_files_task
            }

        }
    }

    private condaInstall(project, envPath, condaExecutable, packages) {
        packages.collect { e -> (envPath != null ? [condaExecutable, "install", "-p", envPath, "-y"] : [condaExecutable, "install", "-y"]) + e }.each {
            cmd ->
                project.exec {
                    commandLine cmd.flatten()
                }
        }
    }

    private List pipInstall(project, envDir, packages) {
        packages.collect { e -> [project.file("$envDir/${Os.isFamily(Os.FAMILY_WINDOWS) ? 'Scripts/pip.exe' : 'bin/pip'}"), "install"] + e }.each {
            cmd ->
                project.exec {
                    commandLine cmd.flatten()
                }
        }
    }
}

