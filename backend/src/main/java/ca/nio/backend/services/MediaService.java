package ca.nio.backend.services;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import org.jspecify.annotations.Nullable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.unit.DataSize;

import ca.nio.backend.model.Media;
import ca.nio.backend.model.MediaStatus;
import ca.nio.backend.repositories.MediaRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Two-step uploads: {@link #requestUpload} hands out a presigned PUT, then the feature that uses the file
 * (e.g. the employee photo) calls {@link #claim} once the browser reports it is done.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MediaService {

    static final Duration PENDING_TTL = Duration.ofHours(24);

    private final MediaRepository media;
    private final MediaStorage storage;
    private final MediaProperties properties;

    @Transactional
    public UploadTicket requestUpload(UUID ownerId, String contentType, long sizeBytes) {
        DataSize max = maxSize(contentType);
        if (sizeBytes <= 0 || sizeBytes > max.toBytes()) {
            throw new InvalidRequestException(ErrorCode.FILE_SIZE,
                    "File must be between 1 byte and " + max.toMegabytes() + " MB", Map.of("maxMegabytes", max.toMegabytes()));
        }
        Media pending = media.save(new Media(ownerId, contentType, sizeBytes));
        return new UploadTicket(pending.getId(), storage.presignPut(pending.objectKey(), contentType, sizeBytes),
                Instant.now().plus(properties.uploadUrlTtl()));
    }

    /**
     * Verifies the caller's upload landed with the declared type and size and marks it ready.
     * Claiming media that is already ready is allowed so retries are harmless.
     */
    @Transactional
    public Media claim(UUID mediaId, UUID ownerId) {
        Media item = media.findById(mediaId)
                .filter(m -> m.getOwnerId().equals(ownerId))
                .orElseThrow(() -> new NotFoundException(ErrorCode.MEDIA_NOT_FOUND, "Media " + mediaId + " not found"));
        if (item.getStatus() == MediaStatus.READY) {
            return item;
        }
        var head = storage.head(item.objectKey())
                .orElseThrow(() -> new InvalidRequestException(ErrorCode.FILE_NOT_UPLOADED,
                        "Media " + mediaId + " has not been uploaded"));
        if (head.contentLength() != item.getSizeBytes() || !item.getContentType().equals(head.contentType())) {
            storage.delete(item.objectKey());
            media.delete(item);
            throw new InvalidRequestException(ErrorCode.FILE_MISMATCH,
                    "Uploaded file does not match the declared type and size");
        }
        item.setStatus(MediaStatus.READY);
        return media.save(item);
    }

    @Transactional
    public void delete(@Nullable UUID mediaId) {
        if (mediaId == null) {
            return;
        }
        media.findById(mediaId).ifPresent(item -> {
            media.delete(item);
            storage.delete(item.objectKey());
        });
    }

    /** A short-lived download URL, or null when there is no media. Only ready media is ever referenced. */
    public @Nullable URI url(@Nullable UUID mediaId) {
        return mediaId == null ? null : storage.presignGet(Media.objectKey(mediaId));
    }

    /** Uploads that were never claimed: drop the row and whatever bytes made it to storage. */
    @Scheduled(fixedDelayString = "PT1H", initialDelayString = "PT5M")
    @Transactional
    public int purgeStalePending() {
        var stale = media.findByStatusAndCreatedAtBefore(MediaStatus.PENDING, Instant.now().minus(PENDING_TTL));
        for (Media item : stale) {
            storage.delete(item.objectKey());
            media.delete(item);
        }
        if (!stale.isEmpty()) {
            log.info("Purged {} unclaimed uploads", stale.size());
        }
        return stale.size();
    }

    private DataSize maxSize(String contentType) {
        DataSize max = Map.of(
                "image/jpeg", properties.maxImageSize(),
                "image/png", properties.maxImageSize(),
                "image/webp", properties.maxImageSize(),
                "video/mp4", properties.maxVideoSize(),
                "video/webm", properties.maxVideoSize()).get(contentType);
        if (max == null) {
            throw new InvalidRequestException(ErrorCode.FILE_TYPE, "Unsupported media type " + contentType);
        }
        return max;
    }

}
