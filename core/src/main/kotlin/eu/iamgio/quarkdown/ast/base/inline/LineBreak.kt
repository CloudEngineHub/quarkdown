package eu.iamgio.quarkdown.ast.base.inline

import eu.iamgio.quarkdown.ast.Node
import eu.iamgio.quarkdown.visitor.node.NodeVisitor

/**
 * A hard line break.
 */
object LineBreak : Node {
    override fun <T> accept(visitor: NodeVisitor<T>) = visitor.visit(this)
}
