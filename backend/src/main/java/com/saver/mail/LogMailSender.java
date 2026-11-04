package com.saver.mail;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Desarrollo sin servicios externos: el email (con su link) queda en el log del backend. */
public class LogMailSender implements MailSender {

    private static final Logger log = LoggerFactory.getLogger(LogMailSender.class);

    @Override
    public void send(MailMessage m) {
        log.info("""

                ──────── EMAIL (app.mail.provider=log, no se envía) ────────
                Para:    {} <{}>
                Asunto:  {}

                {}
                ────────────────────────────────────────────────────────────""",
                m.toName(), m.to(), m.subject(), m.text());
    }
}
