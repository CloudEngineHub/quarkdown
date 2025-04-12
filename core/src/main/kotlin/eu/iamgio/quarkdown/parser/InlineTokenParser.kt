package eu.iamgio.quarkdown.parser

import eu.iamgio.quarkdown.ast.InlineContent
import eu.iamgio.quarkdown.ast.Node
import eu.iamgio.quarkdown.ast.base.inline.CodeSpan
import eu.iamgio.quarkdown.ast.base.inline.Comment
import eu.iamgio.quarkdown.ast.base.inline.CriticalContent
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
import eu.iamgio.quarkdown.ast.quarkdown.inline.MathSpan
import eu.iamgio.quarkdown.ast.quarkdown.inline.TextSymbol
import eu.iamgio.quarkdown.context.MutableContext
import eu.iamgio.quarkdown.document.size.Size
import eu.iamgio.quarkdown.function.value.factory.IllegalRawValueException
import eu.iamgio.quarkdown.function.value.factory.ValueFactory
import eu.iamgio.quarkdown.lexer.Lexer
import eu.iamgio.quarkdown.lexer.Token
import eu.iamgio.quarkdown.lexer.TokenData
import eu.iamgio.quarkdown.lexer.acceptAll
import eu.iamgio.quarkdown.lexer.tokens.CodeSpanToken
import eu.iamgio.quarkdown.lexer.tokens.CommentToken
import eu.iamgio.quarkdown.lexer.tokens.CriticalContentToken
import eu.iamgio.quarkdown.lexer.tokens.DiamondAutolinkToken
import eu.iamgio.quarkdown.lexer.tokens.EmphasisToken
import eu.iamgio.quarkdown.lexer.tokens.EntityToken
import eu.iamgio.quarkdown.lexer.tokens.EscapeToken
import eu.iamgio.quarkdown.lexer.tokens.ImageToken
import eu.iamgio.quarkdown.lexer.tokens.InlineMathToken
import eu.iamgio.quarkdown.lexer.tokens.LineBreakToken
import eu.iamgio.quarkdown.lexer.tokens.LinkToken
import eu.iamgio.quarkdown.lexer.tokens.PlainTextToken
import eu.iamgio.quarkdown.lexer.tokens.ReferenceImageToken
import eu.iamgio.quarkdown.lexer.tokens.ReferenceLinkToken
import eu.iamgio.quarkdown.lexer.tokens.StrikethroughToken
import eu.iamgio.quarkdown.lexer.tokens.StrongEmphasisToken
import eu.iamgio.quarkdown.lexer.tokens.StrongToken
import eu.iamgio.quarkdown.lexer.tokens.TextSymbolToken
import eu.iamgio.quarkdown.lexer.tokens.UrlAutolinkToken
import eu.iamgio.quarkdown.misc.color.Color
import eu.iamgio.quarkdown.misc.color.decoder.HexColorDecoder
import eu.iamgio.quarkdown.misc.color.decoder.HsvHslColorDecoder
import eu.iamgio.quarkdown.misc.color.decoder.RgbColorDecoder
import eu.iamgio.quarkdown.misc.color.decoder.RgbaColorDecoder
import eu.iamgio.quarkdown.misc.color.decoder.decode
import eu.iamgio.quarkdown.util.iterator
import eu.iamgio.quarkdown.util.nextOrNull
import eu.iamgio.quarkdown.util.trimDelimiters
import eu.iamgio.quarkdown.visitor.token.InlineTokenVisitor
import org.apache.commons.text.StringEscapeUtils

/**
 * ASCII of the character that replaces null characters,
 * following CommonMark's security guideline _(2.3 Insecure characters)_.
 */
private const val NULL_CHAR_REPLACEMENT_ASCII = 65533

/**
 * A parser for inline tokens.
 * @param context additional data to fill during the parsing process
 */
class InlineTokenParser(
    private val context: MutableContext,
) : InlineTokenVisitor<Node> {
    /**
     * @return the parsed content of the tokenization from [this] lexer
     */
    private fun Lexer.tokenizeAndParse(): List<Node> =
        this
            .tokenize()
            .acceptAll(context.flavor.parserFactory.newParser(context))

    /**
     * Tokenizes and parses sub-nodes.
     * @param source source to tokenize using the default inline lexer from this flavor
     * @return parsed nodes
     */
    private fun parseSubContent(source: CharSequence) =
        context.flavor.lexerFactory
            .newInlineLexer(source)
            .tokenizeAndParse()

    /**
     * Tokenizes and parses sub-nodes within a link label.
     * @param source source to tokenize using the link label inline lexer from this flavor
     * @return parsed nodes
     */
    private fun parseLinkLabelSubContent(source: CharSequence) =
        context.flavor.lexerFactory
            .newLinkLabelInlineLexer(source)
            .tokenizeAndParse()

    override fun visit(token: EscapeToken): Node {
        val groups = token.data.groups.iterator(consumeAmount = 2)
        return Text(text = groups.next())
    }

    override fun visit(token: EntityToken): Node {
        val groups = token.data.groups.iterator(consumeAmount = 2)
        val entity = groups.next().trim().lowercase()

        /**
         * @param radix radix to decode the numeric value for (`radix = 10` for decimal, `radix = 16` for hexadecimal)
         * @return [this] string to its corresponding character in [radix] representation.
         */
        fun String.decodeToContent(radix: Int): String {
            val ascii = toIntOrNull(radix) ?: return ""
            // CommonMark's security guideline (2.3 Insecure characters)
            return if (ascii != 0) {
                ascii.toChar()
            } else {
                NULL_CHAR_REPLACEMENT_ASCII.toChar()
            }.toString()
        }

        // Critical because further checks and mappings may be required during the rendering stage.
        return CriticalContent(
            when {
                entity == "colon" -> ":"
                // Hexadecimal (e.g. &#xD06)
                entity.startsWith("#x") -> groups.next().decodeToContent(radix = 16)
                // Decimal (e.g. &#35)
                entity.startsWith("#") -> groups.next().decodeToContent(radix = 10)
                // HTML entity (e.g. &nbsp;)
                else -> StringEscapeUtils.unescapeHtml4(token.data.text)
            },
        )
    }

    override fun visit(token: CriticalContentToken): Node = CriticalContent(token.data.text)

    override fun visit(token: TextSymbolToken): Node {
        // The symbol is then treated separately from text in the renderer.
        // e.g. the HTML renderer converts the symbol to its corresponding HTML entity (© -> &copy;).
        return TextSymbol(token.symbol.result)
    }

    override fun visit(token: CommentToken): Node {
        // Content is ignored.
        return Comment
    }

    override fun visit(token: LineBreakToken): Node = LineBreak

    override fun visit(token: LinkToken): Link {
        val groups = token.data.groups.iterator(consumeAmount = 2)
        return Link(
            label = parseLinkLabelSubContent(groups.next()),
            url = groups.next().trim(),
            // Removes leading and trailing delimiters.
            title = groups.nextOrNull()?.trimDelimiters()?.trim(),
        )
    }

    override fun visit(token: ReferenceLinkToken): ReferenceLink {
        val groups = token.data.groups.iterator(consumeAmount = 2)
        val label = parseLinkLabelSubContent(groups.next())
        // When the reference is collapsed, the label is the same as the reference label.
        return ReferenceLink(
            label = label,
            reference = groups.nextOrNull()?.let { parseLinkLabelSubContent(it) } ?: label,
            fallback = { Text(token.data.text) },
        )
    }

    override fun visit(token: DiamondAutolinkToken): Node {
        val groups = token.data.groups.iterator(consumeAmount = 2)
        val url = groups.next().trim()
        return visit(UrlAutolinkToken(token.data.copy(text = url)))
    }

    override fun visit(token: UrlAutolinkToken): Node {
        val url = token.data.text.trim()
        return Link(
            label = listOf(Text(url)),
            url = url,
            title = null,
        )
    }

    /**
     * Given an image token, extracts its width and height, if they are set.
     * They are stored in the named groups `width` and `height`, both prefixed by [namedGroupPrefix].
     * @param namedGroupPrefix prefix of the named groups
     * @param data token data to extract the size from
     * @return pair of width and height, or `null` if they are either unset or invalid
     */
    private fun extractImageSize(
        namedGroupPrefix: String,
        data: TokenData,
    ): Pair<Size?, Size?> {
        val width = data.namedGroups["${namedGroupPrefix}width"]
        val height = data.namedGroups["${namedGroupPrefix}height"]

        fun toSize(raw: String?): Size? =
            try {
                raw?.let(ValueFactory::size)?.unwrappedValue // Parses the value.
            } catch (e: IllegalRawValueException) {
                null
            }

        return toSize(width) to toSize(height)
    }

    override fun visit(token: ImageToken): Node {
        val link = visit(LinkToken(token.data))
        val (width, height) = extractImageSize("img", token.data)

        return Image(link, width, height)
    }

    override fun visit(token: ReferenceImageToken): Node {
        val link = visit(ReferenceLinkToken(token.data))
        val (width, height) = extractImageSize("refimg", token.data)

        return ReferenceImage(link, width, height)
    }

    override fun visit(token: CodeSpanToken): Node {
        val groups = token.data.groups.iterator(consumeAmount = 3)
        val rawText = groups.next().replace("\n", " ")

        // If the text start and ends by a space, and does contain non-space characters,
        // the leading and trailing spaces are trimmed (according to CommonMark).
        val hasNonSpaceChars = rawText.any { it != ' ' }
        val hasSpaceCharsOnBothEnds = rawText.firstOrNull() == ' ' && rawText.lastOrNull() == ' '

        // Trimmed final text.
        val text =
            if (hasNonSpaceChars && hasSpaceCharsOnBothEnds) {
                rawText.trimDelimiters()
            } else {
                rawText
            }

        // Additional content brought by the code span.
        // If null, no additional content is present.
        val content: CodeSpan.ContentInfo? =
            // Color decoding. Named colors are disabled due to performance reasons.
            Color
                .decode(text, HexColorDecoder, RgbColorDecoder, RgbaColorDecoder, HsvHslColorDecoder)
                ?.let(CodeSpan::ColorContent)

        return CodeSpan(text, content)
    }

    override fun visit(token: PlainTextToken): Node = Text(token.data.text)

    /**
     * @param token emphasis token to parse the content for
     * @return parsed content of an emphasis token
     */
    private fun emphasisContent(token: Token): InlineContent {
        // The raw string content, without the delimiters.
        val text =
            token.data.groups
                .iterator(consumeAmount = 3)
                .next()
        return parseSubContent(text)
    }

    override fun visit(token: EmphasisToken): Node = Emphasis(emphasisContent(token))

    override fun visit(token: StrongToken): Node = Strong(emphasisContent(token))

    override fun visit(token: StrongEmphasisToken): Node = StrongEmphasis(emphasisContent(token))

    override fun visit(token: StrikethroughToken): Node = Strikethrough(emphasisContent(token))

    override fun visit(token: InlineMathToken): Node {
        val groups = token.data.groups.iterator(consumeAmount = 2)
        return MathSpan(expression = groups.next().trim())
    }
}
