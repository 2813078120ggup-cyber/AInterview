package com.tyut.aiinterview.account;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public final class AccountNotificationPreferenceDtos {
    private AccountNotificationPreferenceDtos() {}

    public record ChannelAvailability(
            boolean siteAvailable,
            boolean emailAvailable,
            String emailUnavailableReason,
            boolean smsAvailable,
            String smsUnavailableReason) {
    }

    public record Preference(
            String eventType,
            String label,
            String description,
            String group,
            boolean siteEnabled,
            boolean emailEnabled,
            boolean smsEnabled,
            boolean siteForced,
            boolean emailForced,
            String sitePolicyReason,
            String emailPolicyReason,
            Integer version) {
    }

    public record Preferences(
            ChannelAvailability channels,
            List<Preference> preferences) {
    }

    public record UpdatePreference(
            @NotNull String eventType,
            @NotNull Boolean siteEnabled,
            @NotNull Boolean emailEnabled,
            @NotNull Boolean smsEnabled,
            @NotNull Integer version) {
    }

    public record UpdateRequest(@NotEmpty List<@Valid UpdatePreference> preferences) {
    }
}
