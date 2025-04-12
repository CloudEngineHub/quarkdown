package eu.iamgio.quarkdown

import eu.iamgio.quarkdown.ast.attributes.SectionLocation
import eu.iamgio.quarkdown.ast.attributes.id.getId
import eu.iamgio.quarkdown.ast.base.block.Heading
import eu.iamgio.quarkdown.ast.base.inline.Strong
import eu.iamgio.quarkdown.ast.base.inline.Text
import eu.iamgio.quarkdown.context.MutableContext
import eu.iamgio.quarkdown.context.toc.TableOfContents
import eu.iamgio.quarkdown.document.numbering.AlphaNumberingSymbol
import eu.iamgio.quarkdown.document.numbering.DecimalNumberingSymbol
import eu.iamgio.quarkdown.document.numbering.NumberingFixedSymbol
import eu.iamgio.quarkdown.document.numbering.NumberingFormat
import eu.iamgio.quarkdown.flavor.quarkdown.QuarkdownFlavor
import eu.iamgio.quarkdown.localization.LocaleLoader
import eu.iamgio.quarkdown.localization.LocaleNotSetException
import eu.iamgio.quarkdown.localization.LocalizationKeyNotFoundException
import eu.iamgio.quarkdown.localization.LocalizationLocaleNotFoundException
import eu.iamgio.quarkdown.localization.LocalizationTable
import eu.iamgio.quarkdown.localization.LocalizationTableNotFoundException
import eu.iamgio.quarkdown.localization.jvm.JVMLocaleLoader
import eu.iamgio.quarkdown.pipeline.output.ArtifactType
import eu.iamgio.quarkdown.pipeline.output.BinaryOutputArtifact
import eu.iamgio.quarkdown.pipeline.output.FileResourceExporter
import eu.iamgio.quarkdown.pipeline.output.LazyOutputArtifact
import eu.iamgio.quarkdown.pipeline.output.OutputResourceGroup
import eu.iamgio.quarkdown.pipeline.output.TextOutputArtifact
import eu.iamgio.quarkdown.rendering.html.HtmlIdentifierProvider
import eu.iamgio.quarkdown.rendering.html.QuarkdownHtmlNodeRenderer
import eu.iamgio.quarkdown.util.StringCase
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Tests for miscellaneous classes.
 */
class MiscTest {
    @Test
    fun identifiers() {
        val provider = HtmlIdentifierProvider.of(QuarkdownHtmlNodeRenderer(MutableContext(QuarkdownFlavor)))
        assertEquals("abc", provider.getId(Heading(1, listOf(Text("Abc")))))
        assertEquals("abc-def", provider.getId(Heading(1, listOf(Strong(listOf(Text("Abc Def")))))))
        assertEquals("hello-world", provider.getId(Heading(1, listOf(Text("Hello, World!")))))
    }

    @Test
    fun `table of contents`() {
        val headings1 =
            sequenceOf(
                Heading(1, listOf(Text("ABC"))),
                Heading(2, listOf(Text("DEF"))),
                Heading(2, listOf(Text("GHI"))),
                Heading(3, listOf(Text("JKL"))),
                Heading(2, listOf(Text("MNO"))),
                Heading(1, listOf(Text("PQR"))),
            )

        TableOfContents.generate(headings1).let { toc ->
            assertEquals(2, toc.items.size)
            assertEquals(3, toc.items[0].subItems.size)
            assertEquals(
                1,
                toc.items[0]
                    .subItems[1]
                    .subItems.size,
            )

            assertNodeEquals(Text("ABC"), toc.items[0].text.first())
            assertNodeEquals(
                Text("DEF"),
                toc.items[0]
                    .subItems[0]
                    .text
                    .first(),
            )
            assertNodeEquals(
                Text("GHI"),
                toc.items[0]
                    .subItems[1]
                    .text
                    .first(),
            )
            assertNodeEquals(
                Text("JKL"),
                toc.items[0]
                    .subItems[1]
                    .subItems[0]
                    .text
                    .first(),
            )
            assertNodeEquals(
                Text("MNO"),
                toc.items[0]
                    .subItems[2]
                    .text
                    .first(),
            )
            assertNodeEquals(Text("PQR"), toc.items[1].text.first())
        }

        val headings2 =
            sequenceOf(
                Heading(1, listOf(Text("ABC"))),
                Heading(3, listOf(Text("DEF"))),
                Heading(2, listOf(Text("GHI"))),
            )

        TableOfContents.generate(headings2).let { toc ->
            assertEquals(1, toc.items.size)
            assertEquals(2, toc.items[0].subItems.size)

            assertNodeEquals(Text("ABC"), toc.items[0].text.first())
            assertNodeEquals(
                Text("DEF"),
                toc.items[0]
                    .subItems[0]
                    .text
                    .first(),
            )
            assertNodeEquals(
                Text("GHI"),
                toc.items[0]
                    .subItems[1]
                    .text
                    .first(),
            )
        }

        /*
        val headings3 =
            sequenceOf(
                Heading(2, listOf(Text("ABC"))),
                Heading(3, listOf(Text("DEF"))),
                Heading(2, listOf(Text("GHI"))),
                Heading(1, listOf(Text("JKL"))),
            )

        TableOfContents.generate(headings3, maxDepth = 3).let { toc ->
            println(toc.items[0].subItems)
            assertEquals(3, toc.items.size)
            assertEquals(1, toc.items[0].subItems.size)

            assertEquals(Text("ABC"), toc.items[0].text.first())
            assertEquals(Text("DEF"), toc.items[0].subItems[0].text.first())
            assertEquals(Text("GHI"), toc.items[1].text.first())
            assertEquals(Text("JKL"), toc.items[1].text.first())
        }
         */
    }

    @Test
    fun locale() {
        val retriever = JVMLocaleLoader

        assertEquals(retriever, LocaleLoader.SYSTEM)

        with(retriever.fromTag("en")) {
            assertNotNull(this)
            assertEquals(this, retriever.fromName("English"))
            assertEquals(this, retriever.find("English"))
            assertEquals(this, retriever.find("eNgLiSh"))
            assertEquals("en", code)
            assertEquals("en", tag)
            assertEquals("English", displayName)
            assertEquals("English", localizedName)
            assertNull(countryCode)
            assertNull(localizedCountryName)
        }

        with(retriever.find("it")) {
            assertNotNull(this)
            assertEquals(this, retriever.fromName("Italian"))
            assertEquals(this, retriever.find("Italian"))
            assertEquals(this, retriever.find("iTaLiAn"))
            assertEquals("it", code)
            assertEquals("it", tag)
            assertEquals("Italian", displayName)
            assertEquals("italiano", localizedName)
            assertNull(countryCode)
            assertNull(localizedCountryName)
        }

        with(retriever.find("en-US")) {
            assertNotNull(this)
            assertEquals(this, retriever.find("English (United States)"))
            assertEquals(this, retriever.find("En-us"))
            assertEquals("en", code)
            assertEquals("en-US", tag)
            assertEquals("English (United States)", displayName)
            assertEquals("English (United States)", localizedName)
            assertEquals("US", countryCode)
            assertEquals("United States", localizedCountryName)
        }

        with(retriever.find("fr-CA")) {
            assertNotNull(this)
            assertEquals(this, retriever.find("French (Canada)"))
            assertEquals("fr", code)
            assertEquals("fr-CA", tag)
            assertEquals("French (Canada)", displayName)
            assertEquals("français (Canada)", localizedName)
            assertEquals("CA", countryCode)
            assertEquals("Canada", localizedCountryName)
        }

        assertNull(retriever.fromTag("nonexistent"))
        assertNull(retriever.fromName("nonexistent"))
        assertNull(retriever.find("nonexistent"))

        assertTrue(retriever.all.iterator().hasNext())
    }

    @Test
    fun localization() {
        val loader = LocaleLoader.SYSTEM

        val table: LocalizationTable =
            mapOf(
                loader.fromName("English")!! to
                    mapOf(
                        "morning" to "Good morning",
                        "evening" to "Good evening",
                    ),
                loader.fromName("Italian")!! to
                    mapOf(
                        "morning" to "Buongiorno",
                        "evening" to "Buonasera",
                    ),
            )

        assertEquals("Good morning", table[loader.fromName("English")]!!["morning"])

        val context = MutableContext(QuarkdownFlavor)
        context.localizationTables["mytable"] = table

        assertFailsWith<LocaleNotSetException> { context.localize("mytable", "morning") }

        context.documentInfo.locale = loader.fromName("English")!!
        assertEquals("Good morning", context.localize("mytable", "morning"))
        assertEquals("Good evening", context.localize("mytable", "evening"))
        assertFailsWith<LocalizationKeyNotFoundException> { context.localize("mytable", "afternoon") }
        assertFailsWith<LocalizationTableNotFoundException> { context.localize("sometable", "morning") }

        context.documentInfo.locale = loader.fromName("Italian")!!
        assertEquals("Buongiorno", context.localize("mytable", "morning"))
        assertEquals("Buonasera", context.localize("mytable", "evening"))

        context.documentInfo.locale = loader.fromName("French")!!
        assertFailsWith<LocalizationLocaleNotFoundException> { context.localize("mytable", "morning") }
    }

    @Test
    fun numbering() {
        assertEquals("3", DecimalNumberingSymbol.map(3))
        assertEquals("b", AlphaNumberingSymbol(StringCase.Lower).map(2))
        assertEquals("C", AlphaNumberingSymbol(StringCase.Upper).map(3))

        val format = NumberingFormat.fromString("1.1.a-A")

        with(format.symbols.iterator()) {
            assertIs<DecimalNumberingSymbol>(next())
            assertEquals('.', (next() as NumberingFixedSymbol).value)
            assertIs<DecimalNumberingSymbol>(next())
            assertEquals('.', (next() as NumberingFixedSymbol).value)

            next().let {
                assertIs<AlphaNumberingSymbol>(it)
                assertEquals(StringCase.Lower, it.case)
            }

            assertEquals('-', (next() as NumberingFixedSymbol).value)

            next().let {
                assertIs<AlphaNumberingSymbol>(it)
                assertEquals(StringCase.Upper, it.case)
            }
        }

        fun format(
            vararg levels: Int,
            numberingFormat: NumberingFormat = format,
            allowMismatchingLength: Boolean = true,
        ) = numberingFormat.format(SectionLocation(levels.toList()), allowMismatchingLength)

        assertEquals("1.1.a-A", format(1, 1, 1, 1))
        assertEquals("2.2.b-B", format(2, 2, 2, 2))
        assertEquals("2.1.c-A", format(2, 1, 3, 1))
        assertEquals("3.2.d-P", format(3, 2, 4, 16))
        assertEquals("12.20.e-A", format(12, 20, 5, 1))
        assertEquals("0.0.0-0", format(0, 0, 0, 0))
        assertEquals("2.1.b", format(2, 1, 2))
        assertEquals("1", format(1))
        assertEquals("1.2.c-D", format(1, 2, 3, 4, 5, 6))
        assertEquals("", format(1, 2, 3, 4, 5, 6, allowMismatchingLength = false))

        val roman = NumberingFormat.fromString("I.i")

        assertEquals("III", format(3, numberingFormat = roman))
        assertEquals("I.i", format(1, 1, numberingFormat = roman))
        assertEquals("IV.iii", format(4, 3, numberingFormat = roman))
        assertEquals("XVII.lvii", format(17, 57, numberingFormat = roman))
        assertEquals("XVII.0", format(17, 0, numberingFormat = roman))
        assertEquals("0.50000", format(0, 50000, numberingFormat = roman))
    }

    @Test
    fun `resource export`() {
        val dir = Files.createTempDirectory("quarkdown-resource-test")
        val exporter = FileResourceExporter(dir.toFile())

        with("Hello, world!".repeat(1000)) {
            assertEquals(
                this,
                TextOutputArtifact("Artifact 1", this, ArtifactType.HTML)
                    .accept(exporter)
                    .also { assertEquals("Artifact-1.html", it.name) }
                    .readText(),
            )
            assertContentEquals(
                this.toByteArray(),
                BinaryOutputArtifact("a/rt*fact::2", this.toByteArray(), ArtifactType.JAVASCRIPT)
                    .accept(exporter)
                    .also { assertEquals("a-rt-fact-2.js", it.name) }
                    .readBytes(),
            )
        }

        with("Quarkdown".repeat(1000)) {
            LazyOutputArtifact("artifact3", { this.toByteArray() }, ArtifactType.CSS)
                .accept(exporter)
                .also { assertEquals("artifact3.css", it.name) }
                .let { file ->
                    assertEquals(this, file.readText())
                    assertContentEquals(this.toByteArray(), file.readBytes())
                }
        }

        LazyOutputArtifact
            .internal(
                resource = "/media/icon.png",
                name = "artif@ct 4.png",
                type = ArtifactType.AUTO,
                referenceClass = this::class,
            ).run {
                assertContentEquals(
                    this::class.java.getResourceAsStream("/media/icon.png")!!.readBytes(),
                    this
                        .accept(exporter)
                        .also { assertEquals("artif@ct-4.png", it.name) }
                        .readBytes(),
                )
            }

        val group =
            OutputResourceGroup(
                "Group 1",
                setOf(
                    TextOutputArtifact("Artifact 5", "Hello, world!", ArtifactType.HTML),
                    BinaryOutputArtifact("arti-fact6", "Quarkdown".toByteArray(), ArtifactType.JAVASCRIPT),
                    LazyOutputArtifact("artifact7", { "Quarkdown".toByteArray() }, ArtifactType.CSS),
                    OutputResourceGroup(
                        "Group 2",
                        setOf(
                            TextOutputArtifact("Artifact 8", "Hello, world!", ArtifactType.HTML),
                            BinaryOutputArtifact("art*fact/9", "Quarkdown".toByteArray(), ArtifactType.JAVASCRIPT),
                        ),
                    ),
                    LazyOutputArtifact.internal(
                        referenceClass = this::class,
                        resource = "/media/banner.png",
                        name = "artif@ct 10.png",
                        type = ArtifactType.AUTO,
                    ),
                    BinaryOutputArtifact(
                        "artifact11",
                        "Hello world".repeat(100).toByteArray(),
                        ArtifactType.JAVASCRIPT,
                    ),
                ),
            )

        val groupFile = group.accept(exporter)

        assertTrue(groupFile.isDirectory)
        val files = groupFile.listFiles()!!
        assertEquals(6, files.size)

        assertEquals(1, files.count { it.extension == "html" })
        assertEquals(2, files.count { it.extension == "js" })
        assertEquals(1, files.count { it.extension == "css" })
        assertEquals(1, files.count { it.extension == "png" })

        val subGroup = files.single { it.isDirectory }
        subGroup.listFiles()!!.let { subFiles ->
            assertEquals(2, subFiles.size)
            assertEquals(1, subFiles.count { it.extension == "html" })
            assertEquals(1, subFiles.count { it.extension == "js" })
        }
    }
}
