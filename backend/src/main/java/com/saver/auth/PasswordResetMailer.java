package com.saver.auth;

import com.saver.auth.PasswordResetService.ResetRequested;
import com.saver.mail.MailSender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Envía el email después del commit y en segundo plano: la respuesta HTTP no espera al proveedor
 * (y tarda lo mismo exista o no la cuenta).
 */
@Component
class PasswordResetMailer {

    private static final Logger log = LoggerFactory.getLogger(PasswordResetMailer.class);

    private final MailSender mail;

    PasswordResetMailer(MailSender mail) {
        this.mail = mail;
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onResetRequested(ResetRequested event) {
        try {
            mail.send(event.message());
        } catch (RuntimeException e) {
            log.error("Could not send password reset email: {}", e.getMessage());
        }
    }
}
