package com.tyut.aiinterview.notification;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tyut.aiinterview.auth.VerificationCodeProperties;
import com.tyut.aiinterview.domain.UserAccount;
import com.tyut.aiinterview.mapper.UserMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.javamail.JavaMailSender;

class NotificationMailServiceTest {
    @Test
    void preservesSnowflakeCandidateIdSentAsJsonString() throws Exception {
        long candidateId = 2082090955635404802L;
        NotificationDtos.MailSyncRequest request = new ObjectMapper().readValue("""
                {
                  "candidateId": "2082090955635404802",
                  "title": "面试通知",
                  "content": "请按时参加面试",
                  "interviewTitle": "Java 后端工程师模拟面试",
                  "scheduledAt": "2026-07-28 21:32"
                }
                """, NotificationDtos.MailSyncRequest.class);

        UserMapper userMapper = mock(UserMapper.class);
        UserAccount candidate = new UserAccount();
        candidate.setId(candidateId);
        when(userMapper.selectById(candidateId)).thenReturn(candidate);

        @SuppressWarnings("unchecked")
        ObjectProvider<JavaMailSender> mailSenderProvider = mock(ObjectProvider.class);
        when(mailSenderProvider.getIfAvailable()).thenReturn(null);
        NotificationMailService service = new NotificationMailService(
                userMapper, mailSenderProvider, new VerificationCodeProperties());

        NotificationDtos.MailSyncResponse response = service.syncMail(request);

        assertEquals(candidateId, request.candidateId());
        assertFalse(response.sent());
        verify(userMapper).selectById(candidateId);
    }

    @Test
    void fallsBackToUniqueUsernameForLegacyRoundedCandidateId() throws Exception {
        long roundedCandidateId = 2081035848835600400L;
        NotificationDtos.MailSyncRequest request = new ObjectMapper().readValue("""
                {
                  "candidateId": "2081035848835600400",
                  "candidateUsername": "candidate_gao",
                  "title": "面试通知",
                  "content": "请按时参加面试"
                }
                """, NotificationDtos.MailSyncRequest.class);

        UserMapper userMapper = mock(UserMapper.class);
        UserAccount candidate = new UserAccount();
        candidate.setId(2081035848835600386L);
        candidate.setUsername("candidate_gao");
        when(userMapper.selectOne(any())).thenReturn(candidate);

        @SuppressWarnings("unchecked")
        ObjectProvider<JavaMailSender> mailSenderProvider = mock(ObjectProvider.class);
        NotificationMailService service = new NotificationMailService(
                userMapper, mailSenderProvider, new VerificationCodeProperties());

        NotificationDtos.MailSyncResponse response = service.syncMail(request);

        assertFalse(response.sent());
        verify(userMapper).selectById(roundedCandidateId);
        verify(userMapper).selectOne(any());
    }
}
