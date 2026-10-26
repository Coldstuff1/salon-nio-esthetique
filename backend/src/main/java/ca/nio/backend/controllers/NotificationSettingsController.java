package ca.nio.backend.controllers;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import ca.nio.backend.model.NotificationChannel;
import ca.nio.backend.model.NotificationPreference;
import ca.nio.backend.model.NotificationType;
import ca.nio.backend.services.CurrentUserService;
import ca.nio.backend.services.NotificationSettingsService;
import ca.nio.backend.services.NotificationSettingsService.ChannelSetting;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;

/**
 * Which notifications the signed-in person gets by email and push, for the types their roles get. The in-app
 * list always has everything.
 */
@RestController
@RequestMapping("/api/me/notification-settings")
@PreAuthorize("hasAnyRole('CLIENT', 'EMPLOYEE', 'ADMIN')")
@RequiredArgsConstructor
public class NotificationSettingsController {

    private final NotificationSettingsService settings;

    @GetMapping
    public List<ChannelSetting> settings(@AuthenticationPrincipal Jwt jwt) {
        return settings.settings(CurrentUserService.userId(jwt), CurrentUserService.appRoles(jwt));
    }

    /** Changes the channels listed and returns every setting; the others stay as they were. */
    @PutMapping
    public List<ChannelSetting> update(@AuthenticationPrincipal Jwt jwt,
                                       @RequestBody @Size(max = 50) List<@Valid ChannelChoice> choices) {
        return settings.update(CurrentUserService.userId(jwt), CurrentUserService.appRoles(jwt), choices.stream()
                .map(choice -> new NotificationPreference(choice.type(), choice.channel(), choice.enabled()))
                .toList());
    }

    public record ChannelChoice(@NotNull NotificationType type, @NotNull NotificationChannel channel,
                                @NotNull Boolean enabled) {
    }

}
