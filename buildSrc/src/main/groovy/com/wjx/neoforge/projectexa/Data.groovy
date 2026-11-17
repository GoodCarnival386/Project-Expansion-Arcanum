package com.wjx.neoforge.projectexa

import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.ProjectLayout
import org.gradle.api.file.FileTree
import org.gradle.api.model.ObjectFactory
import org.gradle.api.tasks.Exec
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity

import javax.inject.Inject

abstract class Data extends Exec {
    @Internal
    final DirectoryProperty inputDirectory
    @InputFiles
    @PathSensitive(PathSensitivity.RELATIVE)
    final FileTree generatorSources
    @OutputDirectory
    final DirectoryProperty outputDirectory
    Data() {
        inputDirectory = getObjectFactory().directoryProperty().convention(projectLayout.projectDirectory.dir("src/main/generation"))
        generatorSources = inputDirectory.get().asFileTree.matching {
            exclude("node_modules/**", "out/**", "build/**")
        }
        outputDirectory = getObjectFactory().directoryProperty().convention(projectLayout.projectDirectory.dir("src/generated/resources"))
        setWorkingDir(inputDirectory.get().asFile)
        setCommandLine("node", "--no-warnings", "--no-deprecation", "--loader", "ts-node/esm", "run.ts")
    }

    @Inject
    protected abstract ObjectFactory getObjectFactory()

    @Inject
    protected abstract ProjectLayout getProjectLayout()
}
