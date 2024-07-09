package com.jetbrains.python.envs.test

import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner
import spock.lang.TempDir

import java.nio.file.Files
import java.nio.file.Path
import spock.lang.Requires
import spock.lang.Specification

import static org.gradle.testkit.runner.TaskOutcome.SUCCESS
import static org.gradle.testkit.runner.TaskOutcome.UP_TO_DATE

class FunctionalTest extends Specification {
    @TempDir
    Path testProjectDir
    File settingsFile
    File buildFile

    def setup() {
        settingsFile = Files.createFile(testProjectDir.resolve('settings.gradle')).toFile()
        buildFile = Files.createFile(testProjectDir.resolve('build.gradle')).toFile()
    }

    def "install python 2.7.15 and virtualenv"() {
        given:
        settingsFile << "rootProject.name = 'gradle-python-envs'"
        buildFile << """
        plugins {
            id "com.jetbrains.python.envs"
        }
        
        envs {
            bootstrapDirectory = new File(buildDir, 'bootstrap')
            envsDirectory = file(buildDir)
            
            python "python-2.7.15", "2.7.15"
            virtualenv "virtualenv-2.7.15", "python-2.7.15"
        }
        
        """

        when:
        BuildResult result = GradleRunner.create()
                .withProjectDir(testProjectDir.toFile())
                .withArguments('build_envs')
                .withPluginClasspath()
                .build()

        then:
        println(result.output)
        result.output.contains('Installing Python-2.7.15')
        result.output.contains('BUILD SUCCESSFUL')
        result.task(":build_envs").outcome == SUCCESS
    }

    def "install python 3.7.1 and virtualenv"() {
        given:
        settingsFile << "rootProject.name = 'gradle-python-envs'"
        buildFile << """
        plugins {
            id "com.jetbrains.python.envs"
        }
        
        envs {
            bootstrapDirectory = new File(buildDir, 'bootstrap')
            envsDirectory = file(buildDir)
            
            python "python-3.7.1", "3.7.1"
            virtualenv "virtualenv-3.7.1", "python-3.7.1"
        }
        
        """

        when:
        BuildResult result = GradleRunner.create()
                .withProjectDir(testProjectDir.toFile())
                .withArguments('build_envs')
                .withPluginClasspath()
                .build()

        then:
        println(result.output)
        result.output.contains('Installing Python-3.7.1')
        result.output.contains('BUILD SUCCESSFUL')
        result.task(":build_envs").outcome == SUCCESS
    }

    def "install python 3.6.5 32-bit and virtualenv"() {
        given:
        settingsFile << "rootProject.name = 'gradle-python-envs'"
        buildFile << """
        plugins {
            id "com.jetbrains.python.envs"
        }
        
        envs {
            bootstrapDirectory = new File(buildDir, 'bootstrap')
            envsDirectory = file(buildDir)
            
            python "python-3.6.5-32", "3.6.5", "32"
            virtualenv "virtualenv-3.6.5", "python-3.6.5-32"
        }
        
        """

        when:
        BuildResult result = GradleRunner.create()
                .withProjectDir(testProjectDir.toFile())
                .withArguments('build_envs')
                .withPluginClasspath()
                .build()

        then:
        println(result.output)
        result.output.contains('Installing Python-3.6.5')
        result.output.contains('BUILD SUCCESSFUL')
        result.task(":build_envs").outcome == SUCCESS
    }

    def "install python 3.5.6 with package"() {
        given:
        settingsFile << "rootProject.name = 'gradle-python-envs'"
        buildFile << """
        plugins {
            id "com.jetbrains.python.envs"
        }
        
        envs {
            bootstrapDirectory = new File(buildDir, 'bootstrap')
            envsDirectory = file(buildDir)
            
            python "python-3.5.6", "3.5.6", ["django==1.10"]
        }
        
        """

        when:
        BuildResult result = GradleRunner.create()
                .withProjectDir(testProjectDir.toFile())
                .withArguments('build_envs')
                .withPluginClasspath()
                .build()

        then:
        println(result.output)
        result.output.contains('Installing Python-3.5.6')
        result.output.contains('BUILD SUCCESSFUL')
        result.task(":build_envs").outcome == SUCCESS
    }

    def "install latest miniconda3"() {
        given:
        settingsFile << "rootProject.name = 'gradle-python-envs'"
        buildFile << """
        plugins {
            id "com.jetbrains.python.envs"
        }
        
        envs {
            bootstrapDirectory = new File(buildDir, 'bootstrap')
            envsDirectory = file(buildDir)
            
            conda "Miniconda3", "Miniconda3-latest"
        }
        
        """

        when:
        BuildResult result = GradleRunner.create()
                .withProjectDir(testProjectDir.toFile())
                .withArguments('build_envs')
                .withPluginClasspath()
                .build()

        then:
        println(result.output)
        result.output.contains('BUILD SUCCESSFUL')
        result.task(":build_envs").outcome == SUCCESS
    }

    def "install latest miniconda2"() {
        given:
        settingsFile << "rootProject.name = 'gradle-python-envs'"
        buildFile << """
        plugins {
            id "com.jetbrains.python.envs"
        }
        
        envs {
            bootstrapDirectory = new File(buildDir, 'bootstrap')
            envsDirectory = file(buildDir)
            
            conda "Miniconda2", "Miniconda2-latest"
        }
        
        """

        when:
        BuildResult result = GradleRunner.create()
                .withProjectDir(testProjectDir.toFile())
                .withArguments('build_envs')
                .withPluginClasspath()
                .build()

        then:
        println(result.output)
        result.output.contains('BUILD SUCCESSFUL')
        result.task(":build_envs").outcome == SUCCESS
    }

    def "install anaconda2-5.3.1 64 bit with conda package"() {
        given:
        settingsFile << "rootProject.name = 'gradle-python-envs'"
        buildFile << """
        plugins {
            id "com.jetbrains.python.envs"
        }
        
        envs {
            bootstrapDirectory = new File(buildDir, 'bootstrap')
            envsDirectory = file(buildDir)
            
            conda "Anaconda2", "Anaconda2-5.3.1", [condaPackage("PyQt")]
        }
        
        """

        when:
        BuildResult result = GradleRunner.create()
                .withProjectDir(testProjectDir.toFile())
                .withArguments('build_envs')
                .withPluginClasspath()
                .build()

        then:
        println(result.output)
        result.output.contains('BUILD SUCCESSFUL')
        result.task(":build_envs").outcome == SUCCESS
    }

    def "install anaconda3-5.3.1 with python package"() {
        given:
        settingsFile << "rootProject.name = 'gradle-python-envs'"
        buildFile << """
        plugins {
            id "com.jetbrains.python.envs"
        }
        
        envs {
            bootstrapDirectory = new File(buildDir, 'bootstrap')
            envsDirectory = file(buildDir)
            
            conda "Anaconda3", "Anaconda3-5.3.1", ["django==1.8"]
        }
        
        """

        when:
        BuildResult result = GradleRunner.create()
                .withProjectDir(testProjectDir.toFile())
                .withArguments('build_envs')
                .withPluginClasspath()
                .build()

        then:
        println(result.output)
        result.output.contains('BUILD SUCCESSFUL')
        result.task(":build_envs").outcome == SUCCESS
    }

    def "install condaenv with conda package"() {
        given:
        settingsFile << "rootProject.name = 'gradle-python-envs'"
        buildFile << """
        plugins {
            id "com.jetbrains.python.envs"
        }
        
        envs {
            bootstrapDirectory = new File(buildDir, 'bootstrap')
            envsDirectory = file(buildDir)
            
            condaenv "pyqt_env", "2.7", [condaPackage("pyqt")]
        }
        
        """

        when:
        BuildResult result = GradleRunner.create()
                .withProjectDir(testProjectDir.toFile())
                .withArguments('build_envs')
                .withPluginClasspath()
                .build()

        then:
        println(result.output)
        result.output.contains('BUILD SUCCESSFUL')
        result.task(":build_envs").outcome == SUCCESS
    }

    def "install jython and virtualenv"() {
        given:
        settingsFile << "rootProject.name = 'gradle-python-envs'"
        buildFile << """
        plugins {
            id "com.jetbrains.python.envs"
        }
        
        envs {
            bootstrapDirectory = new File(buildDir, 'bootstrap')
            envsDirectory = file(buildDir)
            
            jython "jython"
            virtualenv "envJython", "jython"
        }
        
        """

        when:
        BuildResult result = GradleRunner.create()
                .withProjectDir(testProjectDir.toFile())
                .withArguments('build_envs')
                .withPluginClasspath()
                .build()

        then:
        println(result.output)
        result.output.contains('BUILD SUCCESSFUL')
        result.task(":build_envs").outcome == SUCCESS
    }

    @Requires({ os.isLinux() || os.isMacOs() })
    def "install pypy2 and virtualenv with package"() {
        given:
        settingsFile << "rootProject.name = 'gradle-python-envs'"
        buildFile << """
        plugins {
            id "com.jetbrains.python.envs"
        }
        
        envs {
            bootstrapDirectory = new File(buildDir, 'bootstrap')
            envsDirectory = file(buildDir)
            
            pypy "pypy2", ["django"]
            virtualenv "envPypy2", "pypy2", ["pytest"]
        }
        
        """

        when:
        BuildResult result = GradleRunner.create()
                .withProjectDir(testProjectDir.toFile())
                .withArguments('build_envs')
                .withPluginClasspath()
                .build()

        then:
        println(result.output)
        result.output.contains('BUILD SUCCESSFUL')
        result.task(":build_envs").outcome in [SUCCESS, UP_TO_DATE]
    }

    @Requires({ os.isLinux() || os.isMacOs() })
    def "install pypy3 and virtualenv with package"() {
        given:
        settingsFile << "rootProject.name = 'gradle-python-envs'"
        buildFile << """
        plugins {
            id "com.jetbrains.python.envs"
        }
        
        envs {
            bootstrapDirectory = new File(buildDir, 'bootstrap')
            envsDirectory = file(buildDir)
            
             pypy "pypy3", "pypy3.5-5.8.0", ["nose"]
             virtualenv "envPypy3", "pypy3", ["django"]
        }
        
        """

        when:
        BuildResult result = GradleRunner.create()
                .withProjectDir(testProjectDir.toFile())
                .withArguments('build_envs')
                .withPluginClasspath()
                .build()

        then:
        println(result.output)
        result.output.contains('BUILD SUCCESSFUL')
        result.task(":build_envs").outcome in [SUCCESS, UP_TO_DATE]
    }

    @Requires({ os.isWindows() })
    def "install ironpython32"() {
        given:
        settingsFile << "rootProject.name = 'gradle-python-envs'"
        buildFile << """
        plugins {
            id "com.jetbrains.python.envs"
        }
        
        envs {
            bootstrapDirectory = new File(buildDir, 'bootstrap')
            envsDirectory = file(buildDir)
            
            ironpython "ironpython", "32", ["requests"]
        }
        
        """

        when:
        BuildResult result = GradleRunner.create()
                .withProjectDir(testProjectDir.toFile())
                .withArguments('build_envs')
                .withPluginClasspath()
                .build()

        then:
        println(result.output)
        result.output.contains('Downloading IronPython.2.7.9.zip archive')
        result.output.contains('BUILD SUCCESSFUL')
        result.task(":build_envs").outcome == SUCCESS
    }

    @Requires({ os.isWindows() })
    def "install ironpython64"() {
        given:
        settingsFile << "rootProject.name = 'gradle-python-envs'"
        buildFile << """
        plugins {
            id "com.jetbrains.python.envs"
        }
        
        envs {
            bootstrapDirectory = new File(buildDir, 'bootstrap')
            envsDirectory = file(buildDir)
            
            ironpython "ironpython64", ["requests"]
        }
        
        """

        when:
        BuildResult result = GradleRunner.create()
                .withProjectDir(testProjectDir.toFile())
                .withArguments('build_envs')
                .withPluginClasspath()
                .build()

        then:
        println(result.output)
        result.output.contains('Downloading IronPython.2.7.9.zip archive')
        result.output.contains('BUILD SUCCESSFUL')
        result.task(":build_envs").outcome == SUCCESS
    }

    def "test can't patch pre-built python"() {
        given:
        settingsFile << "rootProject.name = 'gradle-python-envs-test'"
        buildFile << """
        plugins {
            id "com.jetbrains.python.envs"
        }
        
        apply plugin: 'com.jetbrains.python.envs'
        
        envs {
            bootstrapDirectory = new File(buildDir, 'bootstrap')
            envsDirectory = file(buildDir)
            
            zipRepository = new URL("https://example.com/repo/")
            shouldUseZipsFromRepository = true

            python "python-3.10.0", "3.10.0", "64", [], "example.patch" 
        }
        """

        when:
        GradleRunner.create()
            .withProjectDir(testProjectDir.toFile())
            .withArguments('build_envs')
            .withPluginClasspath()
            .build()

        then:
        def e = thrown(Exception.class)
        e.message.contains("A patch is defined for a pre-built Python")
    }

    def "test apply patch"() {
        given:
        def patchContents ="""Index: README.rst
IDEA additional info:
Subsystem: com.intellij.openapi.diff.impl.patch.CharsetEP
<+>UTF-8
===================================================================
diff --git a/README.rst b/README.rst
--- a/README.rst\t(revision 4641afef661e6a22bc64194bd334b161c95edfe2)
+++ b/README.rst\t(date 1634932856418)
@@ -265,3 +265,5 @@
 code but these are entirely optional.
 
 All trademarks referenced herein are property of their respective holders.
+
+hello, world
\\ No newline at end of file

"""
        File patchFile = testProjectDir.newFile("example.patch")
        patchFile << patchContents
        settingsFile << "rootProject.name = 'gradle-python-envs-test'"
        buildFile << """
        plugins {
            id "com.jetbrains.python.envs"
        }
        
        apply plugin: 'com.jetbrains.python.envs'
        
        envs {
            bootstrapDirectory = new File(buildDir, 'bootstrap')
            envsDirectory = file(buildDir)

            python "python-3.10.0", "3.10.0", "64", [], "${patchFile.toURI()}"
        }
        """

        when:
        BuildResult result = GradleRunner.create()
                .withProjectDir(testProjectDir.toFile())
                .withArguments('build_envs')
                .withPluginClasspath()
                .build()

        then:
        println(result.output)
        result.output.contains("patching file README.rst")
        result.output.contains('BUILD SUCCESSFUL')
        result.task(":build_envs").outcome == SUCCESS
    }
}
