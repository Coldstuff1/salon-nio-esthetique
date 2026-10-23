package ca.nio.backend.model;

import java.time.Instant;
import java.util.UUID;

import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A photo or video in the object store. Other aggregates point at it by id (e.g. {@link Employee#getPhotoId()}).
 */
@Getter
@Setter
@NoArgsConstructor
public class Media {

    @Id
    private UUID id;
    private UUID ownerId;
    private String contentType;
    private long sizeBytes;
    private MediaStatus status;
    private Instant createdAt;
    @Version
    private Integer version;

    public Media(UUID ownerId, String contentType, long sizeBytes) {
        this.id = UUID.randomUUID();
        this.ownerId = ownerId;
        this.contentType = contentType;
        this.sizeBytes = sizeBytes;
        this.status = MediaStatus.PENDING;
        this.createdAt = Instant.now();
    }

    /** Derived from the id alone so URLs can be built without loading the row. */
    public static String objectKey(UUID mediaId) {
        return "media/" + mediaId;
    }

    public String objectKey() {
        return objectKey(id);
    }

}
