package eu.iamgio.quarkdown.ast.base.inline

import eu.iamgio.quarkdown.ast.InlineContent
import eu.iamgio.quarkdown.ast.base.TextNode
import eu.iamgio.quarkdown.visitor.node.NodeVisitor

/**
 * Weakly emphasized content.
 * @param text content
 */
class Emphasis(override val text: InlineContent) : TextNode {
    override fun <T> accept(visitor: NodeVisitor<T>) = visitor.visit(this)
}

/**
 * Strongly emphasized content.
 * @param text content
 */
class Strong(override val text: InlineContent) : TextNode {
    override fun <T> accept(visitor: NodeVisitor<T>) = visitor.visit(this)
}

/**
 * Heavily emphasized content.
 * @param text content
 */
class StrongEmphasis(override val text: InlineContent) : TextNode {
    override fun <T> accept(visitor: NodeVisitor<T>) = visitor.visit(this)
}

/**
 * Strikethrough content.
 * @param text content
 */
class Strikethrough(override val text: InlineContent) : TextNode {
    override fun <T> accept(visitor: NodeVisitor<T>) = visitor.visit(this)
}
