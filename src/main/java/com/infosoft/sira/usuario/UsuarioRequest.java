package com.infosoft.sira.usuario;
import jakarta.validation.constraints.*;
public record UsuarioRequest(@NotNull Long personaId,@NotBlank String username,@NotBlank @Email String correoAcceso,@NotBlank @Size(min=8) String password){}
