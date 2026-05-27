package com.miltonbass.sgi_backend.auditoria.dto;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class AuditoriaDtos {

    public record AuditoriaGeneralResponse(
            UUID                 id,
            String               usuarioEmail,
            String               modulo,
            String               accion,
            String               entidadId,
            String               endpoint,
            String               metodoHttp,
            Map<String, Object>  detalle,
            String               ip,
            String               resultado,
            String               errorMensaje,
            Instant              realizadoEn
    ) {}

    public record AuditoriaPageResponse(
            List<AuditoriaGeneralResponse> registros,
            int    pagina,
            int    tamano,
            long   total,
            int    totalPaginas
    ) {}
}
