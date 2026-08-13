package com.tyut.aiinterview.recruitment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tyut.aiinterview.ai.DeepSeekGateway;
import com.tyut.aiinterview.domain.AiTask;
import com.tyut.aiinterview.domain.CandidateResume;
import com.tyut.aiinterview.domain.CandidateResumeAnalysis;
import com.tyut.aiinterview.domain.JobApplication;
import com.tyut.aiinterview.domain.JobMatchEvaluation;
import com.tyut.aiinterview.domain.JobPosition;
import com.tyut.aiinterview.mapper.CandidateResumeAnalysisMapper;
import com.tyut.aiinterview.mapper.CandidateResumeMapper;
import com.tyut.aiinterview.mapper.JobApplicationMapper;
import com.tyut.aiinterview.mapper.JobMatchEvaluationMapper;
import com.tyut.aiinterview.mapper.JobPositionMapper;
import java.util.List;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RecruitmentJobMatchServiceTest {
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void initializeMyBatisPlusLambdaMetadata() {
        Configuration configuration = new Configuration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "test");
        for (Class<?> entityType : List.of(JobMatchEvaluation.class, CandidateResumeAnalysis.class,
                JobApplication.class, CandidateResume.class, JobPosition.class)) {
            if (TableInfoHelper.getTableInfo(entityType) == null) TableInfoHelper.initTableInfo(assistant, entityType);
        }
    }

    @Test
    void storesExplainableJdMatchAndScore() throws Exception {
        JobApplicationMapper applicationMapper = mock(JobApplicationMapper.class);
        JobPositionMapper positionMapper = mock(JobPositionMapper.class);
        CandidateResumeMapper resumeMapper = mock(CandidateResumeMapper.class);
        CandidateResumeAnalysisMapper analysisMapper = mock(CandidateResumeAnalysisMapper.class);
        JobMatchEvaluationMapper evaluationMapper = mock(JobMatchEvaluationMapper.class);
        DeepSeekGateway gateway = mock(DeepSeekGateway.class);
        RecruitmentJobMatchService service = new RecruitmentJobMatchService(applicationMapper, positionMapper,
                resumeMapper, analysisMapper, evaluationMapper, gateway, objectMapper);

        JobApplication application = new JobApplication();
        application.setId(12L); application.setPositionId(13L); application.setResumeId(14L);
        JobPosition position = new JobPosition();
        position.setId(13L); position.setName("Java 开发工程师"); position.setRequirements("熟悉 Java 和 Redis");
        position.setSkillTags("[\"Java\",\"Redis\"]");
        CandidateResume resume = new CandidateResume();
        resume.setId(14L); resume.setParseStatus("SUCCESS"); resume.setParseVersion(2); resume.setSkills("[\"Java\",\"Redis\"]");
        resume.setSummary("三年 Java 后端经验");
        CandidateResumeAnalysis analysis = new CandidateResumeAnalysis();
        analysis.setStatus("SUCCESS"); analysis.setAnalysisVersion(2); analysis.setProfileJson("{\"candidateProfile\":\"三年后端经验\"}");
        analysis.setExtractedText("三年 Java 后端经验，负责 Redis 缓存治理。");
        AiTask task = new AiTask(); task.setId(99L); task.setCreatedBy(7L);
        task.setInputPayload("{\"applicationId\":12,\"positionId\":13,\"resumeId\":14,\"resumeVersion\":2,\"evaluationVersion\":1}");
        when(applicationMapper.selectById(12L)).thenReturn(application);
        when(applicationMapper.update(any(), any())).thenReturn(1);
        when(positionMapper.selectById(13L)).thenReturn(position);
        when(resumeMapper.selectById(14L)).thenReturn(resume);
        when(analysisMapper.selectOne(any())).thenReturn(analysis);
        when(gateway.matchResumeToJob(any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(new DeepSeekGateway.Generated<>("req", "recruitment.job_match", 1,
                        objectMapper.readTree("{\"score\":87,\"summary\":\"Java 与 Redis 经验和岗位要求高度相关\",\"matchedSkills\":[\"Java\",\"Redis\"],\"gaps\":[\"分布式事务待核实\"],\"recommendation\":\"建议进入面试\"}")));

        service.process(task);

        assertEquals("SUCCESS", application.getMatchStatus());
        assertEquals(94, application.getMatchScore().intValue());
        assertEquals(2, application.getMatchVersion());
        assertEquals("Java 与 Redis 经验和岗位要求高度相关", application.getMatchSummary());
        JsonNode details = objectMapper.readTree(application.getMatchDetails());
        assertEquals(87, details.path("aiScore").asInt());
        assertEquals("建议进入面试", details.path("recommendation").asText());
        verify(evaluationMapper).insert(any(JobMatchEvaluation.class));
    }

    @Test
    void rejectsOutOfRangeScoreAndMarksMatchFailed() throws Exception {
        JobApplicationMapper applicationMapper = mock(JobApplicationMapper.class);
        JobPositionMapper positionMapper = mock(JobPositionMapper.class);
        CandidateResumeMapper resumeMapper = mock(CandidateResumeMapper.class);
        CandidateResumeAnalysisMapper analysisMapper = mock(CandidateResumeAnalysisMapper.class);
        JobMatchEvaluationMapper evaluationMapper = mock(JobMatchEvaluationMapper.class);
        DeepSeekGateway gateway = mock(DeepSeekGateway.class);
        RecruitmentJobMatchService service = new RecruitmentJobMatchService(applicationMapper, positionMapper,
                resumeMapper, analysisMapper, evaluationMapper, gateway, objectMapper);
        JobApplication application = new JobApplication();
        application.setId(12L); application.setPositionId(13L); application.setResumeId(14L);
        CandidateResume resume = new CandidateResume(); resume.setId(14L); resume.setParseStatus("MANUAL"); resume.setParseVersion(1);
        when(applicationMapper.selectById(12L)).thenReturn(application);
        when(applicationMapper.update(any(), any())).thenReturn(1);
        when(positionMapper.selectById(13L)).thenReturn(new JobPosition());
        when(resumeMapper.selectById(14L)).thenReturn(resume);
        when(gateway.matchResumeToJob(any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(new DeepSeekGateway.Generated<>("req", "recruitment.job_match", 1, objectMapper.readTree("{\"score\":101,\"summary\":\"无效\"}")));
        AiTask task = new AiTask(); task.setInputPayload("{\"applicationId\":12,\"resumeVersion\":1}");

        assertThrows(IllegalStateException.class, () -> service.process(task));
        assertEquals("FAILED", application.getMatchStatus());
        assertEquals("岗位匹配失败，请检查简历和岗位信息后重试", application.getMatchError());
    }
}
