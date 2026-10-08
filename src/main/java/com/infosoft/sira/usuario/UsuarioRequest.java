package com.infosoft.sira.usuario;
import jakarta.validation.constraints.*;
import java.util.List;
public record UsuarioRequest(@NotNull Long personaId,@NotBlank String username,@Email String correoAcceso,String password,List<Long> roles,String estado,Boolean bloqueado){}
