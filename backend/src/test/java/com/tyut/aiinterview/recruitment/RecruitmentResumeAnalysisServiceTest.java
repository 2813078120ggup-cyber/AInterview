package com.tyut.aiinterview.recruitment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tyut.aiinterview.ai.DeepSeekGateway;
import com.tyut.aiinterview.config.StorageProperties;
import com.tyut.aiinterview.domain.AiTask;
import com.tyut.aiinterview.domain.CandidateResume;
import com.tyut.aiinterview.domain.CandidateResumeAnalysis;
import com.tyut.aiinterview.domain.MediaFile;
import com.tyut.aiinterview.freeinterview.ResumeTextExtractor;
import com.tyut.aiinterview.mapper.CandidateResumeAnalysisMapper;
import com.tyut.aiinterview.mapper.CandidateResumeMapper;
import com.tyut.aiinterview.mapper.MediaFileMapper;
import com.tyut.aiinterview.media.LocalObjectStorage;
import com.tyut.aiinterview.media.UploadSecurityValidator;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

class RecruitmentResumeAnalysisServiceTest {
    @Test
    void storesStructuredProfileAndUpdatesResumeAfterSuccessfulAnalysis() throws Exception {
        CandidateResumeMapper resumeMapper = mock(CandidateResumeMapper.class);
        CandidateResumeAnalysisMapper analysisMapper = mock(CandidateResumeAnalysisMapper.class);
        MediaFileMapper mediaMapper = mock(MediaFileMapper.class);
        LocalObjectStorage storage = new LocalObjectStorage(new StorageProperties("./target/recruitment-test-media", 100_000_000));
        ResumeTextExtractor extractor = new ResumeTextExtractor(mock(UploadSecurityValidator.class));
        DeepSeekGateway gateway = mock(DeepSeekGateway.class);
        ObjectMapper objectMapper = new ObjectMapper();
        RecruitmentResumeAnalysisService service = new RecruitmentResumeAnalysisService(resumeMapper, analysisMapper,
                mediaMapper, storage, extractor, gateway, objectMapper, mock(ApplicationEventPublisher.class));

        String key = storage.save("txt", new java.io.ByteArrayInputStream(("我有三年 Java 后端开发经验，负责订单系统和库存服务建设。"
                + "熟悉 Java、Spring Boot、MySQL、Redis，并有性能优化和故障排查经历。").getBytes(StandardCharsets.UTF_8)));
        MediaFile media = new MediaFile();
        media.setId(9L); media.setObjectKey(key); media.setOriginalName("resume.txt"); media.setStatus(MediaFile.AVAILABLE);
        CandidateResume resume = new CandidateResume();
        resume.setId(7L); resume.setMediaId(9L); resume.setParseStatus("PENDING"); resume.setParseVersion(1);
        CandidateResumeAnalysis analysis = new CandidateResumeAnalysis();
        analysis.setId(8L); analysis.setResumeId(7L); analysis.setAnalysisVersion(1); analysis.setStatus("PENDING");
        AiTask task = new AiTask(); task.setId(11L); task.setCreatedBy(3L);
        task.setInputPayload("{\"resumeId\":7,\"analysisId\":8,\"analysisVersion\":1}");
        when(resumeMapper.selectById(7L)).thenReturn(resume);
        when(analysisMapper.selectById(8L)).thenReturn(analysis);
        when(mediaMapper.selectById(9L)).thenReturn(media);
        when(gateway.analyzeResume(any(String.class), any(), any())).thenReturn(new DeepSeekGateway.Generated<>(
                "req-1", "resume.analysis", 1, objectMapper.readTree("{\"candidateProfile\":\"三年Java后端经验\",\"skills\":[\"Java\",\"Redis\"]}")));

        service.process(task);

        assertEquals("SUCCESS", analysis.getStatus());
        assertEquals("SUCCESS", resume.getParseStatus());
        assertEquals("三年Java后端经验", resume.getSummary());
        assertEquals("[\"Java\",\"Redis\"]", resume.getSkills());
    }

    @Test
    void leaseExpiryMarksCurrentResumeAnalysisFailed() {
        CandidateResumeMapper resumeMapper = mock(CandidateResumeMapper.class);
        CandidateResumeAnalysisMapper analysisMapper = mock(CandidateResumeAnalysisMapper.class);
        RecruitmentResumeAnalysisService service = new RecruitmentResumeAnalysisService(resumeMapper, analysisMapper,
                mock(MediaFileMapper.class), mock(LocalObjectStorage.class), mock(ResumeTextExtractor.class),
                mock(DeepSeekGateway.class), new ObjectMapper(), mock(ApplicationEventPublisher.class));
        AiTask task = new AiTask();
        task.setId(11L);
        task.setInputPayload("{\"resumeId\":7,\"analysisId\":8,\"analysisVersion\":1}");

        service.markLeaseExpired(task);

        verify(analysisMapper).update(any(), any());
        verify(resumeMapper).update(any(), any());
    }
}
