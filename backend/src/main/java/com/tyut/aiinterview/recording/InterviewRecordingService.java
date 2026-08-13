package com.tyut.aiinterview.recording;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.tyut.aiinterview.common.BusinessException;
import com.tyut.aiinterview.domain.Interview;
import com.tyut.aiinterview.domain.InterviewQuestion;
import com.tyut.aiinterview.domain.InterviewRecording;
import com.tyut.aiinterview.domain.InterviewRecordingSegment;
import com.tyut.aiinterview.domain.InterviewTimelineEvent;
import com.tyut.aiinterview.domain.MediaFile;
import com.tyut.aiinterview.mapper.InterviewMapper;
import com.tyut.aiinterview.mapper.InterviewQuestionMapper;
import com.tyut.aiinterview.mapper.InterviewRecordingMapper;
import com.tyut.aiinterview.mapper.InterviewRecordingSegmentMapper;
import com.tyut.aiinterview.mapper.InterviewTimelineEventMapper;
import com.tyut.aiinterview.mapper.MediaFileMapper;
import com.tyut.aiinterview.media.LocalObjectStorage;
import com.tyut.aiinterview.media.MediaDtos;
import com.tyut.aiinterview.media.MediaService;
import com.tyut.aiinterview.recruitment.CompanyAccessService;
import com.tyut.aiinterview.security.CurrentUser;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class InterviewRecordingService {
    private static final Set<String> MODES = Set.of("TEXT", "AUDIO", "VIDEO");
    private static final Set<String> EVENT_TYPES = Set.of(
            "QUESTION_STARTED", "ANSWER_SUBMITTED", "FOLLOW_UP", "TRANSITION", "QUESTION_COMPLETED",
            "RECORDING_STARTED", "RECORDING_STOPPED", "RECORDING_ERROR");

    private final InterviewRecordingMapper recordingMapper;
    private final InterviewRecordingSegmentMapper segmentMapper;
    private final InterviewTimelineEventMapper eventMapper;
    private final InterviewMapper interviewMapper;
    private final InterviewQuestionMapper questionMapper;
    private final MediaFileMapper mediaMapper;
    private final MediaService mediaService;
    private final LocalObjectStorage storage;
    private final CurrentUser currentUser;
    private CompanyAccessService companyAccess;

    public InterviewRecordingService(InterviewRecordingMapper recordingMapper,
                                     InterviewRecordingSegmentMapper segmentMapper,
                                     InterviewTimelineEventMapper eventMapper,
                                     InterviewMapper interviewMapper,
                                     InterviewQuestionMapper questionMapper,
                                     MediaFileMapper mediaMapper,
                                     MediaService mediaService,
                                     LocalObjectStorage storage,
                                     CurrentUser currentUser) {
        this.recordingMapper = recordingMapper;
        this.segmentMapper = segmentMapper;
        this.eventMapper = eventMapper;
        this.interviewMapper = interviewMapper;
        this.questionMapper = questionMapper;
        this.mediaMapper = mediaMapper;
        this.mediaService = mediaService;
        this.storage = storage;
        this.currentUser = currentUser;
    }

    @Transactional
    public RecordingDtos.RecordingView selectMode(Long interviewId, RecordingDtos.SelectModeRequest request) {
        Interview interview = requireInterview(interviewId);
        requireCandidate(interview);
        if (interview.getStatus() != Interview.IN_PROGRESS) {
            throw BusinessException.badRequest("仅进行中的面试可以选择面试方式");
        }
        String mode = request.mode().trim().toUpperCase();
        if (!MODES.contains(mode)) throw BusinessException.badRequest("面试方式只支持 TEXT、AUDIO 或 VIDEO");
        InterviewRecording existing = find(interviewId);
        if (existing != null) {
            if (!mode.equals(existing.getMode())) throw BusinessException.badRequest("面试方式选定后不能更改");
            return toView(existing);
        }
        InterviewRecording recording = new InterviewRecording();
        recording.setInterviewId(interviewId);
        recording.setMode(mode);
        recording.setStatus("TEXT".equals(mode) ? "SELECTED" : "RECORDING");
        recording.setStartedAt(LocalDateTime.now());
        recording.setCreatedBy(currentUser.id());
        recordingMapper.insert(recording);
        return toView(recording);
    }

    public RecordingDtos.RecordingView get(Long interviewId) {
        Interview interview = requireInterview(interviewId);
        requireReadAccess(interview);
        InterviewRecording recording = find(interviewId);
        return recording == null ? null : toView(recording);
    }

    /** Company-scoped read path used by HR report review. */
    public RecordingDtos.RecordingView companyView(Long interviewId) {
        requireCompanyReadAccess(interviewId);
        InterviewRecording recording = find(interviewId);
        return recording == null ? null : toView(recording);
    }

    @Transactional
    public RecordingDtos.TimelineEventView addEvent(Long interviewId, RecordingDtos.TimelineEventRequest request) {
        Interview interview = requireInterview(interviewId);
        requireCandidate(interview);
        InterviewRecording recording = requireRecording(interviewId);
        String eventType = request.eventType().trim().toUpperCase();
        if (!EVENT_TYPES.contains(eventType)) throw BusinessException.badRequest("不支持的时间轴事件类型");
        if (request.interviewQuestionId() != null) requireQuestion(interviewId, request.interviewQuestionId());
        InterviewTimelineEvent event = new InterviewTimelineEvent();
        event.setRecordingId(recording.getId());
        event.setInterviewQuestionId(request.interviewQuestionId());
        event.setEventType(eventType);
        event.setOffsetMs(request.offsetMs());
        event.setContent(trimToNull(request.content()));
        eventMapper.insert(event);
        return toEventView(event);
    }

    @Transactional
    public RecordingDtos.SegmentView uploadSegment(Long interviewId, Long interviewQuestionId,
                                                   long startedOffsetMs, long endedOffsetMs,
                                                   MultipartFile file) {
        Interview interview = requireInterview(interviewId);
        requireCandidate(interview);
        InterviewRecording recording = requireRecording(interviewId);
        if ("TEXT".equals(recording.getMode())) throw BusinessException.badRequest("文字面试不能上传录制文件");
        if (startedOffsetMs < 0 || endedOffsetMs < startedOffsetMs || endedOffsetMs > 28_800_000L) {
            throw BusinessException.badRequest("录制分段时间范围不合法");
        }
        requireQuestion(interviewId, interviewQuestionId);
        String declaredType = file == null || file.getContentType() == null ? "" : file.getContentType().toLowerCase();
        if (("VIDEO".equals(recording.getMode()) && !declaredType.startsWith("video/"))
                || ("AUDIO".equals(recording.getMode()) && !declaredType.startsWith("audio/"))) {
            throw BusinessException.badRequest("录制文件类型与面试方式不匹配");
        }
        MediaDtos.MediaVO media = mediaService.upload(file);
        if (!recording.getMode().equals(media.mediaType().toUpperCase())) {
            throw BusinessException.badRequest("录制文件类型与面试方式不匹配");
        }
        InterviewRecordingSegment latest = segmentMapper.selectOne(new LambdaQueryWrapper<InterviewRecordingSegment>()
                .eq(InterviewRecordingSegment::getRecordingId, recording.getId())
                .orderByDesc(InterviewRecordingSegment::getSegmentNo).last("LIMIT 1"));
        InterviewRecordingSegment segment = new InterviewRecordingSegment();
        segment.setRecordingId(recording.getId());
        segment.setInterviewQuestionId(interviewQuestionId);
        segment.setMediaId(media.id());
        segment.setSegmentNo(latest == null ? 1 : latest.getSegmentNo() + 1);
        segment.setStartedOffsetMs(startedOffsetMs);
        segment.setEndedOffsetMs(endedOffsetMs);
        segmentMapper.insert(segment);
        return toSegmentView(interviewId, segment, media.contentType());
    }

    @Transactional
    public RecordingDtos.RecordingView complete(Long interviewId) {
        Interview interview = requireInterview(interviewId);
        requireCandidate(interview);
        completeForInterview(interviewId);
        return get(interviewId);
    }

    @Transactional
    public void completeForInterview(Long interviewId) {
        InterviewRecording recording = find(interviewId);
        if (recording == null || "COMPLETED".equals(recording.getStatus())) return;
        recording.setStatus("COMPLETED");
        recording.setEndedAt(LocalDateTime.now());
        recordingMapper.updateById(recording);
    }

    public boolean requiresSequentialMode(Long interviewId) {
        InterviewRecording recording = find(interviewId);
        return recording != null && ("AUDIO".equals(recording.getMode()) || "VIDEO".equals(recording.getMode()));
    }

    public RecordingDtos.RecordingContent content(Long interviewId, Long segmentId) {
        Interview interview = requireInterview(interviewId);
        requireReadAccess(interview);
        InterviewRecording recording = requireRecording(interviewId);
        InterviewRecordingSegment segment = segmentMapper.selectById(segmentId);
        if (segment == null || !recording.getId().equals(segment.getRecordingId())) {
            throw BusinessException.notFound("录制分段不存在");
        }
        MediaFile media = mediaMapper.selectById(segment.getMediaId());
        if (media == null || media.getStatus() != MediaFile.AVAILABLE) throw BusinessException.notFound("录制文件不存在或不可用");
        return new RecordingDtos.RecordingContent(storage.resource(media.getObjectKey()), media.getContentType(),
                media.getSizeBytes(), media.getOriginalName() == null ? "recording.webm" : media.getOriginalName());
    }

    private RecordingDtos.RecordingView toView(InterviewRecording recording) {
        List<RecordingDtos.SegmentView> segments = segmentMapper.selectList(new LambdaQueryWrapper<InterviewRecordingSegment>()
                        .eq(InterviewRecordingSegment::getRecordingId, recording.getId())
                        .orderByAsc(InterviewRecordingSegment::getStartedOffsetMs)
                        .orderByAsc(InterviewRecordingSegment::getId)).stream()
                .map(segment -> {
                    MediaFile media = mediaMapper.selectById(segment.getMediaId());
                    return toSegmentView(recording.getInterviewId(), segment,
                            media == null ? "application/octet-stream" : media.getContentType());
                }).toList();
        List<RecordingDtos.TimelineEventView> events = eventMapper.selectList(new LambdaQueryWrapper<InterviewTimelineEvent>()
                        .eq(InterviewTimelineEvent::getRecordingId, recording.getId())
                        .orderByAsc(InterviewTimelineEvent::getOffsetMs)
                        .orderByAsc(InterviewTimelineEvent::getId)).stream()
                .map(this::toEventView).toList();
        return new RecordingDtos.RecordingView(recording.getId(), recording.getInterviewId(), recording.getMode(),
                recording.getStatus(), recording.getStartedAt(), recording.getEndedAt(), segments, events);
    }

    private RecordingDtos.SegmentView toSegmentView(Long interviewId, InterviewRecordingSegment segment, String contentType) {
        return new RecordingDtos.SegmentView(segment.getId(), segment.getInterviewQuestionId(), segment.getMediaId(),
                segment.getSegmentNo(), segment.getStartedOffsetMs(), segment.getEndedOffsetMs(), contentType,
                "/v1/interviews/" + interviewId + "/recording/segments/" + segment.getId() + "/content");
    }

    private RecordingDtos.TimelineEventView toEventView(InterviewTimelineEvent event) {
        return new RecordingDtos.TimelineEventView(event.getId(), event.getInterviewQuestionId(), event.getEventType(),
                event.getOffsetMs(), event.getContent(), event.getCreatedAt());
    }

    private Interview requireInterview(Long id) {
        Interview interview = interviewMapper.selectById(id);
        if (interview == null) throw BusinessException.notFound("面试不存在");
        return interview;
    }

    private InterviewRecording find(Long interviewId) {
        return recordingMapper.selectOne(new LambdaQueryWrapper<InterviewRecording>()
                .eq(InterviewRecording::getInterviewId, interviewId).last("LIMIT 1"));
    }

    private InterviewRecording requireRecording(Long interviewId) {
        InterviewRecording recording = find(interviewId);
        if (recording == null) throw BusinessException.badRequest("请先选择面试方式");
        return recording;
    }

    private void requireQuestion(Long interviewId, Long questionId) {
        InterviewQuestion question = questionMapper.selectById(questionId);
        if (question == null || !interviewId.equals(question.getInterviewId())) {
            throw BusinessException.notFound("面试题目不存在");
        }
    }

    private void requireCandidate(Interview interview) {
        if (!currentUser.id().equals(interview.getCandidateId())) throw BusinessException.forbidden("仅候选人可操作面试录制");
    }

    private void requireParticipant(Interview interview) {
        Long userId = currentUser.id();
        if (!(userId.equals(interview.getCandidateId()) || userId.equals(interview.getInterviewerId())
                || currentUser.hasRole("ADMIN"))) throw BusinessException.forbidden("无权查看本场面试录制");
    }

    private void requireReadAccess(Interview interview) {
        if (currentUser.hasCompanyRole()) {
            requireCompanyReadAccess(interview.getId());
            return;
        }
        requireParticipant(interview);
    }

    private void requireCompanyReadAccess(Long interviewId) {
        if (companyAccess == null) throw BusinessException.forbidden("企业录制访问未配置");
        companyAccess.requireAnyPermission("interview:review", "report:read");
        companyAccess.requireAuthorizedInterview(interviewId);
    }

    @Autowired
    public void setCompanyAccessService(CompanyAccessService companyAccess) {
        this.companyAccess = companyAccess;
    }

    private String trimToNull(String value) {
        if (value == null || value.isBlank()) return null;
        return value.trim();
    }
}
