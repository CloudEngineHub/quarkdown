package eu.iamgio.quarkdown.stdlib

import eu.iamgio.quarkdown.ast.InlineContent
import eu.iamgio.quarkdown.ast.InlineMarkdownContent
import eu.iamgio.quarkdown.ast.MarkdownContent
import eu.iamgio.quarkdown.ast.base.block.Table
import eu.iamgio.quarkdown.ast.dsl.buildInline
import eu.iamgio.quarkdown.ast.quarkdown.block.Box
import eu.iamgio.quarkdown.ast.quarkdown.block.Clipped
import eu.iamgio.quarkdown.ast.quarkdown.block.Collapse
import eu.iamgio.quarkdown.ast.quarkdown.block.Container
import eu.iamgio.quarkdown.ast.quarkdown.block.FullColumnSpan
import eu.iamgio.quarkdown.ast.quarkdown.block.Numbered
import eu.iamgio.quarkdown.ast.quarkdown.block.Stacked
import eu.iamgio.quarkdown.ast.quarkdown.inline.InlineCollapse
import eu.iamgio.quarkdown.ast.quarkdown.inline.Whitespace
import eu.iamgio.quarkdown.context.Context
import eu.iamgio.quarkdown.document.size.Size
import eu.iamgio.quarkdown.document.size.Sizes
import eu.iamgio.quarkdown.function.reflect.annotation.Injected
import eu.iamgio.quarkdown.function.reflect.annotation.Name
import eu.iamgio.quarkdown.function.value.MarkdownContentValue
import eu.iamgio.quarkdown.function.value.NodeValue
import eu.iamgio.quarkdown.function.value.Value
import eu.iamgio.quarkdown.function.value.data.Lambda
import eu.iamgio.quarkdown.function.value.factory.ValueFactory
import eu.iamgio.quarkdown.function.value.wrappedAsValue
import eu.iamgio.quarkdown.misc.color.Color

/**
 * `Layout` stdlib module exporter.
 * This module handles position and shape of an element.
 */
val Layout: Module =
    setOf(
        ::container,
        ::align,
        ::center,
        ::float,
        ::row,
        ::column,
        ::grid,
        ::fullColumnSpan,
        ::whitespace,
        ::clip,
        ::box,
        ::collapse,
        ::inlineCollapse,
        ::numbered,
        ::table,
    )

/**
 * A general-purpose container that groups content.
 * Any layout rules (e.g. from [align], [row], [column], [grid]) are ignored inside this container.
 * @param width width of the container. No constraint if unset
 * @param height height of the container. No constraint if unset
 * @param fullWidth whether the container should take up the full width of the parent. Overridden by [width]. False if unset
 * @param foregroundColor text color. Default if unset
 * @param backgroundColor background color. Transparent if unset
 * @param borderColor border color. Default if unset and [borderWidth] is set
 * @param borderWidth border width. Default if unset and [borderColor] is set
 * @param borderStyle border style. Normal (solid) if unset and [borderColor] or [borderWidth] is set
 * @param margin whitespace outside the content. None if unset
 * @param padding whitespace around the content. None if unset
 * @param cornerRadius corner (and border) radius. None if unset
 * @param alignment alignment of the content. Default if unset
 * @param textAlignment alignment of the text. [alignment] if unset
 * @param float floating position of the container within the parent. Not floating if unset
 * @param body content to group
 * @return the new container node
 */
fun container(
    width: Size? = null,
    height: Size? = null,
    @Name("fullwidth") fullWidth: Boolean = false,
    @Name("foreground") foregroundColor: Color? = null,
    @Name("background") backgroundColor: Color? = null,
    @Name("border") borderColor: Color? = null,
    @Name("borderwidth") borderWidth: Sizes? = null,
    @Name("borderstyle") borderStyle: Container.BorderStyle? = null,
    @Name("margin") margin: Sizes? = null,
    @Name("padding") padding: Sizes? = null,
    @Name("radius") cornerRadius: Sizes? = null,
    alignment: Container.Alignment? = null,
    @Name("textalignment") textAlignment: Container.Alignment? = alignment,
    float: Container.FloatAlignment? = null,
    body: MarkdownContent? = null,
) = Container(
    width,
    height,
    fullWidth,
    foregroundColor,
    backgroundColor,
    borderColor,
    borderWidth,
    borderStyle,
    margin,
    padding,
    cornerRadius,
    alignment,
    textAlignment,
    float,
    body?.children ?: emptyList(),
).wrappedAsValue()

/**
 * Aligns content and text within its parent.
 * @param alignment content alignment anchor and text alignment
 * @param body content to center
 * @return the new aligned container
 * @see container
 */
fun align(
    alignment: Container.Alignment,
    body: MarkdownContent,
) = container(
    fullWidth = true,
    alignment = alignment,
    textAlignment = alignment,
    body = body,
)

/**
 * Centers content and text within its parent.
 * @param body content to center
 * @return the new aligned container
 * @see align
 */
fun center(body: MarkdownContent) = align(Container.Alignment.CENTER, body)

/**
 * Turns content into a floating element, allowing subsequent content to wrap around it.
 * @param alignment floating position
 * @param body content to float
 * @return the new floating container
 */
fun float(
    alignment: Container.FloatAlignment,
    body: MarkdownContent,
) = container(
    float = alignment,
    body = body,
)

/**
 * Stacks content together, according to the specified type.
 * @param layout stack type
 * @param mainAxisAlignment content alignment along the main axis
 * @param crossAxisAlignment content alignment along the cross axis
 * @param gap blank space between children. If omitted, the default value is used
 * @param body content to stack
 * @return the new stacked block
 * @see row
 * @see column
 */
private fun stack(
    layout: Stacked.Layout,
    mainAxisAlignment: Stacked.MainAxisAlignment = Stacked.MainAxisAlignment.START,
    crossAxisAlignment: Stacked.CrossAxisAlignment = Stacked.CrossAxisAlignment.CENTER,
    gap: Size? = null,
    body: MarkdownContent,
) = Stacked(layout, mainAxisAlignment, crossAxisAlignment, gap, body.children).wrappedAsValue()

/**
 * Stacks content horizontally.
 * @param mainAxisAlignment content alignment along the main axis
 * @param crossAxisAlignment content alignment along the cross axis
 * @param gap blank space between children. If omitted, the default value is used
 * @param body content to stack
 * @return the new stacked block
 */
fun row(
    @Name("alignment") mainAxisAlignment: Stacked.MainAxisAlignment = Stacked.MainAxisAlignment.START,
    @Name("cross") crossAxisAlignment: Stacked.CrossAxisAlignment = Stacked.CrossAxisAlignment.CENTER,
    gap: Size? = null,
    body: MarkdownContent,
) = stack(Stacked.Row, mainAxisAlignment, crossAxisAlignment, gap, body)

/**
 * Stacks content vertically.
 * @param mainAxisAlignment content alignment along the main axis
 * @param crossAxisAlignment content alignment along the cross axis
 * @param gap blank space between children. If omitted, the default value is used
 * @param body content to stack
 * @return the new stacked block
 */
fun column(
    @Name("alignment") mainAxisAlignment: Stacked.MainAxisAlignment = Stacked.MainAxisAlignment.START,
    @Name("cross") crossAxisAlignment: Stacked.CrossAxisAlignment = Stacked.CrossAxisAlignment.CENTER,
    gap: Size? = null,
    body: MarkdownContent,
) = stack(Stacked.Column, mainAxisAlignment, crossAxisAlignment, gap, body)

/**
 * Stacks content in a grid layout.
 * Each child is placed in a cell in a row, and a row ends when its cell count reaches [columnCount].
 * @param columnCount number of columns. Must be greater than 0
 * @param mainAxisAlignment content alignment along the main axis
 * @param crossAxisAlignment content alignment along the cross axis
 * @param gap blank space between rows and columns. If omitted, the default value is used
 * @param body content to stack
 * @return the new stacked block
 */
fun grid(
    @Name("columns") columnCount: Int,
    @Name("alignment") mainAxisAlignment: Stacked.MainAxisAlignment = Stacked.MainAxisAlignment.CENTER,
    @Name("cross") crossAxisAlignment: Stacked.CrossAxisAlignment = Stacked.CrossAxisAlignment.CENTER,
    gap: Size? = null,
    body: MarkdownContent,
) = when {
    columnCount <= 0 -> throw IllegalArgumentException("Column count must be at least 1")
    else -> stack(Stacked.Grid(columnCount), mainAxisAlignment, crossAxisAlignment, gap, body)
}

/**
 * If the document has a multi-column layout (set via [pageFormat]), makes content span across all columns in a multi-column layout.
 * If the document has a single-column layout, the effect is the same as [container].
 * @param body content to span across all columns
 * @return the new full column span node
 */
@Name("fullspan")
fun fullColumnSpan(body: MarkdownContent) = FullColumnSpan(body.children).wrappedAsValue()

/**
 * An empty square that adds whitespace to the layout.
 * If at least one of the dimensions is set, the square will have a fixed size.
 * If both dimensions are unset, a blank character is used, which can be useful for spacing and adding line breaks.
 * @param width width of the square. If unset, it defaults to zero
 * @param height height of the square. If unset, it defaults to zero
 * @return the new whitespace node
 */
fun whitespace(
    width: Size? = null,
    height: Size? = null,
) = Whitespace(width, height).wrappedAsValue()

/**
 * Applies a clipping path to its content.
 * @param clip clip type to apply
 * @return the new clipped block
 */
fun clip(
    clip: Clipped.Clip,
    body: MarkdownContent,
) = Clipped(clip, body.children).wrappedAsValue()

/**
 * Inserts content in a box.
 * @param title box title. If unset:
 * - If the locale ([docLanguage]) is set and supported, the title is localized according to the box [type]
 * - Otherwise, the box is untitled
 * @param type box type. If unset, it defaults to a callout box
 * @param padding padding around the box. If unset, the box uses the default padding
 * @param backgroundColor background color. If unset, the box uses the default color
 * @param foregroundColor foreground (text) color. If unset, the box uses the default color
 * @param body box content
 * @return the new box node
 */
fun box(
    @Injected context: Context,
    title: InlineMarkdownContent? = null,
    type: Box.Type = Box.Type.CALLOUT,
    padding: Size? = null,
    @Name("background") backgroundColor: Color? = null,
    @Name("foreground") foregroundColor: Color? = null,
    body: MarkdownContent,
): NodeValue {
    // Localizes the title according to the box type,
    // if the title is not manually set.
    fun localizedTitle(): InlineContent? =
        Stdlib.localizeOrNull(type.name, context)?.let {
            buildInline { text(it) }
        }

    return Box(
        title?.children ?: localizedTitle(),
        type,
        padding,
        backgroundColor,
        foregroundColor,
        body.children,
    ).wrappedAsValue()
}

/**
 * Inserts content in a collapsible block, whose content can be hidden or shown by interacting with it.
 * @param title title of the block
 * @param open whether the block is open at the beginning
 * @return the new [Collapse] node
 */
fun collapse(
    title: InlineMarkdownContent,
    open: Boolean = false,
    body: MarkdownContent,
) = Collapse(title.children, open, body.children).wrappedAsValue()

/**
 * Inserts content in a collapsible text span, whose content can be expanded or collapsed by interacting with it.
 * @param full content to show when the node is expanded
 * @param short content to show when the node is collapsed
 * @param open whether the block is open at the beginning
 * @return the new [InlineCollapse] node
 */
@Name("textcollapse")
fun inlineCollapse(
    full: InlineMarkdownContent,
    short: InlineMarkdownContent,
    open: Boolean = false,
) = InlineCollapse(full.children, short.children, open).wrappedAsValue()

/**
 * Node that can be numbered depending on its location in the document
 * and the amount of occurrences according to its [key].
 * @param key name to group (and count) numbered nodes
 * @param body content, with the formatted location of this element (as a string) as an argument
 */
fun numbered(
    key: String,
    body: Lambda,
): NodeValue {
    val node =
        Numbered(key) { number ->
            body
                .invoke<MarkdownContent, MarkdownContentValue>(number.wrappedAsValue())
                .unwrappedValue
                .children
        }
    return node.wrappedAsValue()
}

/**
 * Creates a table out of a collection of columns.
 *
 * The following example joins 5 columns:
 * ```
 * .table
 *     .foreach {1..5}
 *         | Header .1 |
 *         |-----------|
 *         |  Cell .1  |
 * ```
 *
 * @param subTables independent tables (as Markdown sources) that will be parsed and joined together into a single table
 * @return a new [Table] node
 */
fun table(
    @Injected context: Context,
    subTables: Iterable<Value<String>>,
): NodeValue {
    val columns =
        subTables
            .asSequence()
            .map { it.unwrappedValue }
            .map { ValueFactory.blockMarkdown(it, context).unwrappedValue }
            .map { it.children.first() }
            .filterIsInstance<Table>()
            .flatMap { it.columns }

    return Table(columns.toList()).wrappedAsValue()
}
