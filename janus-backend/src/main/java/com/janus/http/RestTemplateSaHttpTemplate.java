package com.janus.http;

import cn.dev33.satoken.http.SaHttpTemplate;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
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

/**
 * 基于 RestTemplate 的 Sa-Token HTTP 请求模板（认证中心服务端版）。
 *
 * <p>Sa-Token 1.45.x 自带的 SaHttpTemplateDefaultImpl 仅为占位实现（调用即抛
 * NotImplException）。SLO 单点登出时认证中心需要主动 HTTP GET 各已注册客户端的
 * logoutCall 回调（{@code SaSsoServerTemplate#notifyClientLogout / pushToClientByLogoutCall}），
 * 服务端必须提供真实 HTTP 处理器，否则推送线程直接抛
 * {@code NotImplException: HTTP 请求处理器未实现}，客户端永远收不到注销通知。</p>
 *
 * <p>推送在 Sa-Token 的异步线程执行（失败不影响登出主流程），超时设 3s 防止
 * 客户端宕机时线程长时间挂起；同时支持客户端使用自签 HTTPS 证书。</p>
 *
 * @author ppp
 */
@Slf4j
public class RestTemplateSaHttpTemplate implements SaHttpTemplate {

    private static final SSLSocketFactory TRUST_ALL_SSL_FACTORY = buildTrustAllSslSocketFactory();

    private final RestTemplate restTemplate;

    public RestTemplateSaHttpTemplate() {
        this.restTemplate = createRestTemplate(3_000, 3_000);
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
        // INFO：SLO 出站推送频率极低（注销时每客户端一条），打印完整 URL 便于排查客户端未收到注销通知类问题
        log.info("[SSO] SLO 出站推送 GET → {}", url);
        return restTemplate.getForObject(url, String.class);
    }

    @Override
    public String postByFormData(String url, java.util.Map<String, Object> formData) {
        log.info("[SSO] 出站 POST → {}", url);
        throw new UnsupportedOperationException("SSO 服务端当前无 POST 出站场景");
    }
}
