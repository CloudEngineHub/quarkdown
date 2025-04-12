package eu.iamgio.quarkdown.rendering.html

import eu.iamgio.quarkdown.ast.AstRoot
import eu.iamgio.quarkdown.ast.base.block.BlankNode
import eu.iamgio.quarkdown.ast.base.block.BlockQuote
import eu.iamgio.quarkdown.ast.base.block.Code
import eu.iamgio.quarkdown.ast.base.block.Heading
import eu.iamgio.quarkdown.ast.base.block.HorizontalRule
import eu.iamgio.quarkdown.ast.base.block.Html
import eu.iamgio.quarkdown.ast.base.block.LinkDefinition
import eu.iamgio.quarkdown.ast.base.block.Newline
import eu.iamgio.quarkdown.ast.base.block.Paragraph
import eu.iamgio.quarkdown.ast.base.block.Table
import eu.iamgio.quarkdown.ast.base.block.list.ListItem
import eu.iamgio.quarkdown.ast.base.block.list.ListItemVariantVisitor
import eu.iamgio.quarkdown.ast.base.block.list.OrderedList
import eu.iamgio.quarkdown.ast.base.block.list.TaskListItemVariant
import eu.iamgio.quarkdown.ast.base.block.list.UnorderedList
import eu.iamgio.quarkdown.ast.base.inline.CheckBox
import eu.iamgio.quarkdown.ast.base.inline.CodeSpan
import eu.iamgio.quarkdown.ast.base.inline.Comment
import eu.iamgio.quarkdown.ast.base.inline.Emphasis
import eu.iamgio.quarkdown.ast.base.inline.Image
import eu.iamgio.quarkdown.ast.base.inline.LineBreak
import eu.iamgio.quarkdown.ast.base.inline.Link
import eu.iamgio.quarkdown.ast.base.inline.ReferenceImage
import eu.iamgio.quarkdown.ast.base.inline.ReferenceLink
import eu.iamgio.quarkdown.ast.base.inline.Strikethrough
import eu.iamgio.quarkdown.ast.base.inline.Strong
import eu.iamgio.quarkdown.ast.base.inline.StrongEmphasis
import eu.iamgio.quarkdown.ast.base.inline.Text
import eu.iamgio.quarkdown.ast.quarkdown.FunctionCallNode
import eu.iamgio.quarkdown.ast.quarkdown.block.Box
import eu.iamgio.quarkdown.ast.quarkdown.block.Clipped
import eu.iamgio.quarkdown.ast.quarkdown.block.Collapse
import eu.iamgio.quarkdown.ast.quarkdown.block.Container
import eu.iamgio.quarkdown.ast.quarkdown.block.Figure
import eu.iamgio.quarkdown.ast.quarkdown.block.FullColumnSpan
import eu.iamgio.quarkdown.ast.quarkdown.block.Math
import eu.iamgio.quarkdown.ast.quarkdown.block.MermaidDiagram
import eu.iamgio.quarkdown.ast.quarkdown.block.Numbered
import eu.iamgio.quarkdown.ast.quarkdown.block.PageBreak
import eu.iamgio.quarkdown.ast.quarkdown.block.SlidesFragment
import eu.iamgio.quarkdown.ast.quarkdown.block.Stacked
import eu.iamgio.quarkdown.ast.quarkdown.block.list.FocusListItemVariant
import eu.iamgio.quarkdown.ast.quarkdown.block.list.LocationTargetListItemVariant
import eu.iamgio.quarkdown.ast.quarkdown.block.toc.TableOfContentsView
import eu.iamgio.quarkdown.ast.quarkdown.inline.InlineCollapse
import eu.iamgio.quarkdown.ast.quarkdown.inline.MathSpan
import eu.iamgio.quarkdown.ast.quarkdown.inline.PageCounter
import eu.iamgio.quarkdown.ast.quarkdown.inline.TextSymbol
import eu.iamgio.quarkdown.ast.quarkdown.inline.TextTransform
import eu.iamgio.quarkdown.ast.quarkdown.inline.Whitespace
import eu.iamgio.quarkdown.ast.quarkdown.invisible.PageMarginContentInitializer
import eu.iamgio.quarkdown.ast.quarkdown.invisible.SlidesConfigurationInitializer
import eu.iamgio.quarkdown.context.Context
import eu.iamgio.quarkdown.context.resolveOrFallback
import eu.iamgio.quarkdown.rendering.UnsupportedRenderException
import eu.iamgio.quarkdown.rendering.tag.TagNodeRenderer
import eu.iamgio.quarkdown.rendering.tag.buildTag
import eu.iamgio.quarkdown.rendering.tag.tagBuilder
import eu.iamgio.quarkdown.util.toPlainText
import org.apache.commons.text.StringEscapeUtils

/**
 * A renderer for vanilla Markdown ([eu.iamgio.quarkdown.flavor.base.BaseMarkdownFlavor]) nodes that exports their content into valid HTML code.
 * @param context additional information produced by the earlier stages of the pipeline
 */
open class BaseHtmlNodeRenderer(
    context: Context,
) : TagNodeRenderer<HtmlTagBuilder>(context),
    // Along with nodes, this component is also responsible for rendering list item variants.
    // For instance, a checked/unchecked task of attached to a list item.
    // These flavors directly affect the behavior of the HTML list item builder.
    ListItemVariantVisitor<HtmlTagBuilder.() -> Unit> {
    override fun createBuilder(
        name: String,
        pretty: Boolean,
    ) = HtmlTagBuilder(name, renderer = this, pretty)

    override fun escapeCriticalContent(unescaped: String) =
        unescaped
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("\'", "&#39;")

    // Root

    override fun visit(node: AstRoot) = node.children.joinToString(separator = "") { it.accept(this) }

    // Block

    override fun visit(node: Newline) = ""

    override fun visit(node: Code) =
        buildTag("pre") {
            tag("code") {
                +escapeCriticalContent(node.content)

                classNames(
                    // Sets the code language.
                    node.language?.let { "language-$it" },
                    // Disables line numbers.
                    "nohljsln".takeUnless { node.showLineNumbers },
                    // Focuses certain lines.
                    "focus-lines".takeIf { node.focusedLines != null },
                )

                // Focus range.
                optionalAttribute("data-focus-start", node.focusedLines?.start)
                optionalAttribute("data-focus-end", node.focusedLines?.end)
            }
        }

    override fun visit(node: HorizontalRule) =
        tagBuilder("hr")
            .void(true)
            .build()

    override fun visit(node: Heading) = buildTag("h${node.depth}", node.text)

    override fun visit(node: LinkDefinition) = "" // Not rendered

    override fun visit(node: OrderedList) =
        tagBuilder("ol", node.children)
            .optionalAttribute("start", node.startIndex.takeUnless { it == 1 })
            .build()

    override fun visit(node: UnorderedList) = buildTag("ul", node.children)

    // Appends the base content of a list item, following the loose/tight rendering rules (CommonMark 5.3).
    override fun visit(node: ListItem) =
        buildTag("li") {
            // Flavors are executed on this HTML builder.
            node.variants.forEach { it.accept(this@BaseHtmlNodeRenderer).invoke(this) }

            // Loose lists (or items not linked to a list for some reason) are rendered as-is.
            if (node.owner?.isLoose != false) {
                // This base builder is empty by default.
                // If any of the variants added some content (e.g. a task checkbox),
                // the actual content is wrapped in a container for more convenient styling.
                when {
                    this.isEmpty -> +node.children
                    else -> +buildTag("div", node.children)
                }
                return@buildTag
            }
            // Tight lists don't wrap paragraphs in <p> tags (CommonMark 5.3).
            node.children.forEach {
                when (it) {
                    is Paragraph -> +it.text
                    else -> +it
                }
            }
        }

    // GFM 5.3 extension.
    override fun visit(variant: TaskListItemVariant): HtmlTagBuilder.() -> Unit =
        {
            className("task-list-item")
            +visit(CheckBox(variant.isChecked))
        }

    override fun visit(node: Html) = node.content

    /**
     * Table tag builder, enhanceable by subclasses.
     */
    protected fun tableBuilder(node: Table): HtmlTagBuilder =
        tagBuilder("table") {
            // Tables are stored by columns and here transposed to a row-based structure.
            val header = tag("thead")
            val headerRow = header.tag("tr")
            val body = tag("tbody")
            val bodyRows = mutableListOf<HtmlTagBuilder>()

            node.columns.forEach { column ->
                // Value to assign to the 'align' attribute for each cell of this column.
                val alignment = column.alignment.takeUnless { it == Table.Alignment.NONE }?.asCSS

                // Header cell.
                headerRow
                    .tag("th", column.header.text)
                    .optionalAttribute("align", alignment)

                // Body cells.
                column.cells.forEachIndexed { index, cell ->
                    // Adding a new row if needed.
                    if (index >= bodyRows.size) {
                        bodyRows += body.tag("tr")
                    }
                    // Adding a cell.
                    bodyRows[index]
                        .tag("td", cell.text)
                        .optionalAttribute("align", alignment)
                }
            }
        }

    override fun visit(node: Table) = tableBuilder(node).build()

    override fun visit(node: Paragraph) = buildTag("p", node.text)

    override fun visit(node: BlockQuote) = buildTag("blockquote", node.children)

    override fun visit(node: BlankNode) = "" // Fallback block, should not happen

    // Inline

    override fun visit(node: Comment) = "" // Ignored

    override fun visit(node: LineBreak) =
        tagBuilder("br")
            .void(true)
            .build()

    override fun visit(node: Link) =
        tagBuilder("a", node.label)
            .attribute("href", node.url)
            .optionalAttribute("title", node.title)
            .build()

    // The fallback node is rendered if a corresponding definition can't be found.
    override fun visit(node: ReferenceLink) = context.resolveOrFallback(node).accept(this)

    override fun visit(node: Image) =
        tagBuilder("img")
            .attribute("src", context.mediaStorage.resolveMediaLocationOrFallback(node.link.url))
            .attribute("alt", node.link.label.toPlainText(renderer = this)) // Emphasis is discarded (CommonMark 6.4)
            .optionalAttribute("title", node.link.title)
            .style {
                "width" value node.width
                "height" value node.height
            }.void(true)
            .build()

    override fun visit(node: ReferenceImage) = context.resolveOrFallback(node).accept(this)

    override fun visit(node: CheckBox) =
        tagBuilder("input") {}
            .attribute("disabled", "")
            .attribute("type", "checkbox")
            .optionalAttribute("checked", "".takeIf { node.isChecked })
            .void(true)
            .build()

    override fun visit(node: Text) = node.text

    override fun visit(node: TextSymbol) = StringEscapeUtils.escapeHtml4(node.text)!! // e.g. © -> &copy;

    override fun visit(node: CodeSpan) = buildTag("code", escapeCriticalContent(node.text))

    override fun visit(node: Emphasis) = buildTag("em", node.children)

    override fun visit(node: Strong) = buildTag("strong", node.children)

    override fun visit(node: StrongEmphasis) =
        buildTag("em") {
            tag("strong") {
                +node.children
            }
        }

    override fun visit(node: Strikethrough) = buildTag("del", node.children)

    // Quarkdown - implemented by QuarkdownHtmlNodeRenderer

    override fun visit(node: FunctionCallNode): CharSequence = throw UnsupportedRenderException(node)

    override fun visit(node: Figure<*>): CharSequence = throw UnsupportedRenderException(node)

    override fun visit(node: PageBreak): CharSequence = throw UnsupportedRenderException(node)

    override fun visit(node: Math): CharSequence = throw UnsupportedRenderException(node)

    override fun visit(node: Container): CharSequence = throw UnsupportedRenderException(node)

    override fun visit(node: Stacked): CharSequence = throw UnsupportedRenderException(node)

    override fun visit(node: Numbered): CharSequence = throw UnsupportedRenderException(node)

    override fun visit(node: FullColumnSpan): CharSequence = throw UnsupportedRenderException(node)

    override fun visit(node: Clipped): CharSequence = throw UnsupportedRenderException(node)

    override fun visit(node: Box): CharSequence = throw UnsupportedRenderException(node)

    override fun visit(node: Collapse): CharSequence = throw UnsupportedRenderException(node)

    override fun visit(node: Whitespace): CharSequence = throw UnsupportedRenderException(node)

    override fun visit(node: TableOfContentsView): CharSequence = throw UnsupportedRenderException(node)

    override fun visit(node: MermaidDiagram): CharSequence = throw UnsupportedRenderException(node)

    override fun visit(node: PageMarginContentInitializer): CharSequence = throw UnsupportedRenderException(node)

    override fun visit(node: PageCounter): CharSequence = throw UnsupportedRenderException(node)

    override fun visit(node: SlidesConfigurationInitializer): CharSequence = throw UnsupportedRenderException(node)

    override fun visit(node: MathSpan): CharSequence = throw UnsupportedRenderException(node)

    override fun visit(node: TextTransform): CharSequence = throw UnsupportedRenderException(node)

    override fun visit(node: InlineCollapse): CharSequence = throw UnsupportedRenderException(node)

    override fun visit(node: SlidesFragment): CharSequence = throw UnsupportedRenderException(node)

    override fun visit(variant: FocusListItemVariant): HtmlTagBuilder.() -> Unit = throw UnsupportedRenderException(variant::class)

    override fun visit(variant: LocationTargetListItemVariant): HtmlTagBuilder.() -> Unit = throw UnsupportedRenderException(variant::class)
}
