package com.quarkdown.core.parser

import com.quarkdown.core.ast.InlineContent
import com.quarkdown.core.ast.Node
import com.quarkdown.core.ast.base.TextNode
import com.quarkdown.core.ast.base.block.BlankNode
import com.quarkdown.core.ast.base.block.BlockQuote
import com.quarkdown.core.ast.base.block.Code
import com.quarkdown.core.ast.base.block.FootnoteDefinition
import com.quarkdown.core.ast.base.block.Heading
import com.quarkdown.core.ast.base.block.HorizontalRule
import com.quarkdown.core.ast.base.block.Html
import com.quarkdown.core.ast.base.block.LinkDefinition
import com.quarkdown.core.ast.base.block.Newline
import com.quarkdown.core.ast.base.block.Paragraph
import com.quarkdown.core.ast.base.block.Table
import com.quarkdown.core.ast.base.block.list.ListBlock
import com.quarkdown.core.ast.base.block.list.ListItem
import com.quarkdown.core.ast.base.block.list.ListItemVariant
import com.quarkdown.core.ast.base.block.list.OrderedList
import com.quarkdown.core.ast.base.block.list.TaskListItemVariant
import com.quarkdown.core.ast.base.block.list.UnorderedList
import com.quarkdown.core.ast.base.inline.Image
import com.quarkdown.core.ast.quarkdown.block.ImageFigure
import com.quarkdown.core.ast.quarkdown.block.Math
import com.quarkdown.core.ast.quarkdown.block.PageBreak
import com.quarkdown.core.context.MutableContext
import com.quarkdown.core.lexer.Lexer
import com.quarkdown.core.lexer.Token
import com.quarkdown.core.lexer.acceptAll
import com.quarkdown.core.lexer.patterns.PatternHelpers
import com.quarkdown.core.lexer.tokens.BlockCodeToken
import com.quarkdown.core.lexer.tokens.BlockQuoteToken
import com.quarkdown.core.lexer.tokens.BlockTextToken
import com.quarkdown.core.lexer.tokens.FencesCodeToken
import com.quarkdown.core.lexer.tokens.FootnoteDefinitionToken
import com.quarkdown.core.lexer.tokens.FunctionCallToken
import com.quarkdown.core.lexer.tokens.HeadingToken
import com.quarkdown.core.lexer.tokens.HorizontalRuleToken
import com.quarkdown.core.lexer.tokens.HtmlToken
import com.quarkdown.core.lexer.tokens.LinkDefinitionToken
import com.quarkdown.core.lexer.tokens.ListItemToken
import com.quarkdown.core.lexer.tokens.MultilineMathToken
import com.quarkdown.core.lexer.tokens.NewlineToken
import com.quarkdown.core.lexer.tokens.OnelineMathToken
import com.quarkdown.core.lexer.tokens.OrderedListToken
import com.quarkdown.core.lexer.tokens.PageBreakToken
import com.quarkdown.core.lexer.tokens.ParagraphToken
import com.quarkdown.core.lexer.tokens.SetextHeadingToken
import com.quarkdown.core.lexer.tokens.TableToken
import com.quarkdown.core.lexer.tokens.UnorderedListToken
import com.quarkdown.core.parser.walker.funcall.WalkedFunctionCall
import com.quarkdown.core.util.iterator
import com.quarkdown.core.util.nextOrNull
import com.quarkdown.core.util.removeOptionalPrefix
import com.quarkdown.core.util.trimDelimiters
import com.quarkdown.core.visitor.token.BlockTokenVisitor

/**
 * The position of this character in the delimiter of a table header defines its column alignment.
 */
private const val TABLE_ALIGNMENT_CHAR = ':'

/**
 * A parser for block tokens.
 * @param context additional data to fill during the parsing process
 */
class BlockTokenParser(
    private val context: MutableContext,
) : BlockTokenVisitor<Node> {
    /**
     * @return the parsed content of the tokenization from [this] lexer
     */
    private fun Lexer.tokenizeAndParse(): List<Node> =
        this
            .tokenize()
            .acceptAll(context.flavor.parserFactory.newParser(context))

    /**
     * @return [this] raw string tokenized and parsed into processed inline content,
     *                based on this [flavor]'s specifics
     */
    private fun String.toInline(): InlineContent =
        context.flavor.lexerFactory
            .newInlineLexer(this)
            .tokenizeAndParse()

    override fun visit(token: NewlineToken): Node = Newline

    override fun visit(token: BlockCodeToken): Node =
        Code(
            language = null,
            // Removes first indentation.
            content =
                token.data.text
                    .replace("^ {1,4}".toRegex(RegexOption.MULTILINE), "")
                    .trim(),
        )

    override fun visit(token: FencesCodeToken): Node {
        val groups = token.data.groups.iterator(consumeAmount = 2)
        val language = token.data.namedGroups["fencescodelang"]
        val caption = token.data.namedGroups["fencescodecaption"]?.trim()
        val referenceId = token.data.namedGroups["fencescodecustomid"]?.trim()

        return Code(
            language = language?.takeIf { it.isNotBlank() }?.trim(),
            caption = caption?.trimDelimiters(),
            referenceId = referenceId,
            content = groups.next().trim(),
        )
    }

    override fun visit(token: MultilineMathToken): Node {
        val groups = token.data.groups.iterator(consumeAmount = 2)
        val customId = token.data.namedGroups["multilinemathcustomid"]?.trim()

        return Math(
            expression = groups.next().trim(),
            referenceId = customId,
        )
    }

    override fun visit(token: OnelineMathToken): Node {
        val groups = token.data.groups.iterator(consumeAmount = 2)
        val customId = token.data.namedGroups["onelinemathcustomid"]?.trim()

        return Math(
            expression = groups.next().trim(),
            referenceId = customId,
        )
    }

    override fun visit(token: HorizontalRuleToken): Node = HorizontalRule

    override fun visit(token: HeadingToken): Node {
        val groups = token.data.groups.iterator(consumeAmount = 2)

        val depth = groups.next().length // Amount of # characters.

        // e.g. ###! Heading => the heading is decorative, meaning it's not part of the document structure.
        val isDecorative = groups.next() == "!"

        val text = groups.next().trim()
        val customId = token.data.namedGroups["headingcustomid"]?.trim()

        return Heading(
            depth,
            text.toInline(),
            isDecorative,
            customId,
        )
    }

    override fun visit(token: SetextHeadingToken): Node {
        val groups = token.data.groups.iterator(consumeAmount = 2)

        val text = groups.next().trim()
        val customId = token.data.namedGroups["setextcustomid"]?.trim()

        return Heading(
            text = text.toInline(),
            depth =
                when (groups.next().firstOrNull()) {
                    '=' -> 1
                    '-' -> 2
                    else -> throw IllegalStateException("Invalid setext heading characters") // Should not happen
                },
            customId = customId,
        )
    }

    override fun visit(token: LinkDefinitionToken): Node {
        val groups = token.data.groups.iterator(consumeAmount = 2)

        return LinkDefinition(
            label = groups.next().trim().toInline(),
            url = groups.next().trim(),
            // Remove first and last character
            title = groups.nextOrNull()?.trimDelimiters()?.trim(),
        )
    }

    override fun visit(token: FootnoteDefinitionToken): Node {
        val groups = token.data.groups.iterator(consumeAmount = 2)

        return FootnoteDefinition(
            label = groups.next().trim(),
            text =
                groups
                    .next()
                    .trim()
                    .toInline(),
        )
    }

    /**
     * Parses list items from a list [token].
     * @param token list token to extract the items from
     */
    private fun extractListItems(token: Token) =
        context.flavor.lexerFactory
            .newListLexer(source = token.data.text)
            .tokenizeAndParse()
            .dropLastWhile { it is Newline } // Remove trailing blank lines

    /**
     * Sets [list] as the owner of each of its [ListItem]s.
     * Ownership is used while rendering to determine whether a [ListItem]
     * is part of a loose or tight list.
     * @param list list to set ownership for
     */
    private fun updateListItemsOwnership(list: ListBlock) {
        list.children
            .asSequence()
            .filterIsInstance<ListItem>()
            .forEach { it.owner = list }
    }

    override fun visit(token: UnorderedListToken): Node {
        val children = extractListItems(token)

        return UnorderedList(
            isLoose = children.any { it is Newline },
            children,
        ).also(::updateListItemsOwnership)
    }

    override fun visit(token: OrderedListToken): Node {
        val children = extractListItems(token)
        val groups = token.data.groups.iterator(consumeAmount = 3)

        // e.g. "1."
        val marker = groups.next().trim()

        return OrderedList(
            startIndex = marker.dropLast(1).toIntOrNull() ?: 1,
            isLoose = children.any { it is Newline },
            children,
        ).also(::updateListItemsOwnership)
    }

    /**
     * Like [String.trimIndent], but each line requires at least [minIndent] whitespaces trimmed.
     */
    private fun trimMinIndent(
        lines: Sequence<String>,
        minIndent: Int,
    ): String {
        // Gets the amount of indentation to trim from the content.
        var indent = minIndent
        for (char in lines.first()) {
            if (char.isWhitespace()) {
                indent++
            } else {
                break
            }
        }

        // Removes indentation from each line.
        val trimmedContent =
            lines.joinToString(separator = "\n") {
                it.replaceFirst("^ {1,$indent}".toRegex(), "")
            }

        return trimmedContent
    }

    override fun visit(token: ListItemToken): Node {
        val groups = token.data.groups.iterator(consumeAmount = 2)
        val marker = groups.next() // Bullet/number
        groups.next() // Consume
        val task = groups.next() // Optional GFM task

        val content =
            token.data.text
                .removePrefix(marker)
                .removePrefix(task)
        val lines = content.lineSequence()

        if (lines.none()) {
            return ListItem(children = emptyList())
        }

        // Trims the content, removing common indentation.
        val trimmedContent = trimMinIndent(lines, minIndent = marker.trim().length)

        // Additional features of this list item.
        val variants =
            buildList<ListItemVariant> {
                // GFM 5.3 task list item.
                if (task.isNotBlank()) {
                    val isChecked = "[ ]" !in task
                    add(TaskListItemVariant(isChecked))
                }
            }

        // Parsed content.
        val children =
            context.flavor.lexerFactory
                .newBlockLexer(source = trimmedContent)
                .tokenizeAndParse()

        return ListItem(variants, children)
    }

    override fun visit(token: TableToken): Node {
        val groups = token.data.groups.iterator(consumeAmount = 2)
        val columns = mutableListOf<Table.MutableColumn>()

        /**
         * Extracts the cells from a table row as raw strings.
         */
        fun splitRow(row: String): Sequence<String> =
            row
                .split("(?<!\\\\)\\|".toRegex())
                .asSequence()
                .filter { it.isNotEmpty() }
                .map { it.trim() }

        /**
         * Extracts the cells from a table row as processed [Table.Cell]s.
         */
        fun parseRow(row: String): Sequence<Table.Cell> = splitRow(row).map { Table.Cell(it.toInline()) }

        // Header row.
        parseRow(groups.next()).forEach {
            columns += Table.MutableColumn(Table.Alignment.NONE, it, mutableListOf())
        }

        // Delimiter row (defines alignment).
        splitRow(groups.next()).forEachIndexed { index, delimiter ->
            columns.getOrNull(index)?.alignment =
                when {
                    // :---:
                    delimiter.firstOrNull() == TABLE_ALIGNMENT_CHAR &&
                        delimiter.lastOrNull() == TABLE_ALIGNMENT_CHAR -> Table.Alignment.CENTER
                    // :---
                    delimiter.firstOrNull() == TABLE_ALIGNMENT_CHAR -> Table.Alignment.LEFT
                    // ---:
                    delimiter.lastOrNull() == TABLE_ALIGNMENT_CHAR -> Table.Alignment.RIGHT
                    // ---
                    else -> Table.Alignment.NONE
                }
        }

        // Quarkdown extension: a table may have metadata.
        // A caption is located at the end of the table, after a line break, wrapped by a delimiter, the same way as a link/image title.
        // "This is a caption", 'This is a caption', (This is a caption)
        // A custom ID, e.g. {#custom-id}, can be set for cross-referencing.
        val titlePattern = PatternHelpers.DELIMITED_TITLE
        val customIdPattern = PatternHelpers.customId("table")
        val metadataRegex = Regex("^[ \\t]*($titlePattern)?[ \\t]*$customIdPattern?[ \\t]*$")

        // The found caption and custom ID (reference ID) of the table, if any.
        var metadataFound = false
        var caption: String? = null
        var customId: String? = null

        // Other rows.
        groups
            .next()
            .lineSequence()
            .filterNot { it.isBlank() }
            .onEach { row ->
                // Extract the metadata if this is the metadata row.
                metadataRegex.find(row)?.let { metadataMatch ->
                    metadataFound = true
                    caption =
                        metadataMatch.groupValues
                            .getOrNull(1)
                            ?.takeIf { it.isNotBlank() }
                            ?.trimDelimiters()
                    customId =
                        metadataMatch.groupValues
                            .getOrNull(2)
                            ?.takeIf { it.isNotBlank() }
                            ?.trim()
                }
            }.filterNot { metadataFound } // The metadata row is at the end of the table and not part of the table itself.
            .forEach { row ->
                var cellCount = 0
                // Push cell.
                parseRow(row).forEachIndexed { index, cell ->
                    columns.getOrNull(index)?.cells?.add(cell)
                    cellCount = index
                }
                // Fill missing cells.
                for (remainingRow in cellCount + 1 until columns.size) {
                    columns[remainingRow].cells += Table.Cell(emptyList())
                }
            }

        return Table(
            columns = columns.map { it.toColumn() },
            caption = caption,
            referenceId = customId,
        )
    }

    override fun visit(token: HtmlToken): Node =
        Html(
            content = token.data.text.trim(),
        )

    override fun visit(token: ParagraphToken): Node {
        val text =
            token.data.text
                .trim()
                .toInline()

        // If the paragraph only consists of a single child, it could be a special block.
        return when (val singleChild = text.singleOrNull()) {
            // Single image -> a figure.
            is Image -> ImageFigure(singleChild)
            // Regular paragraph otherwise (most cases).
            else -> Paragraph(text)
        }
    }

    override fun visit(token: BlockQuoteToken): Node {
        // Remove leading >
        var text =
            token.data.text
                .replace("^ *>[ \\t]?".toRegex(RegexOption.MULTILINE), "")
                .trim()

        // Blockquote type, if any. e.g. Tip, note, warning.
        val type: BlockQuote.Type? =
            BlockQuote.Type.entries.find { type ->
                val prefix = type.name + ": " // e.g. Tip:, Note:, Warning:
                // If the text begins with the prefix, it's a blockquote of that type.
                val (newText, prefixFound) = text.removeOptionalPrefix(prefix, ignoreCase = true)
                // If the prefix was found, it is stripped off.
                if (prefixFound) {
                    text = newText
                }

                // If the prefix was found, the type is set.
                prefixFound
            }

        // Content nodes.
        var children =
            context.flavor.lexerFactory
                .newBlockLexer(source = text)
                .tokenizeAndParse()

        // If the last child is a single-item unordered list, then it's not part of the blockquote,
        // but rather its content is the attribution of the citation.
        // Example:
        // > To be, or not to be, that is the question.
        // > - William Shakespeare
        val attribution: InlineContent? =
            (children.lastOrNull() as? UnorderedList)
                ?.children
                ?.singleOrNull()
                ?.let { it as? ListItem } // Only lists with one item are considered.
                ?.children
                ?.firstOrNull()
                ?.let { it as? TextNode } // Usually a paragraph.
                ?.text // The text of the attribution, as inline content.
                ?.also { children = children.dropLast(1) } // If found, the attribution is not part of the children.

        return BlockQuote(
            type,
            attribution,
            children,
        )
    }

    override fun visit(token: BlockTextToken): Node = BlankNode

    override fun visit(token: PageBreakToken): Node = PageBreak()

    override fun visit(token: FunctionCallToken): Node {
        val result =
            token.data.walkerResult
                ?: throw IllegalStateException("Function call walker result not found.")

        val call = result.value as WalkedFunctionCall

        // The range of the function call in the source code.
        // Note: the end index is provided by the walker, not the lexer.
        val sourceRangeStart = token.data.position.start
        val sourceRangeEnd = sourceRangeStart + result.endIndex
        val sourceRange = sourceRangeStart..sourceRangeEnd

        // The syntax-only information held by the walked function call is converted to a context-aware function call node.
        // Function chaining is also handled here, delegated to the refiner.
        val callNode = FunctionCallRefiner(context, call, token.isBlock, sourceRange).toNode()

        // Enqueuing the function call, in order to expand it in the next stage of the pipeline.
        context.register(callNode)

        return callNode
    }
}
