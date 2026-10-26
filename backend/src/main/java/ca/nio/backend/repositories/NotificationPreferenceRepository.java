package ca.nio.backend.repositories;

import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import ca.nio.backend.model.NotificationChannel;
import ca.nio.backend.model.NotificationPreference;
import ca.nio.backend.model.NotificationType;

import lombok.RequiredArgsConstructor;

/** Plain SQL: a preference is a value keyed by person, type and channel, not an entity with an id of its own. */
@Repository
@RequiredArgsConstructor
public class NotificationPreferenceRepository {

    private static final RowMapper<NotificationPreference> ROW = (rs, row) -> new NotificationPreference(
            NotificationType.valueOf(rs.getString("type")), NotificationChannel.valueOf(rs.getString("channel")),
            rs.getBoolean("enabled"));

    private final JdbcClient jdbc;

    public List<NotificationPreference> findByRecipientId(UUID recipientId) {
        return jdbc.sql("SELECT type, channel, enabled FROM notification_preference WHERE recipient_id = ?")
                .param(recipientId)
                .query(ROW).list();
    }

    public List<NotificationPreference> findByRecipientIdAndType(UUID recipientId, NotificationType type) {
        return jdbc.sql("""
                        SELECT type, channel, enabled FROM notification_preference
                        WHERE recipient_id = ? AND type = ?""")
                .params(recipientId, type.name())
                .query(ROW).list();
    }

    public void save(UUID recipientId, NotificationPreference preference) {
        jdbc.sql("""
                        INSERT INTO notification_preference (recipient_id, type, channel, enabled, updated_at)
                        VALUES (?, ?, ?, ?, now())
                        ON CONFLICT (recipient_id, type, channel) DO UPDATE SET
                            enabled = EXCLUDED.enabled,
                            updated_at = now()""")
                .params(recipientId, preference.type().name(), preference.channel().name(), preference.enabled())
                .update();
    }

    public void delete(UUID recipientId, NotificationType type, NotificationChannel channel) {
        jdbc.sql("DELETE FROM notification_preference WHERE recipient_id = ? AND type = ? AND channel = ?")
                .params(recipientId, type.name(), channel.name())
                .update();
    }

}
