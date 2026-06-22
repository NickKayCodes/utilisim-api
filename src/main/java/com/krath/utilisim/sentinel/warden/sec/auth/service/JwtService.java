package com.krath.utilisim.sentinel.warden.sec.auth.service;

import io.jsonwebtoken.Jwt;
import org.springframework.stereotype.Service;

import java.security.Key;
import java.util.Date;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.springframework.security.core.userdetails.UserDetails;


import javax.crypto.SecretKey;

@Service
public class JwtService {
    private final SecretKey key = Jwts.SIG.HS256.key().build();

    public String extractEmail(String token){
        return Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload().getSubject();
    }

    public String generateToken(UserDetails userDetails){
        return Jwts.builder().subject(userDetails.getUsername()).issuedAt(new Date()).expiration(new Date(System.currentTimeMillis()+864000000)).signWith(key).compact();
    }

    //is token valid
    public boolean isTokenValid(String token, UserDetails userDetails){
        String email = extractEmail(token);
        return email.equals(userDetails.getUsername()) && !isTokenExpired(token);
    }

    //check if token is expired, send token expiry date back
    public boolean isTokenExpired(String token){
        Date expiry = Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload().getExpiration();
        return expiry.before(new Date());
    }
}
