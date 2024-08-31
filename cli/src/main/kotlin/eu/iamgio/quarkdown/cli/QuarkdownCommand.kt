package eu.iamgio.quarkdown.cli

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.optional
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.file
import eu.iamgio.quarkdown.media.storage.options.ReadOnlyMediaStorageOptions
import eu.iamgio.quarkdown.pipeline.PipelineOptions
import eu.iamgio.quarkdown.pipeline.error.BasePipelineErrorHandler
import eu.iamgio.quarkdown.pipeline.error.StrictPipelineErrorHandler
import java.io.File

/**
 * Name of the default directory to save output files in.
 * It can be overridden by the user.
 */
const val DEFAULT_OUTPUT_DIRECTORY = "output"

/**
 * Main command of the Quarkdown CLI, that processes and executes a Quarkdown source file.
 */
class QuarkdownCommand : CliktCommand() {
    /**
     * Optional Quarkdown source file to process.
     * If not set, the program runs in REPL mode.
     */
    private val source: File? by argument(help = "Source file").file(
        mustExist = true,
        canBeDir = false,
        mustBeReadable = true,
    ).optional()

    /**
     * Optional output directory.
     * If not set, the output is saved in [DEFAULT_OUTPUT_DIRECTORY].
     */
    private val outputDirectory: File? by option("-o", "--out", help = "Output directory").file(
        mustExist = false,
        canBeFile = false,
        canBeDir = true,
    ).default(File(DEFAULT_OUTPUT_DIRECTORY))

    /**
     * When enabled, the rendering stage produces pretty output code.
     */
    private val prettyOutput: Boolean by option("--pretty", help = "Pretty output").flag()

    /**
     * When enabled, the rendered code isn't wrapped in a template code.
     * For example, an HTML wrapper may add `<html><head>...</head><body>...</body></html>`, with the content injected in `body`.
     * @see eu.iamgio.quarkdown.rendering.wrapper.RenderWrapper
     */
    private val noWrap: Boolean by option("--nowrap", help = "Don't wrap output").flag()

    /**
     * When enabled, the process is aborted whenever any pipeline error occurs.
     * By default, this is disabled and error messages are displayed in the final document without killing the pipeline.
     */
    private val strict: Boolean by option("--strict", help = "Exit on error").flag()

    /**
     * When enabled, the output directory is cleaned before generating new files.
     */
    private val clean: Boolean by option("--clean", help = "Clean output directory").flag()

    /**
     * When enabled, the program does not store any media (e.g. images) into the output directory `media` directory
     * and nodes that reference those media objects are not updated to reflect the new local path.
     */
    private val noMediaStorage: Boolean by option("--no-media-storage", help = "Disables media storage").flag()

    override fun run() {
        val cliOptions =
            CliOptions(
                source,
                outputDirectory,
                clean,
            )

        val pipelineOptions =
            PipelineOptions(
                prettyOutput = prettyOutput,
                wrapOutput = !noWrap,
                workingDirectory = source?.parentFile,
                enableMediaStorage = !noMediaStorage,
                mediaStorageOptionsOverrides = ReadOnlyMediaStorageOptions(),
                errorHandler =
                    when {
                        strict -> StrictPipelineErrorHandler()
                        else -> BasePipelineErrorHandler()
                    },
            )

        // Executes the Quarkdown pipeline.
        runQuarkdown(cliOptions, pipelineOptions)
    }
}
