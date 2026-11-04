package com.saver.auth;

import com.saver.mail.MailMessage;
import org.springframework.web.util.HtmlUtils;

/** Email de restablecimiento de contraseña en el idioma del usuario (es por defecto, en). */
final class PasswordResetEmail {

    private PasswordResetEmail() {}

    static MailMessage build(String to, String name, String locale, String link, long minutes) {
        boolean en = "en".equals(locale);
        String subject = en ? "Reset your Saver password" : "Restablecé tu contraseña de Saver";
        String hello = en ? "Hi " + name + "," : "Hola " + name + ",";
        String body = en
                ? "We received a request to reset your password. Use the button below to choose a new one."
                : "Recibimos un pedido para restablecer tu contraseña. Usá el botón de abajo para elegir una nueva.";
        String button = en ? "Choose a new password" : "Elegir nueva contraseña";
        String expiry = en
                ? "The link is valid for " + minutes + " minutes and can be used only once."
                : "El link vale por " + minutes + " minutos y se puede usar una sola vez.";
        String ignore = en
                ? "If you didn't ask for this, ignore this email: your password won't change."
                : "Si no lo pediste, ignorá este email: tu contraseña no va a cambiar.";

        String text = String.join("\n\n", hello, body, link, expiry, ignore, "— Saver");
        String html = """
                <!doctype html><html><body style="margin:0;padding:24px;background:#f8fafc;font-family:Segoe UI,Roboto,Arial,sans-serif;color:#0f172a">
                <table role="presentation" width="100%%" style="max-width:520px;margin:0 auto;background:#ffffff;border:1px solid #e2e8f0;border-radius:12px">
                <tr><td style="padding:28px">
                <p style="margin:0 0 16px;font-size:20px;font-weight:700;color:#0f766e">Saver</p>
                <p style="margin:0 0 12px">%s</p>
                <p style="margin:0 0 20px;line-height:1.5">%s</p>
                <p style="margin:0 0 20px"><a href="%s" style="display:inline-block;background:#0f766e;color:#ffffff;text-decoration:none;padding:12px 20px;border-radius:8px;font-weight:600">%s</a></p>
                <p style="margin:0 0 8px;font-size:13px;color:#64748b">%s</p>
                <p style="margin:0 0 20px;font-size:13px;color:#64748b">%s</p>
                <p style="margin:0;font-size:12px;color:#94a3b8;word-break:break-all">%s</p>
                </td></tr></table></body></html>
                """.formatted(esc(hello), esc(body), esc(link), esc(button), esc(expiry), esc(ignore), esc(link));
        return new MailMessage(to, name, subject, text, html);
    }

    private static String esc(String s) {
        return HtmlUtils.htmlEscape(s);
    }
}
