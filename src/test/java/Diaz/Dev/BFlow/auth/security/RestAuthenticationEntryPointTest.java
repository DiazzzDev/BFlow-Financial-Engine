package Diaz.Dev.BFlow.auth.security;

import bflow.auth.security.RestAuthenticationEntryPoint;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RestAuthenticationEntryPointTest {

    private final ObjectMapper objectMapper =
            new ObjectMapper().registerModule(new JavaTimeModule());

    @Test
    void respondsWithUnauthorizedForInvalidCredentials() throws Exception {
        RestAuthenticationEntryPoint entryPoint =
                new RestAuthenticationEntryPoint(objectMapper);
        MockHttpServletRequest request = request();
        MockHttpServletResponse response = new MockHttpServletResponse();

        entryPoint.commence(
                request, response, new BadCredentialsException("bad token"));

        assertEquals(401, response.getStatus());
        assertEquals("application/json", response.getContentType());
        JsonNode body = objectMapper.readTree(response.getContentAsString());
        assertEquals("Authentication required", body.get("message").asText());
        assertEquals("/private/resource", body.get("path").asText());
    }

    @Test
    void respondsWithServiceUnavailableAndThrottlesRepeatedInfrastructureErrors()
            throws Exception {
        RestAuthenticationEntryPoint entryPoint =
                new RestAuthenticationEntryPoint(objectMapper);
        ReflectionTestUtils.setField(entryPoint, "throttleSeconds", 3600L);
        MockHttpServletRequest request = request();
        MockHttpServletResponse firstResponse = new MockHttpServletResponse();
        MockHttpServletResponse repeatedResponse = new MockHttpServletResponse();

        entryPoint.commence(
                request, firstResponse,
                new AuthenticationServiceException("JWKS unavailable"));
        entryPoint.commence(
                request, repeatedResponse,
                new AuthenticationServiceException("JWKS unavailable"));

        assertEquals(503, firstResponse.getStatus());
        assertEquals(503, repeatedResponse.getStatus());
        JsonNode body = objectMapper.readTree(
                repeatedResponse.getContentAsString());
        assertEquals(
                "Authentication is temporarily unavailable; "
                        + "please try again shortly",
                body.get("message").asText());
        assertEquals(1, ((AtomicInteger) ReflectionTestUtils.getField(
                entryPoint, "suppressedCount")).get());
    }

    private MockHttpServletRequest request() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setMethod("GET");
        request.setRequestURI("/private/resource");
        return request;
    }
}
