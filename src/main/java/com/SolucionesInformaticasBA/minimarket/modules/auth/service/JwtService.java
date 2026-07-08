package com.SolucionesInformaticasBA.minimarket.modules.auth.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;

import javax.crypto.SecretKey;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

@Service
public class JwtService {

    private final String secret;
    private final long expirationMillis;

    public JwtService(
            @Value("${jwt.secret:minimarket-secret-key-2026}") String secret,
            @Value("${jwt.expiration-ms:28800000}") long expirationMillis) {
        this.secret = secret;
        this.expirationMillis = expirationMillis;
    }

    public String generateToken(String username, String role) {
        Instant now = Instant.now();
        Claims claims = Jwts.claims();
        claims.setSubject(username);
        claims.put("role", role);
        claims.setIssuedAt(Date.from(now));
        claims.setExpiration(Date.from(now.plus(Duration.ofMillis(expirationMillis))));

        return Jwts.builder()
                .setClaims(claims)
                .signWith(getSigningKey())
                .compact();
    }

    public String generatePasswordResetToken(String username) {
        Instant now = Instant.now();
        Claims claims = Jwts.claims();
        claims.setSubject(username);
        claims.put("action", "password_reset");
        claims.setIssuedAt(Date.from(now));
        claims.setExpiration(Date.from(now.plus(Duration.ofHours(1))));

        return Jwts.builder()
                .setClaims(claims)
                .signWith(getSigningKey())
                .compact();
    }

    public boolean isTokenValid(String token) {
        try {
            parseClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    public String extractUsername(String token) {
        return parseClaims(token).getSubject();
    }

    public String extractRole(String token) {
        Claims claims = parseClaims(token);
        return claims.get("role", String.class);
    }

    public String extractAction(String token) {
        Claims claims = parseClaims(token);
        return claims.get("action", String.class);
    }

    private Claims parseClaims(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(getSigningKey())
                .build()
                .parseClaimsJws(token)
                .getBody();
    }

    private SecretKey getSigningKey() {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] keyBytes = digest.digest(secret.getBytes(StandardCharsets.UTF_8));
            return Keys.hmacShaKeyFor(keyBytes);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("No se pudo inicializar la clave de firma JWT", e);
        }
    }
}
