package com.miltonbass.sgi_backend.usuarios.service;

import com.miltonbass.sgi_backend.configuracion.service.ConfiguracionSmtpService;
import com.miltonbass.sgi_backend.configuracion.service.ConfiguracionSmtpService.SmtpActivo;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.util.Properties;

@Service
@Profile("prod")
public class ProdEmailService implements EmailService {

    private static final Logger log = LoggerFactory.getLogger(ProdEmailService.class);

    private final ConfiguracionSmtpService smtpService;
    private final String appUrl;

    public ProdEmailService(ConfiguracionSmtpService smtpService,
                            @Value("${sgi.app.url:http://localhost:4200}") String appUrl) {
        this.smtpService = smtpService;
        this.appUrl      = appUrl;
    }

    @Override
    public void enviarActivacion(String email, String nombre, String token) {
        smtpService.obtenerSmtpActivo().ifPresentOrElse(
                smtp -> enviarConSmtp(smtp, email, nombre, token),
                ()   -> log.info("[EMAIL PROD] SMTP no configurado — token activacion para {} ({}): {}",
                                 nombre, email, token));
    }

    @SuppressWarnings("null")
    private void enviarConSmtp(SmtpActivo smtp, String email, String nombre, String token) {
        try {
            JavaMailSenderImpl sender = buildSender(smtp);
            MimeMessage msg = sender.createMimeMessage();
            MimeMessageHelper h = new MimeMessageHelper(msg, false, "UTF-8");
            h.setFrom(smtp.remitente());
            h.setTo(email);
            h.setSubject("Bienvenido al Sistema SGI — Activa tu cuenta");
            h.setText(cuerpoActivacion(nombre, token), false);
            sender.send(msg);
            log.info("[EMAIL PROD] Activación enviada a {}", email);
        } catch (Exception e) {
            log.error("[EMAIL PROD] Error enviando activación a {}: {}", email, e.getMessage());
        }
    }

    private String cuerpoActivacion(String nombre, String token) {
        return "Hola " + nombre + ",\n\n"
             + "Tu acceso al Sistema de Gestión SGI ha sido creado.\n\n"
             + "Para activar tu cuenta visita:\n"
             + appUrl + "/activar?token=" + token + "\n\n"
             + "Si no solicitaste este acceso, ignora este mensaje.\n\n"
             + "— Equipo SGI";
    }

    private JavaMailSenderImpl buildSender(SmtpActivo smtp) {
        JavaMailSenderImpl s = new JavaMailSenderImpl();
        s.setHost(smtp.host());
        s.setPort(smtp.puerto());
        s.setUsername(smtp.usuario());
        s.setPassword(smtp.password());

        Properties p = s.getJavaMailProperties();
        p.put("mail.transport.protocol",     "smtp");
        p.put("mail.smtp.auth",              "true");
        p.put("mail.smtp.connectiontimeout", "5000");
        p.put("mail.smtp.timeout",           "5000");

        switch (smtp.cifrado()) {
            case "TLS" -> {
                p.put("mail.smtp.ssl.enable", "true");
                p.put("mail.smtp.ssl.trust",  smtp.host());
            }
            case "STARTTLS" -> {
                p.put("mail.smtp.starttls.enable",   "true");
                p.put("mail.smtp.starttls.required", "true");
            }
            default -> {}
        }
        return s;
    }
}
