package com.tyut.aiinterview.notification;

import com.tyut.aiinterview.common.BusinessException;
import com.tyut.aiinterview.domain.UserAccount;
import com.tyut.aiinterview.mapper.UserMapper;
import com.tyut.aiinterview.auth.VerificationCodeProperties;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class NotificationMailService {
    private final UserMapper userMapper;
    private final JavaMailSender mailSender;
    private final VerificationCodeProperties mailProperties;

    public NotificationMailService(UserMapper userMapper, ObjectProvider<JavaMailSender> mailSenderProvider,
            VerificationCodeProperties mailProperties) {
        this.userMapper = userMapper;
        this.mailSender = mailSenderProvider.getIfAvailable();
        this.mailProperties = mailProperties;
    }

    public NotificationDtos.MailSyncResponse syncMail(NotificationDtos.MailSyncRequest request) {
        UserAccount candidate = userMapper.selectById(request.candidateId());
        if (candidate == null && StringUtils.hasText(request.candidateUsername())) {
            candidate = userMapper.selectOne(new LambdaQueryWrapper<UserAccount>()
                    .eq(UserAccount::getUsername, request.candidateUsername().trim())
                    .last("LIMIT 1"));
        }
        if (candidate == null) {
            throw BusinessException.notFound("候选人不存在");
        }
        if (!StringUtils.hasText(candidate.getEmail())) {
            return new NotificationDtos.MailSyncResponse(false, null);
        }
        if (mailSender == null || !StringUtils.hasText(mailProperties.getMailFrom())) {
            throw BusinessException.badRequest("邮箱服务未配置");
        }
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(mailProperties.getMailFrom());
        message.setTo(candidate.getEmail());
        message.setSubject(request.title());
        message.setText(buildContent(request));
        mailSender.send(message);
        return new NotificationDtos.MailSyncResponse(true, candidate.getEmail());
    }

    private static String buildContent(NotificationDtos.MailSyncRequest request) {
        StringBuilder builder = new StringBuilder(request.content().trim());
        if (StringUtils.hasText(request.interviewTitle()) || StringUtils.hasText(request.scheduledAt())) {
            builder.append("\n\n");
            if (StringUtils.hasText(request.interviewTitle())) {
                builder.append("面试：").append(request.interviewTitle()).append('\n');
            }
            if (StringUtils.hasText(request.scheduledAt())) {
                builder.append("时间：").append(request.scheduledAt()).append('\n');
            }
        }
        return builder.toString();
    }
}
