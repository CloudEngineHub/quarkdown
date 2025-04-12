package eu.iamgio.quarkdown.pipeline.error

import eu.iamgio.quarkdown.function.Function
import eu.iamgio.quarkdown.log.Log

/**
 * Simple pipeline error handler that logs the error message.
 */
class BasePipelineErrorHandler : PipelineErrorHandler {
    override fun handle(
        error: PipelineException,
        sourceFunction: Function<*>?,
        action: () -> Unit,
    ) {
        val message = error.message ?: "Unknown error"
        Log.error(message)

        action()
    }
}
