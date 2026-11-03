package com.krath.utilisim.sentinel.warden.sec;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import static org.junit.jupiter.api.Assertions.*;
import java.util.NoSuchElementException;

class ApiExceptionHandlerTest {

    private final ApiExceptionHandler handler = new ApiExceptionHandler();

    @Test
    @DisplayName("authentication failures become 401 responses")
    void handlesAuthFailures() {
        var response = handler.handleAuthFailure(new BadCredentialsException("bad"));

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        assertEquals("Unauthorized", response.getBody().get("error"));
        assertEquals("Invalid email or password", response.getBody().get("message"));
    }

    @Test
    @DisplayName("unknown usernames also become 401 responses")
    void handlesUnknownUsers() {
        var response = handler.handleAuthFailure(new UsernameNotFoundException("missing"));

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
    }

    @Test
    @DisplayName("illegal arguments become 400 responses with their message")
    void handlesIllegalArgument() {
        var response = handler.handleBadRequest(new IllegalArgumentException("bad input"));

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("Bad Request", response.getBody().get("error"));
        assertEquals("bad input", response.getBody().get("message"));
    }

    @Test
    @DisplayName("missing illegal-argument messages fall back to a generic one")
    void handlesIllegalArgumentWithoutMessage() {
        var response = handler.handleBadRequest(new IllegalArgumentException());

        assertEquals("Invalid request", response.getBody().get("message"));
    }

    @Test
    @DisplayName("unreadable bodies become 400 responses")
    void handlesUnreadableBody() {
        var response = handler.handleUnreadableBody(
                new HttpMessageNotReadableException("bad", new org.springframework.mock.http.MockHttpInputMessage(new byte[0])));

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("Malformed JSON request body", response.getBody().get("message"));
    }

    @Test
    @DisplayName("missing resources become 404 responses")
    void handlesMissingResource() {
        var response = handler.handleNotFound(new NoResourceFoundException(
                org.springframework.http.HttpMethod.GET, "/api/nope", "No static resource api/nope."));

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertEquals("Not Found", response.getBody().get("error"));
        // NoResourceFoundException normalizes the resource path, so accept either
        // the leading-slash form or the stripped form.
        assertTrue(response.getBody().get("message").contains("api/nope"));
    }

    @Test
    @DisplayName("a missing domain resource becomes 404")
    void handlesMissingDomainResource() {
        var response = handler.handleMissing(new NoSuchElementException("Account not found"));

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertEquals("Not Found", response.getBody().get("error"));
        assertEquals("Account not found", response.getBody().get("message"));
    }

    @Test
    @DisplayName("unexpected exceptions become 500 responses without leaking details")
    void handlesUnexpectedException() {
        var response = handler.handleUnexpected(new RuntimeException("boom: internal class com.krath.Secret"));

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertEquals("Internal Server Error", response.getBody().get("error"));
        assertEquals("Unexpected error", response.getBody().get("message"));
        assertFalse(response.getBody().get("message").contains("boom"));
    }

    @Test
    @DisplayName("unexpected exceptions without a message use the same generic text")
    void handlesUnexpectedExceptionWithoutMessage() {
        var response = handler.handleUnexpected(new RuntimeException());

        assertEquals("Unexpected error", response.getBody().get("message"));
    }
}
