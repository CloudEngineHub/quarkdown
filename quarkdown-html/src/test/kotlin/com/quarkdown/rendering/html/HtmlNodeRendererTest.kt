@file:Suppress("ktlint:standard:no-wildcard-imports")

package com.quarkdown.rendering.html

import com.quarkdown.core.BibliographySamples.article
import com.quarkdown.core.BibliographySamples.book
import com.quarkdown.core.BibliographySamples.misc
import com.quarkdown.core.ast.InlineContent
import com.quarkdown.core.ast.Node
import com.quarkdown.core.ast.attributes.MutableAstAttributes
import com.quarkdown.core.ast.attributes.reference.setDefinition
import com.quarkdown.core.ast.base.block.BlockQuote
import com.quarkdown.core.ast.base.block.Code
import com.quarkdown.core.ast.base.block.FootnoteDefinition
import com.quarkdown.core.ast.base.block.Heading
import com.quarkdown.core.ast.base.block.HorizontalRule
import com.quarkdown.core.ast.base.block.Html
import com.quarkdown.core.ast.base.block.LinkDefinition
import com.quarkdown.core.ast.base.block.Paragraph
import com.quarkdown.core.ast.base.block.Table
import com.quarkdown.core.ast.base.block.list.ListItem
import com.quarkdown.core.ast.base.block.list.OrderedList
import com.quarkdown.core.ast.base.block.list.TaskListItemVariant
import com.quarkdown.core.ast.base.block.list.UnorderedList
import com.quarkdown.core.ast.base.block.setIndex
import com.quarkdown.core.ast.base.inline.CodeSpan
import com.quarkdown.core.ast.base.inline.Comment
import com.quarkdown.core.ast.base.inline.CriticalContent
import com.quarkdown.core.ast.base.inline.Emphasis
import com.quarkdown.core.ast.base.inline.Image
import com.quarkdown.core.ast.base.inline.LineBreak
import com.quarkdown.core.ast.base.inline.Link
import com.quarkdown.core.ast.base.inline.ReferenceFootnote
import com.quarkdown.core.ast.base.inline.ReferenceImage
import com.quarkdown.core.ast.base.inline.ReferenceLink
import com.quarkdown.core.ast.base.inline.Strikethrough
import com.quarkdown.core.ast.base.inline.Strong
import com.quarkdown.core.ast.base.inline.StrongEmphasis
import com.quarkdown.core.ast.base.inline.Text
import com.quarkdown.core.ast.dsl.buildBlock
import com.quarkdown.core.ast.dsl.buildBlocks
import com.quarkdown.core.ast.dsl.buildInline
import com.quarkdown.core.ast.quarkdown.bibliography.BibliographyView
import com.quarkdown.core.ast.quarkdown.block.Box
import com.quarkdown.core.ast.quarkdown.block.Clipped
import com.quarkdown.core.ast.quarkdown.block.Collapse
import com.quarkdown.core.ast.quarkdown.block.Container
import com.quarkdown.core.ast.quarkdown.block.FullColumnSpan
import com.quarkdown.core.ast.quarkdown.block.ImageFigure
import com.quarkdown.core.ast.quarkdown.block.Math
import com.quarkdown.core.ast.quarkdown.block.PageBreak
import com.quarkdown.core.ast.quarkdown.block.list.FocusListItemVariant
import com.quarkdown.core.ast.quarkdown.inline.InlineCollapse
import com.quarkdown.core.ast.quarkdown.inline.LastHeading
import com.quarkdown.core.ast.quarkdown.inline.MathSpan
import com.quarkdown.core.ast.quarkdown.inline.TextSymbol
import com.quarkdown.core.ast.quarkdown.inline.TextTransform
import com.quarkdown.core.ast.quarkdown.inline.TextTransformData
import com.quarkdown.core.attachMockPipeline
import com.quarkdown.core.bibliography.Bibliography
import com.quarkdown.core.bibliography.style.BibliographyStyle
import com.quarkdown.core.context.BaseContext
import com.quarkdown.core.context.Context
import com.quarkdown.core.context.MutableContext
import com.quarkdown.core.context.MutableContextOptions
import com.quarkdown.core.document.size.Sizes
import com.quarkdown.core.document.size.cm
import com.quarkdown.core.document.size.inch
import com.quarkdown.core.document.size.percent
import com.quarkdown.core.document.size.px
import com.quarkdown.core.flavor.base.BaseMarkdownFlavor
import com.quarkdown.core.flavor.quarkdown.QuarkdownFlavor
import com.quarkdown.core.function.value.data.Range
import com.quarkdown.core.misc.color.Color
import com.quarkdown.core.misc.color.decoder.HexColorDecoder
import com.quarkdown.core.pipeline.PipelineOptions
import com.quarkdown.core.pipeline.Pipelines
import com.quarkdown.core.readSource
import com.quarkdown.core.rendering.NodeRenderer
import com.quarkdown.core.util.normalizeLineSeparators
import com.quarkdown.core.util.toPlainText
import com.quarkdown.rendering.html.extension.html
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * HTML node rendering tests.
 */
class HtmlNodeRendererTest {
    private fun readParts(path: String) =
        readSource("/rendering/$path")
            .normalizeLineSeparators()
            .split("\n---\n")
            .map { it.trim() }
            .iterator()

    private fun renderer(context: Context = MutableContext(QuarkdownFlavor)): NodeRenderer {
        if (context.attachedPipeline == null) {
            // Attach a mock pipeline to the context, allowing to render pretty output
            // (since its value is retrieved from the attached pipeline)
            Pipelines.attach(
                context,
                MutableContext(context.flavor).attachMockPipeline(PipelineOptions(prettyOutput = true)),
            )
        }

        return context.flavor.rendererFactory
            .html(context)
            .nodeRenderer
    }

    private fun Node.render(context: Context = MutableContext(QuarkdownFlavor)) = this.accept(renderer(context))

    // Inline
    @Test
    fun comment() {
        assertEquals("", Comment.render())
    }

    @Test
    fun lineBreak() {
        assertEquals("<br />", LineBreak.render())
    }

    @Test
    fun criticalContent() {
        assertEquals("&amp;", CriticalContent("&").render())
        assertEquals("&gt;", CriticalContent(">").render())
        assertEquals("~", CriticalContent("~").render())
    }

    @Test
    fun link() {
        val out = readParts("inline/link.html")

        assertEquals(
            out.next(),
            Link(label = listOf(Text("Foo bar")), url = "https://google.com", title = null).render(),
        )
        assertEquals(
            out.next(),
            Link(label = listOf(Strong(listOf(Text("Foo bar")))), url = "/url", title = null).render(),
        )
        assertEquals(
            out.next(),
            Link(label = listOf(Text("Foo bar baz")), url = "url", title = "Title").render(),
        )
    }

    @Test
    fun referenceLink() {
        val out = readParts("inline/reflink.html")

        val label = listOf(Strong(listOf(Text("Foo"))))

        val attributes =
            MutableAstAttributes(
                linkDefinitions =
                    mutableListOf(
                        LinkDefinition(
                            label,
                            url = "/url",
                            title = "Title",
                        ),
                    ),
            )

        val context = BaseContext(attributes, QuarkdownFlavor)

        val fallback = { Emphasis(listOf(Text("fallback"))) }

        assertEquals(
            out.next(),
            ReferenceLink(label, label, fallback).render(context),
        )
        assertEquals(
            out.next(),
            ReferenceLink(listOf(Text("label")), label, fallback).render(context),
        )
        assertEquals(
            out.next(),
            ReferenceLink(listOf(Text("label")), label, fallback).render(),
        )
    }

    @Test
    fun image() {
        val out = readParts("inline/image.html")

        assertEquals(
            out.next(),
            Image(
                Link(label = listOf(), url = "/url", title = null),
                width = null,
                height = null,
            ).render(),
        )
        assertEquals(
            out.next(),
            Image(
                Link(label = listOf(), url = "/url", title = "Title"),
                width = null,
                height = null,
            ).render(),
        )
        assertEquals(
            out.next(),
            Image(
                Link(label = buildInline { text("Foo bar") }, url = "/url", title = null),
                width = 150.px,
                height = 100.px,
            ).render(),
        )
        assertEquals(
            out.next(),
            Image(
                Link(label = buildInline { text("Foo bar") }, url = "/url", title = "Title"),
                width = 3.2.cm,
                height = null,
            ).render(),
        )
    }

    @Test
    fun referenceImage() {
        val out = readParts("inline/refimage.html")

        val label = listOf(Text("Foo"))

        val attributes =
            MutableAstAttributes(
                linkDefinitions =
                    mutableListOf(
                        LinkDefinition(
                            label,
                            url = "/url",
                            title = "Title",
                        ),
                    ),
            )

        val context = BaseContext(attributes, QuarkdownFlavor)

        val fallback = { Emphasis(listOf(Text("fallback"))) }

        assertEquals(
            out.next(),
            ReferenceImage(
                ReferenceLink(label, label, fallback),
                width = null,
                height = null,
            ).render(context),
        )
        assertEquals(
            out.next(),
            ReferenceImage(
                ReferenceLink(
                    listOf(Text("label")),
                    label,
                    fallback,
                ),
                width = null,
                height = null,
            ).render(context),
        )
        assertEquals(
            out.next(),
            ReferenceImage(
                ReferenceLink(
                    listOf(Text("label")),
                    label,
                    fallback,
                ),
                width = 150.px,
                height = 100.px,
            ).render(context),
        )
        assertEquals(
            out.next(),
            ReferenceImage(
                ReferenceLink(
                    listOf(Text("label")),
                    label,
                    fallback,
                ),
                width = null,
                height = null,
            ).render(),
        )
    }

    @Test
    fun figure() {
        val out = readParts("quarkdown/figure.html")

        assertEquals(
            out.next(),
            ImageFigure(
                Image(
                    Link(label = listOf(), url = "/url", title = ""),
                    width = null,
                    height = null,
                ),
            ).render(),
        )
        assertEquals(
            out.next(),
            ImageFigure(
                Image(
                    Link(label = listOf(), url = "/url", title = "Title"),
                    width = null,
                    height = null,
                ),
            ).render(),
        )
        assertEquals(
            out.next(),
            ImageFigure(
                Image(
                    Link(label = listOf(), url = "/url", title = "Title"),
                    width = 150.px,
                    height = 100.px,
                ),
            ).render(),
        )
    }

    @Test
    fun footnoteDefinition() {
        val out = readParts("block/footnote.html")
        val context = MutableContext(QuarkdownFlavor)

        val definition =
            FootnoteDefinition(
                label = "label",
                text = buildInline { text("Foo bar") },
            )
        definition.setIndex(context, 0)

        assertEquals(
            out.next(),
            definition.render(context),
        )
    }

    @Test
    fun footnoteReference() {
        val out = readParts("inline/reffootnote.html")
        val context = MutableContext(QuarkdownFlavor)

        val definition =
            FootnoteDefinition(
                label = "label",
                text = buildInline { text("Foo bar") },
            )
        definition.setIndex(context, 0)

        val reference =
            ReferenceFootnote(
                label = "label",
                fallback = { Text("fallback") },
            )

        reference.setDefinition(context, definition)

        assertEquals(
            out.next(),
            reference.render(context),
        )
        assertEquals(
            out.next(),
            reference.render(),
        )
    }

    @Test
    fun text() {
        assertEquals("Foo bar", Text("Foo bar").render())
        assertEquals("&copy;", TextSymbol('©').render())
    }

    @Test
    fun codeSpan() {
        val out = readParts("inline/codespan.html")

        // The Quarkdown rendering wraps the content in a span which allows additional content, such as color.
        val base = MutableContext(BaseMarkdownFlavor)
        val quarkdown = MutableContext(QuarkdownFlavor)

        val spanWithColor =
            CodeSpan(
                "#FFFF00",
                CodeSpan.ColorContent(HexColorDecoder.decode("#FFFF00")!!),
            )

        assertEquals(out.next(), CodeSpan("Foo bar").render(base))
        assertEquals(out.next(), CodeSpan("<a href=\"#\">").render(base))
        assertEquals(out.next(), spanWithColor.render(quarkdown))
        assertEquals(out.next(), spanWithColor.render(base))
        assertEquals(out.next(), CodeSpan("Foo bar").render(quarkdown))
    }

    @Test
    fun emphasis() {
        val out = readParts("inline/emphasis.html")

        assertEquals(out.next(), Emphasis(listOf(Text("Foo bar"))).render())
        assertEquals(out.next(), Emphasis(listOf(Emphasis(listOf(Text("Foo bar"))))).render())
    }

    @Test
    fun strong() {
        val out = readParts("inline/strong.html")

        assertEquals(out.next(), Strong(listOf(Text("Foo bar"))).render())
        assertEquals(out.next(), Strong(listOf(Strong(listOf(Text("Foo bar"))))).render())
    }

    @Test
    fun strongEmphasis() {
        val out = readParts("inline/strongemphasis.html")

        assertEquals(out.next(), StrongEmphasis(listOf(Text("Foo bar"))).render())
        assertEquals(out.next(), StrongEmphasis(listOf(StrongEmphasis(listOf(Text("Foo bar"))))).render())
    }

    @Test
    fun strikethrough() {
        val out = readParts("inline/strikethrough.html")

        assertEquals(out.next(), Strikethrough(listOf(Text("Foo bar"))).render())
        assertEquals(out.next(), Strikethrough(listOf(Strong(listOf(Text("Foo bar"))))).render())
    }

    @Test
    fun plainTextConversion() {
        val inline: InlineContent =
            listOf(
                Text("abc"),
                Strong(
                    listOf(
                        Emphasis(
                            listOf(
                                Text("def"),
                                CodeSpan("ghi"),
                            ),
                        ),
                        CodeSpan("jkl"),
                    ),
                ),
                Text("mno"),
                CriticalContent("&"),
            )

        assertEquals("abcdefghijklmno&", inline.toPlainText())
        // Critical content is rendered differently
        assertEquals("abcdefghijklmno&amp;", inline.toPlainText(renderer()))
    }

    // Block

    @Test
    fun code() {
        val out = readParts("block/code.html")

        assertEquals(out.next(), Code("Code", language = null, showLineNumbers = true).render())
        assertEquals(out.next(), Code("Code", language = null, showLineNumbers = false).render())
        assertEquals(out.next(), Code("class Point {\n    ...\n}", language = null, showLineNumbers = true).render())
        assertEquals(out.next(), Code("class Point {\n    ...\n}", language = "java", showLineNumbers = false).render())
        assertEquals(out.next(), Code("<a href=\"#\">", language = "html", showLineNumbers = true).render())
        assertEquals(
            out.next(),
            Code("class Point {\n    ...\n}", language = "java", focusedLines = Range(1, 2)).render(),
        )
        assertEquals(
            out.next(),
            Code("class Point {\n    ...\n}", language = "java", focusedLines = Range(2, null)).render(),
        )
        assertEquals(
            out.next(),
            Code("class Point {\n    ...\n}", language = "java", focusedLines = Range(null, 1)).render(),
        )
        assertEquals(
            out.next(),
            Code("class Point {\n    ...\n}", language = "java", caption = "A Java code example.").render(),
        )
    }

    @Test
    fun horizontalRule() {
        assertEquals("<hr />", HorizontalRule.render())
    }

    @Test
    fun pageBreak() {
        assertEquals("<div class=\"page-break\" data-hidden=\"\">\n</div>", PageBreak().render())
    }

    @Test
    fun heading() {
        val out = readParts("block/heading.html")

        // No automatic ID, no automatic page break.
        val noIdNoPageBreak =
            MutableContext(
                QuarkdownFlavor,
                options = MutableContextOptions(autoPageBreakHeadingMaxDepth = 0, enableAutomaticIdentifiers = false),
            )

        assertEquals(out.next(), Heading(1, listOf(Text("Foo bar"))).render(noIdNoPageBreak))
        assertEquals(out.next(), Heading(2, listOf(Text("Foo bar"))).render(noIdNoPageBreak))
        assertEquals(out.next(), Heading(2, listOf(Text("Foo bar")), isDecorative = true).render(noIdNoPageBreak))
        assertEquals(out.next(), Heading(3, listOf(Text("Foo bar")), customId = "my-id").render(noIdNoPageBreak))
        assertEquals(out.next(), Heading(3, listOf(Strong(listOf(Text("Foo bar"))))).render(noIdNoPageBreak))
        assertEquals(out.next(), Heading(4, listOf(Text("Foo"), Emphasis(listOf(Text("bar"))))).render(noIdNoPageBreak))

        // Automatic ID, no automatic page break.
        val idNoPageBreak =
            MutableContext(
                QuarkdownFlavor,
                options = MutableContextOptions(autoPageBreakHeadingMaxDepth = 0),
            )

        assertEquals(out.next(), Heading(1, listOf(Text("Foo bar"))).render(idNoPageBreak))
        assertEquals(out.next(), Heading(1, listOf(Text("Foo bar")), customId = "custom-id").render(idNoPageBreak))
        assertEquals(out.next(), Heading(4, listOf(Text("Foo"), Emphasis(listOf(Text("bar"))))).render(idNoPageBreak))

        // Automatic ID, force page break on depth <= 2
        val autoPageBreak =
            MutableContext(QuarkdownFlavor, options = MutableContextOptions(autoPageBreakHeadingMaxDepth = 2))

        assertEquals(out.next(), Heading(1, listOf(Text("Foo bar"))).render(autoPageBreak))
        assertEquals(out.next(), Heading(2, listOf(Text("Foo bar"))).render(autoPageBreak))
        assertEquals(out.next(), Heading(3, listOf(Text("Foo bar"))).render(autoPageBreak))
        assertEquals(out.next(), Heading(4, listOf(Text("Foo bar"))).render(autoPageBreak))
    }

    private fun listItems() =
        listOf(
            ListItem(
                children =
                    listOf(
                        Paragraph(listOf(Text("A1"))),
                        HorizontalRule,
                        Paragraph(listOf(Text("A2"))),
                    ),
            ),
            ListItem(
                children =
                    listOf(
                        Paragraph(listOf(Text("B1"))),
                        HorizontalRule,
                        Paragraph(listOf(Text("B2"))),
                    ),
            ),
            ListItem(
                children =
                    listOf(
                        Paragraph(listOf(Text("C1"))),
                        HorizontalRule,
                        Paragraph(listOf(Text("C2"))),
                    ),
            ),
            ListItem(
                variants = listOf(FocusListItemVariant(isFocused = true)),
                children =
                    listOf(
                        Paragraph(listOf(Text("D1"))),
                        HorizontalRule,
                        Paragraph(listOf(Text("D2"))),
                    ),
            ),
            ListItem(
                variants = listOf(TaskListItemVariant(isChecked = true)),
                listOf(
                    Paragraph(listOf(Text("E1"))),
                    HorizontalRule,
                    Paragraph(listOf(Text("E2"))),
                ),
            ),
        )

    @Test
    fun orderedList() {
        val out = readParts("block/orderedlist.html")

        assertEquals(out.next(), OrderedList(startIndex = 1, isLoose = false, emptyList()).render())

        assertEquals(
            out.next(),
            OrderedList(
                startIndex = 1,
                isLoose = true,
                listItems(),
            ).render(),
        )

        assertEquals(
            out.next(),
            OrderedList(
                startIndex = 12,
                isLoose = true,
                listItems(),
            ).render(),
        )

        assertEquals(
            out.next(),
            OrderedList(
                startIndex = 1,
                isLoose = false,
                listItems(),
            ).also { list ->
                list.children
                    .asSequence()
                    .filterIsInstance<ListItem>()
                    .forEach { it.owner = list }
            }.render(),
        )
    }

    @Test
    fun unorderedList() {
        val out = readParts("block/unorderedlist.html")

        assertEquals(out.next(), UnorderedList(isLoose = false, emptyList()).render())

        assertEquals(
            out.next(),
            UnorderedList(
                isLoose = true,
                listItems(),
            ).render(),
        )

        assertEquals(
            out.next(),
            UnorderedList(
                isLoose = false,
                listItems(),
            ).also { list ->
                list.children
                    .asSequence()
                    .filterIsInstance<ListItem>()
                    .forEach { it.owner = list }
            }.render(),
        )
    }

    @Test
    fun html() {
        assertEquals("<p><strong>test</p></strong>", Html("<p><strong>test</p></strong>").render())
    }

    @Test
    fun paragraph() {
        val out = readParts("block/paragraph.html")

        assertEquals(out.next(), Paragraph(listOf(Text("Foo bar"))).render())
        assertEquals(out.next(), Paragraph(listOf(Text("Foo"), LineBreak, Text("bar"))).render())
    }

    @Test
    fun blockquote() {
        val out = readParts("block/blockquote.html")

        assertEquals(
            out.next(),
            buildBlock {
                blockQuote {
                    paragraph { text("Foo bar") }
                    paragraph { text("Baz bim") }
                }
            }.render(),
        )

        assertEquals(
            out.next(),
            buildBlock {
                blockQuote(attribution = { text("William Shakespeare") }) {
                    paragraph { text("To be, or not to be.") }
                    paragraph { text("That is the question.") }
                }
            }.render(),
        )

        // The 'Tip' label is not rendered here because
        // it requires the stdlib localization table.
        assertEquals(
            out.next(),
            buildBlock {
                blockQuote(
                    type = BlockQuote.Type.TIP,
                    attribution = { text("Someone") },
                ) {
                    paragraph { text("Hi there!") }
                }
            }.render(),
        )
    }

    @Test
    fun table() {
        val out = readParts("block/table.html")

        assertEquals(
            out.next(),
            Table(
                listOf(
                    Table.Column(
                        Table.Alignment.NONE,
                        header = Table.Cell(listOf(Text("A"))),
                        cells =
                            listOf(
                                Table.Cell(listOf(Text("C"))),
                                Table.Cell(listOf(Text("E"))),
                            ),
                    ),
                    Table.Column(
                        Table.Alignment.NONE,
                        header = Table.Cell(listOf(Text("B"))),
                        cells =
                            listOf(
                                Table.Cell(listOf(Text("D"))),
                                Table.Cell(listOf(Text("F"))),
                            ),
                    ),
                ),
            ).render(),
        )

        assertEquals(
            out.next(),
            Table(
                listOf(
                    Table.Column(
                        Table.Alignment.CENTER,
                        header = Table.Cell(listOf(Text("A"))),
                        cells =
                            listOf(
                                Table.Cell(listOf(Text("C"))),
                                Table.Cell(listOf(Text("E"))),
                            ),
                    ),
                    Table.Column(
                        Table.Alignment.RIGHT,
                        header = Table.Cell(listOf(Text("B"))),
                        cells =
                            listOf(
                                Table.Cell(listOf(Text("D"))),
                                Table.Cell(listOf(Strong(listOf(Text("F"))))),
                            ),
                    ),
                ),
            ).render(),
        )

        assertEquals(
            out.next(),
            Table(
                listOf(
                    Table.Column(
                        Table.Alignment.NONE,
                        header = Table.Cell(listOf(Text("A"))),
                        cells =
                            listOf(
                                Table.Cell(listOf(Text("C"))),
                                Table.Cell(listOf(Text("E"))),
                            ),
                    ),
                    Table.Column(
                        Table.Alignment.NONE,
                        header = Table.Cell(listOf(Text("B"))),
                        cells =
                            listOf(
                                Table.Cell(listOf(Text("D"))),
                                Table.Cell(listOf(Text("F"))),
                            ),
                    ),
                ),
                caption = "Table 'caption'.",
            ).render(),
        )
    }

    // Quarkdown

    @Test
    fun mathBlock() {
        val out = readParts("block/math.html")

        assertEquals(out.next(), Math("some expression").render())
        assertEquals(out.next(), Math("\\lim_{x\\to\\infty}x").render())
    }

    @Test
    fun mathSpan() {
        val out = readParts("inline/math.html")

        assertEquals(out.next(), MathSpan("some expression").render())
        assertEquals(out.next(), MathSpan("\\lim_{x\\to\\infty}x").render())
    }

    @Test
    fun container() {
        val out = readParts("quarkdown/container.html")
        val children =
            buildBlocks {
                paragraph { text("Foo bar") }
                blockQuote { paragraph { text("Baz") } }
            }

        assertEquals(out.next(), Container(children = children).render())

        assertEquals(
            out.next(),
            Container(
                foregroundColor = Color(100, 20, 80),
                backgroundColor = Color(10, 20, 30),
                children = children,
            ).render(),
        )

        assertEquals(
            out.next(),
            Container(
                backgroundColor = Color(10, 20, 30),
                padding = Sizes(vertical = 2.0.cm, horizontal = 3.0.cm),
                cornerRadius = Sizes(all = 12.0.px),
                children = children,
            ).render(),
        )

        assertEquals(
            out.next(),
            Container(
                fullWidth = true,
                borderColor = Color(30, 20, 10),
                borderWidth = Sizes(all = 1.0.cm),
                margin = Sizes(all = 2.0.cm),
                padding = Sizes(2.0.inch, 3.percent, 4.0.inch, 5.0.inch),
                cornerRadius = Sizes(all = 6.0.px),
                alignment = Container.Alignment.CENTER,
                textAlignment = Container.TextAlignment.JUSTIFY,
                children = children,
            ).render(),
        )

        assertEquals(
            out.next(),
            Container(
                borderColor = Color(30, 20, 10),
                borderStyle = Container.BorderStyle.DOTTED,
                alignment = Container.Alignment.END,
                children = children,
            ).render(),
        )

        assertEquals(
            out.next(),
            Container(
                textTransform =
                    TextTransformData(
                        size = TextTransformData.Size.LARGE,
                        style = TextTransformData.Style.ITALIC,
                        decoration = TextTransformData.Decoration.STRIKETHROUGH,
                        weight = TextTransformData.Weight.BOLD,
                        case = TextTransformData.Case.UPPERCASE,
                        variant = TextTransformData.Variant.SMALL_CAPS,
                    ),
                children = children,
            ).render(),
        )
    }

    @Test
    fun fullSpan() {
        val out = readParts("quarkdown/fullspan.html")
        val paragraph = Paragraph(listOf(Text("Foo"), LineBreak, Text("bar")))

        assertEquals(out.next(), FullColumnSpan(listOf(paragraph)).render())
    }

    @Test
    fun clipped() {
        val out = readParts("quarkdown/clipped.html")
        val paragraph = Paragraph(listOf(Text("Foo"), LineBreak, Text("bar")))

        assertEquals(out.next(), Clipped(Clipped.Clip.CIRCLE, listOf(paragraph)).render())
        assertEquals(out.next(), Clipped(Clipped.Clip.CIRCLE, listOf(paragraph, paragraph)).render())
    }

    @Test
    fun box() {
        val out = readParts("quarkdown/box.html")
        val paragraph = Paragraph(listOf(Text("Foo"), LineBreak, Text("bar")))

        assertEquals(
            out.next(),
            Box(
                title = listOf(Text("Title")),
                type = Box.Type.CALLOUT,
                padding = null,
                backgroundColor = null,
                foregroundColor = null,
                listOf(paragraph),
            ).render(),
        )

        assertEquals(
            out.next(),
            Box(
                title = listOf(Text("Title"), Emphasis(listOf(Text("Title")))),
                type = Box.Type.WARNING,
                padding = null,
                backgroundColor = null,
                foregroundColor = null,
                listOf(paragraph),
            ).render(),
        )

        assertEquals(
            out.next(),
            Box(
                title = null,
                type = Box.Type.ERROR,
                padding = 4.0.cm,
                backgroundColor = null,
                foregroundColor = null,
                listOf(paragraph),
            ).render(),
        )

        assertEquals(
            out.next(),
            Box(
                title = listOf(Text("Title")),
                type = Box.Type.ERROR,
                padding = 3.0.inch,
                backgroundColor = Color(255, 0, 120),
                foregroundColor = Color(0, 10, 25),
                listOf(paragraph),
            ).render(),
        )
    }

    @Test
    fun collapse() {
        val out = readParts("quarkdown/collapse.html")

        assertEquals(
            out.next(),
            Collapse(
                title = listOf(Emphasis(listOf(Text("Hello")))),
                isOpen = false,
                children = listOf(Strong(listOf(Text("world")))),
            ).render(),
        )

        assertEquals(
            out.next(),
            Collapse(
                title = listOf(Text("Hello")),
                isOpen = true,
                children = listOf(BlockQuote(children = listOf(Paragraph(listOf(Text("world")))))),
            ).render(),
        )
    }

    @Test
    fun `inline collapse`() {
        val out = readParts("quarkdown/inlinecollapse.html")

        assertEquals(
            out.next(),
            InlineCollapse(
                text = buildInline { text("Foo bar") },
                placeholder = buildInline { text("Placeholder") },
                isOpen = false,
            ).render(),
        )

        assertEquals(
            out.next(),
            InlineCollapse(
                text = buildInline { text("Foo bar") },
                placeholder = buildInline { text("Placeholder") },
                isOpen = true,
            ).render(),
        )
    }

    @Test
    fun `text transform`() {
        val out = readParts("quarkdown/texttransform.html")

        assertEquals(
            out.next(),
            TextTransform(
                TextTransformData(
                    size = TextTransformData.Size.LARGE,
                    style = TextTransformData.Style.ITALIC,
                    decoration = TextTransformData.Decoration.STRIKETHROUGH,
                ),
                listOf(Text("Foo")),
            ).render(),
        )

        assertEquals(
            out.next(),
            TextTransform(
                TextTransformData(
                    size = TextTransformData.Size.TINY,
                    weight = TextTransformData.Weight.BOLD,
                    decoration = TextTransformData.Decoration.UNDEROVERLINE,
                    variant = TextTransformData.Variant.SMALL_CAPS,
                ),
                listOf(Emphasis(listOf(Text("Foo"))), Text("bar")),
            ).render(),
        )

        assertEquals(
            out.next(),
            TextTransform(
                TextTransformData(
                    case = TextTransformData.Case.CAPITALIZE,
                    decoration = TextTransformData.Decoration.ALL,
                    color = Color(255, 0, 0),
                ),
                listOf(Text("Foo")),
            ).render(),
        )

        assertEquals(
            out.next(),
            TextTransform(
                TextTransformData(),
                listOf(Text("Foo")),
            ).render(),
        )
    }

    @Test
    fun `last heading`() {
        val out = readParts("quarkdown/lastheading.html")

        assertEquals(
            out.next(),
            LastHeading(depth = 3).render(),
        )
    }

    @Test
    fun bibliography() {
        val out = readParts("quarkdown/bibliography.html")

        assertEquals(
            out.next(),
            BibliographyView(
                title = buildInline { text("Bibliography (plain)") },
                bibliography = Bibliography(listOf(article, book, misc)),
                style = BibliographyStyle.Plain,
            ).render(),
        )

        assertEquals(
            out.next(),
            BibliographyView(
                title = buildInline { text("Bibliography (ieeetr)") },
                bibliography = Bibliography(listOf(article, book, misc)),
                style = BibliographyStyle.Ieeetr,
            ).render(),
        )
    }
}
