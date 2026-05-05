package com.miltonbass.sgi_backend.reportes.controller;

import com.miltonbass.sgi_backend.reportes.dto.ResumenDtos.*;
import com.miltonbass.sgi_backend.reportes.service.ResumenService;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/eventos/{eventoId}/resumen")
@PreAuthorize("hasAnyRole('ADMIN_SEDE','PASTOR_SEDE')")
public class ResumenController {

    private final ResumenService resumenService;

    public ResumenController(ResumenService resumenService) {
        this.resumenService = resumenService;
    }

    @GetMapping
    public ResponseEntity<EventoResumenResponse> resumen(@PathVariable UUID eventoId) {
        return ResponseEntity.ok(resumenService.resumen(eventoId));
    }

    @GetMapping("/comparativa")
    public ResponseEntity<ComparativaResponse> comparativa(@PathVariable UUID eventoId) {
        return ResponseEntity.ok(resumenService.comparativa(eventoId));
    }

    @GetMapping("/exportar")
    public ResponseEntity<byte[]> exportar(@PathVariable UUID eventoId) {
        byte[] excel = resumenService.exportarExcel(eventoId);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
        headers.setContentDisposition(
                ContentDisposition.attachment()
                        .filename("asistencia-" + eventoId + ".xlsx")
                        .build());
        return ResponseEntity.ok().headers(headers).body(excel);
    }
}
