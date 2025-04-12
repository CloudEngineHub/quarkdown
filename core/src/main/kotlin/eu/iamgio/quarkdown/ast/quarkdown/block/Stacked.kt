package eu.iamgio.quarkdown.ast.quarkdown.block

import eu.iamgio.quarkdown.ast.NestableNode
import eu.iamgio.quarkdown.ast.Node
import eu.iamgio.quarkdown.document.size.Size
import eu.iamgio.quarkdown.rendering.representable.RenderRepresentable
import eu.iamgio.quarkdown.rendering.representable.RenderRepresentableVisitor
import eu.iamgio.quarkdown.visitor.node.NodeVisitor

/**
 * A block that contains nodes grouped together according to the given [layout].
 * @param layout the way nodes are placed together
 * @param mainAxisAlignment content alignment along the main axis
 * @param crossAxisAlignment content alignment along the cross axis
 * @param gap space between nodes
 */
class Stacked(
    val layout: Layout,
    val mainAxisAlignment: MainAxisAlignment,
    val crossAxisAlignment: CrossAxisAlignment,
    val gap: Size?,
    override val children: List<Node>,
) : NestableNode {
    override fun <T> accept(visitor: NodeVisitor<T>) = visitor.visit(this)

    /**
     * Defines a way nodes of a [Stacked] block are placed together.
     * @see Column
     * @see Row
     * @see Grid
     */
    sealed interface Layout : RenderRepresentable

    /**
     * A layout that stacks nodes vertically.
     */
    data object Column : Layout {
        override fun <T> accept(visitor: RenderRepresentableVisitor<T>): T = visitor.visit(this)
    }

    /**
     * A layout that stacks nodes horizontally.
     */
    data object Row : Layout {
        override fun <T> accept(visitor: RenderRepresentableVisitor<T>): T = visitor.visit(this)
    }

    /**
     * A layout that stacks nodes in a grid.
     * @param columnCount number of columns
     */
    data class Grid(val columnCount: Int) : Layout {
        override fun <T> accept(visitor: RenderRepresentableVisitor<T>): T = visitor.visit(this)
    }

    /**
     * Possible alignment types along the main axis of a [Stacked] block.
     */
    enum class MainAxisAlignment : RenderRepresentable {
        START,
        CENTER,
        END,
        SPACE_BETWEEN,
        SPACE_AROUND,
        SPACE_EVENLY,
        ;

        override fun <T> accept(visitor: RenderRepresentableVisitor<T>) = visitor.visit(this)
    }

    /**
     * Possible alignment types along the cross axis of a [Stacked] block.
     */
    enum class CrossAxisAlignment : RenderRepresentable {
        START,
        CENTER,
        END,
        STRETCH,
        ;

        override fun <T> accept(visitor: RenderRepresentableVisitor<T>) = visitor.visit(this)
    }
}
