package com.infosoft.sira.invitacion;
import org.springframework.web.bind.annotation.*;
import java.util.Map;
@RestController
@RequestMapping("/api/v1/publico/invitaciones")
public class PublicInvitacionController {
 private final InvitacionController service;
 public PublicInvitacionController(InvitacionController service){this.service=service;}
 @PostMapping("/validar") public Map<String,Object> validar(@RequestBody Map<String,Object> p){return service.validar(p);}
}
