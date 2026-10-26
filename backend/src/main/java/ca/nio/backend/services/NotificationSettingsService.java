package ca.nio.backend.services;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;

import ca.nio.backend.model.NotificationChannel;
import ca.nio.backend.model.NotificationPreference;
import ca.nio.backend.model.NotificationType;
import ca.nio.backend.repositories.NotificationPreferenceRepository;

/**
 * Which channels each person gets each type of notification over: the type's defaults, changed by their own
 * choices (the settings page, or an email's unsubscribe link). Only choices that differ from the default are
 * stored, so changing a default reaches everyone who didn't choose.
 */
@Service
public class NotificationSettingsService {

    /** Signature bytes kept in an unsubscribe token: plenty against guessing, short enough for a URL. */
    private static final int SIGNATURE_BYTES = 16;
    private static final Base64.Encoder BASE64URL = Base64.getUrlEncoder().withoutPadding();

    private final NotificationPreferenceRepository preferences;
    private final SecretKeySpec unsubscribeKey;

    /**
     * @param enabled   what the person gets now
     * @param byDefault what they'd get had they never chosen
     */
    public record ChannelSetting(NotificationType type, NotificationChannel channel, boolean enabled,
                                 boolean byDefault) {
    }

    public NotificationSettingsService(NotificationPreferenceRepository preferences,
                                       NotificationProperties properties) {
        Assert.hasText(properties.unsubscribeSecret(), "salon.notifications.unsubscribe-secret is required");
        this.preferences = preferences;
        this.unsubscribeKey = new SecretKeySpec(properties.unsubscribeSecret().getBytes(StandardCharsets.UTF_8),
                "HmacSHA256");
    }

    /** Where one notification of this type goes for this person. */
    public Set<NotificationChannel> channels(UUID recipientId, NotificationType type) {
        Set<NotificationChannel> channels = EnumSet.noneOf(NotificationChannel.class);
        channels.addAll(type.defaultChannels());
        for (NotificationPreference preference : preferences.findByRecipientIdAndType(recipientId, type)) {
            if (preference.enabled()) {
                channels.add(preference.channel());
            } else {
                channels.remove(preference.channel());
            }
        }
        return channels;
    }

    /** Every type someone with these roles can get, by every channel, in the enum's order. */
    @Transactional(readOnly = true)
    public List<ChannelSetting> settings(UUID recipientId, Collection<String> roles) {
        List<NotificationPreference> chosen = preferences.findByRecipientId(recipientId);
        return Arrays.stream(NotificationType.values())
                .filter(type -> type.concerns(roles))
                .flatMap(type -> Arrays.stream(NotificationChannel.values()).map(channel -> {
                    boolean byDefault = type.defaultChannels().contains(channel);
                    boolean enabled = chosen.stream()
                            .filter(p -> p.type() == type && p.channel() == channel)
                            .map(NotificationPreference::enabled)
                            .findFirst().orElse(byDefault);
                    return new ChannelSetting(type, channel, enabled, byDefault);
                }))
                .toList();
    }

    /** Applies the person's choices; a type their roles never get is refused. */
    @Transactional
    public List<ChannelSetting> update(UUID recipientId, Collection<String> roles,
                                       List<NotificationPreference> changes) {
        for (NotificationPreference change : changes) {
            if (!change.type().concerns(roles)) {
                throw new InvalidRequestException(ErrorCode.INVALID_REQUEST,
                        "You don't get " + change.type() + " notifications");
            }
            choose(recipientId, change);
        }
        return settings(recipientId, roles);
    }

    /**
     * The token in an email's unsubscribe link: who and which type, signed so it can stop those emails without
     * signing in. It never expires; changing the secret voids every link already sent.
     */
    public String unsubscribeToken(UUID recipientId, NotificationType type) {
        String subject = recipientId + "." + type;
        return subject + "." + BASE64URL.encodeToString(sign(subject));
    }

    /** Stops the emails an unsubscribe link is about; using it again changes nothing. */
    @Transactional
    public NotificationType unsubscribe(String token) {
        int last = token.lastIndexOf('.');
        int first = token.indexOf('.');
        try {
            if (first < 0 || first == last) {
                throw new IllegalArgumentException("Malformed");
            }
            String subject = token.substring(0, last);
            byte[] signature = Base64.getUrlDecoder().decode(token.substring(last + 1));
            if (!MessageDigest.isEqual(signature, sign(subject))) {
                throw new IllegalArgumentException("Bad signature");
            }
            UUID recipientId = UUID.fromString(token.substring(0, first));
            NotificationType type = NotificationType.valueOf(token.substring(first + 1, last));
            choose(recipientId, new NotificationPreference(type, NotificationChannel.EMAIL, false));
            return type;
        } catch (IllegalArgumentException e) {
            throw new InvalidRequestException(ErrorCode.INVALID_UNSUBSCRIBE_LINK, "Invalid unsubscribe link");
        }
    }

    private void choose(UUID recipientId, NotificationPreference choice) {
        if (choice.enabled() == choice.type().defaultChannels().contains(choice.channel())) {
            preferences.delete(recipientId, choice.type(), choice.channel());
        } else {
            preferences.save(recipientId, choice);
        }
    }

    private byte[] sign(String subject) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(unsubscribeKey);
            return Arrays.copyOf(mac.doFinal(subject.getBytes(StandardCharsets.UTF_8)), SIGNATURE_BYTES);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

}
