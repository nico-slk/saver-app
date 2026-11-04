package com.saver.mail;

/** Puerto de envío de emails. Los adaptadores no deben loguear el contenido en producción. */
public interface MailSender {

    /** @throws MailException si el proveedor rechaza o no responde. */
    void send(MailMessage message);
}
