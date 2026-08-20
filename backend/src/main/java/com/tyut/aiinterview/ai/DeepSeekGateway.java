package com.tyut.aiinterview.ai;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tyut.aiinterview.config.DeepSeekProperties;
import com.tyut.aiinterview.domain.AiGenerationRecord;
import com.tyut.aiinterview.governance.RecruitmentAiGovernanceService;
import com.tyut.aiinterview.prompt.PromptCatalog;
import com.tyut.aiinterview.prompt.PromptTemplateService;
import com.tyut.aiinterview.settings.AiProviderService;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Autowired;

@Component
public class DeepSeekGateway {
    private static final String PROVIDER = "deepseek";

    private final DeepSeekProperties properties;
    private final ObjectMapper objectMapper;
    private final PromptTemplateService promptTemplates;
    private final AiGenerationAuditService auditService;
    private final AiProviderService aiProviderService;
    private final RecruitmentAiGovernanceService governanceService;
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(20)).build();

    public DeepSeekGateway(DeepSeekProperties properties, ObjectMapper objectMapper,
                           PromptTemplateService promptTemplates, AiGenerationAuditService auditService,
                           AiProviderService aiProviderService) {
        this(properties, objectMapper, promptTemplates, auditService, aiProviderService, null);
    }

    @Autowired
    public DeepSeekGateway(DeepSeekProperties properties, ObjectMapper objectMapper,
                           PromptTemplateService promptTemplates, AiGenerationAuditService auditService,
                           AiProviderService aiProviderService, RecruitmentAiGovernanceService governanceService) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.promptTemplates = promptTemplates;
        this.auditService = auditService;
        this.aiProviderService = aiProviderService;
        this.governanceService = governanceService;
    }

    public String followUp(String originalQuestion, String answer) {
        return followUp(originalQuestion, answer, "big-tech");
    }

    public String followUp(String originalQuestion, String answer, String interviewerStyle) {
        return followUp(originalQuestion, answer, interviewerStyle,
                AiGenerationContext.standalone("FOLLOW_UP")).content();
    }

    public Generated<String> followUp(String originalQuestion, String answer, String interviewerStyle,
                                      AiGenerationContext context) {
        PromptTemplateService.RenderedPrompt prompt = promptTemplates.render(PromptCatalog.SIMULATION_FOLLOW_UP,
                Map.of("interviewerStyle", stylePrompt(interviewerStyle), "originalQuestion", originalQuestion,
                        "answer", answer));
        return executeText(prompt, context, null, 90);
    }

    public String openingQuestion(String question) {
        return openingQuestion(question, "big-tech");
    }

    public String openingQuestion(String question, String interviewerStyle) {
        return openingQuestion(question, interviewerStyle,
                AiGenerationContext.standalone("OPENING")).content();
    }

    public Generated<String> openingQuestion(String question, String interviewerStyle,
                                              AiGenerationContext context) {
        PromptTemplateService.RenderedPrompt prompt = promptTemplates.render(PromptCatalog.SIMULATION_OPENING,
                Map.of("interviewerStyle", stylePrompt(interviewerStyle), "question", question));
        return executeText(prompt, context, null, 90);
    }

    public JsonNode generateTrainingPlan(String reportContext) {
        String prompt = """
                请基于候选人的面试报告生成一份个性化训练计划。
                要求具体、可执行，重点补齐最低分能力项，不要空泛鼓励。

                报告数据：%s

                仅返回 JSON，不要使用 Markdown 或代码块：
                {
                  "priority":"当前最优先提升的一句话结论",
                  "durationDays":7,
                  "focusAreas":["最多4个训练重点"],
                  "dailyPlan":[{"day":1,"title":"训练主题","tasks":["2到3个具体任务"]}],
                  "recommendedBanks":["推荐题库或训练方向"],
                  "interviewDrills":["推荐模拟面试练习方式"],
                  "successCriteria":["完成训练后的可衡量标准"]
                }
                """.formatted(reportContext);
        return executeJson(inlinePrompt("training.plan.inline",
                        "你是资深面试训练教练，擅长把评测报告转化为 7 天训练计划，输出必须是合法 JSON。", prompt),
                AiGenerationContext.standalone("TRAINING_PLAN"), null, 90).content();
    }

    public String interviewCoach(String conversation) {
        String instruction = """
                你是 InterviewOS 的 AI 面试教练，服务对象是正在准备技术、产品、运营或通用职场面试的候选人。
                你的任务是帮助用户理解面试问题、组织回答结构、发现知识盲点、进行一轮模拟追问，以及把经历表达得更清晰。
                必须使用中文，语气专业、直接、友善；优先给出可执行的框架、示例表达或练习步骤。
                不要冒充正在进行正式考核的面试官，不要虚构用户经历，不要声称能保证录用。
                当问题涉及代码、系统设计或专业知识时，先给思路和关键点，再用简短示例说明；不要只给结论。
                当用户要求代写、作弊或规避真实考核时，拒绝该部分，并转为提供学习与表达建议。
                单次回答控制在 300 个中文字符以内；如需追问，每次只问一个最关键的问题。
                """;
        return executeText(inlinePrompt("coach.chat.inline", instruction, conversation),
                AiGenerationContext.standalone("COACH_CHAT"), null, 90).content();
    }

    public GovernanceTarget currentGovernanceTarget(String promptCode) {
        ResolvedProvider provider = resolveProvider();
        return new GovernanceTarget(provider.code(), provider.model(), promptTemplates.activeVersionNo(promptCode),
                provider.configured());
    }

    public JsonNode evaluateAnswer(String question, String referenceAnswer, String candidateAnswer) {
        return evaluateAnswer(question, referenceAnswer, candidateAnswer,
                AiGenerationContext.standalone("ANSWER_EVALUATION"), null).content();
    }

    public Generated<JsonNode> evaluateAnswer(String question, String referenceAnswer, String candidateAnswer,
                                               AiGenerationContext context, Integer fixedVersion) {
        Map<String, Object> variables = Map.of("question", question,
                "referenceAnswer", blankToDefault(referenceAnswer, "无"),
                "candidateAnswer", blankToDefault(candidateAnswer, "未提交任何回答"));
        PromptTemplateService.RenderedPrompt prompt = fixedVersion == null
                ? promptTemplates.render(PromptCatalog.ANSWER_EVALUATION, variables)
                : promptTemplates.renderVersion(PromptCatalog.ANSWER_EVALUATION, fixedVersion, variables);
        return executeJson(prompt, context, 450, 60);
    }

    public JsonNode generateReport(String evaluationContext) {
        return generateReport(evaluationContext, AiGenerationContext.standalone("SIMULATION_REPORT"), null).content();
    }

    public Generated<JsonNode> generateReport(String evaluationContext, AiGenerationContext context,
                                              Integer fixedVersion) {
        Map<String, Object> variables = Map.of("evaluationContext", evaluationContext);
        PromptTemplateService.RenderedPrompt prompt = fixedVersion == null
                ? promptTemplates.render(PromptCatalog.SIMULATION_REPORT, variables)
                : promptTemplates.renderVersion(PromptCatalog.SIMULATION_REPORT, fixedVersion, variables);
        return executeJson(prompt, context, 900, 60);
    }

    public JsonNode analyzeResume(String resumeText, String targetRole) {
        return analyzeResume(resumeText, targetRole,
                AiGenerationContext.standalone("RESUME_ANALYSIS")).content();
    }

    public Generated<JsonNode> analyzeResume(String resumeText, String targetRole, AiGenerationContext context) {
        PromptTemplateService.RenderedPrompt prompt = promptTemplates.render(PromptCatalog.RESUME_ANALYSIS,
                Map.of("targetRole", blankToDefault(targetRole, "未指定，以简历内容为准"), "resumeText", resumeText));
        return executeJson(prompt, context, 900, 45);
    }

    public Generated<JsonNode> matchResumeToJob(String jobTitle, String jobDescription, String requirements,
                                                String skillTags, String resumeProfile, String resumeSkills,
                                                String resumeText, AiGenerationContext context) {
        Map<String, Object> variables = Map.ofEntries(
                Map.entry("jobTitle", blankToDefault(jobTitle, "未填写")),
                Map.entry("jobDescription", blankToDefault(jobDescription, "未填写")),
                Map.entry("requirements", blankToDefault(requirements, "未填写")),
                Map.entry("skillTags", blankToDefault(skillTags, "未填写")),
                Map.entry("resumeProfile", blankToDefault(resumeProfile, "未形成结构化画像")),
                Map.entry("resumeSkills", blankToDefault(resumeSkills, "未提取技能")),
                Map.entry("resumeText", blankToDefault(resumeText, "未提供可读文本")));
        PromptTemplateService.RenderedPrompt prompt = promptTemplates.render(PromptCatalog.RECRUITMENT_JOB_MATCH, variables);
        return executeJson(prompt, context, 700, 60);
    }

    public String generateFreeInterviewFollowUp(String resumeSummary, String transcript, int nextTurn) {
        return generateFreeInterviewFollowUp(resumeSummary, transcript, nextTurn,
                AiGenerationContext.standalone("FREE_FOLLOW_UP")).content();
    }

    public Generated<String> generateFreeInterviewFollowUp(String resumeSummary, String transcript, int nextTurn,
                                                           AiGenerationContext context) {
        PromptTemplateService.RenderedPrompt prompt = promptTemplates.render(PromptCatalog.FREE_INTERVIEW_FOLLOW_UP,
                Map.of("nextTurn", nextTurn, "resumeSummary", resumeSummary, "transcript", transcript));
        return executeText(prompt, context, 220, 10);
    }

    public JsonNode generateFreeInterviewReport(String resumeSummary, String transcript) {
        return generateFreeInterviewReport(resumeSummary, transcript,
                AiGenerationContext.standalone("FREE_REPORT")).content();
    }

    public Generated<JsonNode> generateFreeInterviewReport(String resumeSummary, String transcript,
                                                           AiGenerationContext context) {
        PromptTemplateService.RenderedPrompt prompt = promptTemplates.render(PromptCatalog.FREE_INTERVIEW_REPORT,
                Map.of("resumeSummary", resumeSummary, "transcript", transcript));
        return executeJson(prompt, context, null, 90);
    }

    private Generated<String> executeText(PromptTemplateService.RenderedPrompt prompt,
                                          AiGenerationContext context, Integer maxTokens, int timeoutSeconds) {
        ResolvedProvider provider = resolveProvider();
        RecruitmentAiGovernanceService.Permit permit = governanceService == null
                ? RecruitmentAiGovernanceService.Permit.ungoverned()
                : governanceService.authorize(context, prompt.code(), prompt.version(), provider.code(), provider.model(),
                        prompt.systemPrompt().length() + prompt.userPrompt().length(), maxTokens);
        AiGenerationRecord audit = governanceService == null
                ? auditService.start(context, prompt.code(), prompt.version(), provider.code(), provider.model(),
                        prompt.systemPrompt().length() + prompt.userPrompt().length())
                : auditService.start(context, prompt.code(), prompt.version(), provider.code(), provider.model(),
                        prompt.systemPrompt().length() + prompt.userPrompt().length(), permit);
        Integer httpStatus = null;
        try {
            RawCompletion completion = ask(provider, prompt.systemPrompt(), prompt.userPrompt(), false,
                    maxTokens, timeoutSeconds);
            httpStatus = completion.httpStatus();
            RecruitmentAiGovernanceService.Settlement settlement = governanceService == null
                    ? RecruitmentAiGovernanceService.Settlement.none()
                    : governanceService.settle(permit, audit.getRequestId(), completion.promptTokens(),
                            completion.completionTokens(), completion.totalTokens());
            if (governanceService == null) {
                auditService.success(audit, completion.content().length(), completion.promptTokens(),
                        completion.completionTokens(), completion.totalTokens(), completion.httpStatus());
            } else {
                auditService.success(audit, completion.content().length(), completion.promptTokens(),
                        completion.completionTokens(), completion.totalTokens(), completion.httpStatus(), settlement.actualCostUsd());
            }
            return new Generated<>(audit.getRequestId(), prompt.code(), prompt.version(), completion.content(),
                    provider.code(), provider.model());
        } catch (RuntimeException exception) {
            if (governanceService != null) governanceService.release(permit, audit.getRequestId());
            auditService.failure(audit, exception, status(exception, httpStatus));
            throw exception;
        }
    }

    private Generated<JsonNode> executeJson(PromptTemplateService.RenderedPrompt prompt,
                                            AiGenerationContext context, Integer maxTokens, int timeoutSeconds) {
        ResolvedProvider provider = resolveProvider();
        RecruitmentAiGovernanceService.Permit permit = governanceService == null
                ? RecruitmentAiGovernanceService.Permit.ungoverned()
                : governanceService.authorize(context, prompt.code(), prompt.version(), provider.code(), provider.model(),
                        prompt.systemPrompt().length() + prompt.userPrompt().length(), maxTokens);
        AiGenerationRecord audit = governanceService == null
                ? auditService.start(context, prompt.code(), prompt.version(), provider.code(), provider.model(),
                        prompt.systemPrompt().length() + prompt.userPrompt().length())
                : auditService.start(context, prompt.code(), prompt.version(), provider.code(), provider.model(),
                        prompt.systemPrompt().length() + prompt.userPrompt().length(), permit);
        Integer httpStatus = null;
        try {
            RawCompletion completion = ask(provider, prompt.systemPrompt(), prompt.userPrompt(), true,
                    maxTokens, timeoutSeconds);
            httpStatus = completion.httpStatus();
            String normalized = completion.content().trim().replaceFirst("^```(?:json)?\\s*", "")
                    .replaceFirst("\\s*```$", "");
            JsonNode result = objectMapper.readTree(normalized);
            if (!result.isObject()) throw new IllegalStateException("DeepSeek 返回的评测结果不是 JSON 对象");
            RecruitmentAiGovernanceService.Settlement settlement = governanceService == null
                    ? RecruitmentAiGovernanceService.Settlement.none()
                    : governanceService.settle(permit, audit.getRequestId(), completion.promptTokens(),
                            completion.completionTokens(), completion.totalTokens());
            if (governanceService == null) {
                auditService.success(audit, completion.content().length(), completion.promptTokens(),
                        completion.completionTokens(), completion.totalTokens(), completion.httpStatus());
            } else {
                auditService.success(audit, completion.content().length(), completion.promptTokens(),
                        completion.completionTokens(), completion.totalTokens(), completion.httpStatus(), settlement.actualCostUsd());
            }
            return new Generated<>(audit.getRequestId(), prompt.code(), prompt.version(), result,
                    provider.code(), provider.model());
        } catch (IOException exception) {
            IllegalStateException wrapped = new IllegalStateException("DeepSeek 返回的评测结果不是合法 JSON", exception);
            if (governanceService != null) governanceService.release(permit, audit.getRequestId());
            auditService.failure(audit, wrapped, httpStatus);
            throw wrapped;
        } catch (RuntimeException exception) {
            if (governanceService != null) governanceService.release(permit, audit.getRequestId());
            auditService.failure(audit, exception, status(exception, httpStatus));
            throw exception;
        }
    }

    private RawCompletion ask(ResolvedProvider provider, String instruction, String content, boolean jsonOutput,
                              Integer maxTokens, int timeoutSeconds) {
        if (!provider.configured()) {
            throw new IllegalStateException("未找到可用的大模型配置。请在系统设置中启用文字默认大模型并填写 API Key，或配置 DEEPSEEK_API_KEY");
        }
        try {
            String payload = objectMapper.writeValueAsString(new Request(provider.model(), List.of(
                    new Message("system", instruction), new Message("user", content)),
                    jsonOutput ? new ResponseFormat("json_object") : null, jsonOutput ? 0.2 : 0.7, maxTokens));
            HttpRequest request = HttpRequest.newBuilder(URI.create(provider.baseUrl().replaceAll("/$", "") + "/chat/completions"))
                    .timeout(Duration.ofSeconds(timeoutSeconds))
                    .header("Authorization", "Bearer " + provider.apiKey())
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(payload)).build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new ProviderException(response.statusCode(),
                        "DeepSeek API 请求失败（HTTP " + response.statusCode() + "）：" + response.body());
            }
            JsonNode result = objectMapper.readTree(response.body());
            String reply = result.path("choices").path(0).path("message").path("content").asText();
            if (reply.isBlank()) throw new ProviderException(response.statusCode(), "DeepSeek 未返回有效内容");
            JsonNode usage = result.path("usage");
            return new RawCompletion(reply, response.statusCode(), nullableInt(usage, "prompt_tokens"),
                    nullableInt(usage, "completion_tokens"), nullableInt(usage, "total_tokens"));
        } catch (IOException exception) {
            throw new IllegalStateException("调用 DeepSeek API 失败", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("调用 DeepSeek API 被中断", exception);
        }
    }

    private ResolvedProvider resolveProvider() {
        AiProviderService.RuntimeProvider stored = aiProviderService.defaultLlmProvider().orElse(null);
        if (stored != null && hasText(stored.apiKey()) && hasText(stored.baseUrl()) && hasText(stored.serviceId())) {
            return new ResolvedProvider(blankToDefault(stored.code(), PROVIDER), stored.baseUrl(),
                    stored.serviceId(), stored.apiKey(), true);
        }
        if (properties.configured()) {
            return new ResolvedProvider(PROVIDER, properties.baseUrl(), properties.model(), properties.apiKey(), true);
        }
        if (stored != null) {
            return new ResolvedProvider(blankToDefault(stored.code(), PROVIDER), stored.baseUrl(),
                    stored.serviceId(), stored.apiKey(), true);
        }
        return new ResolvedProvider(PROVIDER, properties.baseUrl(), properties.model(), properties.apiKey(),
                properties.enabled());
    }

    private PromptTemplateService.RenderedPrompt inlinePrompt(String code, String system, String user) {
        return new PromptTemplateService.RenderedPrompt(code, 0, system, user);
    }

    private Integer nullableInt(JsonNode node, String field) {
        return node.has(field) && node.get(field).canConvertToInt() ? node.get(field).asInt() : null;
    }

    private Integer status(RuntimeException exception, Integer fallback) {
        return exception instanceof ProviderException providerException ? providerException.statusCode() : fallback;
    }

    private String blankToDefault(String value, String defaultValue) {
        return value == null || value.isBlank() ? defaultValue : value;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private String stylePrompt(String style) {
        return switch (style == null ? "" : style.trim()) {
            case "gentle" -> "温和型：语气友好、降低压迫感，用引导式问题帮助候选人展开，但仍保持专业标准。";
            case "pressure" -> "压迫型：节奏更快、追问更尖锐，重点检验边界、漏洞和抗压表达，但不能羞辱候选人。";
            case "hr" -> "HR 综合面：关注动机、沟通、稳定性、团队协作、职业规划和行为事件。";
            case "project-deep" -> "项目深挖型：围绕项目背景、个人贡献、难点、取舍、数据结果和复盘持续追问。";
            case "campus-basic" -> "校招基础型：从基础概念和常见场景切入，适合应届生，问题清晰、难度逐步上升。";
            default -> "大厂技术面：标准正式、技术深挖，关注原理、复杂度、工程实践、异常场景和系统性思考。";
        };
    }

    public record Generated<T>(String requestId, String promptCode, int promptVersion, T content,
                               String providerCode, String model) {
        public Generated(String requestId, String promptCode, int promptVersion, T content) {
            this(requestId, promptCode, promptVersion, content, null, null);
        }
    }

    public record GovernanceTarget(String provider, String model, int promptVersion, boolean configured) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private record Request(String model, List<Message> messages,
                           @JsonProperty("response_format") ResponseFormat responseFormat,
                           Double temperature, @JsonProperty("max_tokens") Integer maxTokens) {}
    private record Message(String role, String content) {}
    private record ResponseFormat(String type) {}
    private record RawCompletion(String content, int httpStatus, Integer promptTokens,
                                 Integer completionTokens, Integer totalTokens) {}
    private record ResolvedProvider(String code, String baseUrl, String model, String apiKey, boolean enabled) {
        private boolean configured() {
            return enabled && baseUrl != null && !baseUrl.isBlank()
                    && model != null && !model.isBlank()
                    && apiKey != null && !apiKey.isBlank();
        }
    }

    private static final class ProviderException extends IllegalStateException {
        private final int statusCode;

        private ProviderException(int statusCode, String message) {
            super(message);
            this.statusCode = statusCode;
        }

        private int statusCode() {
            return statusCode;
        }
    }
}
