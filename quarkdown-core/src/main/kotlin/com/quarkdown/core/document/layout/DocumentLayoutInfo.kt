package com.quarkdown.core.document.layout

import com.quarkdown.core.document.layout.caption.CaptionPositionInfo
import com.quarkdown.core.document.layout.font.FontInfo
import com.quarkdown.core.document.layout.page.PageFormatInfo
import com.quarkdown.core.document.layout.paragraph.ParagraphStyleInfo

/**
 * Mutable information about the layout options of a document.
 * When any of the fields is `null`, the default value supplied by the underlying renderer is used.
 * @param pageFormat format of the pages of the document
 * @param fonts list of font configurations used in the document, in order of precedence
 * @param paragraphStyle style of paragraphs in the document
 * @param captionPosition position of captions of figures, tables, and more in the document
 */
data class DocumentLayoutInfo(
    val pageFormat: PageFormatInfo = PageFormatInfo(),
    val fonts: List<FontInfo> = emptyList(),
    val paragraphStyle: ParagraphStyleInfo = ParagraphStyleInfo(),
    val captionPosition: CaptionPositionInfo = CaptionPositionInfo(),
)
