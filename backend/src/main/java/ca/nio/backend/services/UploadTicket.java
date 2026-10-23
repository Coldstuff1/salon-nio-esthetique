package ca.nio.backend.services;

import java.net.URI;
import java.time.Instant;
import java.util.UUID;

/**
 * Where and until when the browser may PUT the file. It must send the same {@code Content-Type} it declared.
 */
public record UploadTicket(UUID mediaId, URI uploadUrl, Instant expiresAt) {
}
