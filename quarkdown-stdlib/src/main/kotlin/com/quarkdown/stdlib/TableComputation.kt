package com.quarkdown.stdlib

import com.quarkdown.core.ast.InlineContent
import com.quarkdown.core.ast.MarkdownContent
import com.quarkdown.core.ast.NestableNode
import com.quarkdown.core.ast.base.block.Table
import com.quarkdown.core.ast.dsl.buildInline
import com.quarkdown.core.context.Context
import com.quarkdown.core.function.library.module.QuarkdownModule
import com.quarkdown.core.function.library.module.moduleOf
import com.quarkdown.core.function.reflect.annotation.Injected
import com.quarkdown.core.function.reflect.annotation.LikelyBody
import com.quarkdown.core.function.reflect.annotation.Name
import com.quarkdown.core.function.value.BooleanValue
import com.quarkdown.core.function.value.DynamicValue
import com.quarkdown.core.function.value.IterableValue
import com.quarkdown.core.function.value.NodeValue
import com.quarkdown.core.function.value.OrderedCollectionValue
import com.quarkdown.core.function.value.OutputValue
import com.quarkdown.core.function.value.data.Lambda
import com.quarkdown.core.function.value.factory.ValueFactory
import com.quarkdown.core.function.value.wrappedAsValue
import com.quarkdown.core.util.toPlainText

/**
 * `TableComputation` stdlib module exporter.
 * This module provides advanced functionality for tables, enhancing their capabilities
 * beyond basic data representation.
 * It adds dynamic operations like sorting, filtering, calculations.
 */
val TableComputation: QuarkdownModule =
    moduleOf(
        ::tableSort,
        ::tableFilter,
        ::tableCompute,
        ::tableColumn,
        ::tableColumns,
        ::generateTableByRows,
    )

/**
 * The sorting order for a table column.
 * @see [tableSort]
 */
enum class TableSortOrder {
    ASCENDING,
    DESCENDING,
    ;

    /**
     * Applies the sorting order to a sequence of items.
     * @param sequence the sequence to sort
     * @param by the function to extract the sorting key from each item
     * @return the sorted sequence
     */
    fun <T, R : Comparable<R>> apply(
        sequence: Sequence<T>,
        by: (T) -> R,
    ): Sequence<T> =
        when (this) {
            ASCENDING -> sequence.sortedBy(by)
            DESCENDING -> sequence.sortedByDescending(by)
        }
}

/**
 * Finds a table nested in a given content.
 * The reason to go deeper instead of searching just in the first layer is because of function calls:
 * a function call node is a [NestableNode] which saves its output content into its children.
 * This allows to use table computation features not only on Markdown tables, but also on
 * function-generated tables, for example via [csv].
 * @param content the content to search for a table
 * @return the first table found
 * @throws IllegalArgumentException if no table is found
 */
private fun findTable(content: NestableNode): Table =
    when (val child = content.children.firstOrNull()) {
        is Table -> child
        is NestableNode -> findTable(child)
        else -> throw IllegalArgumentException("A table is not provided and cannot be found.")
    }

/**
 * Retrieves a specific column from a table.
 * @param table the table to extract the column from
 * @param columnIndex index of the column (starting from 1)
 * @return a triple containing the table, the column, and the cells of the column, in order, as strings
 * @throws IllegalArgumentException if the column index is out of bounds
 */
private fun getTableColumn(
    table: Table,
    columnIndex: Int,
): Triple<Table, Table.Column, List<String>> {
    // Index starts from 1.
    val normalizedColumnIndex = columnIndex - INDEX_STARTS_AT

    val column =
        table.columns.getOrNull(normalizedColumnIndex)
            ?: throw IllegalArgumentException("Column index must be between 1 and ${table.columns.size}.")

    val values = column.cells.map { it.text.toPlainText() }

    return Triple(table, column, values)
}

/**
 * Retrieves a specific column from a table nested in a given content.
 * @param content the content to search for a table
 * @param columnIndex index of the column (starting from 1)
 * @return a triple containing the table, the column, and the cells of the column, in order, as strings
 * @throws IllegalArgumentException if no table is found or if the column index is out of bounds
 */
private fun findTableColumn(
    content: NestableNode,
    columnIndex: Int,
) = getTableColumn(findTable(content), columnIndex)

/**
 * Edits a table by replacing its columns with the specified ones.
 * @param table the original table
 * @param columns the new columns to replace the original ones
 * @return a new table with the specified columns, and the same other properties as the original
 */
private fun editTable(
    table: Table,
    columns: List<Table.Column>,
) = Table(columns, table.caption)

/**
 * Reconstructs a table based on the specified row indexes.
 * @param table the original table
 * @param orderedRowIndexes the list of ordered row indexes
 * @return a new table with the same content as [table],
 * with the rows rearranged or filtered according to the new indexes
 */
private fun reconstructTable(
    table: Table,
    orderedRowIndexes: List<Int>,
): Table {
    val newColumns =
        table.columns.map {
            it.copy(cells = orderedRowIndexes.map(it.cells::get))
        }

    return editTable(table, newColumns)
}

/**
 * Sorts a table based on the values of a column.
 *
 * Example:
 * ```
 * .tablesort {2}
 *     | Name | Age | City |
 *     |------|-----|------|
 *     | John | 25  | NY   |
 *     | Lisa | 32  | LA   |
 *     | Mike | 19  | CHI  |
 * ```
 *
 * Result:
 * ```
 * | Name | Age | City |
 * |------|-----|------|
 * | Mike | 19  | CHI  |
 * | John | 25  | NY   |
 * | Lisa | 32  | LA   |
 * ```
 *
 * @param column index of the column (starting from 1)
 * @param order sorting order (`ascending` or `descending`)
 * @param content table to sort
 * @return the sorted [Table] node
 * @wiki Table manipulation
 */
@Name("tablesort")
fun tableSort(
    @Name("column") columnIndex: Int,
    order: TableSortOrder = TableSortOrder.ASCENDING,
    @Name("table") @LikelyBody content: MarkdownContent,
): NodeValue {
    val (table, _, values) = findTableColumn(content, columnIndex)

    // Obtain the indexes of the rows sorted by the reference column.
    val orderedRowIndexes: List<Int> =
        values
            .asSequence()
            .withIndex()
            .let { order.apply(it) { item -> item.value } }
            .map { it.index }
            .toList()

    return reconstructTable(table, orderedRowIndexes).wrappedAsValue()
}

/**
 * Filters the rows of a table based on a boolean expression on a specific column.
 *
 * Example:
 * ```
 * .tablefilter {2} {@lambda x: .x::isgreater {20}}
 *     | Name | Age | City |
 *     |------|-----|------|
 *     | John | 25  | NY   |
 *     | Lisa | 32  | LA   |
 *     | Mike | 19  | CHI  |
 * ```
 *
 * Result:
 * ```
 * | Name | Age | City |
 * |------|-----|------|
 * | John | 25  | NY   |
 * | Lisa | 32  | LA   |
 * ```
 *
 * @param column index of the column (starting from 1)
 * @param filter a lambda function that returns a boolean value. When `true`, the rows is to be kept.
 * The lambda accepts a single argument, which is the cell value of the column.
 * @param content table to filter
 * @return the filtered [Table] node
 * @wiki Table manipulation
 */
@Name("tablefilter")
fun tableFilter(
    @Name("column") columnIndex: Int,
    filter: Lambda,
    @Name("table") @LikelyBody content: MarkdownContent,
): NodeValue {
    val (table, _, values) = findTableColumn(content, columnIndex)

    val filteredRowIndexes =
        values
            .withIndex()
            .filter { item -> filter.invoke<Boolean, BooleanValue>(DynamicValue(item.value)).unwrappedValue }
            .map { it.index }

    return reconstructTable(table, filteredRowIndexes).wrappedAsValue()
}

/**
 * Performs a computation on a specific column of a table, appending the result to a new cell in the bottom.
 *
 * Example:
 * ```
 * .tablecompute {2} {@lambda x: .x::average::round}
 *     | Name | Age | City |
 *     |------|-----|------|
 *     | John | 25  | NY   |
 *     | Lisa | 32  | LA   |
 *     | Mike | 19  | CHI  |
 * ```
 *
 * Result:
 * ```
 * | Name | Age | City |
 * |------|-----|------|
 * | John | 25  | NY   |
 * | Lisa | 32  | LA   |
 * | Mike | 19  | CHI  |
 * |      | 25  |      |
 * ```
 *
 * @param column index of the column (starting from 1)
 * @param compute a lambda function that returns any value, which is the output of the computation.
 * The lambda accepts a single argument, which is the ordered collection of cell values of the column.
 * @param content table to compute on
 * @return the computed [Table] node, of size `columns * (rows + 1)`
 * @wiki Table manipulation
 */
@Name("tablecompute")
fun tableCompute(
    @Name("column") columnIndex: Int,
    compute: Lambda,
    @Name("table") @LikelyBody content: MarkdownContent,
): NodeValue {
    val (table, column, values) = findTableColumn(content, columnIndex)

    // `compute` is called with the collection of cell values as an argument.
    val cellValuesCollection = OrderedCollectionValue(values.map(::DynamicValue))
    val computedCell = compute.invokeDynamic(cellValuesCollection).unwrappedValue

    // Append the computed cell to the column, and empty cells to the others.
    val newColumns =
        table.columns.map {
            val resultCell =
                Table.Cell(
                    buildInline { if (it === column) text(computedCell.toString()) },
                )
            it.copy(cells = it.cells + resultCell)
        }

    return editTable(table, newColumns).wrappedAsValue()
}

/**
 * Retrieves a specific column from a table as a collection of values.
 *
 * Example:
 * ```
 * .tablecolumn {2}
 *     | Name | Age | City |
 *     |------|-----|------|
 *     | John | 25  | NY   |
 *     | Lisa | 32  | LA   |
 *     | Mike | 19  | CHI  |
 * ```
 *
 * Result:
 * ```
 * - 25
 * - 32
 * - 19
 * ```
 *
 * @param column index of the column (starting from 1)
 * @param content table to extract the column from
 * @return the extracted cells
 * @wiki Table manipulation
 */
@Name("tablecolumn")
fun tableColumn(
    @Name("column") columnIndex: Int,
    @Name("of") @LikelyBody content: MarkdownContent,
): IterableValue<OutputValue<*>> {
    val (_, _, values) = findTableColumn(content, columnIndex)
    return OrderedCollectionValue(values.map(::DynamicValue))
}

/**
 * Retrieves all columns from a table as a collection of collections.
 *
 * Example:
 * ```
 * .tablecolumns
 *     | Name | Age | City |
 *     |------|-----|------|
 *     | John | 25  | NY   |
 *     | Lisa | 32  | LA   |
 *     | Mike | 19  | CHI  |
 * ```
 *
 * Result:
 * ```
 * - - John
 *   - Lisa
 *   - Mike
 *
 * - - 25
 *   - 32
 *   - 19
 *
 * - - NY
 *   - LA
 *   - CHI
 * ```
 *
 * @param content table to extract the columns from
 * @return the extracted cells, grouped by column
 * @wiki Table manipulation
 */
@Name("tablecolumns")
fun tableColumns(
    @Name("of") @LikelyBody content: MarkdownContent,
): IterableValue<IterableValue<out OutputValue<*>>> {
    val table = findTable(content)
    return table.columns
        .mapIndexed { index, column ->
            val (_, _, values) = getTableColumn(table, index + INDEX_STARTS_AT)
            values.map(::DynamicValue).wrappedAsValue()
        }.wrappedAsValue()
}

/**
 * Generates a table from a list of rows, where each row is a list of cell values.
 * Optionally, headers can be provided for the columns.
 *
 * Example:
 * ```
 * .var {headers}
 *    - Name
 *    - Age
 *    - City
 *
 * .newtablebyrows {.headers}
 *   - - John
 *     - 25
 *     - NY
 *   - - Lisa
 *     - 32
 *     - LA
 *   - - Mike
 *     - 19
 *     - CHI
 * ```
 *
 * Result:
 * ```
 * | Name | Age | City |
 * |------|-----|------|
 * | John | 25  | NY   |
 * | Lisa | 32  | LA   |
 * | Mike | 19  | CHI  |
 * ```
 *
 * @param context the current context, injected automatically
 * @param headers optional list of headers for the columns. If not provided, no headers are used.
 * @param rows list of rows, where each row is an iterable of cell values.
 * Rows can have varying lengths; missing cells will be filled with empty content.
 * @return the generated [Table] node
 * @wiki Table manipulation
 */
@Name("tablebyrows")
fun generateTableByRows(
    @Injected context: Context,
    headers: List<OutputValue<*>> = emptyList(),
    rows: List<IterableValue<out OutputValue<*>>>,
): NodeValue {
    if (rows.isEmpty()) return Table(emptyList()).wrappedAsValue()

    fun valueToInlineContent(value: OutputValue<*>?): InlineContent =
        value
            ?.unwrappedValue
            ?.let { ValueFactory.inlineMarkdown(it, context).unwrappedValue.children }
            ?: emptyList()

    val columnCount = rows.maxOf { it.unwrappedValue.toList().size }
    val columns =
        List(columnCount) {
            val header = headers.getOrNull(it)
            Table.MutableColumn(
                alignment = Table.Alignment.NONE,
                header = Table.Cell(valueToInlineContent(header)),
                cells = mutableListOf(),
            )
        }

    for (row in rows) {
        repeat(columnCount) { i ->
            val row = row.unwrappedValue.toList().getOrNull(i)
            columns[i].cells += Table.Cell(valueToInlineContent(row))
        }
    }

    return Table(columns.map { it.toColumn() })
        .wrappedAsValue()
}
