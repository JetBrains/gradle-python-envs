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
            File condaExecutable = getExecutable("conda", null, installDir, EnvType.CONDA)

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

                condaInstall(project, condaExecutable, installDir, envs.condaBasePackages)
            }
        }
    }

    private void installPythonBuild(Project project, File installDir) {
        project.tasks.create(name: 'install_python_build') {
            outputs.dir(installDir)
            onlyIf {
                !installDir.exists() && Os.isFamily(Os.FAMILY_UNIX)
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

    private static File getExecutable(String executable, PythonEnv env = null, File dir = null, EnvType type = null) {
        String pathString

        switch (type ?: env.type) {
            case [EnvType.PYTHON, EnvType.CONDA]:
                if (executable in ["pip", "virtualenv", "conda"]) {
                    pathString = Os.isFamily(Os.FAMILY_WINDOWS) ? "Scripts/${executable}.exe" : "bin/${executable}"
                } else if (executable == "python") {
                    pathString = "${executable}${Os.isFamily(Os.FAMILY_WINDOWS) ? '.exe' : ''}"
                } else {
                    throw new RuntimeException("$executable is not supported for $env.type yet")
                }
                break
            case EnvType.JYTHON:
                pathString = "bin/${executable}${Os.isFamily(Os.FAMILY_WINDOWS) ? '.exe' : ''}"
                break
            case EnvType.VIRTUALENV:
                pathString = "Scripts/$executable${Os.isFamily(Os.FAMILY_WINDOWS) ? '.exe' : ''}"
                break
            default:
                throw new RuntimeException("$env.type env type is not supported yet")
        }

        return new File(dir ?: env.envDir, pathString)
    }

    private static getPipFile(Project project) {
        new File(project.buildDir, "get-pip.py").with { file ->
            if (!file.exists()){
                project.ant.get(dest: file) {
                    url(url: "https://bootstrap.pypa.io/get-pip.py")
                }
            }
            return file
        }
    }

    private Task createPythonEnvUnix(Project project, PythonEnv env) {
        return project.tasks.create(name: "Create $env.type env '$env.name'") {
            onlyIf {
                !env.envDir.exists() && Os.isFamily(Os.FAMILY_UNIX)
            }

            dependsOn "install_python_build"

            doLast {
                try {
                    project.exec {
                        executable new File(project.buildDir, "python-build/bin/python-build")
                        args env.version, env.envDir
                    }
                }
                catch (Exception e) {
                    printRedText(e.message)
                    throw new StopExecutionException()
                }

                pipInstall(project, env, env.packages)
            }
        }
    }

    private Task createPythonEnvWindows(Project project, PythonEnv env) {
        return project.tasks.create(name: "Create $env.type env '$env.name'") {
            onlyIf {
                !env.envDir.exists() && Os.isFamily(Os.FAMILY_WINDOWS)
            }

            doLast {
                try {
                    boolean is64 = true // TODO 64 bits stuff
                    String extension = VersionNumber.parse(env.version) >= VersionNumber.parse("3.5.0") ? "exe" : "msi"
                    String filename = "python-${env.version}${is64 ? (extension == "msi" ? "." : "-") + "amd64" : ""}.$extension"
                    File installer = new File(project.buildDir, filename)

                    project.ant.get(dest: installer) {
                        url(url: "https://www.python.org/ftp/python/${env.version}/$filename")
                    }
                    if (extension == "msi") {
                        project.exec {
                            commandLine "msiexec", "/i", installer, "/quiet", "TARGETDIR=$env.envDir.absolutePath"
                        }
                    } else if (extension == "exe") {
                        project.mkdir(env.envDir)
                        project.exec {
                            executable installer
                            args installer, "/i", "/quiet", "TargetDir=$env.envDir.absolutePath", "Include_launcher=0",
                                    "InstallLauncherAllUsers=0", "Shortcuts=0", "AssociateFiles=0"
                        }
                    }

                    if (!getExecutable("pip", env).exists()) {
                        project.exec {
                            executable getExecutable("python", env)
                            args getPipFile(project)
                        }
                    }
                    // It's better to save installer for good uninstall
//                    installer.delete()
                }
                catch (Exception e) {
                    printRedText(e.message)
                    throw new StopExecutionException()
                }

                pipInstall(project, env, env.packages)
            }
        }
    }

    private Closure printRedText = { String base ->
        println("\u001B[31m$base\u001B[0m")
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

                envs.pythonEnvs.findAll { it.type == EnvType.PYTHON }.each { env ->
                    if (Os.isFamily(Os.FAMILY_UNIX)) {
                        dependsOn createPythonEnvUnix(project, env)
                    } else if (Os.isFamily(Os.FAMILY_WINDOWS)) {
                        dependsOn createPythonEnvWindows(project, env)
                    } else {
                        printRedText("Something is wrong with os: $os")
                    }
                }
            }

            Task conda_envs_task = project.tasks.create(name: 'build_conda_envs') {
                onlyIf { !envs.condaEnvs.empty }

                envs.condaEnvs.each { env ->
                    dependsOn project.tasks.create("Create conda env '$env.name'") {
                        boolean is64 = !env.name.endsWith("_32")

                        if (is64) {
                            dependsOn "bootstrap_conda_64"
                        } else {
                            dependsOn "bootstrap_conda_32"
                        }

                        outputs.dir(env.envDir)
                        onlyIf {
                            !env.envDir.exists()
                        }

                        File condaExecutable = is64 ? envs.minicondaExecutable64 : envs.minicondaExecutable32

                        doLast {
                            project.exec {
                                executable condaExecutable
                                args "create", "-p", env.envDir, "-y", "python=$env.version"
                                args env.condaPackages
                            }

                            pipInstall(project, env, env.packages)

                            if (env.linkWithVersion && Os.isFamily(Os.FAMILY_WINDOWS)) {
                                // *nix envs have such links already
                                Path source = getExecutable("python", env).toPath()
                                Path dest = getExecutable("python${env.version}.exe", env).toPath()

                                Files.createLink(dest, source)
                            }
                        }
                    }
                }
            }

            Task jython_envs_task = project.tasks.create(name: 'build_jython_envs') {
                onlyIf { !envs.condaEnvs.empty }

                envs.pythonEnvs.findAll { it.type == EnvType.JYTHON }.each { env ->
                    dependsOn project.tasks.create(name: "Create jython env '$env.name'") {
                        onlyIf {
                            !env.envDir.exists()
                        }

                        doLast {
                            project.javaexec {
                                main = '-jar'
                                args project.configurations.jython.singleFile, '-s', '-d', env.envDir, '-t', 'standard'
                            }

                            pipInstall(project, env, env.packages)
                        }
                    }
                }
            }

            Task envs_from_zip_task = project.tasks.create(name: 'build_envs_from_zip') {
                onlyIf { !envs.envsFromZip.empty }

                envs.envsFromZip.each { env ->
                    dependsOn project.tasks.create(name: "Create env '$env.name' from archive $env.url") {
                        onlyIf {
                            !env.envDir.exists()
                        }

                        doLast {
                            try {
                                String archiveName = env.url.toString().with { urlString ->
                                    urlString.substring(urlString.lastIndexOf('/') + 1, urlString.length())
                                }
                                if (!archiveName.endsWith("zip")) {
                                    throw new RuntimeException("Wrong archive extension, only zip is supported")
                                }

                                File zipArchive = new File(project.buildDir, archiveName)
                                project.ant.get(dest: zipArchive) {
                                    url(url: env.url)
                                }
                                project.ant.unzip(src: zipArchive, dest: env.envDir)

                                if (env.type != null) {
                                    if (!getExecutable("pip", env).exists()) {
                                        project.exec {
                                            executable getExecutable("python", env)
                                            args getPipFile(project)
                                        }
                                    } else {
                                        project.exec {
                                            executable getExecutable("python", env)
                                            args "-m", "pip", "install", "--upgrade", "--force", "setuptools", "pip"
                                        }
                                    }
                                }

                                zipArchive.delete()
                            }
                            catch (Exception e) {
                                printRedText(e.message)
                                throw new StopExecutionException()
                            }

                            pipInstall(project, env, env.packages)
                        }
                    }
                }
            }

            Task virtualenvs_task = project.tasks.create(name: 'build_virtualenvs') {
                shouldRunAfter python_envs_task, conda_envs_task, jython_envs_task, envs_from_zip_task

                onlyIf { !envs.virtualEnvs.empty }

                envs.virtualEnvs.each { env ->
                    dependsOn project.tasks.create("Create virtualenv '$env.name'") {
                        onlyIf {
                            !env.envDir.exists() && env.sourceEnv.type != null
                        }

                        doLast {
                            if (env.sourceEnv.type == EnvType.CONDA) {
                                File condaExecutable = env.sourceEnv.name.endsWith("_64") ? envs.minicondaExecutable64 : envs.minicondaExecutable32
                                condaInstall(project, condaExecutable, env.sourceEnv.envDir, ["virtualenv"])
                            } else {
                                pipInstall(project, env.sourceEnv, ["virtualenv"])
                            }

                            project.exec {
                                executable getExecutable("virtualenv", env.sourceEnv)
                                args env.envDir, "--always-copy"
                                workingDir env.sourceEnv.envDir
                            }

                            pipInstall(project, env, env.packages)
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
                dependsOn python_envs_task,
                        conda_envs_task,
                        jython_envs_task,
                        envs_from_zip_task,
                        virtualenvs_task,
                        create_files_task
            }

        }
    }

    private void condaInstall(Project project, File condaExecutable, File envDir, List<String> packages) {
        if (packages == null) {
            return
        }
        packages.each { pckg ->
            project.exec {
                commandLine condaExecutable, "install", "-y", "-p", envDir, pckg
            }
        }
    }

    private void pipInstall(Project project, PythonEnv env, List<String> packages) {
        if (packages == null || env.type == null) {
            return
        }
        File pipExecutable = getExecutable("pip", env)
        packages.each { pckg ->
            project.exec {
                commandLine pipExecutable, "install", pckg
            }
        }
    }
}
