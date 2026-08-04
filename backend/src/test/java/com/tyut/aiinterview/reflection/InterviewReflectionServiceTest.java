package com.tyut.aiinterview.reflection;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.tyut.aiinterview.common.BusinessException;
import com.tyut.aiinterview.domain.Interview;
import com.tyut.aiinterview.domain.InterviewReflection;
import com.tyut.aiinterview.domain.Report;
import com.tyut.aiinterview.mapper.InterviewMapper;
import com.tyut.aiinterview.mapper.InterviewReflectionMapper;
import com.tyut.aiinterview.mapper.ReportMapper;
import com.tyut.aiinterview.security.CurrentUser;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class InterviewReflectionServiceTest {
    private final InterviewReflectionMapper reflectionMapper = mock(InterviewReflectionMapper.class);
    private final InterviewMapper interviewMapper = mock(InterviewMapper.class);
    private final ReportMapper reportMapper = mock(ReportMapper.class);
    private final CurrentUser currentUser = mock(CurrentUser.class);
    private InterviewReflectionService service;
    private Interview interview;

    @BeforeEach
    void setUp() {
        service = new InterviewReflectionService(reflectionMapper, interviewMapper, reportMapper, currentUser);
        interview = new Interview();
        interview.setId(11L);
        interview.setCandidateId(7L);
        interview.setTitle("Java 模拟面试");
        interview.setStatus(Interview.COMPLETED);
        when(currentUser.hasRole("CANDIDATE")).thenReturn(true);
        when(currentUser.id()).thenReturn(7L);
        when(interviewMapper.selectById(11L)).thenReturn(interview);
        when(reportMapper.selectOne(any())).thenReturn(null);
    }

    @Test
    void savesReflectionAfterInterviewEnds() {
        when(reflectionMapper.selectOne(any())).thenReturn(null);
        when(reflectionMapper.insert(any(InterviewReflection.class))).thenAnswer(invocation -> {
            invocation.<InterviewReflection>getArgument(0).setId(31L);
            return 1;
        });

        ReflectionDtos.ReflectionView result = service.save(11L, new ReflectionDtos.SaveRequest(
                78, 4, "表达比上一次更有结构。", "先给出了结论", "边界条件说明不足", "完成两次并发专题练习"));

        assertEquals(31L, result.reflectionId());
        assertEquals(78, result.selfScore());
        assertEquals(4, result.confidenceLevel());
        verify(reflectionMapper).insert(any(InterviewReflection.class));
    }

    @Test
    void rejectsReflectionBeforeInterviewEnds() {
        interview.setStatus(Interview.IN_PROGRESS);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.save(11L, new ReflectionDtos.SaveRequest(
                        70, 3, "仍在面试中。", null, null, null)));

        assertEquals("面试结束后才能记录心得", exception.getMessage());
        verify(reflectionMapper, never()).insert(any(InterviewReflection.class));
    }

    @Test
    void rejectsAnotherCandidatesInterview() {
        interview.setCandidateId(9L);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.get(11L));

        assertEquals("无权访问该面试心得", exception.getMessage());
    }

    @Test
    void summarizesReflectionAndPublishedReportTrends() {
        InterviewReflection first = reflection(31L, 11L, 70, 3, LocalDateTime.of(2026, 7, 20, 10, 0));
        InterviewReflection latest = reflection(32L, 12L, 85, 4, LocalDateTime.of(2026, 7, 25, 10, 0));
        Interview firstInterview = ownedInterview(11L, "第一次模拟面试", LocalDateTime.of(2026, 7, 20, 9, 0));
        Interview latestInterview = ownedInterview(12L, "第二次模拟面试", LocalDateTime.of(2026, 7, 25, 9, 0));
        Report firstReport = report(11L, "68.5");
        Report latestReport = report(12L, "88.0");

        when(reflectionMapper.selectList(any())).thenReturn(List.of(first, latest));
        when(interviewMapper.selectBatchIds(any())).thenReturn(List.of(firstInterview, latestInterview));
        when(reportMapper.selectList(any())).thenReturn(List.of(firstReport, latestReport));

        ReflectionDtos.CandidateSummary result = service.mine();

        assertEquals(2, result.reflectionCount());
        assertEquals(new BigDecimal("77.5"), result.averageSelfScore());
        assertEquals(new BigDecimal("3.5"), result.averageConfidenceLevel());
        assertEquals(new BigDecimal("78.3"), result.averageAiScore());
        assertEquals(85, result.latestSelfScore());
        assertEquals(15, result.changeFromPrevious());
        assertEquals(11L, result.reflections().get(0).interviewId());
        assertEquals(12L, result.reflections().get(1).interviewId());
    }

    private InterviewReflection reflection(Long id, Long interviewId, int selfScore, int confidence, LocalDateTime createdAt) {
        InterviewReflection reflection = new InterviewReflection();
        reflection.setId(id);
        reflection.setInterviewId(interviewId);
        reflection.setCandidateId(7L);
        reflection.setSelfScore(selfScore);
        reflection.setConfidenceLevel(confidence);
        reflection.setContent("复盘内容");
        reflection.setCreatedAt(createdAt);
        reflection.setUpdatedAt(createdAt);
        return reflection;
    }

    private Interview ownedInterview(Long id, String title, LocalDateTime scheduledAt) {
        Interview owned = new Interview();
        owned.setId(id);
        owned.setCandidateId(7L);
        owned.setTitle(title);
        owned.setScheduledAt(scheduledAt);
        owned.setStatus(Interview.COMPLETED);
        return owned;
    }

    private Report report(Long interviewId, String totalScore) {
        Report report = new Report();
        report.setInterviewId(interviewId);
        report.setStatus(1);
        report.setTotalScore(new BigDecimal(totalScore));
        return report;
    }
}
