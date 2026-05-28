package com.happyim.user.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
public class MailService {

    private static final Logger log = LoggerFactory.getLogger(MailService.class);

    private final JavaMailSender mailSender;

    @Value("${spring.mail.username}")
    private String from;

    public MailService(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    public void sendVerificationCode(String to, String code) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(from);
            message.setTo(to);
            message.setSubject("HappyIM 邮箱验证码");
            message.setText("您的验证码是: " + code + "，有效期5分钟。请勿将验证码透露给他人。");
            mailSender.send(message);
            log.info("验证码已发送至 {}", to);
        } catch (Exception e) {
            log.error("发送邮件失败: {}", to, e);
            throw new RuntimeException("邮件发送失败", e);
        }
    }
}
