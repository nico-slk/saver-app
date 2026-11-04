package com.saver.flow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.saver.mail.MailMessage;
import com.saver.mail.MailSender;
import com.saver.support.IntegrationTest;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;

@Import(PasswordResetFlowTest.CapturingMail.class)
class PasswordResetFlowTest extends IntegrationTest {

    static final BlockingQueue<MailMessage> OUTBOX = new LinkedBlockingQueue<>();

    @TestConfiguration
    static class CapturingMail {
        @Bean
        @Primary
        MailSender capturingMailSender() {
            return OUTBOX::add;
        }
    }

    private void forgot(String email) throws Exception {
        mvc.perform(post("/api/auth/password/forgot").contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(Map.of("email", email))))
                .andExpect(status().isNoContent());
    }

    private String tokenFrom(MailMessage m) {
        Matcher matcher = Pattern.compile("reset-password\\?token=([^\\s\"&<]+)").matcher(m.text());
        assertThat(matcher.find()).isTrue();
        return URLDecoder.decode(matcher.group(1), StandardCharsets.UTF_8);
    }

    @Test
    void resetFlowSendsEmailChangesPasswordAndInvalidatesToken() throws Exception {
        OUTBOX.clear();
        String email = "reset-" + UUID.randomUUID() + "@saver.test";
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content(toJson(Map.of("email", email, "password", "password123", "displayName", "Ana", "locale", "es"))))
                .andExpect(status().isCreated());

        forgot(email.toUpperCase());
        MailMessage mail = OUTBOX.poll(5, TimeUnit.SECONDS);
        assertThat(mail).isNotNull();
        assertThat(mail.to()).isEqualTo(email);
        assertThat(mail.subject()).contains("contraseña");
        assertThat(mail.text()).contains("http://localhost:5173/reset-password?token=");
        String token = tokenFrom(mail);

        mvc.perform(post("/api/auth/password/reset").contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(Map.of("token", token, "password", "short"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.password").value("auth.password_length"));

        mvc.perform(post("/api/auth/password/reset").contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(Map.of("token", token, "password", "nueva-clave-123"))))
                .andExpect(status().isNoContent());

        // El token no se puede reutilizar
        mvc.perform(post("/api/auth/password/reset").contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(Map.of("token", token, "password", "otra-clave-456"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("auth.reset_invalid"));

        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(Map.of("email", email, "password", "password123"))))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(Map.of("email", email, "password", "nueva-clave-123"))))
                .andExpect(status().isOk());
    }

    @Test
    void unknownEmailGetsSameResponseAndNoEmail() throws Exception {
        OUTBOX.clear();
        forgot("nadie-" + UUID.randomUUID() + "@saver.test");
        assertThat(OUTBOX.poll(1, TimeUnit.SECONDS)).isNull();
    }

    @Test
    void newRequestInvalidatesPreviousTokenAndRequestsAreRateLimited() throws Exception {
        OUTBOX.clear();
        String email = "rl-" + UUID.randomUUID() + "@saver.test";
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content(toJson(Map.of("email", email, "password", "password123", "displayName", "Bob", "locale", "en"))))
                .andExpect(status().isCreated());

        forgot(email);
        String first = tokenFrom(OUTBOX.poll(5, TimeUnit.SECONDS));
        forgot(email);
        MailMessage second = OUTBOX.poll(5, TimeUnit.SECONDS);
        assertThat(second.subject()).isEqualTo("Reset your Saver password");

        mvc.perform(post("/api/auth/password/reset").contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(Map.of("token", first, "password", "nueva-clave-123"))))
                .andExpect(status().isBadRequest());

        forgot(email);
        assertThat(OUTBOX.poll(5, TimeUnit.SECONDS)).isNotNull();
        forgot(email); // 4.º pedido en 15 minutos: no se envía
        assertThat(OUTBOX.poll(1, TimeUnit.SECONDS)).isNull();
    }

    @Test
    void invalidTokenIsRejected() throws Exception {
        mvc.perform(post("/api/auth/password/reset").contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(Map.of("token", "no-existe", "password", "nueva-clave-123"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("auth.reset_invalid"));
    }
}
