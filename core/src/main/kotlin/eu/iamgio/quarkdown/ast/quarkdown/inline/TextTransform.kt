package eu.iamgio.quarkdown.ast.quarkdown.inline

import eu.iamgio.quarkdown.ast.NestableNode
import eu.iamgio.quarkdown.ast.Node
import eu.iamgio.quarkdown.misc.Color
import eu.iamgio.quarkdown.rendering.representable.RenderRepresentable
import eu.iamgio.quarkdown.rendering.representable.RenderRepresentableVisitor
import eu.iamgio.quarkdown.visitor.node.NodeVisitor

/**
 * Text transformation a portion of text can undergo.
 * If a property is set to `null` it is not specified, hence ignored
 * and the default value is used.
 * @param size font size
 * @param weight font weight
 * @param style font style
 * @param decoration text decoration
 * @param case text case
 * @param variant font variant
 * @param color text (foreground) color
 */
class TextTransformData(
    val size: Size? = null,
    val weight: Weight? = null,
    val style: Style? = null,
    val decoration: Decoration? = null,
    val case: Case? = null,
    val variant: Variant? = null,
    val color: Color? = null,
) {
    /**
     * Font size, relative to the default font size.
     */
    enum class Size : RenderRepresentable {
        /**
         * Tiny font size (50%).
         */
        TINY,

        /**
         * Small font size (75%).
         */
        SMALL,

        /**
         * Normal font size (100%).
         */
        NORMAL,

        /**
         * Medium font size (125%).
         */
        MEDIUM,

        /**
         * Larger font size (150%).
         */
        LARGER,

        /**
         * Large font size (200%).
         */
        LARGE,

        /**
         * Huge font size (300%).
         */
        HUGE,
        ;

        override fun <T> accept(visitor: RenderRepresentableVisitor<T>): T = visitor.visit(this)
    }

    /**
     * Font weight.
     */
    enum class Weight : RenderRepresentable {
        /**
         * Normal font weight.
         */
        NORMAL,

        /**
         * Bold font weight.
         */
        BOLD,
        ;

        override fun <T> accept(visitor: RenderRepresentableVisitor<T>): T = visitor.visit(this)
    }

    /**
     * Font style.
     */
    enum class Style : RenderRepresentable {
        /**
         * Normal font style.
         */
        NORMAL,

        /**
         * Italic font style.
         */
        ITALIC,
        ;

        override fun <T> accept(visitor: RenderRepresentableVisitor<T>): T = visitor.visit(this)
    }

    /**
     * Text decoration.
     */
    enum class Decoration : RenderRepresentable {
        /**
         * No text decoration.
         */
        NONE,

        /**
         * Line under the text.
         */
        UNDERLINE,

        /**
         * Line over the text.
         */
        OVERLINE,

        /**
         * Lines under and over the text.
         */
        UNDEROVERLINE,

        /**
         * Line through the text.
         */
        STRIKETHROUGH,

        /**
         * Lines under, over and through the text.
         */
        ALL,
        ;

        override fun <T> accept(visitor: RenderRepresentableVisitor<T>): T = visitor.visit(this)
    }

    /**
     * Text case transformation.
     */
    enum class Case : RenderRepresentable {
        /**
         * No text case transformation.
         */
        NONE,

        /**
         * Uppercase text.
         */
        UPPERCASE,

        /**
         * Lowercase text.
         */
        LOWERCASE,

        /**
         * Capitalize text (first letter of each word is uppercase).
         */
        CAPITALIZE,
        ;

        override fun <T> accept(visitor: RenderRepresentableVisitor<T>): T = visitor.visit(this)
    }

    /**
     * Font variant.
     */
    enum class Variant : RenderRepresentable {
        /**
         * No font variant.
         */
        NORMAL,

        /**
         * Small-caps font variant.
         */
        SMALL_CAPS,
        ;

        override fun <T> accept(visitor: RenderRepresentableVisitor<T>): T = visitor.visit(this)
    }
}

/**
 * A portion of text with a specific visual transformation.
 * @param data transformation the text undergoes
 */
data class TextTransform(
    val data: TextTransformData,
    override val children: List<Node>,
) : NestableNode {
    override fun <T> accept(visitor: NodeVisitor<T>): T = visitor.visit(this)
}
