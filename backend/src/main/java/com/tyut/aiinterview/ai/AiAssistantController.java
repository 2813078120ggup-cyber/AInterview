package com.tyut.aiinterview.ai;

import com.tyut.aiinterview.common.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/ai-assistant")
@PreAuthorize("isAuthenticated()")
public class AiAssistantController {
    private final DeepSeekGateway deepSeekGateway;

    public AiAssistantController(DeepSeekGateway deepSeekGateway) {
        this.deepSeekGateway = deepSeekGateway;
    }

    @PostMapping("/chat")
    public ApiResponse<ChatResponse> chat(@Valid @RequestBody ChatRequest request) {
        String context = request.messages().stream()
                .map(item -> ("assistant".equals(item.role()) ? "教练：" : "候选人：") + item.content().trim())
                .reduce((left, right) -> left + "\n" + right).orElse("");
        return ApiResponse.ok(new ChatResponse(deepSeekGateway.interviewCoach(context)));
    }

    public record ChatRequest(@Size(min = 1, max = 12) List<@Valid ChatMessage> messages) {}
    public record ChatMessage(
            @NotBlank @Pattern(regexp = "user|assistant") String role,
            @NotBlank @Size(max = 2000) String content) {}
    public record ChatResponse(String reply) {}
}
