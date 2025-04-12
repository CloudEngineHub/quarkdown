package eu.iamgio.quarkdown.cli.creator.template

import eu.iamgio.quarkdown.template.TemplateProcessor

/**
 * Factory that creates a [TemplateProcessor] that helps generate the main file of a new Quarkdown project.
 * @see TemplateProcessor
 */
interface ProjectCreatorTemplateProcessorFactory {
    /**
     * @return the [TemplateProcessor] that processes the main file of a new Quarkdown project
     */
    fun create(): TemplateProcessor
}
