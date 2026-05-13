package com.miltonbass.sgi_backend.reportes.dto;

import java.util.List;
import java.util.UUID;

public final class CrecimientoRetencionDtos {

    private CrecimientoRetencionDtos() {}

    public record DesgloseBajas(int inactividad, int traslado, int sinContacto) {}

    public record PeriodoRetencion(
            String periodo,
            String label,
            int miembrosInicio,
            int miembrosFin,
            int altas,
            int bajas,
            Double tasaRetencion,
            DesgloseBajas desgloseBajas) {}

    public record CrecimientoRetencionResponse(
            List<PeriodoRetencion> periodos,
            int meses,
            UUID sedeId,
            String sedeNombre) {}
}
