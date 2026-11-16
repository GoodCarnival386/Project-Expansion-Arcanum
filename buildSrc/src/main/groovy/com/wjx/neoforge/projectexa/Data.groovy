package com.wjx.neoforge.projectexa

import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.ProjectLayout
import org.gradle.api.file.RegularFile
import org.gradle.api.file.FileTree
import org.gradle.api.model.ObjectFactory
import org.gradle.api.tasks.Exec
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity

import javax.inject.Inject

abstract class Data extends Exec {
    @InputFile
    final RegularFile script
    @Internal
    final DirectoryProperty inputDirectory
    @InputFiles
    @PathSensitive(PathSensitivity.RELATIVE)
    final FileTree generatorSources
    @OutputDirectory
    final DirectoryProperty outputDirectory
    Data() {
        script = projectLayout.projectDirectory.file("scripts/generate-assets.mjs")
        inputDirectory = getObjectFactory().directoryProperty().convention(projectLayout.projectDirectory.dir("src/main/generation"))
        generatorSources = inputDirectory.get().asFileTree.matching {
            exclude("node_modules/**", "out/**", "build/**")
        }
        outputDirectory = getObjectFactory().directoryProperty().convention(projectLayout.projectDirectory.dir("src/generated/resources"))
        setCommandLine("node", script.getAsFile().getAbsolutePath())
        // Check the local dependency cache even when the generated assets are current.
        outputs.upToDateWhen {
            new File(inputDirectory.get().asFile, "node_modules/.projectexa-generator-deps").isFile() &&
                    new File(inputDirectory.get().asFile, "node_modules/ts-node/esm.mjs").isFile() &&
                    new File(inputDirectory.get().asFile, "node_modules/typescript/lib/typescript.js").isFile()
        }
    }

    @Inject
    protected abstract ObjectFactory getObjectFactory()

    @Inject
    protected abstract ProjectLayout getProjectLayout()
}
