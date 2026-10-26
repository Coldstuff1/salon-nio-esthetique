package ca.nio.backend.services;

import java.io.UnsupportedEncodingException;
import java.util.Locale;
import java.util.Map;

import org.jspecify.annotations.Nullable;
import org.springframework.mail.MailPreparationException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import ca.nio.backend.model.Notification;
import ca.nio.backend.model.NotificationChannel;
import ca.nio.backend.repositories.CustomerRepository;
import ca.nio.backend.repositories.NotificationRecipientRepository;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;

import lombok.RequiredArgsConstructor;

/**
 * Emails a notification in the recipient's language, as HTML ({@code templates/email/notification.html}) with a
 * plain-text alternative.
 */
@Component
@RequiredArgsConstructor
public class EmailSender implements ChannelSender {

    private final JavaMailSender mail;
    private final TemplateEngine templates;
    private final NotificationMessages messages;
    private final NotificationRecipientRepository recipients;
    private final CustomerRepository customers;
    private final NotificationProperties properties;
    private final NotificationSettingsService settings;

    private record Contact(@Nullable String email, String name, Locale locale) {
    }

    @Override
    public NotificationChannel channel() {
        return NotificationChannel.EMAIL;
    }

    @Override
    public void send(Notification notification) {
        Contact contact = contact(notification);
        if (contact.email() == null || contact.email().isBlank()) {
            throw new UndeliverableException("No email address for " + notification.getRecipientId());
        }
        NotificationMessages.Text text = messages.text(notification, contact.locale());
        String greeting = messages.message("email.greeting", contact.locale(), contact.name());
        String footer = messages.message("email.footer", contact.locale());
        String url = notification.getLink() == null ? null : siteUrl(notification.getLink());
        String token = settings.unsubscribeToken(notification.getRecipientId(), notification.getType());
        String unsubscribeUrl = UriComponentsBuilder.fromUri(properties.siteUrl()).path("/unsubscribe")
                .queryParam("token", token).build().toUriString();
        String settingsUrl = siteUrl("/notifications/settings");
        String unsubscribe = messages.message("email.unsubscribe", contact.locale());
        String manage = messages.message("email.settings", contact.locale());

        Context context = new Context(contact.locale(), Map.of(
                "lang", contact.locale().getLanguage(),
                "greeting", greeting,
                "title", text.title(),
                "body", text.body(),
                "footer", footer,
                "unsubscribe", unsubscribe,
                "unsubscribeUrl", unsubscribeUrl,
                "settings", manage,
                "settingsUrl", settingsUrl));
        if (url != null) {
            context.setVariable("url", url);
            context.setVariable("action", text.action());
        }
        String html = templates.process("email/notification", context);
        String plain = greeting + "\n\n" + text.body() + "\n\n"
                + (url == null ? "" : text.action() + "\n" + url + "\n\n")
                + "-- \n" + footer + "\n"
                + unsubscribe + "\n" + unsubscribeUrl + "\n"
                + manage + "\n" + settingsUrl + "\n";

        MimeMessage message = mail.createMimeMessage();
        try {
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(properties.fromAddress(), properties.fromName());
            helper.setTo(contact.email());
            helper.setSubject(text.title());
            helper.setText(plain, html);
            // tells mail servers and vacation responders not to answer it
            message.setHeader("Auto-Submitted", "auto-generated");
            // mail apps show their own unsubscribe button, which posts here without opening anything (RFC 8058)
            message.setHeader("List-Unsubscribe", "<" + UriComponentsBuilder.fromUri(properties.siteUrl())
                    .path("/api/public/unsubscribe").queryParam("token", token).build().toUriString() + ">");
            message.setHeader("List-Unsubscribe-Post", "List-Unsubscribe=One-Click");
        } catch (MessagingException | UnsupportedEncodingException e) {
            throw new MailPreparationException(e);
        }
        mail.send(message);
    }

    private String siteUrl(String path) {
        return UriComponentsBuilder.fromUri(properties.siteUrl()).path(path).build().toUriString();
    }

    /** From the person's last visit; clients who never loaded the site since have their profile's email. */
    private Contact contact(Notification notification) {
        return recipients.findById(notification.getRecipientId())
                .map(r -> new Contact(r.getEmail(), r.getDisplayName(), NotificationMessages.locale(r.getLocale())))
                .or(() -> customers.findById(notification.getRecipientId())
                        .map(c -> new Contact(c.getEmail(), c.getDisplayName(), NotificationMessages.locale(null))))
                .orElseThrow(() -> new UndeliverableException("Nothing known about " + notification.getRecipientId()));
    }

}
