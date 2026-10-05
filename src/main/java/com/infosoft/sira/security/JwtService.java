package com.infosoft.sira.security;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
@Service
public class JwtService {
    private final SecretKey key;
    public JwtService(@Value("${sira.jwt-secret}") String secret) { this.key=Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8)); }
    public String crearToken(String username) { Instant now=Instant.now(); return Jwts.builder().subject(username).issuedAt(Date.from(now)).expiration(Date.from(now.plusSeconds(3600))).signWith(key).compact(); }
    public String obtenerUsuario(String token) { return Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload().getSubject(); }
}
