package com.jetbrains.python.envs

import org.apache.tools.ant.filters.StringInputStream
import org.apache.tools.ant.taskdefs.condition.Os
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.util.VersionNumber

import java.nio.file.Paths

class PythonEnvsPlugin implements Plugin<Project> {
    private static String osName = System.getProperty('os.name').replaceAll(' ', '').with {
        return it.contains("Windows") ? "Windows" : it
    }

    private static Boolean isWindows = Os.isFamily(Os.FAMILY_WINDOWS)
    private static Boolean isUnix = Os.isFamily(Os.FAMILY_UNIX)
    private static Boolean isMacOsX = Os.isFamily(Os.FAMILY_MAC)

    private static URL getUrlToDownloadConda(Conda conda) {
        final String repository = (conda.version.toLowerCase().contains("miniconda")) ? "miniconda" : "archive"
        final String arch = getArch()
        final String ext = isWindows ? "exe" : "sh"

        return new URL("https://repo.continuum.io/$repository/${conda.version}-$osName-$arch.$ext")
    }

    private static String getArch() {
        def arch = System.getProperty("os.arch")
        switch (arch) {
            case ~/x86|i386|ia-32|i686/:
                arch = "x86"
                break
            case ~/x86_64|amd64|x64|x86-64/:
                arch = "x86_64"
                break
            case ~/arm|arm-v7|armv7|arm32/:
                arch = "armv7l"
                break
            case ~/aarch64|arm64|arm-v8/:
                arch = isMacOsX ? "arm64" : "aarch64"
                break
        }
        return arch
    }

    private static File getExecutable(String executable, Python env = null, File dir = null, EnvType type = null) {
        String pathString

        switch (type ?: env.type) {
            case [EnvType.PYTHON, EnvType.CONDA]:
                if (executable in ["pip", "virtualenv", "conda"]) {
                    pathString = isWindows ? "Scripts/${executable}.exe" : "bin/${executable}"
                } else if (executable.startsWith("python")) {
                    pathString = isWindows ? "${executable}.exe" : "bin/${executable}"
                } else {
                    throw new RuntimeException("$executable is not supported for $env.type yet")
                }
                break
            case [EnvType.JYTHON, EnvType.PYPY]:
                if (env.type == EnvType.JYTHON && executable == "python") executable = "jython"
                pathString = "bin/${executable}${isWindows ? '.exe' : ''}"
                break
            case EnvType.IRONPYTHON:
                if (executable in ["ipy", "python"] ) {
                    pathString = "net45/${env.is64 ? "ipy.exe" : "ipy32.exe"}"
                } else {
                    pathString = "Scripts/${executable}.exe"
                }
                break
            case EnvType.VIRTUALENV:
                pathString = isWindows ? "Scripts/${executable}.exe" : "bin/${executable}"
                break
            default:
                throw new RuntimeException("$env.type env type is not supported yet")
        }

        return new File(dir ?: env.envDir, pathString)
    }

    private static File getPipFile(Project project) {
        new File(project.buildDir, "get-pip.py").with { file ->
            if (!file.exists()) {
                project.ant.get(dest: file) {
                    url(url: "https://bootstrap.pypa.io/get-pip.py")
                }
            }
            return file
        }
    }

    private static Task createInstallPythonBuildTask(Project project, File installDir) {
        return project.tasks.create(name: 'install_python_build') {

            onlyIf {
                isUnix && !installDir.exists()
            }

            doFirst {
                project.buildDir.mkdirs()
            }

            doLast {
                new File(project.buildDir, "pyenv.zip").with { pyenvZip ->
                    project.logger.quiet("Downloading latest pyenv from github")
                    project.ant.get(dest: pyenvZip) {
                        url(url: "https://github.com/pyenv/pyenv/archive/master.zip")
                    }

                    File unzipFolder = new File(project.buildDir, "python-build-tmp")
                    String pathToPythonBuildInPyenv = "pyenv-master/plugins/python-build"

                    project.logger.quiet("Unzipping python-build to $unzipFolder")
                    project.copy {
                        from project.zipTree(pyenvZip)
                        into unzipFolder
                        include "$pathToPythonBuildInPyenv/**"
                        eachFile { file ->
                            file.path = file.path.replaceFirst(pathToPythonBuildInPyenv, '')
                        }
                    }

                    project.logger.quiet("Installing python-build via bash to $installDir")
                    project.exec {
                        commandLine "bash", new File(unzipFolder, "install.sh")
                        environment PREFIX: installDir
                    }

                    project.logger.quiet("Removing garbage")
                    unzipFolder.deleteDir()
                    pyenvZip.delete()
                }
            }
        }
    }

    private Task createPythonUnixTask(Project project, Python env) {

        return project.tasks.create(name: "Bootstrap_${env.type}_'$env.name'") {

            dependsOn "install_python_build"

            onlyIf {
                isUnix && (!env.envDir.exists() || isPythonInvalid(project, env))
            }

            doFirst {
                env.envDir.mkdirs()
                env.envDir.deleteDir()
            }

            doLast {
                project.logger.quiet("Creating $env.type '$env.name' at $env.envDir directory")
                try {
                    project.exec {
                        executable new File(project.buildDir, "python-build/bin/python-build")
                        if (env.patchFileUri != null) {
                            project.logger.quiet("Applying patch from ${env.patchFileUri} to ${env.name}")
                            if (Paths.get(env.patchFileUri).isAbsolute()) {
                                standardInput = new FileInputStream(env.patchFileUri)
                            }
                            else {
                                standardInput = new StringInputStream(new URI(env.patchFileUri).toURL().text)
                            }
                            args "-p", env.version, env.envDir
                        }
                        else {
                            args env.version, env.envDir
                        }
                    }
                    project.logger.quiet("Successfully")
                }
                catch (Exception e) {
                    if (isPythonInvalid(project, env)) {
                        project.logger.error(e.message)
                        throw new GradleException(e.message)
                    } else {
                        project.logger.warn(e.message)
                    }
                }

                upgradePipAndSetuptools(project, env)
                pipInstall(project, env, env.packages)
            }
        }
    }

    private Task createPythonWindowsTask(Project project, Python env) {
        return project.tasks.create(name: "Bootstrap_${env.type}_'$env.name'") {
            onlyIf {
                isWindows && (!env.envDir.exists() || isPythonInvalid(project, env))
            }

            doFirst {
                project.buildDir.mkdir()
                env.envDir.mkdirs()
                env.envDir.deleteDir()
            }

            doLast {
                project.logger.quiet("Creating $env.type '$env.name' at $env.envDir directory")

                try {
                    String extension = VersionNumber.parse(env.version) >= VersionNumber.parse("3.5.0") ? "exe" : "msi"
                    String filename = "python-${env.version}${env.is64 ? (extension == "msi" ? "." : "-") + "amd64" : ""}.$extension"
                    File installer = new File(project.buildDir, filename)

                    project.logger.quiet("Downloading $installer")
                    project.ant.get(dest: installer) {
                        url(url: "https://www.python.org/ftp/python/${env.version}/$filename")
                    }

                    project.logger.quiet("Installing $env.name")
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
                        project.logger.quiet("Downloading & installing pip and setuptools")
                        project.exec {
                            executable getExecutable("python", env)
                            args getPipFile(project)
                        }
                    }
                    // It's better to save installer for good uninstall
//                    installer.delete()
                }
                catch (Exception e) {
                    project.logger.error(e.message)
                    throw new GradleException(e.message)
                }

                pipInstall(project, env, env.packages)
            }
        }
    }

    private Task createJythonTask(Project project, Python env) {
        return project.tasks.create(name: "Bootstrap_${env.type}_'$env.name'") {
            onlyIf {
                !env.envDir.exists() || isPythonInvalid(project, env)
            }

            doFirst {
                env.envDir.deleteDir()
            }

            doLast {
                project.logger.quiet("Creating $env.type '$env.name' at $env.envDir directory")

                project.javaexec {
                    main = '-jar'
                    args project.configurations.jython.singleFile, '-s', '-d', env.envDir, '-t', 'standard'
                }

                pipInstall(project, env, env.packages)
            }
        }
    }

    @Override
    void apply(Project project) {
        PythonEnvsExtension envs = project.extensions.create("envs", PythonEnvsExtension.class)

        project.repositories {
            mavenCentral()
        }

        project.configurations {
            jython
        }

        project.afterEvaluate {
            project.configurations.jython.incoming.beforeResolve {
                project.dependencies {
                    jython group: 'org.python', name: 'jython-installer', version: '2.7.1'
                }
            }

            createInstallPythonBuildTask(project, new File(project.buildDir, "python-build"))

            Task python_task = project.tasks.create(name: 'build_pythons') {

                onlyIf { !envs.pythons.empty }

                envs.pythons.each { env ->
                    switch (env.type) {
                        case EnvType.PYTHON:
                            if (isUnix) {
                                dependsOn createPythonUnixTask(project, env)
                            } else if (isWindows) {
                                dependsOn createPythonWindowsTask(project, env)
                            } else {
                                project.logger.error("Something is wrong with os: $osName")
                            }
                            break
                        case EnvType.JYTHON:
                            dependsOn createJythonTask(project, env)
                            break
                        case EnvType.PYPY:
                            if (isUnix) {
                                dependsOn createPythonUnixTask(project, env)
                            } else {
                                project.logger.warn("PyPy installation isn't supported for $osName, please use envFromZip instead")
                            }
                            break
                        default:
                            project.logger.error("$env.type isn't supported yet")
                    }
                }
            }

            Task python_from_zip_task = project.tasks.create(name: 'build_pythons_from_zip') {

                onlyIf { !envs.pythonsFromZip.empty }

                envs.pythonsFromZip.each { env ->
                    dependsOn project.tasks.create(name: "Bootstrap_${env.type ? env.type : ''}_'$env.name'_from_archive") {
                        onlyIf {
                            !env.envDir.exists() || isPythonInvalid(project, env)
                        }

                        doFirst {
                            project.buildDir.mkdir()
                            env.envDir.mkdirs()
                            env.envDir.deleteDir()
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
                                project.logger.quiet("Downloading $archiveName archive from $env.url")
                                project.ant.get(dest: zipArchive) {
                                    url(url: env.url)
                                }

                                project.logger.quiet("Unzipping downloaded $archiveName archive")
                                project.ant.unzip(src: zipArchive, dest: env.envDir)

                                env.envDir.with { dir ->
                                    if (dir.listFiles().length == 1) {
                                        File intermediateDir = dir.listFiles().last()
                                        if (!intermediateDir.isDirectory()) {
                                            throw new RuntimeException("Archive is wrong, $env.url")
                                        }
                                        project.ant.move(todir: dir) {
                                            fileset(dir: intermediateDir)
                                        }
                                    } else {
                                        return dir
                                    }
                                }

                                if (env.type != null) {
                                    if (!getExecutable("pip", env).exists()) {
                                        project.logger.quiet("Downloading & installing pip and setuptools")
                                        project.exec {
                                            if (env.type == EnvType.IRONPYTHON) {
                                                executable getExecutable("ipy", env)
                                                args "-m", "ensurepip"
                                            } else {
                                                executable getExecutable("python", env)
                                                args getPipFile(project)
                                            }
                                        }
                                    }
                                    upgradePipAndSetuptools(project, env)
                                }

                                project.logger.quiet("Deleting $archiveName archive")
                                zipArchive.delete()
                            }
                            catch (Exception e) {
                                project.logger.error(e.message)
                                throw new GradleException(e.message)
                            }

                            pipInstall(project, env, env.packages)
                        }
                    }
                }
            }

            Task virtualenvs_task = project.tasks.create(name: 'build_virtual_envs') {
                shouldRunAfter python_task, python_from_zip_task

                onlyIf { !envs.virtualEnvs.empty }

                envs.virtualEnvs.each { env ->
                    if (env.sourceEnv.type == EnvType.IRONPYTHON) {
                        project.logger.warn("IronPython doesn't support virtualenvs")
                        return
                    }

                    dependsOn project.tasks.create("Create_virtualenv_'$env.name'") {
                        onlyIf {
                            (!env.envDir.exists() || isPythonInvalid(project, env)) && env.sourceEnv.type != null
                        }

                        doFirst {
                            env.envDir.mkdirs()
                            env.envDir.deleteDir()
                        }

                        doLast {
                            project.logger.quiet("Installing needed virtualenv package")

                            pipInstall(project, env.sourceEnv, ["virtualenv"])

                            project.logger.quiet("Creating virtualenv from $env.sourceEnv.name at $env.envDir")
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

            Task conda_task = project.tasks.create(name: "build_condas") {

                onlyIf { !envs.condas.empty }

                envs.condas.each { Conda env ->
                    dependsOn project.tasks.create(name: "Bootstrap_${env.type}_'$env.name'") {
                        onlyIf {
                            !env.envDir.exists() || isPythonInvalid(project, env)
                        }

                        doFirst {
                            project.buildDir.mkdir()
                            env.envDir.mkdirs()
                            env.envDir.deleteDir()
                        }

                        doLast {
                            URL urlToConda = getUrlToDownloadConda(env)
                            File installer = new File(project.buildDir, urlToConda.toString().split("/").last())

                            if (!installer.exists()) {
                                project.logger.quiet("Downloading $installer.name")
                                project.ant.get(dest: installer) {
                                    url(url: urlToConda)
                                }
                            }

                            project.logger.quiet("Bootstraping to $env.envDir")
                            project.exec {
                                if (isWindows) {
                                    commandLine installer, "/InstallationType=JustMe", "/AddToPath=0", "/RegisterPython=0", "/S", "/D=$env.envDir"
                                } else {
                                    commandLine "bash", installer, "-b", "-p", env.envDir
                                }
                            }

                            pipInstall(project, env, env.packages)
                            condaInstall(project, env, env.condaPackages)
                        }
                    }
                }
            }

            Task conda_envs_task = project.tasks.create(name: 'build_conda_envs') {
                shouldRunAfter conda_task

                onlyIf { !envs.condaEnvs.empty }

                envs.condaEnvs.each { env ->
                    dependsOn project.tasks.create("Create_conda_env_'$env.name'") {
                        onlyIf {
                            !env.envDir.exists() || isPythonInvalid(project, env)
                        }

                        doFirst {
                            env.envDir.mkdirs()
                            env.envDir.deleteDir()
                        }

                        doLast {
                            project.logger.quiet("Creating condaenv '$env.name' at $env.envDir directory")
                            project.exec {
                                executable getExecutable("conda", env.sourceEnv)
                                args "create", "-p", env.envDir, "-y", "python=$env.version"
                                args env.condaPackages
                            }

                            pipInstall(project, env, env.packages)
                        }
                    }
                }
            }

            project.tasks.create(name: 'build_envs') {
                dependsOn python_task,
                        python_from_zip_task,
                        virtualenvs_task,
                        conda_task,
                        conda_envs_task
            }
        }
    }

    private void upgradePipAndSetuptools(Project project, Python env) {
        project.logger.quiet("Force upgrade pip and setuptools")
        List<String> command = [
                getExecutable("python", env), "-m", "pip", "install",
                *project.extensions.findByName("envs").getProperty("pipInstallOptions").split(" "),
                "--upgrade", "--force", "pip", "setuptools"
        ]

        project.logger.quiet("Executing '${command.join(" ")}'")
        if (project.exec {
            commandLine command
        }.exitValue != 0) throw new GradleException("pip & setuptools upgrade failed")
    }

    private void pipInstall(Project project, Python env, List<String> packages) {
        if (packages == null || packages.empty || env.type == null) {
            return
        }
        project.logger.quiet("Installing packages via pip: $packages")

        List<String> command = [
                getExecutable("pip", env),
                "install",
                *project.extensions.findByName("envs").getProperty("pipInstallOptions").split(" "),
                *packages
        ]
        project.logger.quiet("Executing '${command.join(" ")}'")

        if (project.exec {
            commandLine command
        }.exitValue != 0) throw new GradleException("pip install failed")
    }

    private void condaInstall(Project project, Conda conda, List<String> packages) {
        if (packages == null || packages.empty) {
            return
        }
        project.logger.quiet("Installing packages via conda: $packages")

        List<String> command = [
                getExecutable("conda", conda),
                "install", "-y",
                "-p", conda.envDir,
                *packages
        ]
        project.logger.quiet("Executing '${command.join(" ")}'")

        if (project.exec {
            commandLine command
        }.exitValue != 0) throw new GradleException("conda install failed")
    }

    private boolean isPythonValid(Project project, Python env) {
        File exec = getExecutable("python", env)
        if (!exec.exists()) return false

        int exitValue
        try {
            exitValue = project.exec { commandLine exec, "-c", "'print(1)'" }.exitValue
        } catch (ignored) {
            return false
        }

        return exitValue == 0
    }

    private boolean isPythonInvalid(Project project, Python env) {
        return !isPythonValid(project, env)
    }
}
