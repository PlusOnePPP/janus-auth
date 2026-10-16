package com.janus.client.template;

import cn.dev33.satoken.context.SaHolder;
import cn.dev33.satoken.sso.template.SaSsoClientTemplate;
import lombok.extern.slf4j.Slf4j;

import java.net.URI;
import java.util.Set;

/**
 * Janus 定制 SSO 客户端模板：解决"同机部署 + server-url 回环地址"场景下的登录跳转问题。
 *
 * <p>预装/同机部署时，{@code janus.client.server-url} 通常配置为
 * {@code https://127.0.0.1:8899}——后端链路（Redis 直连验票、启动注册、增量对账、
 * SLO 回调）全部走本机回环，与机器对外 IP 无关。但登录跳转地址
 * （{@code serverUrl + authUrl}）是 302 给浏览器访问的，回环地址会让
 * 用户浏览器跳到自己的电脑。</p>
 *
 * <p>本模板在 {@link #buildServerAuthUrl(String, String)} 出口做修正：
 * 当 server-url 主机部分为回环地址（127.0.0.1 / localhost / ::1），
 * 或为不含点的主机名（docker 容器名，如 {@code janus}——浏览器无法解析）时，
 * 用<b>当前请求的 Host</b>（即用户浏览器实际访问本系统所用的主机名/IP）替换之，
 * 协议与端口仍取自 server-url。用户从哪个 IP/域名进来，就跳哪个 IP/域名的认证中心——
 * 多网卡多 IP、用户修改 IP 均零配置自适应。</p>
 *
 * <p>适用前提：认证中心与本系统发布在同一台宿主机（同机部署/容器预装），
 * 浏览器访问本系统的 Host 上认证中心端口同样可达——同机场景下天然成立。</p>
 *
 * <p>server-url 配置为真实可达地址（IP/域名，含点）时行为与 Sa-Token 原生完全一致。</p>
 *
 * @author ppp
 */
@Slf4j
public class JanusSsoClientTemplate extends SaSsoClientTemplate {

    /** 视为回环的主机名：这些地址浏览器无法远程访问，需要跟随请求 Host 修正 */
    private static final Set<String> LOOPBACK_HOSTS = Set.of("127.0.0.1", "localhost", "::1", "[::1]");

    @Override
    public String buildServerAuthUrl(String back, String currSsoLogin) {
        String url = super.buildServerAuthUrl(back, currSsoLogin);
        try {
            String serverUrl = getClientConfig().getServerUrl();
            if (serverUrl == null || serverUrl.isBlank()) {
                return url;
            }
            URI serverUri = URI.create(serverUrl.trim());
            String serverHost = serverUri.getHost();
            // 明确可达地址（IP/域名，含点且非回环）：维持 Sa-Token 原生行为
            if (serverHost == null || !(isLoopback(serverHost) || isShortHostname(serverHost))) {
                return url;
            }
            // 仅修正本模板拼接的基址部分（避免误改 query 中已编码的 redirect 参数）
            if (!url.startsWith(serverUrl)) {
                return url;
            }
            // 取用户浏览器实际访问的主机名（getServerName，不含端口）
            String requestHost = SaHolder.getRequest().getHost();
            if (requestHost == null || requestHost.isBlank() || requestHost.equals(serverHost)) {
                return url;
            }
            int q = url.indexOf('?');
            String base = q >= 0 ? url.substring(0, q) : url;
            String query = q >= 0 ? url.substring(q) : "";
            URI baseUri = URI.create(base);
            // 协议与端口保留自 server-url，仅替换主机部分
            String replaced = new URI(serverUri.getScheme(), null, requestHost,
                    baseUri.getPort() >= 0 ? baseUri.getPort() : serverUri.getPort(),
                    baseUri.getPath(), null, null).toString();
            log.info("[Janus][认证] server-url 主机浏览器不可达（回环/容器名），登录跳转跟随浏览器访问地址: {} -> {}", url, replaced + query);
            return replaced + query;
        } catch (Exception e) {
            // 非请求上下文或地址解析异常：回退原生行为，不阻断登录流程
            log.debug("[Janus][认证] 登录跳转地址修正跳过: {}", e.getMessage());
            return url;
        }
    }

    /** 判断主机是否为回环地址（含 127.0.0.0/8 网段） */
    public static boolean isLoopback(String host) {
        if (host == null) {
            return false;
        }
        String h = host.toLowerCase();
        return LOOPBACK_HOSTS.contains(h) || h.startsWith("127.");
    }

    /**
     * 判断主机是否为浏览器无法解析的短主机名（不含点，不含冒号）：
     * docker 容器名（janus、janus-postgres 等）属于此类；真实可达地址为 IP 或域名，必含点。
     */
    public static boolean isShortHostname(String host) {
        if (host == null) {
            return false;
        }
        return host.indexOf('.') < 0 && host.indexOf(':') < 0;
    }
}
