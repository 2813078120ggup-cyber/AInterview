package com.tyut.aiinterview.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tyut.aiinterview.common.BusinessException;
import com.tyut.aiinterview.domain.AiTask;
import com.tyut.aiinterview.domain.Interview;
import com.tyut.aiinterview.domain.InterviewAnswer;
import com.tyut.aiinterview.domain.InterviewQuestion;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class AiTaskServiceFollowUpTest {
    private final AiTaskMapper taskMapper = mock(AiTaskMapper.class);
    private final InterviewMapper interviewMapper = mock(InterviewMapper.class);
    private final InterviewQuestionMapper interviewQuestionMapper = mock(InterviewQuestionMapper.class);
    private final InterviewAnswerMapper answerMapper = mock(InterviewAnswerMapper.class);
    private final DeepSeekGateway deepSeekGateway = mock(DeepSeekGateway.class);
    private final CurrentUser currentUser = mock(CurrentUser.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private AiTaskService service;

    @BeforeEach
    void setUp() {
        service = new AiTaskService(taskMapper, interviewMapper, interviewQuestionMapper, answerMapper,
                mock(QuestionMapper.class), mock(EvaluationMapper.class), mock(ReportMapper.class),
                mock(FreeInterviewSessionMapper.class), mock(FreeInterviewTurnMapper.class),
                deepSeekGateway, mock(PromptTemplateService.class),
                mock(ChoiceAnswerScorer.class), objectMapper, currentUser,
                Runnable::run, Runnable::run, 12);
        when(currentUser.id()).thenReturn(7L);
        when(interviewMapper.selectById(11L)).thenReturn(interview());
        when(interviewQuestionMapper.selectById(21L)).thenReturn(interviewQuestion());
    }

    @Test
    void createsThirdFollowUpFromSavedConversationAndServerQuestion() throws Exception {
        when(answerMapper.selectOne(any())).thenReturn(answer("""
                [
                  {"role":"candidate","content":"第一次回答"},
                  {"role":"assistant","content":"追问一","kind":"follow-up"},
                  {"role":"candidate","content":"第二次回答"},
                  {"role":"assistant","content":"追问二","kind":"follow-up"},
                  {"role":"candidate","content":"最新回答"}
                ]
                """));
        when(taskMapper.insert(any(AiTask.class))).thenAnswer(invocation -> {
            invocation.<AiTask>getArgument(0).setId(41L);
            return 1;
        });

        AiTask result = service.requestFollowUp(11L, 21L, "不可信的请求答案");

        ArgumentCaptor<AiTask> captor = ArgumentCaptor.forClass(AiTask.class);
        verify(taskMapper).insert(captor.capture());
        AiTask created = captor.getValue();
        JsonNode input = objectMapper.readTree(created.getInputPayload());
        assertEquals(41L, result.getId());
        assertEquals(31L, created.getAnswerId());
        assertEquals("simulation-follow-up:21:3", created.getDedupeKey());
        assertEquals("服务端题目", input.path("question").asText());
        assertEquals("最新回答", input.path("answer").asText());
        assertEquals("追问一\u001E追问二", input.path("previousFollowUps").asText());
        assertEquals(true, input.path("conversationContext").asText().contains("候选人：最新回答"));
    }

    @Test
    void rejectsFourthFollowUpWithoutCreatingTask() {
        when(answerMapper.selectOne(any())).thenReturn(answer("""
                [
                  {"role":"assistant","content":"追问一","kind":"follow-up"},
                  {"role":"assistant","content":"追问二","kind":"follow-up"},
                  {"role":"assistant","content":"追问三","kind":"follow-up"}
                ]
                """));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.requestFollowUp(11L, 21L, "第四次回答"));

        assertEquals("本题已达到 3 次追问上限，请进入下一题", exception.getMessage());
        verify(taskMapper, never()).insert(any(AiTask.class));
    }

    @Test
    void retriesOneInvalidGenerationBeforeCompletingTask() throws Exception {
        AiTask task = new AiTask();
        task.setId(51L);
        task.setInterviewId(11L);
        task.setTaskType(AiTaskService.FOLLOW_UP);
        task.setStatus("PENDING");
        task.setAttempts(0);
        task.setMaxAttempts(3);
        task.setInputPayload("{\"question\":\"服务端题目\",\"answer\":\"候选人回答\",\"conversationContext\":\"候选人：候选人回答\",\"previousFollowUps\":\"\"}");
        when(taskMapper.selectById(51L)).thenReturn(task);
        when(taskMapper.update(any(AiTask.class), any())).thenReturn(1);
        when(deepSeekGateway.followUp(anyString(), anyString(), anyString(), any()))
                .thenReturn(new DeepSeekGateway.Generated<>("request-1", "simulation.follow_up", 2,
                                "这部分不错。第一个问题是什么？第二个问题是什么？"),
                        new DeepSeekGateway.Generated<>("request-2", "simulation.follow_up", 2,
                                "你提到使用内存屏障，具体是哪类屏障保证了写入可见性？"));

        service.process(51L);

        verify(deepSeekGateway, times(2)).followUp(anyString(), anyString(), anyString(), any());
        assertEquals("SUCCESS", task.getStatus());
        assertEquals("你提到使用内存屏障，具体是哪类屏障保证了写入可见性？",
                objectMapper.readTree(task.getOutputPayload()).path("followUp").asText());
    }

    private Interview interview() {
        Interview interview = new Interview();
        interview.setId(11L);
        interview.setCandidateId(7L);
        interview.setStatus(Interview.IN_PROGRESS);
        interview.setRemark("interviewerStyle=big-tech");
        return interview;
    }

    private InterviewQuestion interviewQuestion() {
        InterviewQuestion question = new InterviewQuestion();
        question.setId(21L);
        question.setInterviewId(11L);
        question.setQuestionSnapshot("{\"content\":\"服务端题目\",\"questionType\":\"subjective\"}");
        return question;
    }

    private InterviewAnswer answer(String answerData) {
        InterviewAnswer answer = new InterviewAnswer();
        answer.setId(31L);
        answer.setInterviewQuestionId(21L);
        answer.setAnswerData(answerData);
        return answer;
    }
}
