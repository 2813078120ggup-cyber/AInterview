package com.tyut.aiinterview.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("interview_recording_segment")
public class InterviewRecordingSegment {
    private Long id;
    private Long recordingId;
    private Long interviewQuestionId;
    private Long mediaId;
    private Integer segmentNo;
    private Long startedOffsetMs;
    private Long endedOffsetMs;
    private LocalDateTime createdAt;
}
