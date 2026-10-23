package ca.nio.backend.controllers;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import ca.nio.backend.services.CurrentUserService;
import ca.nio.backend.services.MediaService;
import ca.nio.backend.services.UploadTicket;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;

/**
 * Step one of any upload: get a presigned URL to PUT the file to, then pass the returned media id to the
 * endpoint that uses it (e.g. {@code PUT /api/staff/me/photo} or a bug report's screenshot). Clients may only
 * upload images.
 */
@RestController
@RequestMapping("/api/media")
@PreAuthorize("hasAnyRole('CLIENT', 'EMPLOYEE', 'ADMIN')")
@RequiredArgsConstructor
public class MediaController {

    private final MediaService mediaService;

    @PostMapping("/uploads")
    @ResponseStatus(HttpStatus.CREATED)
    public UploadTicket requestUpload(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody UploadRequest request) {
        List<String> roles = CurrentUserService.appRoles(jwt);
        boolean allowVideo = roles.contains("employee") || roles.contains("admin");
        return mediaService.requestUpload(CurrentUserService.userId(jwt), request.contentType(), request.sizeBytes(),
                allowVideo);
    }

    public record UploadRequest(@NotBlank String contentType, @Positive long sizeBytes) {
    }

}
