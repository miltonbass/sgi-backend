// MockEmailService.java
package com.miltonbass.sgi_backend.usuarios.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@Service
@Profile("dev")
public class MockEmailService implements EmailService {

    private static final Logger log = LoggerFactory.getLogger(MockEmailService.class);

    @Override
    public void enviarActivacion(String email, String nombre, String token) {
        log.info("=================================================");
        log.info("[MOCK EMAIL] Para: {} ({})", nombre, email);
        log.info("[MOCK EMAIL] Link activacion:");
        log.info("[MOCK EMAIL] http://localhost:4200/activar?token={}", token);
        log.info("=================================================");
    }
}