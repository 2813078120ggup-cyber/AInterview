package com.tyut.aiinterview.governance;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class RecruitmentSensitiveDataRedactorTest {
    private final RecruitmentSensitiveDataRedactor redactor = new RecruitmentSensitiveDataRedactor(new ObjectMapper());

    @Test
    void redactsRecruitmentSensitiveFieldsWithoutRemovingExperienceEvidence() {
        String input = """
                姓名：张伟
                性别：男
                出生日期：1993-02-01
                电话：13800138000
                邮箱：zhangwei@example.com
                身份证：110101199302011234
                家庭住址：北京市朝阳区测试路 1 号
                5 年 Java、Spring Boot 和 MySQL 项目经验。
                """;

        RecruitmentSensitiveDataRedactor.Result result = redactor.redact(input);

        assertThat(result.value()).doesNotContain("张伟", "男", "1993-02-01", "13800138000",
                "zhangwei@example.com", "110101199302011234", "北京市朝阳区测试路 1 号");
        assertThat(result.value()).contains("5 年 Java、Spring Boot 和 MySQL 项目经验");
        assertThat(result.categories()).contains("NAME", "GENDER", "AGE_BIRTH", "PHONE", "EMAIL",
                "ID_NUMBER", "ADDRESS");
        assertThat(result.replacementCount()).isGreaterThanOrEqualTo(7);
    }

    @Test
    void redactsSelfIntroductionAndStructuredSensitiveKeys() {
        RecruitmentSensitiveDataRedactor.Result text = redactor.redact("我叫李娜。负责高并发订单系统。我的邮箱是 lina@example.com");
        RecruitmentSensitiveDataRedactor.Result json = redactor.redactJson("""
                {"candidateName":"李娜","gender":"女","contactEmail":"lina@example.com",
                 "religion":"测试宗教","skills":["Java","MySQL"],"profile":"电话 13900139000"}
                """);

        assertThat(text.value()).doesNotContain("李娜", "lina@example.com").contains("高并发订单系统");
        assertThat(json.value()).doesNotContain("李娜", "13900139000", "lina@example.com", "测试宗教")
                .contains("Java", "MySQL", "[已脱敏]");
        assertThat(json.detected()).isTrue();
    }
}
