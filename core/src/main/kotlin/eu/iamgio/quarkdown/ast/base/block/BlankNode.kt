package eu.iamgio.quarkdown.ast.base.block

import eu.iamgio.quarkdown.ast.Node
import eu.iamgio.quarkdown.visitor.node.NodeVisitor

/**
 * Any unknown node type (should not happen).
 */
object BlankNode : Node {
    override fun <T> accept(visitor: NodeVisitor<T>) = visitor.visit(this)
}
