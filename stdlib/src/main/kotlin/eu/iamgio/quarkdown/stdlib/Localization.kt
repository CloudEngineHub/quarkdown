package eu.iamgio.quarkdown.stdlib

import eu.iamgio.quarkdown.context.Context
import eu.iamgio.quarkdown.context.MutableContext
import eu.iamgio.quarkdown.function.reflect.annotation.Injected
import eu.iamgio.quarkdown.function.reflect.annotation.Name
import eu.iamgio.quarkdown.function.value.DictionaryValue
import eu.iamgio.quarkdown.function.value.OutputValue
import eu.iamgio.quarkdown.function.value.StringValue
import eu.iamgio.quarkdown.function.value.VoidValue
import eu.iamgio.quarkdown.function.value.wrappedAsValue
import eu.iamgio.quarkdown.localization.Locale
import eu.iamgio.quarkdown.localization.LocaleLoader
import eu.iamgio.quarkdown.localization.LocalizationEntries
import eu.iamgio.quarkdown.localization.LocalizationTable

/**
 * `Localization` stdlib module exporter.
 * This module handles localization-related features.
 */
val Localization: Module =
    setOf(
        ::localization,
        ::localize,
    )

/**
 * Builds a localization table from the given dictionary of locales and their key-value entries.
 */
private fun buildLocalizationTable(contents: Map<String, DictionaryValue<OutputValue<String>>>): LocalizationTable =
    contents
        .asSequence()
        .map { (key, value) ->
            // The locale name is the first element of each list item:
            // English <-- this is the locale name
            //   - key1: value1
            //   - key2: value2
            val locale: Locale =
                LocaleLoader.SYSTEM.find(key)
                    ?: throw IllegalArgumentException("Could not find locale \"${key}\".")

            val entries: LocalizationEntries =
                value.unwrappedValue.mapValues { (_, value) -> value.unwrappedValue }

            locale to entries
        }.toMap()

/**
 * Merges two localization tables, giving priority to the new one.
 */
private fun mergeLocalizationTables(
    existingTable: LocalizationTable,
    newTable: LocalizationTable,
): LocalizationTable =
    existingTable.toMutableMap().apply {
        newTable.forEach { (locale, entries) ->
            merge(locale, entries) { existingEntries, newEntries -> existingEntries + newEntries }
        }
    }

/**
 * Defines and registers a new localization table, whose entries are key-value pairs for each locale and defined by a Markdown dictionary.
 *
 * Example:
 * ```
 * .localization {mytable}
 *     - English
 *         - morning: Good morning
 *         - evening: Good evening
 *     - Italian
 *         - morning: Buongiorno
 *         - evening: Buonasera
 * ```
 * The localization entries can then be accessed via the [localize] function, after setting the document language via [docLanguage]:
 * ```
 * .doclang {english}
 *
 * .localize {mytable:morning} <!-- Good morning -->
 * ```
 *
 * If [merge] is set to true, it can be used to expand an existing localization table.
 *
 * Example, extending stdlib's localization table:
 * ```
 * .doclang {fr-CA}
 *
 * .localization {std} merge:{true}
 *    - fr-CA
 *        - warning: Avertissement
 *
 * .box type:{warning}
 *     Box content
 * ```
 * In this example, the warning box will automatically feature the "Avertissement" title,
 * since the `std:warning` localization key is accessed by the `.box` function.
 *
 * @param tableName name of the localization table. Must be unique if [merge] is false.
 * @param merge if enabled and a table with the same name already exists, the two tables will be merged, with higher priority to the new one
 * @param contents dictionary of locales and their key-value entries
 * @throws IllegalArgumentException if the contents are not in the correct format,
 * or if the table name is already defined and [merge] is false
 */
fun localization(
    @Injected context: MutableContext,
    @Name("name") tableName: String,
    merge: Boolean = false,
    contents: Map<String, DictionaryValue<OutputValue<String>>>,
): VoidValue {
    val tableExists = tableName in context.localizationTables

    // Duplicate table names are not allowed.
    if (!merge && tableExists) {
        throw IllegalArgumentException(
            "Localization table \"$tableName\" is already defined. " +
                "To merge an existing table, use the merge parameter.",
        )
    }

    var table = buildLocalizationTable(contents)

    if (merge && tableExists) {
        val existingTable = context.localizationTables[tableName]!!
        table = mergeLocalizationTables(existingTable, table)
    }

    // The table is registered in the context.
    context.localizationTables[tableName] = table

    return VoidValue
}

/**
 * Localizes a key from a pre-existing localization table (defined via [localization]).
 *
 * Example:
 * ```
 * .localize("mytable:key")
 * ```
 *
 * @param key key to localize, in the format `tableName:keyName`
 * @param separator separator between the table name and the key name. Defaults to `:`
 * @return the localized value
 * @throws eu.iamgio.quarkdown.localization.LocalizationException if an error occurs during the lookup
 * @see localization
 */
fun localize(
    @Injected context: Context,
    key: String,
    separator: String = ":",
): StringValue {
    val (tableName, keyName) = key.split(separator, limit = 2)
    return context.localize(tableName, keyName).wrappedAsValue()
}
