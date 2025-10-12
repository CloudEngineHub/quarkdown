package com.quarkdown.rendering.html

import com.quarkdown.core.ast.attributes.presence.markMathPresence
import com.quarkdown.core.context.MutableContext
import com.quarkdown.core.document.DocumentInfo
import com.quarkdown.core.document.DocumentTheme
import com.quarkdown.core.document.DocumentType
import com.quarkdown.core.document.deepCopy
import com.quarkdown.core.document.layout.DocumentLayoutInfo
import com.quarkdown.core.document.layout.font.FontInfo
import com.quarkdown.core.document.layout.page.PageFormatInfo
import com.quarkdown.core.document.size.Sizes
import com.quarkdown.core.document.size.inch
import com.quarkdown.core.document.tex.TexInfo
import com.quarkdown.core.flavor.quarkdown.QuarkdownFlavor
import com.quarkdown.core.localization.LocaleLoader
import com.quarkdown.core.media.ResolvableMedia
import com.quarkdown.core.media.storage.MEDIA_SUBDIRECTORY_NAME
import com.quarkdown.core.misc.font.FontFamily
import com.quarkdown.core.pipeline.output.ArtifactType
import com.quarkdown.core.pipeline.output.OutputResource
import com.quarkdown.core.pipeline.output.OutputResourceGroup
import com.quarkdown.core.pipeline.output.TextOutputArtifact
import com.quarkdown.core.template.TemplateProcessor
import com.quarkdown.core.util.normalizeLineSeparators
import com.quarkdown.rendering.html.post.HtmlPostRenderer
import java.io.File
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * HTML post renderer tests.
 */
class HtmlPostRendererTest {
    private lateinit var context: MutableContext

    private fun postRenderer(
        template: String,
        block: TemplateProcessor.() -> TemplateProcessor = { this },
    ): HtmlPostRenderer =
        HtmlPostRenderer(context, baseTemplateProcessor = {
            TemplateProcessor(template).block()
        })

    private fun setFontInfo(fontInfo: FontInfo) {
        context.documentInfo = context.documentInfo.deepCopy(layoutFont = fontInfo)
    }

    @BeforeTest
    fun setup() {
        context = MutableContext(QuarkdownFlavor)
    }

    @Test
    fun empty() {
        val postRenderer = postRenderer("<html><head></head><body></body></html>")
        assertEquals(
            "<html><head></head><body></body></html>",
            postRenderer.createTemplateProcessor().process(),
        )
    }

    @Test
    fun `with content, single line`() {
        val postRenderer =
            postRenderer("<html><head></head><body>[[CONTENT]]</body></html>") {
                content("<strong>Hello, world!</strong>")
            }
        assertEquals(
            "<html><head></head><body><strong>Hello, world!</strong></body></html>",
            postRenderer.createTemplateProcessor().process(),
        )
    }

    @Test
    fun `with content, multiline`() {
        val postRenderer =
            postRenderer(
                """
                <body>
                    [[CONTENT]]
                </body>
                """.trimIndent(),
            ) {
                content("<strong>Hello, world!</strong>")
            }
        assertEquals(
            """
            <body>
                <strong>Hello, world!</strong>
            </body>
            """.trimIndent(),
            postRenderer.createTemplateProcessor().process(),
        )
    }

    @Test
    fun `with title`() {
        context.documentInfo = DocumentInfo(name = "Doc title")
        val postRenderer =
            postRenderer("<head><title>[[TITLE]]</title></head><body>[[CONTENT]]</body>") {
                content("<strong>Hello, world!</strong>")
            }
        assertEquals(
            "<head><title>Doc title</title></head><body><strong>Hello, world!</strong></body>",
            postRenderer.createTemplateProcessor().process(),
        )
    }

    @Test
    fun `math conditional`() {
        context.attributes.markMathPresence()
        val postRenderer =
            postRenderer("<body>[[if:MATH]][[CONTENT]][[endif:MATH]]</body>") {
                content("<em>Hello, world!</em>")
            }
        assertEquals(
            "<body><em>Hello, world!</em></body>",
            postRenderer.createTemplateProcessor().process(),
        )
    }

    @Test
    fun `code conditional`() {
        val postRenderer =
            postRenderer(
                """
                <body>
                    [[if:!CODE]]
                    [[CONTENT]]
                    [[endif:!CODE]]
                </body>
                """.trimIndent(),
            ) {
                content("<em>Hello, world!</em>")
            }
        assertEquals(
            "<body>\n    <em>Hello, world!</em>\n</body>",
            postRenderer.createTemplateProcessor().process(),
        )
    }

    @Test
    fun `slides conditional`() {
        context.documentInfo = DocumentInfo(type = DocumentType.PLAIN)
        val postRenderer =
            postRenderer(
                """
                <body>
                    [[if:SLIDES]]
                    <em>Hello, world!</em>
                    [[endif:SLIDES]]
                    [[if:!SLIDES]]
                    <strong>Hello, world!</strong>
                    [[endif:!SLIDES]]
                </body>
                """.trimIndent(),
            ) {
                content("Hello, world!")
            }
        assertEquals(
            """
            <body>
                <strong>Hello, world!</strong>
            </body>
            """.trimIndent(),
            postRenderer.createTemplateProcessor().process(),
        )
    }

    private val fontFamilyProcessor =
        TemplateProcessor(
            """
            [[for:FONTFACE]]
            [[FONTFACE]]
            [[endfor:FONTFACE]]
            
            body {
                [[if:MAINFONTFAMILY]]--qd-main-font: '[[MAINFONTFAMILY]]';[[endif:MAINFONTFAMILY]]
                [[if:HEADINGFONTFAMILY]]--qd-heading-font: '[[HEADINGFONTFAMILY]]';[[endif:HEADINGFONTFAMILY]]
            }
            """.trimIndent(),
        )

    private fun fontFamilyProcessorResult() =
        HtmlPostRenderer(context, baseTemplateProcessor = { fontFamilyProcessor })
            .createTemplateProcessor()
            .process()
            .trim()

    @Test
    fun `system font`() {
        setFontInfo(FontInfo(mainFamily = FontFamily.System("Arial")))

        assertEquals(
            """
            @font-face { font-family: '63529059'; src: local('Arial'); }
            
            body {
                --qd-main-font: '63529059';
            }
            """.trimIndent(),
            fontFamilyProcessorResult(),
        )
    }

    @Test
    fun `local font, no media storage`() {
        val workingDirectory = File("src/test/resources")
        val path = "media/NotoSans-Regular.ttf"
        val media = ResolvableMedia(path, workingDirectory)

        setFontInfo(FontInfo(mainFamily = FontFamily.Media(media, path)))

        assertEquals(
            """
            @font-face { font-family: '${path.hashCode()}'; src: url('${File(workingDirectory, path).absolutePath}'); }
            
            body {
                --qd-main-font: '${path.hashCode()}';
            }
            """.trimIndent(),
            fontFamilyProcessorResult(),
        )
    }

    @Test
    fun `local font, with media storage`() {
        val workingDirectory = File("src/test/resources")
        val path = "media/NotoSans-Regular.ttf"
        val file = File(workingDirectory, path)
        val media = ResolvableMedia(path, workingDirectory)

        setFontInfo(FontInfo(mainFamily = FontFamily.Media(media, path)))

        context.options.enableLocalMediaStorage = true
        context.mediaStorage.register(path, media)

        assertEquals(
            """
            @font-face { font-family: '${path.hashCode()}'; src: url('media/NotoSans-Regular@${file.hashCode()}.ttf'); }
            
            body {
                --qd-main-font: '${path.hashCode()}';
            }
            """.trimIndent(),
            fontFamilyProcessorResult(),
        )
    }

    @Test
    fun `remote font, no media storage`() {
        val url =
            "https://fonts.gstatic.com/s/notosans/v39/o-0mIpQlx3QUlC5A4PNB6Ryti20_6n1iPHjcz6L1SoM-jCpoiyD9A-9U6VTYyWtZ3rKW9w.woff"
        val media = ResolvableMedia(url)

        setFontInfo(FontInfo(mainFamily = FontFamily.Media(media, url)))

        assertEquals(
            """
            @font-face { font-family: '${url.hashCode()}'; src: url('$url'); }
            
            body {
                --qd-main-font: '${url.hashCode()}';
            }
            """.trimIndent(),
            fontFamilyProcessorResult(),
        )
    }

    @Test
    fun `remote font, with media storage`() {
        val url =
            "https://fonts.gstatic.com/s/notosans/v39/o-0mIpQlx3QUlC5A4PNB6Ryti20_6n1iPHjcz6L1SoM-jCpoiyD9A-9U6VTYyWtZ3rKW9w.woff"
        val media = ResolvableMedia(url)

        setFontInfo(FontInfo(mainFamily = FontFamily.Media(media, url)))

        context.options.enableRemoteMediaStorage = true
        context.mediaStorage.register(url, media)

        assertEquals(
            """
            @font-face { font-family: '${url.hashCode()}'; src: url('media/https-fonts.gstatic.com-s-notosans-v39-o-0mIpQlx3QUlC5A4PNB6Ryti20_6n1iPHjcz6L1SoM-jCpoiyD9A-9U6VTYyWtZ3rKW9w.woff'); }
            
            body {
                --qd-main-font: '${url.hashCode()}';
            }
            """.trimIndent(),
            fontFamilyProcessorResult(),
        )
    }

    @Test
    fun `google font`() {
        val name = "Karla"

        setFontInfo(FontInfo(mainFamily = FontFamily.GoogleFont(name)))

        assertEquals(
            """
            @import url('https://fonts.googleapis.com/css2?family=$name&display=swap');
            
            body {
                --qd-main-font: '$name';
            }
            """.trimIndent(),
            fontFamilyProcessorResult(),
        )
    }

    @Test
    fun `main and heading fonts`() {
        setFontInfo(
            FontInfo(
                mainFamily = FontFamily.System("Arial"),
                headingFamily = FontFamily.GoogleFont("Roboto"),
            ),
        )

        assertEquals(
            """
            @font-face { font-family: '63529059'; src: local('Arial'); }
            @import url('https://fonts.googleapis.com/css2?family=Roboto&display=swap');
            
            body {
                --qd-main-font: '63529059';
                --qd-heading-font: 'Roboto';
            }
            """.trimIndent(),
            fontFamilyProcessorResult(),
        )
    }

    @Test
    fun `semi-real`() {
        context.documentInfo =
            DocumentInfo(
                name = "Quarkdown",
                locale = LocaleLoader.SYSTEM.fromName("english"),
                type = DocumentType.SLIDES,
                layout = DocumentLayoutInfo(font = FontInfo(mainFamily = FontFamily.System("Arial"))),
            )
        context.attributes.markMathPresence()

        val postRenderer =
            postRenderer(
                """
                <html[[if:LANG]] lang="[[LANG]]"[[endif:LANG]]>
                <head>
                    [[if:SLIDES]]
                    <link rel="stylesheet" href="...css"></link>
                    [[endif:SLIDES]]
                    [[if:CODE]]
                    <script src="...js"></script>
                    <script src="...js"></script>
                    [[endif:CODE]]
                    [[if:MATH]]
                    <script src="...js"></script>
                    <script src="...js"></script>
                    [[endif:MATH]]
                    <title>[[TITLE]]</title>
                    <style>
                    [[for:FONTFACE]]
                    [[FONTFACE]]
                    [[endfor:FONTFACE]]
                    
                    [[if:MATH]]
                    mjx-container {
                        margin: 0 0.3em;
                    }
                    [[endif:MATH]]
                    </style>
                </head>
                <body>
                    [[if:SLIDES]]
                    <div class="reveal">
                        <div class="slides">
                            [[CONTENT]]
                        </div>
                    </div>
                    <script src="slides.js"></script>
                    [[endif:SLIDES]]
                    [[if:!SLIDES]]
                    [[CONTENT]]
                    [[endif:!SLIDES]]
                </body>
                </html>
                """.trimIndent(),
            ) {
                content("<p><em>Hello, world!</em></p>")
            }

        assertEquals(
            """
            <html lang="en">
            <head>
                <link rel="stylesheet" href="...css"></link>
                <script src="...js"></script>
                <script src="...js"></script>
                <title>Quarkdown</title>
                <style>
                @font-face { font-family: '63529059'; src: local('Arial'); }
                
                mjx-container {
                    margin: 0 0.3em;
                }
                </style>
            </head>
            <body>
                <div class="reveal">
                    <div class="slides">
                        <p><em>Hello, world!</em></p>
                    </div>
                </div>
                <script src="slides.js"></script>
            </body>
            </html>
            """.trimIndent(),
            postRenderer.createTemplateProcessor().process(),
        )
    }

    @Test
    fun real() {
        context.documentInfo =
            DocumentInfo(
                name = "Quarkdown",
                locale = LocaleLoader.SYSTEM.fromName("english"),
                type = DocumentType.SLIDES,
                layout =
                    DocumentLayoutInfo(
                        font = FontInfo(mainFamily = FontFamily.System("Arial")),
                        pageFormat = PageFormatInfo(pageWidth = 8.5.inch, margin = Sizes(1.0.inch)),
                    ),
                tex = TexInfo(macros = mutableMapOf("\\R" to "\\mathbb{R}", "\\Z" to "\\mathbb{Z}")),
            )
        context.attributes.markMathPresence()

        val postRenderer =
            HtmlPostRenderer(context, baseTemplateProcessor = {
                TemplateProcessor
                    .fromResourceName("/postrendering/html-test-wrapper.html")
                    .content("<p><em>Hello, world!</em></p>")
            })

        assertEquals(
            javaClass
                .getResourceAsStream("/postrendering/html-test-result.html")!!
                .reader()
                .readText()
                .normalizeLineSeparators(),
            postRenderer.createTemplateProcessor().process(),
        )
    }

    private fun assertThemeGroupContains(
        resources: Set<OutputResource>,
        expectedThemes: Set<String>,
        notExpectedThemes: Set<String> = emptySet(),
    ) {
        val themeGroup = resources.filterIsInstance<OutputResourceGroup>().first { it.name == "theme" }
        val themes = themeGroup.resources.map { it.name }
        expectedThemes.forEach { assertTrue(it in themes) }
        notExpectedThemes.forEach { assertFalse(it in themes) }

        val theme = themeGroup.resources.first { it.name == "theme" } as TextOutputArtifact

        assertEquals(ArtifactType.CSS, theme.type)
        expectedThemes.filter { it != "theme" }.forEach {
            assertTrue("@import url('$it.css');" in theme.content)
        }
    }

    private val plainHtml = "<html><head></head><body></body></html>"

    private fun `resource generation`(block: (Set<OutputResource>) -> Unit) {
        context.documentInfo =
            DocumentInfo(
                type = DocumentType.SLIDES,
                theme = DocumentTheme(color = "darko", layout = "minimal"),
            )
        context.attributes.markMathPresence()

        val postRenderer = HtmlPostRenderer(context)
        val resources = postRenderer.generateResources(plainHtml)

        block(resources)

        resources.filterIsInstance<TextOutputArtifact>().first { it.type == ArtifactType.HTML }.let {
            assertEquals(plainHtml, it.content)
        }

        assertThemeGroupContains(resources, setOf("darko", "minimal", "global", "theme"))

        val scriptGroup = resources.filterIsInstance<OutputResourceGroup>().first { it.name == "script" }
        assertEquals("quarkdown", scriptGroup.resources.single().name)
    }

    @Test
    fun `resource generation, no media`() =
        `resource generation` { resources ->
            assertEquals(3, resources.size)
            assertFalse(MEDIA_SUBDIRECTORY_NAME in resources.map { it.name }) // Media storage is empty.
        }

    @Test
    fun `resource generation, with media`() {
        context.options.enableLocalMediaStorage = true
        context.mediaStorage.register("src/test/resources/media/file.txt", workingDirectory = null)
        `resource generation` { resources ->
            assertEquals(4, resources.size)
            assertTrue(MEDIA_SUBDIRECTORY_NAME in resources.map { it.name }) // Media storage is empty.
        }
    }

    @Test
    fun `resource generation, default theme`() {
        val context = MutableContext(QuarkdownFlavor)

        val postRenderer = HtmlPostRenderer(context)
        val html = "<html><head></head><body></body></html>"

        val resources = postRenderer.generateResources(html)
        assertEquals(3, resources.size)

        assertThemeGroupContains(
            resources,
            setOf("paperwhite", "latex", "global", "theme"),
            notExpectedThemes = setOf("zh"),
        )
    }

    @Test
    fun `resource generation, with specific localized theme`() {
        val context = MutableContext(QuarkdownFlavor)
        context.documentInfo = DocumentInfo(locale = LocaleLoader.SYSTEM.find("zh-CN"))

        val postRenderer = HtmlPostRenderer(context)
        val resources = postRenderer.generateResources(plainHtml)
        assertEquals(3, resources.size)

        assertThemeGroupContains(
            resources,
            setOf("paperwhite", "latex", "global", "theme", "zh"),
        )
    }

    @Test
    fun `resource generation, with missing localized theme`() {
        val context = MutableContext(QuarkdownFlavor)
        context.documentInfo = DocumentInfo(locale = LocaleLoader.SYSTEM.find("akan"))

        val postRenderer = HtmlPostRenderer(context)
        val resources = postRenderer.generateResources(plainHtml)
        assertEquals(3, resources.size)

        assertThemeGroupContains(
            resources,
            expectedThemes = emptySet(),
            notExpectedThemes = setOf("akan"),
        )
    }
}
