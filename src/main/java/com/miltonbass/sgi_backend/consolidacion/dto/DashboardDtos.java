package com.miltonbass.sgi_backend.consolidacion.dto;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class DashboardDtos {

    private DashboardDtos() {}

    public record DashboardResumen(
            int totalAsignados,
            /** Miembros con más de 7 días sin contacto o sin ningún contacto */
            int sinContactoReciente,
            /** Miembros cuya fechaProximaAccion ya pasó */
            int conAccionVencida,
            /** Contactos registrados hoy por este consolidador */
            int contactosHoy) {}

    public record MiembroConsolidacionItem(
            UUID miembroId,
            String nombres,
            String apellidos,
            String telefono,
            String estado,
            /** ALTA (>14 días o acción vencida) | MEDIA (7–14 días) | BAJA (<7 días) */
            String prioridad,
            LocalDate ultimoContactoFecha,
            String ultimoContactoTipo,
            String ultimoContactoResumen,
            /** null si nunca se ha contactado */
            Integer diasSinContacto,
            String proximaAccion,
            LocalDate fechaProximaAccion,
            /** true si fechaProximaAccion < hoy */
            boolean accionVencida,
            /** true si diasSinContacto > 7 o nunca se ha contactado */
            boolean alertaSinContacto) {}

    public record DashboardResponse(
            DashboardResumen resumen,
            List<MiembroConsolidacionItem> miembros,
            int pageNumber,
            int pageSize,
            long totalElements,
            int totalPages) {}
}
