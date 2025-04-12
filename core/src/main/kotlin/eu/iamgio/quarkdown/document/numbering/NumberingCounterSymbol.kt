package eu.iamgio.quarkdown.document.numbering

import com.github.fracpete.romannumerals4j.RomanNumeralFormat
import eu.iamgio.quarkdown.util.StringCase
import eu.iamgio.quarkdown.util.case

/**
 * Represents a [NumberingSymbol] within a [NumberingFormat] with the responsibility of
 * counting items (e.g. headings) according to a specific rule (strategy).
 */
interface NumberingCounterSymbol : NumberingSymbol {
    /**
     * The range of values that this symbol can map to.
     * If a value is outside this range, it cannot be mapped
     * and an alternative strategy should be used.
     * In the default [NumberingFormat.format] implementation,
     * out-of-range values are simply mapped to their decimal representation.
     * E.g. [AlphaNumberingSymbol] can map values from 1-26 as `A`-`Z`. Value `0` is formatted as `0`.
     */
    val supportedRange: IntRange
        get() = 0..Int.MAX_VALUE

    /**
     * Maps a numeric value to a string according to the numbering strategy.
     * @param index numeric value to map
     * @return string representation of the index according to the numbering strategy
     */
    fun map(index: Int): String
}

/**
 * A numbering strategy that counts items as integers: `0, 1, 2, 3, ...`
 */
data object DecimalNumberingSymbol : NumberingCounterSymbol {
    override fun map(index: Int) = index.toString()
}

/**
 * A numbering strategy that counts items as letters of the latin alphabet: `0, A, B, C, ...`
 * @param case whether the letters should be uppercase or lowercase
 */
data class AlphaNumberingSymbol(val case: StringCase) : NumberingCounterSymbol {
    override val supportedRange: IntRange
        get() = 1..'Z' - 'A' + 1

    override fun map(index: Int) = ('A' + index - 1).toString().case(case)
}

/**
 * A numbering strategy that counts items as Roman numerals: `0, I, II, III, ...`
 * @param case whether the letters should be uppercase or lowercase
 */
data class RomanNumberingSymbol(val case: StringCase) : NumberingCounterSymbol {
    // Provided by the romannumerals4j library: https://github.com/fracpete/romannumerals4j
    private val format = RomanNumeralFormat()

    override val supportedRange: IntRange
        get() = 1..3999

    override fun map(index: Int) =
        index.let {
            format.format(it)?.case(case)
                ?: throw IllegalStateException("Failed to format $it as a roman numeral")
        }
}
