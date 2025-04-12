package eu.iamgio.quarkdown.function.value.data

import eu.iamgio.quarkdown.context.Context
import eu.iamgio.quarkdown.function.SimpleFunction
import eu.iamgio.quarkdown.function.error.InvalidLambdaArgumentCountException
import eu.iamgio.quarkdown.function.library.Library
import eu.iamgio.quarkdown.function.reflect.DynamicValueConverter
import eu.iamgio.quarkdown.function.reflect.FromDynamicType
import eu.iamgio.quarkdown.function.value.DynamicValue
import eu.iamgio.quarkdown.function.value.NoneValue
import eu.iamgio.quarkdown.function.value.OutputValue
import eu.iamgio.quarkdown.function.value.Value
import eu.iamgio.quarkdown.function.value.factory.ValueFactory

private const val LAMBDA_LIBRARY_NAME = "__lambda-parameters__"

data class LambdaParameter(
    val name: String,
    val isOptional: Boolean = false,
)

/**
 * An action block with a variable parameter count.
 * The return type is dynamic (a snippet of raw Quarkdown code is returned), hence it is evaluated and converted to a static type.
 * @param parentContext context this lambda lies in
 * @param explicitParameters named parameters of the lambda. If not present, parameter names are automatically set to .1, .2, etc.
 * @param action action to perform, which takes a variable sequence of [Value]s and this lambda's own forked context as arguments
 *        and returns the output of the lambda.
 */
open class Lambda(
    val parentContext: Context,
    val explicitParameters: List<LambdaParameter> = emptyList(),
    val action: (List<Value<*>>, Context) -> OutputValue<*>,
) {
    /**
     * Registers the arguments in the context, which can be accessed as function calls.
     * @param arguments arguments of the lambda action
     */
    private fun createLambdaParametersLibrary(arguments: List<Value<*>>) =
        Library(
            LAMBDA_LIBRARY_NAME,
            functions =
                arguments
                    .mapIndexed { index, argument ->
                        val parameterName = explicitParameters.getOrNull(index)?.name ?: (index + 1).toString()
                        SimpleFunction(
                            parameterName,
                            parameters = emptyList(),
                        ) {
                            // Value associated to the lambda argument.
                            DynamicValue(argument.unwrappedValue)
                        }
                    }.toSet(),
        )

    /**
     * Checks if the amount of arguments matches the amount of expected parameters.
     * @param arguments arguments of the lambda action
     */
    private fun isArgumentCountValid(arguments: List<Value<*>>): Boolean {
        // If no explicit parameters are present, implicit parameters are automatically set to .1, .2, etc.,
        // hence the argument count is always valid.
        if (explicitParameters.isEmpty()) return true
        // If the amount of arguments matches the amount of mandatory parameters, the argument count is valid.
        val mandatoryParameterCount = explicitParameters.count { !it.isOptional }
        return arguments.size in mandatoryParameterCount..explicitParameters.size
    }

    /**
     * Invokes the lambda action with given arguments.
     * @param arguments arguments of the lambda action
     * @return the result of the lambda action, as an undetermined, thus dynamically-typed, value
     */
    fun invokeDynamic(arguments: List<Value<*>>): OutputValue<*> {
        // Check if the amount of arguments matches the amount of expected parameters.
        // In case parameters are not present, placeholders are automatically set to
        // .1, .2, etc., similarly to Kotlin's 'it' argument.
        // This replacement is handled by ValueFactory.lambda
        if (!isArgumentCountValid(arguments)) {
            throw InvalidLambdaArgumentCountException(explicitParameters.size, arguments.size)
        }

        // The actual arguments to pass to the lambda action, based on the given `arguments`.
        val actualArguments =
            when {
                arguments.size < explicitParameters.size -> {
                    // If the remaining parameters are optional, fill the remaining parameters with 'none' placeholder values.
                    arguments + List(explicitParameters.size - arguments.size) { NoneValue }
                }

                else -> arguments
            }

        // Create a new independent context, copy of the parent one, to execute the lambda block in.
        // Upon invocation, the context is filled with the arguments information,
        // whose values can be retrieved as function calls.
        val context = parentContext.fork()

        // Register the arguments in the context, which can be accessed as function calls.
        context.libraries += createLambdaParametersLibrary(actualArguments)

        // The result of the lambda action is processed.
        return action(actualArguments, context)
    }

    /**
     * @see invokeDynamic
     */
    fun invokeDynamic(vararg arguments: Value<*>): OutputValue<*> = invokeDynamic(arguments.toList())

    /**
     * Invokes the lambda action with given arguments and converts it to a static type.
     * @param values arguments of the lambda action
     * @param T **unwrapped** type to convert the resulting dynamic value to.
     * This type must appear in a [FromDynamicType] annotation on a [ValueFactory] method
     * @param V **wrapped** value type (which wraps [T]) to convert the resulting dynamic value to
     * @return the result of the lambda action, as a statically typed value
     */
    inline fun <reified T, reified V : Value<T>> invoke(vararg values: Value<*>): V {
        // Invoke the lambda action and convert the result to a static type.
        val result = invokeDynamic(*values)

        return if (result is DynamicValue) {
            DynamicValueConverter(result).convertTo(T::class, parentContext)
        } else {
            result
        } as? V
            ?: throw IllegalArgumentException("Unexpected lambda result: expected ${V::class}, found ${result::class}")
    }
}
