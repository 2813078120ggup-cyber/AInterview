package com.tyut.aiinterview.recording;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.tyut.aiinterview.common.BusinessException;
import com.tyut.aiinterview.domain.Interview;
import com.tyut.aiinterview.domain.InterviewQuestion;
import com.tyut.aiinterview.domain.InterviewRecording;
import com.tyut.aiinterview.domain.InterviewTimelineEvent;
import com.tyut.aiinterview.mapper.InterviewMapper;
import com.tyut.aiinterview.mapper.InterviewQuestionMapper;
import com.tyut.aiinterview.mapper.InterviewRecordingMapper;
import com.tyut.aiinterview.mapper.InterviewRecordingSegmentMapper;
import com.tyut.aiinterview.mapper.InterviewTimelineEventMapper;
import com.tyut.aiinterview.mapper.MediaFileMapper;
import com.tyut.aiinterview.media.LocalObjectStorage;
import com.tyut.aiinterview.media.MediaService;
import com.tyut.aiinterview.security.CurrentUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class InterviewRecordingServiceTest {
    private final InterviewRecordingMapper recordingMapper = mock(InterviewRecordingMapper.class);
    private final InterviewRecordingSegmentMapper segmentMapper = mock(InterviewRecordingSegmentMapper.class);
    private final InterviewQuestionMapper questionMapper = mock(InterviewQuestionMapper.class);
    private final InterviewTimelineEventMapper eventMapper = mock(InterviewTimelineEventMapper.class);
    private final InterviewMapper interviewMapper = mock(InterviewMapper.class);
    private final CurrentUser currentUser = mock(CurrentUser.class);
    private InterviewRecordingService service;

    @BeforeEach
    void setUp() {
        service = new InterviewRecordingService(recordingMapper, segmentMapper,
                eventMapper, interviewMapper, questionMapper, mock(MediaFileMapper.class), mock(MediaService.class),
                mock(LocalObjectStorage.class), currentUser);
        Interview interview = new Interview();
        interview.setId(11L);
        interview.setCandidateId(7L);
        interview.setInterviewerId(8L);
        interview.setStatus(Interview.IN_PROGRESS);
        when(interviewMapper.selectById(11L)).thenReturn(interview);
        when(currentUser.id()).thenReturn(7L);
        when(segmentMapper.selectList(any())).thenReturn(java.util.List.of());
        when(eventMapper.selectList(any())).thenReturn(java.util.List.of());
    }

    @Test
    void selectsTextModeWithoutStartingMediaRecording() {
        when(recordingMapper.selectOne(any())).thenReturn(null);
        when(recordingMapper.insert(any(InterviewRecording.class))).thenAnswer(invocation -> {
            invocation.<InterviewRecording>getArgument(0).setId(21L);
            return 1;
        });

        RecordingDtos.RecordingView result = service.selectMode(11L, new RecordingDtos.SelectModeRequest("text"));

        assertEquals("TEXT", result.mode());
        assertEquals("SELECTED", result.status());
        verify(recordingMapper).insert(any(InterviewRecording.class));
    }

    @Test
    void rejectsChangingAnAlreadySelectedMode() {
        InterviewRecording existing = new InterviewRecording();
        existing.setId(21L);
        existing.setInterviewId(11L);
        existing.setMode("AUDIO");
        existing.setStatus("RECORDING");
        when(recordingMapper.selectOne(any())).thenReturn(existing);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.selectMode(11L, new RecordingDtos.SelectModeRequest("VIDEO")));

        assertEquals("面试方式选定后不能更改", exception.getMessage());
        verify(recordingMapper, never()).insert(any(InterviewRecording.class));
    }

    @Test
    void storesQuestionTimelineEventWithServerValidatedQuestion() {
        InterviewRecording existing = new InterviewRecording();
        existing.setId(21L);
        existing.setInterviewId(11L);
        existing.setMode("AUDIO");
        existing.setStatus("RECORDING");
        when(recordingMapper.selectOne(any())).thenReturn(existing);
        InterviewQuestion question = new InterviewQuestion();
        question.setId(31L);
        question.setInterviewId(11L);
        when(questionMapper.selectById(31L)).thenReturn(question);

        RecordingDtos.TimelineEventView result = service.addEvent(11L,
                new RecordingDtos.TimelineEventRequest(31L, "follow_up", 1200L, "请说明边界条件？"));

        assertEquals("FOLLOW_UP", result.eventType());
        assertEquals(31L, result.interviewQuestionId());
        assertEquals(1200L, result.offsetMs());
        verify(eventMapper).insert(any(InterviewTimelineEvent.class));
    }
}
