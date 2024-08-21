package eu.iamgio.quarkdown.ast.base.block

import eu.iamgio.quarkdown.ast.InlineContent
import eu.iamgio.quarkdown.ast.NestableNode
import eu.iamgio.quarkdown.ast.Node
import eu.iamgio.quarkdown.rendering.representable.RenderRepresentable
import eu.iamgio.quarkdown.rendering.representable.RenderRepresentableVisitor
import eu.iamgio.quarkdown.visitor.node.NodeVisitor

/**
 * A table.
 * @param columns columns of the table. Each column has a header and multiple cells
 */
data class Table(
    val columns: List<Column>,
) : NestableNode {
    // Exposing all the cell contents as this table's direct children
    // allows visiting them during a tree traversal.
    // If they were isolated, they would be unreachable.
    override val children: List<Node>
        get() =
            columns.asSequence()
                .flatMap { it.cells + it.header }
                .flatMap { it.text }
                .toList()

    /**
     * A column of a table.
     * @param alignment text alignment
     * @param header header cell
     * @param cells other cells
     */
    data class Column(val alignment: Alignment, val header: Cell, val cells: List<Cell>)

    /**
     * A single cell of a table.
     * @param text content
     */
    data class Cell(val text: InlineContent)

    /**
     * Text alignment of a [Column].
     */
    enum class Alignment : RenderRepresentable {
        LEFT,
        CENTER,
        RIGHT,
        NONE,
        ;

        override fun <T> accept(visitor: RenderRepresentableVisitor<T>): T = visitor.visit(this)
    }

    override fun <T> accept(visitor: NodeVisitor<T>) = visitor.visit(this)
}
