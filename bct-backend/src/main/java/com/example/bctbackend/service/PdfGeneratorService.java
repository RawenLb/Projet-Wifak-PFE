package com.example.bctbackend.service;

import com.itextpdf.text.*;
import com.itextpdf.text.pdf.PdfWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;


@Service
public class PdfGeneratorService {

    private static final Logger log = LoggerFactory.getLogger(PdfGeneratorService.class);

    /**
     * ✅ Générer un PDF à partir d'un contenu texte
     */
    public byte[] generatePdfFromText(String content, String title) throws DocumentException, IOException {
        log.info("📄 Generating PDF: {}", title);

        Document document = new Document(PageSize.A4);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        try {
            PdfWriter.getInstance(document, baos);
            document.open();

            // ✅ Ajouter un titre
            Font titleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 18, BaseColor.BLACK);
            Paragraph titleParagraph = new Paragraph(title, titleFont);
            titleParagraph.setAlignment(Element.ALIGN_CENTER);
            titleParagraph.setSpacingAfter(20);
            document.add(titleParagraph);

            // ✅ Ajouter le contenu
            Font contentFont = FontFactory.getFont(FontFactory.COURIER, 10, BaseColor.DARK_GRAY);
            Paragraph contentParagraph = new Paragraph(content, contentFont);
            document.add(contentParagraph);

            document.close();

            log.info("✅ PDF generated successfully");
            return baos.toByteArray();

        } catch (Exception e) {
            log.error("❌ Error generating PDF", e);
            throw e;
        }
    }

    /**
     * ✅ Générer un PDF formaté avec sections
     */
    public byte[] generatePdfWithSections(String title, java.util.Map<String, String> sections)
            throws DocumentException, IOException {
        log.info("📄 Generating sectioned PDF: {}", title);

        Document document = new Document(PageSize.A4);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        try {
            PdfWriter.getInstance(document, baos);
            document.open();

            // Titre principal
            Font titleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 18, BaseColor.BLACK);
            Paragraph titleParagraph = new Paragraph(title, titleFont);
            titleParagraph.setAlignment(Element.ALIGN_CENTER);
            titleParagraph.setSpacingAfter(20);
            document.add(titleParagraph);

            // Sections
            Font sectionTitleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 14, BaseColor.BLUE);
            Font contentFont = FontFactory.getFont(FontFactory.COURIER, 10, BaseColor.DARK_GRAY);

            for (java.util.Map.Entry<String, String> section : sections.entrySet()) {
                // Titre de section
                Paragraph sectionTitle = new Paragraph(section.getKey(), sectionTitleFont);
                sectionTitle.setSpacingBefore(15);
                sectionTitle.setSpacingAfter(10);
                document.add(sectionTitle);

                // Contenu de section
                Paragraph sectionContent = new Paragraph(section.getValue(), contentFont);
                sectionContent.setSpacingAfter(10);
                document.add(sectionContent);
            }

            document.close();

            log.info("✅ Sectioned PDF generated successfully");
            return baos.toByteArray();

        } catch (Exception e) {
            log.error("❌ Error generating sectioned PDF", e);
            throw e;
        }
    }
}