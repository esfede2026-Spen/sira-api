package com.infosoft.sira.common;
import org.springframework.dao.DataAccessException;import org.springframework.http.*;import org.springframework.web.bind.MethodArgumentNotValidException;import org.springframework.web.bind.annotation.*;import org.springframework.web.server.ResponseStatusException;import java.util.*;
@RestControllerAdvice public class ApiExceptionHandler{
@ExceptionHandler(ResponseStatusException.class) ResponseEntity<Map<String,Object>> status(ResponseStatusException e){return ResponseEntity.status(e.getStatusCode()).body(Map.of("error",true,"mensaje",String.valueOf(e.getReason())));}
@ExceptionHandler(MethodArgumentNotValidException.class) ResponseEntity<Map<String,Object>> validacion(MethodArgumentNotValidException e){return ResponseEntity.badRequest().body(Map.of("error",true,"mensaje","Existen datos requeridos o inválidos"));}
@ExceptionHandler(DataAccessException.class) ResponseEntity<Map<String,Object>> bd(DataAccessException e){String c=e.getMostSpecificCause()==null?e.getMessage():e.getMostSpecificCause().getMessage();return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error",true,"mensaje","Error de base de datos: "+c));}}
