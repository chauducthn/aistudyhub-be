package com.studyhub.aistudyhubbe.service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

@Service
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);

    private final JavaMailSender mailSender;
    private final boolean enabled;
    private final String from;

    public EmailService(
            JavaMailSender mailSender,
            @Value("${app.mail.enabled:true}") boolean enabled,
            @Value("${app.mail.from:no-reply@studyhub.local}") String from) {
        this.mailSender = mailSender;
        this.enabled = enabled;
        this.from = from;
    }

    public void sendPasswordResetEmail(String toEmail, String resetLink) {
        if (!enabled) {
            log.info("Mail disabled — reset link for {}: {}", toEmail, resetLink);
            return;
        }

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, "UTF-8");
            helper.setFrom(from);
            helper.setTo(toEmail);
            helper.setSubject("Reset your AI Study Hub password");
            helper.setText(buildHtml(resetLink), true);
            mailSender.send(message);
            log.info("Password reset email sent to {}", toEmail);
        } catch (MessagingException ex) {
            log.error("Failed to send password reset email to {}", toEmail, ex);
            throw new IllegalStateException("Could not send password reset email", ex);
        }
    }

    private String buildHtml(String resetLink) {
        return """
                <div style="font-family:Inter,Arial,sans-serif;max-width:480px;margin:auto;padding:24px;color:#0b1c30">
                  <h2 style="color:#3525cd">Reset your password</h2>
                  <p>We received a request to reset your AI Study Hub password. Click the button below to choose a new password. This link expires in 30 minutes.</p>
                  <p style="text-align:center;margin:32px 0">
                    <a href="%s" style="background:#3525cd;color:#fff;text-decoration:none;padding:12px 28px;border-radius:10px;font-weight:bold">Reset Password</a>
                  </p>
                  <p style="font-size:13px;color:#74798a">If the button does not work, copy this link into your browser:</p>
                  <p style="font-size:13px;word-break:break-all"><a href="%s">%s</a></p>
                  <p style="font-size:13px;color:#74798a">If you did not request this, you can safely ignore this email.</p>
                </div>
                """.formatted(resetLink, resetLink, resetLink);
    }
}
