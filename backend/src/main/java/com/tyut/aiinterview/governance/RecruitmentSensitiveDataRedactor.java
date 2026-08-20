package com.tyut.aiinterview.governance;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class RecruitmentSensitiveDataRedactor {
    public static final String VERSION = "recruitment-redaction-v1";

    private static final List<Rule> RULES = List.of(
            new Rule("EMAIL", Pattern.compile("(?i)\\b[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}\\b"), "[已脱敏邮箱]"),
            new Rule("PHONE", Pattern.compile("(?<!\\d)(?:\\+?86[- ]?)?1[3-9]\\d{9}(?!\\d)"), "[已脱敏手机号]"),
            new Rule("ID_NUMBER", Pattern.compile("(?<![0-9A-Za-z])\\d{17}[0-9Xx](?![0-9A-Za-z])"), "[已脱敏证件号]"),
            new Rule("NAME", Pattern.compile("(?im)(姓名|name)\\s*[:：]\\s*[^\\s,，;；]{1,32}"), "$1：[已脱敏姓名]"),
            new Rule("NAME", Pattern.compile("(?im)(我叫|本人叫|my name is)\\s*[^\\s,，。.;；]{1,32}"), "$1[已脱敏姓名]"),
            new Rule("GENDER", Pattern.compile("(?im)(性别|gender)\\s*[:：]\\s*[^\\s,，;；]{1,16}"), "$1：[已脱敏]"),
            new Rule("AGE_BIRTH", Pattern.compile("(?im)(年龄|出生(?:日期|年月)?|生日|age|date of birth|dob)\\s*[:：]\\s*[^\\r\\n,，;；]{1,32}"), "$1：[已脱敏]"),
            new Rule("MARITAL_FAMILY", Pattern.compile("(?im)(婚姻(?:状况)?|婚育|生育|家庭状况|marital status|pregnan(?:t|cy))\\s*[:：]\\s*[^\\r\\n,，;；]{1,48}"), "$1：[已脱敏]"),
            new Rule("ETHNICITY", Pattern.compile("(?im)(民族|种族|ethnicity|race)\\s*[:：]\\s*[^\\r\\n,，;；]{1,32}"), "$1：[已脱敏]"),
            new Rule("RELIGION_POLITICS", Pattern.compile("(?im)(宗教(?:信仰)?|政治面貌|religion|political affiliation)\\s*[:：]\\s*[^\\r\\n,，;；]{1,48}"), "$1：[已脱敏]"),
            new Rule("ADDRESS", Pattern.compile("(?im)(家庭住址|现居住地|户籍(?:地址)?|详细地址|address)\\s*[:：]\\s*[^\\r\\n]{1,120}"), "$1：[已脱敏地址]"),
            new Rule("HEALTH_DISABILITY", Pattern.compile("(?im)(健康状况|残疾|疾病史|disability|health status)\\s*[:：]\\s*[^\\r\\n,，;；]{1,80}"), "$1：[已脱敏]"),
            new Rule("PHOTO", Pattern.compile("(?im)(照片|头像|photo)\\s*[:：]\\s*[^\\r\\n,，;；]{1,120}"), "$1：[已移除]"));

    private static final Pattern SENSITIVE_KEY = Pattern.compile(
            "(?i)^(name|realName|fullName|candidateName|userName|姓名|真实姓名|gender|sex|性别|age|年龄|birth.*|birthday|出生.*|生日|marital.*|pregnan.*|婚姻.*|婚育.*|ethnicity|race|民族|种族|religion|宗教.*|political.*|政治面貌|address|.*Address|地址|住址|phone|phoneNumber|mobile|mobileNumber|电话|手机号|email|emailAddress|contactEmail|邮箱|idCard|identity.*|证件号|身份证.*|photo|avatar|照片|头像|health.*|medical.*|disability|健康.*|残疾.*)$");

    private final ObjectMapper objectMapper;

    public RecruitmentSensitiveDataRedactor(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public Result redact(String input) {
        if (!StringUtils.hasText(input)) return new Result(input, List.of(), 0, VERSION);
        String value = input;
        Set<String> categories = new LinkedHashSet<>();
        int count = 0;
        for (Rule rule : RULES) {
            Matcher matcher = rule.pattern().matcher(value);
            StringBuffer buffer = new StringBuffer();
            boolean found = false;
            while (matcher.find()) {
                found = true;
                count++;
                matcher.appendReplacement(buffer, rule.replacement());
            }
            if (found) {
                matcher.appendTail(buffer);
                value = buffer.toString();
                categories.add(rule.category());
            }
        }
        return new Result(value, List.copyOf(categories), count, VERSION);
    }

    public Result redactJson(String json) {
        if (!StringUtils.hasText(json)) return new Result(json, List.of(), 0, VERSION);
        try {
            JsonNode root = objectMapper.readTree(json);
            MutableSummary summary = new MutableSummary();
            JsonNode redacted = redactNode(root, summary);
            return new Result(objectMapper.writeValueAsString(redacted), List.copyOf(summary.categories),
                    summary.count, VERSION);
        } catch (JsonProcessingException ignored) {
            return redact(json);
        }
    }

    private JsonNode redactNode(JsonNode node, MutableSummary summary) {
        if (node == null || node.isNull()) return node;
        if (node.isObject()) {
            ObjectNode result = objectMapper.createObjectNode();
            node.fields().forEachRemaining(entry -> {
                if (SENSITIVE_KEY.matcher(entry.getKey()).matches()) {
                    result.put(entry.getKey(), "[已脱敏]");
                    summary.categories.add("STRUCTURED_" + entry.getKey().toUpperCase(Locale.ROOT));
                    summary.count++;
                } else {
                    result.set(entry.getKey(), redactNode(entry.getValue(), summary));
                }
            });
            return result;
        }
        if (node.isArray()) {
            ArrayNode result = objectMapper.createArrayNode();
            node.forEach(item -> result.add(redactNode(item, summary)));
            return result;
        }
        if (node.isTextual()) {
            Result redacted = redact(node.asText());
            summary.categories.addAll(redacted.categories());
            summary.count += redacted.replacementCount();
            return objectMapper.getNodeFactory().textNode(redacted.value());
        }
        return node.deepCopy();
    }

    public record Result(String value, List<String> categories, int replacementCount, String version) {
        public boolean detected() {
            return replacementCount > 0;
        }

        public String summary() {
            return detected() ? "已按 " + version + " 处理 " + replacementCount + " 处敏感字段（"
                    + String.join("、", categories) + "）" : "未检测到需脱敏字段（" + version + "）";
        }
    }

    private record Rule(String category, Pattern pattern, String replacement) {
    }

    private static final class MutableSummary {
        private final Set<String> categories = new LinkedHashSet<>();
        private int count;
    }
}
