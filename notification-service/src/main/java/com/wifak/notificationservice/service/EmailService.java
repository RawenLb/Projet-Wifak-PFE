package com.wifak.notificationservice.service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.io.UnsupportedEncodingException;

@Service
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);

    private final JavaMailSender mailSender;
    private final TemplateEngine templateEngine;

    public EmailService(JavaMailSender mailSender, TemplateEngine templateEngine) {
        this.mailSender = mailSender;
        this.templateEngine = templateEngine;
    }

    /**
     * Envoie un email HTML de façon asynchrone.
     *
     * @param to           adresse destinataire
     * @param subject      objet de l'email
     * @param templateName nom du template Thymeleaf (sans extension)
     * @param context      variables Thymeleaf
     */
    @Async
    public void sendHtmlEmail(String to, String subject, String templateName, Context context) {
        try {
            String htmlBody = templateEngine.process(templateName, context);
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(htmlBody, true);
            helper.setFrom("rawene.labaoui@gmail.com", "noreply@wifak-bank.tn");

            mailSender.send(message);
            log.info("✉️  Email envoyé à {} — sujet : {}", to, subject);

        } catch (MessagingException e) {
            log.error("❌ Échec envoi email à {} : {}", to, e.getMessage(), e);
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException(e);
        }
    }
}