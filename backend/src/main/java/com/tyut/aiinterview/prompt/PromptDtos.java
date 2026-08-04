package com.tyut.aiinterview.prompt;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

public final class PromptDtos {
    private PromptDtos() {
    }

    public record PromptSummary(String code, String name, String category, String description,
                                Set<String> variables, Integer activeVersion, Integer latestVersion,
                                LocalDateTime activatedAt) {
    }

    public record VersionView(Long id, String code, String name, String category, Integer version,
                              String systemTemplate, String userTemplate, boolean active, String changeNote,
                              Long createdBy, LocalDateTime createdAt, LocalDateTime activatedAt) {
    }

    public record CreateVersionRequest(@NotBlank String systemTemplate, @NotBlank String userTemplate,
                                       @Size(max = 500) String changeNote, boolean activate) {
    }

    public record ActivationRequest(@Size(max = 500) String note) {
    }

    public record ActivationLogView(Long id, String code, Integer fromVersion, Integer toVersion,
                                    String action, String note, Long operatorId, LocalDateTime createdAt) {
    }

    public record PromptDetail(PromptSummary summary, List<VersionView> versions,
                               List<ActivationLogView> activationHistory) {
    }
}
