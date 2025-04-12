package eu.iamgio.quarkdown.localization.jvm

import eu.iamgio.quarkdown.localization.Locale
import eu.iamgio.quarkdown.localization.LocaleLoader

internal typealias JLocale = java.util.Locale

/**
 * Loader of [JVMLocale]s.
 */
internal object JVMLocaleLoader : LocaleLoader {
    override val all: Iterable<Locale>
        get() = JLocale.getAvailableLocales().map(::JVMLocale)

    private fun JLocale?.toLocale() =
        this
            ?.let(::JVMLocale)
            ?.takeIf { it.code.isNotBlank() }

    override fun fromTag(tag: String): Locale? = JLocale.forLanguageTag(tag)?.toLocale()

    override fun fromName(name: String): Locale? =
        JLocale
            .getAvailableLocales()
            .find { it.getDisplayName(JLocale.ENGLISH).equals(name, ignoreCase = true) }
            ?.toLocale()
}
