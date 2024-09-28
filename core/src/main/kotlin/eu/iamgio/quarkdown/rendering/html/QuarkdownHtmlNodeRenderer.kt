package eu.iamgio.quarkdown.rendering.html

import eu.iamgio.quarkdown.ast.AstRoot
import eu.iamgio.quarkdown.ast.Node
import eu.iamgio.quarkdown.ast.base.block.BaseListItem
import eu.iamgio.quarkdown.ast.base.block.BlockQuote
import eu.iamgio.quarkdown.ast.base.block.Heading
import eu.iamgio.quarkdown.ast.base.block.OrderedList
import eu.iamgio.quarkdown.ast.base.inline.CodeSpan
import eu.iamgio.quarkdown.ast.base.inline.Image
import eu.iamgio.quarkdown.ast.base.inline.Link
import eu.iamgio.quarkdown.ast.dsl.buildInline
import eu.iamgio.quarkdown.ast.id.getId
import eu.iamgio.quarkdown.ast.quarkdown.FunctionCallNode
import eu.iamgio.quarkdown.ast.quarkdown.block.Aligned
import eu.iamgio.quarkdown.ast.quarkdown.block.Box
import eu.iamgio.quarkdown.ast.quarkdown.block.Clipped
import eu.iamgio.quarkdown.ast.quarkdown.block.Collapse
import eu.iamgio.quarkdown.ast.quarkdown.block.Math
import eu.iamgio.quarkdown.ast.quarkdown.block.PageBreak
import eu.iamgio.quarkdown.ast.quarkdown.block.SlidesFragment
import eu.iamgio.quarkdown.ast.quarkdown.block.Stacked
import eu.iamgio.quarkdown.ast.quarkdown.block.TableOfContentsView
import eu.iamgio.quarkdown.ast.quarkdown.inline.InlineCollapse
import eu.iamgio.quarkdown.ast.quarkdown.inline.MathSpan
import eu.iamgio.quarkdown.ast.quarkdown.inline.PageCounter
import eu.iamgio.quarkdown.ast.quarkdown.inline.TextTransform
import eu.iamgio.quarkdown.ast.quarkdown.inline.Whitespace
import eu.iamgio.quarkdown.ast.quarkdown.invisible.PageMarginContentInitializer
import eu.iamgio.quarkdown.ast.quarkdown.invisible.SlidesConfigurationInitializer
import eu.iamgio.quarkdown.context.Context
import eu.iamgio.quarkdown.context.localization.localizeOrNull
import eu.iamgio.quarkdown.context.shouldAutoPageBreak
import eu.iamgio.quarkdown.context.toc.TableOfContents
import eu.iamgio.quarkdown.rendering.tag.buildMultiTag
import eu.iamgio.quarkdown.rendering.tag.buildTag
import eu.iamgio.quarkdown.rendering.tag.tagBuilder

private const val BLOCK_MATH_FENCE = "__QD_BLOCK_MATH__"
private const val INLINE_MATH_FENCE = "__QD_INLINE_MATH__"

/**
 * A renderer for Quarkdown ([eu.iamgio.quarkdown.flavor.quarkdown.QuarkdownFlavor]) nodes that exports their content into valid HTML code.
 * @param context additional information produced by the earlier stages of the pipeline
 */
class QuarkdownHtmlNodeRenderer(context: Context) : BaseHtmlNodeRenderer(context) {
    /**
     * A `<div class="styleClass">...</div>` tag.
     */
    private fun div(
        styleClass: String? = null,
        init: HtmlTagBuilder.() -> Unit,
    ) = tagBuilder("div", init = init)
        .`class`(styleClass)
        .build()

    /**
     * A `<div class="styleClass">children</div>` tag.
     */
    private fun div(
        styleClass: String,
        children: List<Node>,
    ) = div(styleClass) { +children }

    // Quarkdown node rendering

    // The function was already expanded by previous stages: its output nodes are stored in its children.
    override fun visit(node: FunctionCallNode): CharSequence = visit(AstRoot(node.children))

    // Block

    // An empty div that acts as a page break.
    override fun visit(node: PageBreak) =
        tagBuilder("div")
            .`class`("page-break")
            .hidden()
            .build()

    // Math is processed by the MathJax library which requires text delimiters instead of tags.
    override fun visit(node: Math) = BLOCK_MATH_FENCE + "$" + node.expression + "$" + BLOCK_MATH_FENCE

    override fun visit(node: Aligned) = div("align align-" + node.alignment.name.lowercase(), node.children)

    override fun visit(node: Stacked) =
        div("stack stack-${node.layout.asCSS}") {
            +node.children

            style {
                if (node.layout is Stacked.Grid) {
                    // The amount of 'auto' matches the amount of columns/rows.
                    "grid-template-columns" value "auto ".repeat(node.layout.columnCount).trimEnd()
                }

                "justify-content" value node.mainAxisAlignment
                "align-items" value node.crossAxisAlignment
                "gap" value node.gap
            }
        }

    override fun visit(node: Clipped) = div("clip clip-${node.clip.asCSS}", node.children)

    override fun visit(node: Box) =
        div {
            classes("box", node.type.asCSS)

            if (node.title != null) {
                tag("header") {
                    tag("h4", node.title)

                    style {
                        "color" value node.foregroundColor // Must be repeated to force override.
                        "padding" value node.padding
                    }
                }
            }

            // Box actual content.
            +div("box-content") {
                +node.children

                style { "padding" value node.padding }
            }

            // Box style. Padding is applied separately to the header and the content.
            style {
                "background-color" value node.backgroundColor
                "color" value node.foregroundColor
            }
        }

    override fun visit(node: Collapse) =
        buildTag("details") {
            if (node.isOpen) {
                attribute("open", "")
            }

            tag("summary") { +node.title }
            +node.children
        }

    override fun visit(node: Whitespace) =
        buildTag("span") {
            style {
                "width" value node.width
                "height" value node.height
            }

            if (node.width == null && node.height == null) {
                +"&nbsp;"
            }
        }

    // Converts TOC items to a renderable OrderedList.
    private fun tableOfContentsItemsToList(
        items: List<TableOfContents.Item>,
        view: TableOfContentsView,
    ): OrderedList {
        // Gets the content of an inner TOC item.
        fun getTableOfContentsItemContent(item: TableOfContents.Item) =
            buildList {
                // A link to the target heading.
                this +=
                    Link(
                        item.text,
                        url = "#" + HtmlIdentifierProvider.of(this@QuarkdownHtmlNodeRenderer).getId(item.target),
                        title = null,
                    )

                // Recursively include sub-items.
                item.subItems.filter { it.depth <= view.maxDepth }
                    .takeIf { it.isNotEmpty() }
                    ?.let { this += tableOfContentsItemsToList(it, view) }
            }

        return OrderedList(
            startIndex = 1,
            isLoose = true,
            children =
                items.map {
                    BaseListItem(
                        // When at least one item is focused, the other items are less visible.
                        // This effect is handled by CSS (global.css).
                        isFocused = view.hasFocus(it),
                        children = getTableOfContentsItemContent(it),
                    )
                },
        )
    }

    override fun visit(node: TableOfContentsView): CharSequence {
        val tableOfContents = context.attributes.tableOfContents ?: return ""

        return buildMultiTag {
            // Localized title.
            val titleText = context.localizeOrNull("tableofcontents")

            // Title heading. Its content is either the node's user-set title or a default localized one.
            +Heading(
                depth = 1,
                text = node.title ?: buildInline { titleText?.let { text(it) } },
                customId = "table-of-contents",
            )
            // Content
            +buildTag("nav") {
                +tableOfContentsItemsToList(tableOfContents.items, node)
            }
        }
    }

    // Inline

    // Math is processed by the MathJax library which requires text delimiters instead of tags.
    override fun visit(node: MathSpan) = INLINE_MATH_FENCE + "$" + node.expression + "$" + INLINE_MATH_FENCE

    override fun visit(node: SlidesFragment): CharSequence =
        tagBuilder("div", node.children)
            .classes("fragment", node.behavior.asCSS)
            .build()

    override fun visit(node: TextTransform) =
        buildTag("span") {
            +node.children

            `class`(node.data.size?.asCSS) // e.g. 'size-small' class

            style {
                "font-weight" value node.data.weight
                "font-style" value node.data.style
                "font-variant" value node.data.variant
                "text-decoration" value node.data.decoration
                "text-transform" value node.data.case
                "color" value node.data.color
            }
        }

    override fun visit(node: InlineCollapse) =
        buildTag("span") {
            // Dynamic behavior is handled by JS.
            `class`("inline-collapse")
            attribute("data-full-text", buildMultiTag { +node.text })
            attribute("data-collapsed-text", buildMultiTag { +node.placeholder })
            attribute("data-collapsed", !node.isOpen)
            +(if (node.isOpen) node.text else node.placeholder)
        }

    // Invisible nodes

    override fun visit(node: PageMarginContentInitializer) =
        // HTML content.
        // In slides and paged documents, these elements are copied to each page through the slides.js or paged.js script.
        div("page-margin-content page-margin-${node.position.asCSS}", node.children)

    override fun visit(node: PageCounter) =
        // The current or total page number.
        // The actual number is filled by a script at runtime
        // (either slides.js or paged.js, depending on the document type).
        buildTag("span") {
            +"-" // The default placeholder in case it is not filled by a script (e.g. plain documents).
            `class`(
                when (node.target) {
                    PageCounter.Target.CURRENT -> "current-page-number"
                    PageCounter.Target.TOTAL -> "total-page-number"
                },
            )
        }

    override fun visit(node: SlidesConfigurationInitializer): CharSequence =
        buildTag("script") {
            // Inject properties that are read by the slides.js script after the document is loaded.
            +buildString {
                node.centerVertically?.let {
                    append("const slides_center = $it;")
                }
                node.showControls?.let {
                    append("const slides_showControls = $it;")
                }
                node.transition?.let {
                    append("const slides_transitionStyle = '${it.style.asCSS}';")
                    append("const slides_transitionSpeed = '${it.speed.asCSS}';")
                }
            }
        }

    // Additional behavior of base nodes

    // On top of the default behavior, an anchor ID is set,
    // and it could force an automatic page break if suitable.
    override fun visit(node: Heading): String {
        val tagBuilder =
            when {
                // When a heading has a depth of 0 (achievable only via functions), it is an invisible marker with an ID.
                node.isMarker ->
                    tagBuilder("div") {
                        `class`("marker")
                        hidden()
                    }
                // Regular headings.
                else -> tagBuilder("h${node.depth}", node.text)
            }

        // The heading tag itself.
        val tag =
            tagBuilder.optionalAttribute(
                "id",
                // Generate an automatic identifier if allowed by settings.
                HtmlIdentifierProvider.of(renderer = this)
                    .takeIf { context.options.enableAutomaticIdentifiers || node.customId != null }
                    ?.getId(node),
            )
                .build()

        return buildMultiTag {
            if (context.shouldAutoPageBreak(node)) {
                +PageBreak()
            }
            +tag
        }
    }

    // On top of the base behavior, a blockquote can have a type and an attribution.
    override fun visit(node: BlockQuote) =
        buildTag("blockquote") {
            // If the quote has a type (e.g. TIP),
            // the whole quote is marked as a 'tip' blockquote
            // and a localized label is shown (e.g. 'Tip:' for English).
            node.type?.asCSS?.let { type ->
                `class`(type)
                // The type is associated to a localized label
                // only if the documant language is set and the set language is supported.
                context.localizeOrNull(type)?.let { localizedLabel ->
                    // The localized label is set as a CSS variable.
                    // Themes can customize label appearance and formatting.
                    style { "--quote-type-label" value "'$localizedLabel'" }
                    // The quote is marked as labeled to allow further customization.
                    attribute("data-labeled", "")
                }
            }

            +node.children
            node.attribution?.let {
                +tagBuilder("p", it)
                    .`class`("attribution")
                    .build()
            }
        }

    // The Quarkdown flavor renders an image title as a figure caption, if present.
    override fun visit(node: Image): String {
        val imgTag = super.visit(node)

        return node.link.title?.let { title ->
            buildTag("figure") {
                +imgTag
                +buildTag("figcaption", title)
            }
        } ?: imgTag
    }

    // A code span can contain additional content, such as a color preview.
    override fun visit(node: CodeSpan): String {
        val codeTag = super.visit(node)

        // The code is wrapped to allow additional content.
        return buildTag("span") {
            `class`("codespan-content")

            +codeTag

            when (node.content) {
                null -> {} // No additional content.
                is CodeSpan.ColorContent -> {
                    // If the code contains a color code, show the color preview.
                    +buildTag("span") {
                        style { "background-color" value node.content.color }
                        `class`("color-preview")
                    }
                }
            }
        }
    }

    // Quarkdown introduces focusable list items.
    override fun visit(node: BaseListItem) =
        buildTag("li") {
            appendListItemContent(node)

            if (node.isFocused) {
                `class`("focused")
            }
        }
}
