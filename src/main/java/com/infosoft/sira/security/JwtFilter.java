package com.infosoft.sira.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
import java.util.List;

@Component
public class JwtFilter extends OncePerRequestFilter {
    private final JwtService jwtService;
    public JwtFilter(JwtService jwtService){this.jwtService=jwtService;}

    @Override
    protected boolean shouldNotFilter(HttpServletRequest req) {
        String p=req.getRequestURI();
        return "OPTIONS".equalsIgnoreCase(req.getMethod())
            || p.startsWith("/api/v1/auth/")
            || p.startsWith("/api/v1/publico/")
            || p.startsWith("/v3/api-docs/")
            || p.startsWith("/swagger-ui/")
            || "/swagger-ui.html".equals(p);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req,HttpServletResponse res,FilterChain chain)
            throws ServletException,IOException {
        String h=req.getHeader("Authorization");
        if(h!=null&&h.startsWith("Bearer ")) {
            try {
                String u=jwtService.obtenerUsuario(h.substring(7));
                SecurityContextHolder.getContext().setAuthentication(
                    new UsernamePasswordAuthenticationToken(u,null,List.of())
                );
            } catch(RuntimeException ex) {
                SecurityContextHolder.clearContext();
            }
        }
        chain.doFilter(req,res);
    }
}
