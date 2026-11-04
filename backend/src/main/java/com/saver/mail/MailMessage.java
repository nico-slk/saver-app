package com.saver.mail;

/** Email a enviar. {@code html} y {@code text} contienen lo mismo (el texto es el respaldo). */
public record MailMessage(String to, String toName, String subject, String text, String html) {}
