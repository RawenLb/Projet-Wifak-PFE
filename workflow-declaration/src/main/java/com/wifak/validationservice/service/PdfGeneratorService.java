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

    public byte[] generatePdfFromText(String content, String title) throws DocumentException, IOException {
        log.info("📄 Generating PDF: {}", title);
        Document document = new Document(PageSize.A4);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        try {
            PdfWriter.getInstance(document, baos);
            document.open();

            Font titleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 18, BaseColor.BLACK);
            Paragraph titleParagraph = new Paragraph(title, titleFont);
            titleParagraph.setAlignment(Element.ALIGN_CENTER);
            titleParagraph.setSpacingAfter(20);
            document.add(titleParagraph);

            Font contentFont = FontFactory.getFont(FontFactory.COURIER, 10, BaseColor.DARK_GRAY);
            document.add(new Paragraph(content, contentFont));
            document.close();
            return baos.toByteArray();
        } catch (Exception e) {
            log.error("❌ Error generating PDF", e);
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

            Font titleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 18, BaseColor.BLACK);
            Paragraph titleParagraph = new Paragraph(title, titleFont);
            titleParagraph.setAlignment(Element.ALIGN_CENTER);
            titleParagraph.setSpacingAfter(20);
            document.add(titleParagraph);

            Font sectionTitleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 14, BaseColor.BLUE);
            Font contentFont = FontFactory.getFont(FontFactory.COURIER, 10, BaseColor.DARK_GRAY);

            for (Map.Entry<String, String> section : sections.entrySet()) {
                Paragraph sectionTitle = new Paragraph(section.getKey(), sectionTitleFont);
                sectionTitle.setSpacingBefore(15);
                sectionTitle.setSpacingAfter(10);
                document.add(sectionTitle);
                Paragraph sectionContent = new Paragraph(section.getValue(), contentFont);
                sectionContent.setSpacingAfter(10);
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
