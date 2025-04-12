package eu.iamgio.quarkdown

/**
 * Exit code when a Quarkdown function was invoked by an incompatible call.
 * @see eu.iamgio.quarkdown.function.error.InvalidFunctionCallException
 */
const val BAD_FUNCTION_CALL_EXIT_CODE = 66

/**
 * Exit code when a Quarkdown function can't be resolved.
 * @see eu.iamgio.quarkdown.function.error.UnresolvedReferenceException
 */
const val UNRESOLVED_REFERENCE_EXIT_CODE = 67

/**
 * Exit code when a dynamic value cannot be converted to a static type via [eu.iamgio.quarkdown.function.value.factory.ValueFactory].
 * @see eu.iamgio.quarkdown.function.value.factory.IllegalRawValueException
 */
const val ILLEGAL_TYPE_CONVERSION_EXIT_CODE = 68

/**
 * Exit code when an element (e.g. an enum value from a Quarkdown function argument)
 * does not exist in a look-up table.
 * @see eu.iamgio.quarkdown.function.error.NoSuchElementException
 */
const val NO_SUCH_ELEMENT_EXIT_CODE = 69

/**
 * Exit code when a I/O error occurs.
 * @see eu.iamgio.quarkdown.pipeline.error.IOPipelineException
 */
const val IO_ERROR_EXIT_CODE = 70

/**
 * Exit code when a runtime error occurs.
 * @see eu.iamgio.quarkdown.function.error.FunctionRuntimeException
 */
const val RUNTIME_ERROR_EXIT_CODE = 71
