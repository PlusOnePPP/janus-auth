package com.janus.client.interceptor;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;

class JanusMonitorAuthInterceptorTest {

    private JanusMonitorAuthInterceptor interceptor;

    @BeforeEach
    void setUp() {
        interceptor = new JanusMonitorAuthInterceptor("admin", "secret123");
    }

    @Test
    @DisplayName("preHandle: POST /sso/janus/openApi/notify 豁免 Basic 认证（HMAC 签名保护）")
    void preHandle_postNotify_bypassesBasicAuth() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/sso/janus/openApi/notify");
        MockHttpServletResponse response = new MockHttpServletResponse();

        boolean allowed = interceptor.preHandle(request, response, new Object());

        assertThat(allowed).isTrue();
        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    @DisplayName("preHandle: GET /sso/janus/openApi/notify 无凭证时被拒绝并返回 401")
    void preHandle_getNotify_withoutCredentials_returns401() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/sso/janus/openApi/notify");
        MockHttpServletResponse response = new MockHttpServletResponse();

        boolean allowed = interceptor.preHandle(request, response, new Object());

        assertThat(allowed).isFalse();
        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getHeader("WWW-Authenticate")).contains("Basic realm=");
    }

    @Test
    @DisplayName("preHandle: GET /sso/janus/openApi/notify 正确 Basic 凭据允许访问")
    void preHandle_getNotify_withValidCredentials_allowed() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/sso/janus/openApi/notify");
        String creds = Base64.getEncoder().encodeToString("admin:secret123".getBytes(StandardCharsets.UTF_8));
        request.addHeader("Authorization", "Basic " + creds);
        MockHttpServletResponse response = new MockHttpServletResponse();

        boolean allowed = interceptor.preHandle(request, response, new Object());

        assertThat(allowed).isTrue();
    }
}
