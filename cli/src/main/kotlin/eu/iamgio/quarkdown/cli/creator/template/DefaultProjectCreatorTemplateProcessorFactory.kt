package eu.iamgio.quarkdown.cli.creator.template

import eu.iamgio.quarkdown.document.DocumentInfo
import eu.iamgio.quarkdown.document.DocumentType
import eu.iamgio.quarkdown.function.value.quarkdownName
import eu.iamgio.quarkdown.template.TemplateProcessor

private const val TEMPLATE = "/creator/main.qmd.template"

/**
 * Implementation of [ProjectCreatorTemplateProcessorFactory]
 * based on the default template, which relies on document information
 * to fill placeholders.
 * @param info document information to inject into the template
 * @see ProjectCreatorTemplatePlaceholders
 */
class DefaultProjectCreatorTemplateProcessorFactory(
    private val info: DocumentInfo,
) : ProjectCreatorTemplateProcessorFactory {
    override fun create(): TemplateProcessor =
        with(ProjectCreatorTemplatePlaceholders) {
            TemplateProcessor.fromResourceName(TEMPLATE).apply {
                optionalValue(NAME, info.name)
                conditional(AUTHORS, info.authors.isNotEmpty())
                iterable(AUTHORS, info.authors.map { it.name })
                optionalValue(TYPE, info.type.quarkdownName)
                optionalValue(LANGUAGE, info.locale?.displayName)
                conditional(HAS_THEME, info.theme?.hasComponent == true)
                optionalValue(COLOR_THEME, info.theme?.color)
                optionalValue(LAYOUT_THEME, info.theme?.layout)
                conditional(USE_PAGE_COUNTER, info.type == DocumentType.PAGED)
            }
        }
}
