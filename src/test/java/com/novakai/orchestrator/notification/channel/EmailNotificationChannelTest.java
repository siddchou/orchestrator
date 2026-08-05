package com.novakai.orchestrator.notification.channel;

import com.novakai.orchestrator.domain.enums.RunStatus;
import com.novakai.orchestrator.notification.spi.ChannelConfig;
import com.novakai.orchestrator.notification.spi.NotificationEvent;
import com.novakai.orchestrator.notification.spi.NotificationException;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class EmailNotificationChannelTest {

    @Mock
    private JavaMailSender mailSender;

    private EmailNotificationChannel channel;

    void setUpChannel() {
        MimeMessage mockMsg = mock(MimeMessage.class);
        when(mailSender.createMimeMessage()).thenReturn(mockMsg);
        doNothing().when(mailSender).send(any(MimeMessage.class));
        channel = new EmailNotificationChannel(mailSender);
    }

    private NotificationEvent createEvent(RunStatus status) {
        return new NotificationEvent(
                100L, 5L, "Test Job", status,
                LocalDateTime.of(2026, 7, 31, 12, 0), "system"
        );
    }

    @Test
    void getType_returns_EMAIL() {
        setUpChannel();
        assertEquals("EMAIL", channel.getType());
    }

    @Test
    void send_throws_when_recipients_missing() {
        setUpChannel();
        ChannelConfig config = new ChannelConfig(Map.of());

        NotificationException ex = assertThrows(NotificationException.class,
                () -> channel.send(createEvent(RunStatus.SUCCESS), config));
        assertTrue(ex.getMessage().contains("recipients"));
    }

    @Test
    void send_calls_mail_sender() {
        setUpChannel();

        NotificationEvent event = createEvent(RunStatus.FAILED);
        ChannelConfig config = new ChannelConfig(Map.of("recipients", List.of("test@example.com")));

        channel.send(event, config);

        verify(mailSender, times(1)).send(any(MimeMessage.class));
    }

    @Test
    void send_wraps_messaging_exception() {
        // Override stub to throw on createMimeMessage
        when(mailSender.createMimeMessage()).thenAnswer(inv -> { throw new jakarta.mail.MessagingException("SMTP down"); });

        channel = new EmailNotificationChannel(mailSender);

        NotificationEvent event = createEvent(RunStatus.SUCCESS);
        ChannelConfig config = new ChannelConfig(Map.of("recipients", List.of("a@b.com")));

        NotificationException ex = assertThrows(NotificationException.class,
                () -> channel.send(event, config));
        assertTrue(ex.getMessage().contains("Failed to send email"));
        assertNotNull(ex.getCause());
    }
}
