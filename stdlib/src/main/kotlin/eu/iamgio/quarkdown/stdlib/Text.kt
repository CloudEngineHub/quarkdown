package eu.iamgio.quarkdown.stdlib

import eu.iamgio.quarkdown.ast.InlineMarkdownContent
import eu.iamgio.quarkdown.ast.base.block.Code
import eu.iamgio.quarkdown.ast.base.inline.Link
import eu.iamgio.quarkdown.ast.quarkdown.inline.TextTransform
import eu.iamgio.quarkdown.ast.quarkdown.inline.TextTransformData
import eu.iamgio.quarkdown.function.reflect.annotation.Name
import eu.iamgio.quarkdown.function.value.NodeValue
import eu.iamgio.quarkdown.function.value.data.EvaluableString
import eu.iamgio.quarkdown.function.value.data.Range
import eu.iamgio.quarkdown.function.value.wrappedAsValue
import eu.iamgio.quarkdown.misc.color.Color
import eu.iamgio.quarkdown.util.toPlainText

/**
 * `Text` stdlib module exporter.
 * This module handles text formatting.
 */
val Text: Module =
    setOf(
        ::text,
        ::code,
        ::loremIpsum,
    )

/**
 * Creates an inline text node with specified formatting and transformation.
 * @param text inline content to transform
 * @param size font size, or default if not specified
 * @param weight font weight, or default if not specified
 * @param style font style, or default if not specified
 * @param decoration text decoration, or default if not specified
 * @param case text case, or default if not specified
 * @param variant font variant, or default if not specified
 * @param color text color, or default if not specified
 * @param url optional URL to link the text to. If empty (but specified), the URL will match the text content.
 */
fun text(
    text: InlineMarkdownContent,
    size: TextTransformData.Size? = null,
    weight: TextTransformData.Weight? = null,
    style: TextTransformData.Style? = null,
    decoration: TextTransformData.Decoration? = null,
    case: TextTransformData.Case? = null,
    variant: TextTransformData.Variant? = null,
    color: Color? = null,
    url: String? = null,
): NodeValue {
    val transform =
        TextTransform(
            TextTransformData(size, weight, style, decoration, case, variant, color),
            text.children,
        )

    return when {
        // If URL is specified, wrap the text in a link
        url != null ->
            Link(
                listOf(transform),
                url = url.takeIf { it.isNotBlank() } ?: text.children.toPlainText(),
                title = null,
            )

        else -> transform
    }.wrappedAsValue()
}

/**
 * Creates a code block. Contrary to its standard Markdown implementation with backtick/tilde fences,
 * this function accepts function calls within its [code] argument,
 * hence it can be used - for example - in combination with [read] to load code from file.
 *
 * Examples:
 *
 * Load from file:
 * ```
 * .code {kotlin} focus:{2..5}
 *     .read {snippet.kt}
 * ```
 *
 * Load dynamically:
 * ```
 * .function {mycode} {mylang}
 *     source:
 *         code {mylang}
 *             .source
 * ```
 *
 * @param language optional language of the code
 * @param showLineNumbers whether to show line numbers
 * @param focusedLines range of lines to focus on. No lines are focused if unset. Supports open ranges.
 * Note: HTML rendering requires [showLineNumbers] to be enabled.
 * @param code code content
 */
fun code(
    @Name("lang") language: String? = null,
    @Name("linenumbers") showLineNumbers: Boolean = true,
    @Name("focus") focusedLines: Range? = null,
    code: EvaluableString,
): NodeValue =
    Code(
        code.content,
        language,
        showLineNumbers,
        focusedLines,
    ).wrappedAsValue()

/**
 * @return a fixed Lorem Ipsum text.
 */
@Name("loremipsum")
fun loremIpsum() =
    object {}::class.java
        .getResourceAsStream("/text/lorem-ipsum.txt")!!
        .reader()
        .readText()
        .wrappedAsValue()
