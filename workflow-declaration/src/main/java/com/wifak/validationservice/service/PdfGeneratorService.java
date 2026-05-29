package com.wifak.validationservice.service;

import com.itextpdf.text.*;
import com.itextpdf.text.pdf.PdfWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Map;

@Service
public class PdfGeneratorService {

    private static final Logger log = LoggerFactory.getLogger(PdfGeneratorService.class);
    private static final int FONT_SIZE_TITLE    = 18;
    private static final int FONT_SIZE_SECTION  = 14;
    private static final int FONT_SIZE_CONTENT  = 10;
    private static final int SPACING_AFTER_TITLE    = 20;
    private static final int SPACING_BEFORE_SECTION = 15;
    private static final int SPACING_AFTER_SECTION  = 10;

    public byte[] generatePdfFromText(String content, String title) throws DocumentException, IOException {
        log.info("ðŸ“„ Generating PDF: {}", title);
        Document document = new Document(PageSize.A4);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        try {
            PdfWriter.getInstance(document, baos);
            document.open();

            Font titleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, FONT_SIZE_TITLE, BaseColor.BLACK);
            Paragraph titleParagraph = new Paragraph(title, titleFont);
            titleParagraph.setAlignment(Element.ALIGN_CENTER);
            titleParagraph.setSpacingAfter(SPACING_AFTER_TITLE);
            document.add(titleParagraph);

            Font contentFont = FontFactory.getFont(FontFactory.COURIER, FONT_SIZE_CONTENT, BaseColor.DARK_GRAY);
            document.add(new Paragraph(content, contentFont));
            document.close();
            return baos.toByteArray();
        } catch (Exception e) {
            log.error("âŒ Error generating PDF", e);
            throw e;
        }
    }

    public byte[] generatePdfWithSections(String title, Map<String, String> sections)
            throws DocumentException, IOException {
        Document document = new Document(PageSize.A4);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        try {
            PdfWriter.getInstance(document, baos);
            document.open();

            Font titleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, FONT_SIZE_TITLE, BaseColor.BLACK);
            Paragraph titleParagraph = new Paragraph(title, titleFont);
            titleParagraph.setAlignment(Element.ALIGN_CENTER);
            titleParagraph.setSpacingAfter(SPACING_AFTER_TITLE);
            document.add(titleParagraph);

            Font sectionTitleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, FONT_SIZE_SECTION, BaseColor.BLUE);
            Font contentFont = FontFactory.getFont(FontFactory.COURIER, FONT_SIZE_CONTENT, BaseColor.DARK_GRAY);

            for (Map.Entry<String, String> section : sections.entrySet()) {
                Paragraph sectionTitle = new Paragraph(section.getKey(), sectionTitleFont);
                sectionTitle.setSpacingBefore(SPACING_BEFORE_SECTION);
                sectionTitle.setSpacingAfter(SPACING_AFTER_SECTION);
                document.add(sectionTitle);
                Paragraph sectionContent = new Paragraph(section.getValue(), contentFont);
                sectionContent.setSpacingAfter(SPACING_AFTER_SECTION);
                document.add(sectionContent);
            }
            return baos.toByteArray();
        } finally {
            if (document.isOpen()) {
                document.close();
            }
        }
    }
}
