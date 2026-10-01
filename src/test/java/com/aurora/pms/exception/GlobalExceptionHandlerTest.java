package com.aurora.pms.exception;

import static org.assertj.core.api.Assertions.assertThat;

import com.aurora.pms.dto.response.ApiErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.server.ResponseStatusException;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void conflictExceptionReturns409WithApiErrorResponse() {
        ResponseEntity<ApiErrorResponse> response = handler.handleConflict(
                new ConflictException("Room is already occupied"),
                request("/api/v1/rooms/1")
        );

        assertError(response, HttpStatus.CONFLICT, "Room is already occupied", "/api/v1/rooms/1");
    }

    @Test
    void accessDeniedExceptionReturns403WithApiErrorResponse() {
        ResponseEntity<ApiErrorResponse> response = handler.handleAccessDenied(
                new AccessDeniedException("Forbidden"),
                request("/api/v1/admin")
        );

        assertError(response, HttpStatus.FORBIDDEN, "Forbidden", "/api/v1/admin");
    }

    @Test
    void httpMethodNotSupportedReturns405WithApiErrorResponse() {
        ResponseEntity<ApiErrorResponse> response = handler.handleHttpRequestMethodNotSupported(
                new HttpRequestMethodNotSupportedException("POST", List.of("GET")),
                request("/api/v1/health")
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.METHOD_NOT_ALLOWED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().status()).isEqualTo(HttpStatus.METHOD_NOT_ALLOWED.value());
        assertThat(response.getBody().error()).isEqualTo(HttpStatus.METHOD_NOT_ALLOWED.getReasonPhrase());
        assertThat(response.getBody().message()).contains("POST");
        assertThat(response.getBody().path()).isEqualTo("/api/v1/health");
    }

    @Test
    void responseStatusExceptionPreservesKnownHttpStatus() {
        ResponseEntity<ApiErrorResponse> response = handler.handleResponseStatus(
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Endpoint not found"),
                request("/missing")
        );

        assertError(response, HttpStatus.NOT_FOUND, "Endpoint not found", "/missing");
    }

    @Test
    void unexpectedExceptionReturns500WithoutSensitiveMessage() {
        ResponseEntity<ApiErrorResponse> response = handler.handleUnexpectedException(
                new IllegalStateException("database password leaked"),
                request("/api/v1/bookings")
        );

        assertError(response, HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred", "/api/v1/bookings");
    }

    private HttpServletRequest request(String uri) {
        return new MockHttpServletRequest("GET", uri);
    }

    private void assertError(
            ResponseEntity<ApiErrorResponse> response,
            HttpStatus status,
            String message,
            String path
    ) {
        assertThat(response.getStatusCode()).isEqualTo(status);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().status()).isEqualTo(status.value());
        assertThat(response.getBody().error()).isEqualTo(status.getReasonPhrase());
        assertThat(response.getBody().message()).isEqualTo(message);
        assertThat(response.getBody().path()).isEqualTo(path);
    }
}
