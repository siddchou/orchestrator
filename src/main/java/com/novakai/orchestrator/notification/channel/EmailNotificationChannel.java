package com.novakai.orchestrator.notification.channel;

import com.novakai.orchestrator.engine.spi.FieldDefinition;
import com.novakai.orchestrator.engine.spi.FieldType;
import com.novakai.orchestrator.notification.spi.ChannelConfig;
import com.novakai.orchestrator.notification.spi.ChannelConfigSchema;
import com.novakai.orchestrator.notification.spi.NotificationChannel;
import com.novakai.orchestrator.notification.spi.NotificationEvent;
import com.novakai.orchestrator.notification.spi.NotificationException;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@ConditionalOnBean(JavaMailSender.class)
@RequiredArgsConstructor
public class EmailNotificationChannel implements NotificationChannel {

    private final JavaMailSender mailSender;

    @Override
    public String getType() {
        return "EMAIL";
    }

    @Override
    public void send(NotificationEvent event, ChannelConfig config) throws NotificationException {
        List<String> recipients = config.getList("recipients");
        if (recipients.isEmpty()) {
            throw new NotificationException("Missing required config field: recipients");
        }

        String fromAddress = config.getString("fromAddress");

        try {
            MimeMessage msg = mailSender.createMimeMessage();
            // Note: multipart=false; for plain text use MimeMessageHelper. For simplicity, use SimpleMailMessage pattern via helper.
            MimeMessageHelper helper = new MimeMessageHelper(msg, false, "UTF-8");

            if (fromAddress != null) {
                helper.setFrom(fromAddress);
            }
            helper.setTo(recipients.toArray(new String[0]));
            helper.setSubject(String.format("[%s] Job '%s' completed", event.status(), event.jobName()));
            helper.setText(buildBody(event), false);

            mailSender.send(msg);
        } catch (MessagingException e) {
            throw new NotificationException("Failed to send email: " + e.getMessage(), e);
        }
    }

    private String buildBody(NotificationEvent event) {
        return String.format(
            "Job Run Completed\n" +
            "------------------\n" +
            "Job: %s\n" +
            "Run ID: %d\n" +
            "Status: %s\n" +
            "Completed At: %s\n" +
            "Triggered By: %s",
            event.jobName(), event.runId(), event.status(),
            event.completedAt(), event.triggeredBy()
        );
    }

    @Override
    public ChannelConfigSchema getConfigSchema() {
        return new ChannelConfigSchema("EMAIL", List.of(
            new FieldDefinition("recipients", "Recipients", FieldType.LIST_STRING, true, null, null,
                "Comma-separated email addresses"),
            new FieldDefinition("fromAddress", "From Address", FieldType.STRING, false, null, null,
                "Override the default SMTP from address")
        ));
    }
}
