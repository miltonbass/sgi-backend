// src/main/java/com/miltonbass/sgi_backend/usuarios/service/ProdEmailService.java
package com.miltonbass.sgi_backend.usuarios.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@Service
@Profile("prod")
public class ProdEmailService implements EmailService {

    private static final Logger log = LoggerFactory.getLogger(ProdEmailService.class);

    @Override
    public void enviarActivacion(String email, String nombre, String token) {
        // TODO: integrar SMTP real (SendGrid, SES, etc.) en historia futura
        log.info("=================================================");
        log.info("[EMAIL PROD] Para: {} ({})", nombre, email);
        log.info("[EMAIL PROD] Token activacion: {}", token);
        log.info("[EMAIL PROD] PENDIENTE: configurar SMTP real");
        log.info("=================================================");
    }
}