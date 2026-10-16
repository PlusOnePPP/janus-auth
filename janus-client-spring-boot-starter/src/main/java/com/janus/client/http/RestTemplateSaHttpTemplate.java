package com.janus.client.http;

import cn.dev33.satoken.http.SaHttpTemplate;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.util.Map;

/**
 * 基于 RestTemplate 的 Sa-Token HTTP 请求模板。
 *
 * <p>Sa-Token 1.45.x 自带的 SaHttpTemplateDefaultImpl 仅为占位实现（调用即抛
 * NotImplException），SSO 模式三的 HTTP 验票 / getData 必须由接入方提供真实
 * HTTP 处理器。本类使用 spring-web 自带的 RestTemplate，业务系统零额外依赖。</p>
 *
 * <p>内网部署认证中心常使用自签或企业内部 CA 证书（且常通过 IP 直接访问），
 * 因此内置请求工厂对 HTTPS 连接默认放行自签证书与主机名校验（仅作用于本客户端通道，
 * 不修改 JVM 全局 SSLSocketFactory）。</p>
 *
 * @author ppp
 */
@Slf4j
public class RestTemplateSaHttpTemplate implements SaHttpTemplate {

    private static final SSLSocketFactory TRUST_ALL_SSL_FACTORY = buildTrustAllSslSocketFactory();

    private final RestTemplate restTemplate;

    public RestTemplateSaHttpTemplate() {
        this.restTemplate = createRestTemplate(5_000, 5_000);
    }

    /**
     * 创建支持自签 HTTPS 证书（跳过证书链与主机名校验）的 RestTemplate。
     */
    public static RestTemplate createRestTemplate(int connectTimeoutMs, int readTimeoutMs) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory() {
            @Override
            protected void prepareConnection(HttpURLConnection connection, String httpMethod) throws IOException {
                if (connection instanceof HttpsURLConnection https) {
                    https.setSSLSocketFactory(TRUST_ALL_SSL_FACTORY);
                    https.setHostnameVerifier((hostname, session) -> true);
                }
                super.prepareConnection(connection, httpMethod);
            }
        };
        factory.setConnectTimeout(connectTimeoutMs);
        factory.setReadTimeout(readTimeoutMs);
        return new RestTemplate(factory);
    }

    private static SSLSocketFactory buildTrustAllSslSocketFactory() {
        try {
            TrustManager[] trustAll = new TrustManager[]{
                    new X509TrustManager() {
                        @Override
                        public void checkClientTrusted(X509Certificate[] chain, String authType) {
                        }

                        @Override
                        public void checkServerTrusted(X509Certificate[] chain, String authType) {
                        }

                        @Override
                        public X509Certificate[] getAcceptedIssuers() {
                            return new X509Certificate[0];
                        }
                    }
            };
            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, trustAll, new SecureRandom());
            return sslContext.getSocketFactory();
        } catch (Exception e) {
            throw new IllegalStateException("初始化 SSL TrustAll 上下文失败", e);
        }
    }

    @Override
    public String get(String url) {
        log.debug("[Janus][HTTP] GET → {}", url);
        return restTemplate.getForObject(url, String.class);
    }

    @Override
    public String postByFormData(String url, Map<String, Object> formData) {
        log.debug("[Janus][HTTP] POST → {}", url);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        formData.forEach((key, value) -> body.add(key, value == null ? "" : value.toString()));
        return restTemplate.postForObject(url, new HttpEntity<>(body, headers), String.class);
    }
}
