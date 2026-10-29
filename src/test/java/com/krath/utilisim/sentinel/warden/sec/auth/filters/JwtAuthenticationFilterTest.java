package com.krath.utilisim.sentinel.warden.sec.auth.filters;

import com.krath.utilisim.sentinel.warden.sec.auth.service.JwtService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class JwtAuthenticationFilterTest {

    private JwtService jwtService;
    private UserDetailsService userDetailsService;
    private JwtAuthenticationFilter filter;

    @BeforeEach
    void setUp() {
        jwtService = mock(JwtService.class);
        userDetailsService = mock(UserDetailsService.class);
        filter = new JwtAuthenticationFilter(jwtService, userDetailsService);
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private MockHttpServletRequest request(String authorization) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        if (authorization != null) {
            request.addHeader("Authorization", authorization);
        }
        return request;
    }

    @Test
    @DisplayName("requests without a bearer token pass through")
    void passesThroughWithoutHeader() throws ServletException, IOException {
        FilterChain chain = mock(FilterChain.class);
        MockHttpServletRequest request = request(null);
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verifyNoInteractions(jwtService, userDetailsService);
    }

    @Test
    @DisplayName("a non-bearer authorization header passes through")
    void passesThroughForBasicAuth() throws ServletException, IOException {
        FilterChain chain = mock(FilterChain.class);
        MockHttpServletRequest request = request("Basic abc");

        filter.doFilter(request, new MockHttpServletResponse(), chain);

        verify(chain).doFilter(any(), any());
        verifyNoInteractions(jwtService, userDetailsService);
    }

    @Test
    @DisplayName("a valid token authenticates the request")
    void authenticatesWithValidToken() throws ServletException, IOException {
        UserDetails user = User.withUsername("ada@example.com").password("x").authorities("ROLE_USER").build();
        when(jwtService.extractEmail("token")).thenReturn("ada@example.com");
        when(userDetailsService.loadUserByUsername("ada@example.com")).thenReturn(user);
        when(jwtService.isTokenValid("token", user)).thenReturn(true);

        FilterChain chain = mock(FilterChain.class);
        filter.doFilter(request("Bearer token"), new MockHttpServletResponse(), chain);

        var authentication = SecurityContextHolder.getContext().getAuthentication();
        assertNotNull(authentication);
        assertEquals(user, authentication.getPrincipal());
        assertTrue(authentication.isAuthenticated());
        verify(chain).doFilter(any(), any());
    }

    @Test
    @DisplayName("an invalid token leaves the request unauthenticated")
    void ignoresInvalidToken() throws ServletException, IOException {
        UserDetails user = User.withUsername("ada@example.com").password("x").build();
        when(jwtService.extractEmail("token")).thenReturn("ada@example.com");
        when(userDetailsService.loadUserByUsername("ada@example.com")).thenReturn(user);
        when(jwtService.isTokenValid("token", user)).thenReturn(false);

        FilterChain chain = mock(FilterChain.class);
        filter.doFilter(request("Bearer token"), new MockHttpServletResponse(), chain);

        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verify(chain).doFilter(any(), any());
    }

    @Test
    @DisplayName("a null email leaves the request unauthenticated")
    void ignoresTokenWithoutEmail() throws ServletException, IOException {
        when(jwtService.extractEmail("token")).thenReturn(null);

        FilterChain chain = mock(FilterChain.class);
        filter.doFilter(request("Bearer token"), new MockHttpServletResponse(), chain);

        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verifyNoInteractions(userDetailsService);
    }

    @Test
    @DisplayName("an existing authentication is not replaced")
    void keepsExistingAuthentication() throws ServletException, IOException {
        var existing = new UsernamePasswordAuthenticationToken("already", null, java.util.List.of());
        SecurityContextHolder.getContext().setAuthentication(existing);
        when(jwtService.extractEmail("token")).thenReturn("ada@example.com");

        FilterChain chain = mock(FilterChain.class);
        filter.doFilter(request("Bearer token"), new MockHttpServletResponse(), chain);

        assertSame(existing, SecurityContextHolder.getContext().getAuthentication());
        verifyNoInteractions(userDetailsService);
    }
}
