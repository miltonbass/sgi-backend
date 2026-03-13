package com.miltonbass.sgi_backend.usuarios.service;

public interface EmailService {
    void enviarActivacion(String email, String nombre, String token);
}
