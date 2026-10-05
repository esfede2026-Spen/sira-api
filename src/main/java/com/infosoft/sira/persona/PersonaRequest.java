package com.infosoft.sira.persona;

import jakarta.validation.constraints.NotBlank;
import java.time.LocalDate;

public record PersonaRequest(
        Integer paisEmisorId,
        Integer tipoDocumentoId,
        @NotBlank String documentoNumero,
        String primerNombre,
        String segundoNombre,
        String primerApellido,
        String segundoApellido,
        String aliasApodo,
        LocalDate fechaNacimiento,
        Long nacionalidadId,
        Long nacionalidadCatalogoId,
        Boolean consentimientoDatos,
        Integer sexoId,
        Long referidoId) {
}
