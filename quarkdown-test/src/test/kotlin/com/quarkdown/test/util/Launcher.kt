package com.quarkdown.test.util

import com.quarkdown.core.context.Context
import com.quarkdown.core.context.MutableContext
import com.quarkdown.core.context.MutableContextOptions
import com.quarkdown.core.document.sub.Subdocument
import com.quarkdown.core.flavor.quarkdown.QuarkdownFlavor
import com.quarkdown.core.graph.VisitableOnceGraph
import com.quarkdown.core.pipeline.Pipeline
import com.quarkdown.core.pipeline.PipelineHooks
import com.quarkdown.core.pipeline.PipelineOptions
import com.quarkdown.core.pipeline.error.PipelineErrorHandler
import com.quarkdown.core.pipeline.error.StrictPipelineErrorHandler
import com.quarkdown.core.pipeline.output.OutputResource
import com.quarkdown.rendering.html.extension.html
import com.quarkdown.stdlib.Stdlib
import java.io.File

// Folder to retrieve test data from.
private const val DATA_FOLDER = "src/test/resources/data"

// Folder to retrieve 'dummy' libraries from, relative to the data folder.
private const val LOCAL_LIBRARY_DIRECTORY = "libraries"

// Folder to retrieve actual libraries from.
private const val GLOBAL_LIBRARY_DIRECTORY = "../quarkdown-libs/src/main/resources"

// Default execution options.
val DEFAULT_OPTIONS =
    MutableContextOptions(
        enableAutomaticIdentifiers = false,
        enableLocationAwareness = false,
    )

/**
 * Executes a Quarkdown source.
 * @param source Quarkdown source to execute
 * @param options execution options
 * @param subdocumentGraph modifier of the subdocument graph before rendering
 * @param loadableLibraries file names to export as libraries from the `data/libraries` folder, and loadable by the user via `.include`
 * @param useDummyLibraryDirectory whether to use the dummy library directory for loading libraries instead of the one from the `libs` module
 * @param errorHandler error handler to use
 * @param enableMediaStorage whether the media storage system should be enabled.
 * If enabled, nodes that reference media (e.g. images) will instead reference the path to the media on the local storage
 * @param minimizeSubdocumentCollisions whether to minimize the risk of subdocument name collisions by using a hash-based name for subdocuments
 * @param outputResourceHook action run after the pipeline execution, with the output resource as a parameter
 * @param hook action run after rendering. Parameters are the pipeline context and the rendered source
 */
fun execute(
    source: String,
    options: MutableContextOptions = DEFAULT_OPTIONS.copy(),
    subdocumentGraph: (VisitableOnceGraph<Subdocument>) -> VisitableOnceGraph<Subdocument> = { it },
    loadableLibraries: Set<String> = emptySet(),
    useDummyLibraryDirectory: Boolean = false,
    errorHandler: PipelineErrorHandler = StrictPipelineErrorHandler(),
    enableMediaStorage: Boolean = false,
    minimizeSubdocumentCollisions: Boolean = false,
    outputResourceHook: Context.(OutputResource?) -> Unit = {},
    hook: Context.(CharSequence) -> Unit,
) {
    val context =
        MutableContext(
            QuarkdownFlavor,
            options = options,
            loadableLibraries =
                LibraryUtils.export(
                    loadableLibraries,
                    if (useDummyLibraryDirectory) {
                        File(DATA_FOLDER, LOCAL_LIBRARY_DIRECTORY)
                    } else {
                        File(GLOBAL_LIBRARY_DIRECTORY)
                    },
                ),
        )

    val hooks =
        PipelineHooks(
            afterTreeTraversal = {
                context.subdocumentGraph = subdocumentGraph(context.subdocumentGraph)
            },
            afterRendering = {
                hook(readOnlyContext, it)
            },
        )

    val pipeline =
        Pipeline(
            context,
            PipelineOptions(
                errorHandler = errorHandler,
                workingDirectory = File(DATA_FOLDER),
                enableMediaStorage = enableMediaStorage,
                minimizeSubdocumentCollisions = minimizeSubdocumentCollisions,
            ),
            libraries = setOf(Stdlib.library),
            renderer = { rendererFactory, ctx -> rendererFactory.html(ctx) },
            hooks,
        )

    val resource = pipeline.execute(source)
    outputResourceHook(context, resource)
}
