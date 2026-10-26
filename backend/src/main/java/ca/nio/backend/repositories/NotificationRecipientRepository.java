package ca.nio.backend.repositories;

import java.util.List;
import java.util.UUID;

import org.jspecify.annotations.Nullable;
import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.Repository;

import ca.nio.backend.model.NotificationRecipient;

public interface NotificationRecipientRepository extends Repository<NotificationRecipient, UUID> {

    /** Inserts or refreshes someone's contact details; a null locale keeps the one stored (French at first). */
    @Modifying
    @Query("""
            INSERT INTO notification_recipient (id, email, display_name, roles, locale, updated_at)
            VALUES (:id, :email, :displayName, :roles, COALESCE(:locale, 'fr'), now())
            ON CONFLICT (id) DO UPDATE SET
                email = EXCLUDED.email,
                display_name = EXCLUDED.display_name,
                roles = EXCLUDED.roles,
                locale = COALESCE(:locale, notification_recipient.locale),
                updated_at = now()""")
    int upsert(UUID id, @Nullable String email, String displayName, String roles, @Nullable String locale);

    /** Everyone whose last token carried the role, e.g. {@code admin}. */
    @Query("SELECT * FROM notification_recipient WHERE ',' || roles || ',' LIKE '%,' || :role || ',%'")
    List<NotificationRecipient> findByRole(String role);

}
