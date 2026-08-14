package com.tyut.aiinterview.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tyut.aiinterview.domain.AiTask;
import com.tyut.aiinterview.domain.Evaluation;
import com.tyut.aiinterview.domain.Interview;
import com.tyut.aiinterview.domain.InterviewAnswer;
import com.tyut.aiinterview.domain.InterviewQuestion;
import com.tyut.aiinterview.domain.Report;
import com.tyut.aiinterview.mapper.AiTaskMapper;
import com.tyut.aiinterview.mapper.EvaluationMapper;
import com.tyut.aiinterview.mapper.FreeInterviewSessionMapper;
import com.tyut.aiinterview.mapper.FreeInterviewTurnMapper;
import com.tyut.aiinterview.mapper.InterviewAnswerMapper;
import com.tyut.aiinterview.mapper.InterviewMapper;
import com.tyut.aiinterview.mapper.InterviewQuestionMapper;
import com.tyut.aiinterview.mapper.QuestionMapper;
import com.tyut.aiinterview.mapper.ReportMapper;
import com.tyut.aiinterview.prompt.PromptTemplateService;
import com.tyut.aiinterview.security.CurrentUser;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AiTaskServiceReportConcurrencyTest {
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ExecutorService scoringExecutor = Executors.newFixedThreadPool(3);

    @BeforeEach
    void initializeMyBatisPlusLambdaMetadata() {
        Configuration configuration = new Configuration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "test");
        for (Class<?> entityType : List.of(AiTask.class, Interview.class, InterviewQuestion.class,
                InterviewAnswer.class, Evaluation.class, Report.class)) {
            if (TableInfoHelper.getTableInfo(entityType) == null) {
                TableInfoHelper.initTableInfo(assistant, entityType);
            }
        }
    }

    @AfterEach
    void shutdownExecutor() {
        scoringExecutor.shutdownNow();
    }

    @Test
    void scoresSubjectiveQuestionsConcurrentlyBeforeGeneratingReport() throws Exception {
        AiTaskMapper taskMapper = mock(AiTaskMapper.class);
        InterviewMapper interviewMapper = mock(InterviewMapper.class);
        InterviewQuestionMapper interviewQuestionMapper = mock(InterviewQuestionMapper.class);
        InterviewAnswerMapper answerMapper = mock(InterviewAnswerMapper.class);
        QuestionMapper questionMapper = mock(QuestionMapper.class);
        EvaluationMapper evaluationMapper = mock(EvaluationMapper.class);
        ReportMapper reportMapper = mock(ReportMapper.class);
        DeepSeekGateway gateway = mock(DeepSeekGateway.class);
        PromptTemplateService prompts = mock(PromptTemplateService.class);
        CurrentUser currentUser = mock(CurrentUser.class);

        AiTask task = evaluationTask();
        Interview interview = interview();
        List<InterviewQuestion> questions = List.of(question(21L, 1), question(22L, 2), question(23L, 3));
        List<InterviewAnswer> answers = List.of(answer(31L, 21L), answer(32L, 22L), answer(33L, 23L));

        when(taskMapper.selectById(51L)).thenReturn(task);
        when(taskMapper.claimPending(any(), anyString(), anyString(), any(), any())).thenReturn(1);
        when(taskMapper.update(any(AiTask.class), any())).thenReturn(1);
        when(taskMapper.updateById(any(AiTask.class))).thenReturn(1);
        when(interviewMapper.selectById(11L)).thenReturn(interview);
        when(interviewMapper.updateById(any(Interview.class))).thenReturn(1);
        when(interviewQuestionMapper.selectList(any())).thenReturn(questions);
        when(answerMapper.selectList(any())).thenReturn(answers);
        when(prompts.activeVersionNo(anyString())).thenReturn(1);
        when(evaluationMapper.selectOne(any())).thenReturn(null);
        when(evaluationMapper.insert(any(Evaluation.class))).thenReturn(1);
        when(reportMapper.selectOne(any())).thenReturn(null);
        when(reportMapper.insert(any(Report.class))).thenAnswer(invocation -> {
            invocation.<Report>getArgument(0).setId(71L);
            return 1;
        });

        CountDownLatch allScoringCallsStarted = new CountDownLatch(3);
        CountDownLatch releaseScoringCalls = new CountDownLatch(1);
        AtomicInteger activeCalls = new AtomicInteger();
        AtomicInteger peakCalls = new AtomicInteger();
        when(gateway.evaluateAnswer(anyString(), anyString(), anyString(), any(), any()))
                .thenAnswer(invocation -> {
                    int active = activeCalls.incrementAndGet();
                    peakCalls.accumulateAndGet(active, Math::max);
                    allScoringCallsStarted.countDown();
                    try {
                        if (!releaseScoringCalls.await(3, TimeUnit.SECONDS)) {
                            throw new IllegalStateException("并发评分测试等待超时");
                        }
                        return new DeepSeekGateway.Generated<>("score-request", "report.answer_evaluation", 1,
                                objectMapper.readTree("""
                                        {"professionalScore":80,"expressionScore":78,"logicScore":82,
                                         "adaptabilityScore":76,"overallScore":80,"comment":"回答结构完整"}
                                        """));
                    } finally {
                        activeCalls.decrementAndGet();
                    }
                });
        when(gateway.generateReport(anyString(), any(), any()))
                .thenReturn(new DeepSeekGateway.Generated<>("report-request", "report.simulation_summary", 1,
                        objectMapper.readTree("""
                                {"summary":"综合表现良好","strengths":"基础扎实","weaknesses":"边界说明不足",
                                 "improvementSuggestions":"补充量化指标与异常场景"}
                                """)));

        AiTaskService service = new AiTaskService(taskMapper, interviewMapper, interviewQuestionMapper, answerMapper,
                questionMapper, evaluationMapper, reportMapper, mock(FreeInterviewSessionMapper.class),
                mock(FreeInterviewTurnMapper.class), gateway, prompts, mock(ChoiceAnswerScorer.class),
                objectMapper, currentUser, mock(com.tyut.aiinterview.recruitment.RecruitmentResumeAnalysisService.class),
                Runnable::run, scoringExecutor, 12, 300);

        CompletableFuture<Void> processing = CompletableFuture.runAsync(() -> service.process(51L));
        assertTrue(allScoringCallsStarted.await(2, TimeUnit.SECONDS), "三道主观题应并行进入评分调用");
        assertEquals(3, peakCalls.get());
        releaseScoringCalls.countDown();
        processing.get(5, TimeUnit.SECONDS);

        assertEquals("SUCCESS", task.getStatus());
        assertEquals(Interview.REPORT_READY, interview.getStatus());
        verify(reportMapper).insert(org.mockito.ArgumentMatchers.<Report>argThat(report ->
                Integer.valueOf(0).equals(report.getStatus()) && report.getPublishedAt() == null));
    }

    private AiTask evaluationTask() {
        AiTask task = new AiTask();
        task.setId(51L);
        task.setInterviewId(11L);
        task.setTaskType(AiTaskService.AUTO_EVALUATION);
        task.setStatus("PENDING");
        task.setAttempts(0);
        task.setMaxAttempts(3);
        task.setCreatedBy(7L);
        task.setInputPayload("{\"interviewId\":11}");
        return task;
    }

    private Interview interview() {
        Interview interview = new Interview();
        interview.setId(11L);
        interview.setCandidateId(7L);
        interview.setStatus(Interview.REPORT_GENERATING);
        return interview;
    }

    private InterviewQuestion question(Long id, int sequence) {
        InterviewQuestion question = new InterviewQuestion();
        question.setId(id);
        question.setInterviewId(11L);
        question.setSequenceNo(sequence);
        question.setQuestionSnapshot("{\"content\":\"请说明你的解决方案\",\"questionType\":\"subjective\"}");
        return question;
    }

    private InterviewAnswer answer(Long id, Long interviewQuestionId) {
        InterviewAnswer answer = new InterviewAnswer();
        answer.setId(id);
        answer.setInterviewQuestionId(interviewQuestionId);
        answer.setAnswerContent("候选人给出了包含原理、边界和实践结果的完整回答。");
        return answer;
    }
}
