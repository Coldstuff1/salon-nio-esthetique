package ca.nio.backend.services;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import org.jspecify.annotations.Nullable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionOperations;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.unit.DataSize;

import ca.nio.backend.model.Media;
import ca.nio.backend.model.MediaStatus;
import ca.nio.backend.repositories.MediaRepository;

import lombok.extern.slf4j.Slf4j;

/**
 * Two-step uploads: {@link #requestUpload} hands out a presigned PUT, then the feature that uses the file
 * (e.g. the employee photo) calls {@link #claim} once the browser reports it is done.
 */
@Slf4j
@Service
public class MediaService {

    static final Duration PENDING_TTL = Duration.ofHours(24);
    /** Enough of the file to recognize every accepted format by its leading bytes. */
    static final int SIGNATURE_BYTES = 16;

    private final MediaRepository media;
    private final MediaStorage storage;
    private final MediaProperties properties;
    /** Commits on its own, so a rejected upload is gone even though the request that found it fails. */
    private final TransactionOperations separately;

    public MediaService(MediaRepository media, MediaStorage storage, MediaProperties properties,
                        PlatformTransactionManager transactions) {
        this.media = media;
        this.storage = storage;
        this.properties = properties;
        TransactionTemplate template = new TransactionTemplate(transactions);
        template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.separately = template;
    }

    /** Pass {@code allowVideo = false} for callers who may only send photos (clients). */
    @Transactional
    public UploadTicket requestUpload(UUID ownerId, String contentType, long sizeBytes, boolean allowVideo) {
        DataSize max = maxSize(contentType);
        if (!allowVideo && !isImage(contentType)) {
            throw new InvalidRequestException(ErrorCode.FILE_TYPE, "Only images are accepted, not " + contentType);
        }
        if (sizeBytes <= 0 || sizeBytes > max.toBytes()) {
            throw new InvalidRequestException(ErrorCode.FILE_SIZE,
                    "File must be between 1 byte and " + max.toMegabytes() + " MB", Map.of("maxMegabytes", max.toMegabytes()));
        }
        if (media.countByOwnerIdAndStatus(ownerId, MediaStatus.PENDING) >= properties.maxPendingUploads()) {
            throw new ConflictException(ErrorCode.TOO_MANY_UPLOADS, "Too many unfinished uploads; try again later",
                    Map.of("max", properties.maxPendingUploads()));
        }
        Media pending = media.save(new Media(ownerId, contentType, sizeBytes));
        return new UploadTicket(pending.getId(), storage.presignPut(pending.objectKey(), contentType, sizeBytes),
                Instant.now().plus(properties.uploadUrlTtl()));
    }

    /**
     * Verifies the caller's upload landed with the declared type and size, and that its bytes really are that
     * type, then marks it ready. Claiming media that is already ready is allowed so retries are harmless.
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
        if (head.contentLength() != item.getSizeBytes() || !item.getContentType().equals(head.contentType())
                || !hasSignature(item.getContentType(), storage.readPrefix(item.objectKey(), SIGNATURE_BYTES))) {
            storage.delete(item.objectKey());
            separately.executeWithoutResult(status -> media.delete(item));
            throw new InvalidRequestException(ErrorCode.FILE_MISMATCH,
                    "Uploaded file does not match the declared type and size");
        }
        item.setStatus(MediaStatus.READY);
        return media.save(item);
    }

    /** {@link #claim}, but only for photos: profile pictures and screenshots must not be videos. */
    @Transactional
    public Media claimImage(UUID mediaId, UUID ownerId) {
        Media item = media.findById(mediaId)
                .filter(m -> m.getOwnerId().equals(ownerId))
                .orElseThrow(() -> new NotFoundException(ErrorCode.MEDIA_NOT_FOUND, "Media " + mediaId + " not found"));
        if (!isImage(item.getContentType())) {
            throw new InvalidRequestException(ErrorCode.FILE_TYPE, "Media " + mediaId + " is not an image");
        }
        return claim(mediaId, ownerId);
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

    private static boolean isImage(String contentType) {
        return contentType.startsWith("image/");
    }

    /** Whether the file starts the way files of {@code contentType} do. */
    static boolean hasSignature(String contentType, byte[] head) {
        return switch (contentType) {
            case "image/jpeg" -> startsWith(head, 0, 0xFF, 0xD8, 0xFF);
            case "image/png" -> startsWith(head, 0, 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A);
            case "image/webp" -> startsWith(head, 0, 'R', 'I', 'F', 'F') && startsWith(head, 8, 'W', 'E', 'B', 'P');
            case "video/mp4" -> startsWith(head, 4, 'f', 't', 'y', 'p');
            case "video/webm" -> startsWith(head, 0, 0x1A, 0x45, 0xDF, 0xA3);
            default -> false;
        };
    }

    private static boolean startsWith(byte[] bytes, int offset, int... expected) {
        if (bytes.length < offset + expected.length) {
            return false;
        }
        for (int i = 0; i < expected.length; i++) {
            if ((bytes[offset + i] & 0xFF) != expected[i]) {
                return false;
            }
        }
        return true;
    }

}
