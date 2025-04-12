package eu.iamgio.quarkdown.stdlib

import eu.iamgio.quarkdown.ast.base.block.Html
import eu.iamgio.quarkdown.function.value.wrappedAsValue

/**
 * `Injection` stdlib module exporter.
 * This module handles code injection of different languages.
 */
val Injection: Module =
    setOf(
        ::html,
    )

/**
 * Creates an HTML element, which is rendered as-is without any additional processing or escaping.
 *
 * @param content raw HTML content to inject
 * @return the HTML node
 */
fun html(content: String) = Html(content).wrappedAsValue()
