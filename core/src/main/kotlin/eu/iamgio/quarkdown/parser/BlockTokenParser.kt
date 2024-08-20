package eu.iamgio.quarkdown.parser

import eu.iamgio.quarkdown.ast.InlineContent
import eu.iamgio.quarkdown.ast.Node
import eu.iamgio.quarkdown.ast.base.block.BaseListItem
import eu.iamgio.quarkdown.ast.base.block.BlockQuote
import eu.iamgio.quarkdown.ast.base.block.BlockText
import eu.iamgio.quarkdown.ast.base.block.Code
import eu.iamgio.quarkdown.ast.base.block.Heading
import eu.iamgio.quarkdown.ast.base.block.HorizontalRule
import eu.iamgio.quarkdown.ast.base.block.Html
import eu.iamgio.quarkdown.ast.base.block.LinkDefinition
import eu.iamgio.quarkdown.ast.base.block.ListBlock
import eu.iamgio.quarkdown.ast.base.block.ListItem
import eu.iamgio.quarkdown.ast.base.block.Newline
import eu.iamgio.quarkdown.ast.base.block.OrderedList
import eu.iamgio.quarkdown.ast.base.block.Paragraph
import eu.iamgio.quarkdown.ast.base.block.Table
import eu.iamgio.quarkdown.ast.base.block.TaskListItem
import eu.iamgio.quarkdown.ast.base.block.UnorderedList
import eu.iamgio.quarkdown.ast.quarkdown.FunctionCallNode
import eu.iamgio.quarkdown.ast.quarkdown.block.Math
import eu.iamgio.quarkdown.ast.quarkdown.block.PageBreak
import eu.iamgio.quarkdown.context.MutableContext
import eu.iamgio.quarkdown.function.call.FunctionCallArgument
import eu.iamgio.quarkdown.function.value.DynamicValue
import eu.iamgio.quarkdown.function.value.ValueFactory
import eu.iamgio.quarkdown.lexer.Lexer
import eu.iamgio.quarkdown.lexer.Token
import eu.iamgio.quarkdown.lexer.acceptAll
import eu.iamgio.quarkdown.lexer.tokens.BlockCodeToken
import eu.iamgio.quarkdown.lexer.tokens.BlockQuoteToken
import eu.iamgio.quarkdown.lexer.tokens.BlockTextToken
import eu.iamgio.quarkdown.lexer.tokens.FencesCodeToken
import eu.iamgio.quarkdown.lexer.tokens.FunctionCallToken
import eu.iamgio.quarkdown.lexer.tokens.HeadingToken
import eu.iamgio.quarkdown.lexer.tokens.HorizontalRuleToken
import eu.iamgio.quarkdown.lexer.tokens.HtmlToken
import eu.iamgio.quarkdown.lexer.tokens.LinkDefinitionToken
import eu.iamgio.quarkdown.lexer.tokens.ListItemToken
import eu.iamgio.quarkdown.lexer.tokens.MultilineMathToken
import eu.iamgio.quarkdown.lexer.tokens.NewlineToken
import eu.iamgio.quarkdown.lexer.tokens.OnelineMathToken
import eu.iamgio.quarkdown.lexer.tokens.OrderedListToken
import eu.iamgio.quarkdown.lexer.tokens.PageBreakToken
import eu.iamgio.quarkdown.lexer.tokens.ParagraphToken
import eu.iamgio.quarkdown.lexer.tokens.SetextHeadingToken
import eu.iamgio.quarkdown.lexer.tokens.TableToken
import eu.iamgio.quarkdown.lexer.tokens.UnorderedListToken
import eu.iamgio.quarkdown.util.iterator
import eu.iamgio.quarkdown.util.nextOrNull
import eu.iamgio.quarkdown.util.takeUntilLastOccurrence
import eu.iamgio.quarkdown.util.trimDelimiters
import eu.iamgio.quarkdown.visitor.token.BlockTokenVisitor

/**
 * The position of this character in the delimiter of a table header defines its column alignment.
 */
private const val TABLE_ALIGNMENT_CHAR = ':'

/**
 * A parser for block tokens.
 * @param context additional data to fill during the parsing process
 */
class BlockTokenParser(private val context: MutableContext) : BlockTokenVisitor<Node> {
    /**
     * @return the parsed content of the tokenization from [this] lexer
     */
    private fun Lexer.tokenizeAndParse(): List<Node> =
        this.tokenize()
            .acceptAll(context.flavor.parserFactory.newParser(context))

    /**
     * @return [this] raw string tokenized and parsed into processed inline content,
     *                based on this [flavor]'s specifics
     */
    private fun String.toInline(): InlineContent =
        context.flavor.lexerFactory.newInlineLexer(this)
            .tokenizeAndParse()

    override fun visit(token: NewlineToken): Node {
        return Newline()
    }

    override fun visit(token: BlockCodeToken): Node {
        return Code(
            language = null,
            // Remove first indentation
            content = token.data.text.replace("^ {1,4}".toRegex(RegexOption.MULTILINE), "").trim(),
        )
    }

    override fun visit(token: FencesCodeToken): Node {
        val groups = token.data.groups.iterator(consumeAmount = 4)
        return Code(
            language = groups.next().takeIf { it.isNotBlank() }?.trim(),
            content = groups.next().trim(),
        )
    }

    override fun visit(token: MultilineMathToken): Node {
        val groups = token.data.groups.iterator(consumeAmount = 3)
        return Math(expression = groups.next().trim())
    }

    override fun visit(token: OnelineMathToken): Node {
        val groups = token.data.groups.iterator(consumeAmount = 2)
        return Math(expression = groups.next().trim())
    }

    override fun visit(token: HorizontalRuleToken): Node {
        return HorizontalRule()
    }

    /**
     * Splits a heading text and its custom ID from a raw text.
     * `Heading {#custom-id}` -> `Heading`, `custom-id`
     * @param text heading text
     * @return a pair of the heading text and its custom ID
     */
    private fun splitHeadingTextAndId(text: String): Pair<String, String?> {
        val customIdMatch = "\\s+\\{#([^}]+)}\$".toRegex().find(text)
        val customId = customIdMatch?.groupValues?.get(1) // {#custom-id} -> custom-id

        // Trim the custom ID from the text.
        val trimmedText = customIdMatch?.let { text.removeSuffix(it.value) } ?: text

        return trimmedText to customId
    }

    override fun visit(token: HeadingToken): Node {
        val groups = token.data.groups.iterator(consumeAmount = 2)

        val depth = groups.next().length // Amount of # characters.

        val rawText = groups.next().trim().takeUntilLastOccurrence(" #") // Remove trailing # characters.
        // Heading {#custom-id} -> Heading, custom-id
        val (text, customId) = splitHeadingTextAndId(rawText)

        return Heading(
            depth,
            text.toInline(),
            customId,
        )
    }

    override fun visit(token: SetextHeadingToken): Node {
        val groups = token.data.groups.iterator(consumeAmount = 2)

        val rawText = groups.next().trim()
        // Heading {#custom-id} -> Heading, custom-id
        val (text, customId) = splitHeadingTextAndId(rawText)

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

    /**
     * Parses list items from a list [token].
     * @param token list token to extract the items from
     */
    private fun extractListItems(token: Token) =
        context.flavor.lexerFactory.newListLexer(source = token.data.text)
            .tokenizeAndParse()
            .dropLastWhile { it is Newline } // Remove trailing blank lines

    /**
     * Sets [list] as the owner of each of its [ListItem]s.
     * Ownership is used while rendering to determine whether a [ListItem]
     * is part of a loose or tight list.
     * @param list list to set ownership for
     */
    private fun updateListItemsOwnership(list: ListBlock) {
        list.children.asSequence()
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

        val content = token.data.text.removePrefix(marker).removePrefix(task)
        val lines = content.lineSequence()

        if (lines.none()) {
            return BaseListItem(children = emptyList())
        }

        //
        val trimmedContent = trimMinIndent(lines, minIndent = marker.trim().length)

        // Parsed content.
        val children =
            context.flavor.lexerFactory
                .newBlockLexer(source = trimmedContent)
                .tokenizeAndParse()

        return when {
            // GFM task list item.
            task.isNotBlank() -> {
                val isChecked = "[ ]" !in task
                TaskListItem(isChecked, children)
            }
            // Regular list item.
            else -> BaseListItem(children)
        }
    }

    override fun visit(token: TableToken): Node {
        /**
         * A temporary mutable [Table.Column].
         */
        class MutableColumn(var alignment: Table.Alignment, val header: Table.Cell, val cells: MutableList<Table.Cell>)

        val groups = token.data.groups.iterator(consumeAmount = 2)
        val columns = mutableListOf<MutableColumn>()

        /**
         * Extracts the cells from a table row as raw strings.
         */
        fun splitRow(row: String): Sequence<String> =
            row.split("(?<!\\\\)\\|".toRegex()).asSequence()
                .filter { it.isNotEmpty() }
                .map { it.trim() }

        /**
         * Extracts the cells from a table row as processed [Table.Cell]s.
         */
        fun parseRow(row: String): Sequence<Table.Cell> = splitRow(row).map { Table.Cell(it.toInline()) }

        // Header row.
        parseRow(groups.next()).forEach {
            columns += MutableColumn(Table.Alignment.NONE, it, mutableListOf())
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

        // Other rows.
        groups.next().lineSequence()
            .filterNot { it.isBlank() }
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
            columns = columns.map { Table.Column(it.alignment, it.header, it.cells) },
        )
    }

    override fun visit(token: HtmlToken): Node {
        return Html(
            content = token.data.text.trim(),
        )
    }

    override fun visit(token: ParagraphToken): Node {
        return Paragraph(
            text = token.data.text.trim().toInline(),
        )
    }

    override fun visit(token: BlockQuoteToken): Node {
        // Remove leading >
        val text = token.data.text.replace("^ *>[ \\t]?".toRegex(RegexOption.MULTILINE), "").trim()

        return BlockQuote(
            children =
                context.flavor.lexerFactory
                    .newBlockLexer(source = text)
                    .tokenizeAndParse(),
        )
    }

    override fun visit(token: BlockTextToken): Node {
        return BlockText()
    }

    override fun visit(token: PageBreakToken): Node {
        return PageBreak()
    }

    override fun visit(token: FunctionCallToken): Node {
        val groups = token.data.groups.iterator(consumeAmount = 2)

        // Function name.
        val name = groups.next()

        // Function arguments.
        val arguments = mutableListOf<FunctionCallArgument>()

        // The name of the next argument. `null` means the next argument is unnamed.
        var argName: String? = null

        // Regular function arguments.
        groups.forEachRemaining { arg ->
            // If this group contains the name of a named argument,
            // it is applied to the very next argument.
            if (arg.firstOrNull() != '{' && arg.lastOrNull() != '}') {
                argName = arg
                return@forEachRemaining
            }

            // Regular argument wrapped in brackets, which are stripped off.
            val argContent = arg.trimDelimiters().trim()

            // An expression from the raw string is created.
            ValueFactory.expression(argContent, context)?.let {
                arguments += FunctionCallArgument(it, argName)
                argName = null // The name of the next named argument is reset.
            }
        }

        // Body function argument.
        // A body argument is always the last one, it goes on a new line and each line is indented.
        token.data.namedGroups["bodyarg"]?.takeUnless { it.isBlank() }?.let { body ->
            // A body argument is treated as plain text, thus nested function calls are not executed by default.
            // They are executed if the argument is used as Markdown content from the referenced function,
            // that runs recursive lexing & parsing on the arg content, triggering function calls.

            // Remove indentation at the beginning of each line.
            val value = DynamicValue(body.trimIndent())

            arguments += FunctionCallArgument(value, isBody = true)
        }

        val call = FunctionCallNode(context, name, arguments, token.isBlock)

        // Enqueuing the function call, in order to expand it in the next stage.
        context.register(call)

        return call
    }
}
