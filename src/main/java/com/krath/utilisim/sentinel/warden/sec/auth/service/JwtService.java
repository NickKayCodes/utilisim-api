package com.krath.utilisim.sentinel.warden.sec.auth.service;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Service
public class JwtService {

    private static final Logger log = LoggerFactory.getLogger(JwtService.class);

    /** Minimum HS256 key length in bytes (RFC 7518 requires >= 256 bits). */
    static final int MIN_SECRET_BYTES = 32;

    private static final long EXPIRY_MILLIS = 86_400_000L * 10; // 10 days

    private final SecretKey key;

    /**
     * Configured key, so tokens survive a restart. Set the {@code JWT_SECRET}
     * environment variable (or {@code utilisim.security.jwt.secret}). When it is
     * absent — local development and tests — an ephemeral key is generated and a
     * warning is logged.
     */
    @Autowired
    public JwtService(@Value("${utilisim.security.jwt.secret:${JWT_SECRET:}}") String secret) {
        this.key = buildKey(secret);
    }

    /** Convenience constructor for tests and local tools: ephemeral key. */
    public JwtService() {
        this("");
    }

    private static SecretKey buildKey(String secret) {
        if (secret == null || secret.isBlank()) {
            log.warn("JWT_SECRET is not configured; using an ephemeral key. "
                    + "Tokens will be invalidated on every restart.");
            return Jwts.SIG.HS256.key().build();
        }

        byte[] bytes = secret.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < MIN_SECRET_BYTES) {
            throw new IllegalStateException(
                    "JWT secret must be at least " + MIN_SECRET_BYTES
                            + " bytes (got " + bytes.length + "). Generate one with: openssl rand -base64 48");
        }
        return Keys.hmacShaKeyFor(bytes);
    }

    public String extractEmail(String token) {
        return Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload().getSubject();
    }

    public String generateToken(UserDetails userDetails) {
        return Jwts.builder()
                .subject(userDetails.getUsername())
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + EXPIRY_MILLIS))
                .signWith(key)
                .compact();
    }

    //is token valid
    public boolean isTokenValid(String token, UserDetails userDetails) {
        String email = extractEmail(token);
        return email.equals(userDetails.getUsername()) && !isTokenExpired(token);
    }

    //check if token is expired, send token expiry date back
    public boolean isTokenExpired(String token) {
        Date expiry = Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload().getExpiration();
        return expiry.before(new Date());
    }
}
