package ca.nio.backend.services;

import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.Locale;
import java.util.Map;

import org.jspecify.annotations.Nullable;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Component;

import ca.nio.backend.model.Notification;
import ca.nio.backend.model.NotificationType;

import lombok.RequiredArgsConstructor;

/**
 * Notification text for what the backend sends itself (emails), from {@code messages*.properties}. The site
 * renders its in-app list from its own translations; keep the two saying the same thing.
 */
@Component
@RequiredArgsConstructor
public class NotificationMessages {

    private final MessageSource messages;
    private final NotificationService notifications;
    private final SalonProperties salon;

    /** @param action label of the link to the notification's page; null when it has none */
    public record Text(String title, String body, @Nullable String action) {
    }

    /** {@code fr} or {@code en} as stored for recipients; Canadian formats either way. */
    public static Locale locale(@Nullable String language) {
        return "en".equals(language) ? Locale.forLanguageTag("en-CA") : Locale.CANADA_FRENCH;
    }

    public Text text(Notification notification, Locale locale) {
        NotificationType type = notification.getType();
        Map<String, String> params = notifications.params(notification);
        String prefix = "notification." + type + ".";
        String body = switch (type) {
            case BOOKING_CONFIRMED, BOOKING_CANCELLED, BOOKING_REMINDER -> message(prefix + "body", locale,
                    params.get("employeeName"), date(params, locale), time(params, locale));
            case STAFF_BOOKING_CREATED, STAFF_BOOKING_CANCELLED, STAFF_BOOKING_OPEN -> message(prefix + "body", locale,
                    params.get("customerName"), date(params, locale), time(params, locale));
            case STAFF_DAILY_AGENDA -> message(prefix + "body", locale, params.get("count"), time(params, locale));
            case LATE_CANCELLATION -> message(prefix + "body", locale, params.get("customerName"),
                    params.get("employeeName"), date(params, locale), time(params, locale));
            case BUG_REPORT_SUBMITTED -> message(prefix + "body", locale, params.get("reporterName"),
                    params.get("title"));
            case BUG_REPORT_CLOSED -> message(prefix + "body." + params.get("status"), locale, params.get("title"));
        };
        String action = notification.getLink() == null ? null : message(prefix + "action", locale);
        return new Text(message(prefix + "title", locale), body, action);
    }

    public String message(String code, Locale locale, @Nullable Object... args) {
        return messages.getMessage(code, args, locale);
    }

    /** "jeudi 29 octobre 2026" / "Thursday, October 29, 2026", in the salon's time zone. */
    private String date(Map<String, String> params, Locale locale) {
        return startsAt(params).format(DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL).withLocale(locale));
    }

    /** "9 h 30" / "9:30 a.m.", in the salon's time zone. */
    private String time(Map<String, String> params, Locale locale) {
        return startsAt(params).format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale));
    }

    private ZonedDateTime startsAt(Map<String, String> params) {
        return Instant.parse(params.get("startsAt")).atZone(salon.zone());
    }

}
