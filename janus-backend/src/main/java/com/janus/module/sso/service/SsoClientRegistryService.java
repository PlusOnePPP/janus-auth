package com.janus.module.sso.service;

import cn.dev33.satoken.sso.config.SaSsoClientModel;
import cn.dev33.satoken.sso.config.SaSsoServerConfig;
import cn.dev33.satoken.sso.template.SaSsoServerTemplate;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.janus.common.result.ServiceException;
import com.janus.module.sso.dto.SsoClientAdminVO;
import com.janus.module.sso.dto.SsoClientRegisterRequest;
import com.janus.module.sso.entity.SsoClientRegistry;
import com.janus.module.sso.mapper.SsoClientRegistryMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * SSO 客户端注册表服务：客户端自助注册的登记、凭证校验与运行时热同步。
 *
 * <p>注册语义为"最新注册为准"（last-writer-wins）：client 首次注册即登记；
 * 后续重复注册（含 secret-key 变更）一律按本次注册内容覆盖更新，
 * 换密钥重试注册即生效，冲突不做拒绝。注册成功后立即同步进 Sa-Token sso-server 的 clients 运行时 Map
 * （{@link SaSsoServerTemplate#getClient} 每次请求实时读取，put 即生效，
 * 验票 / getData 验签 / SLO 推送均无需重启）；认证中心重启后由
 * {@link #run(ApplicationArguments)} 从注册表全量恢复。</p>
 *
 * <p>静态 yml clients 与注册表共存：同名时注册表覆盖（注册表为准），
 * 仅存在于 yml 的应用视为预置数据保留。</p>
 *
 * @author ppp
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SsoClientRegistryService implements ApplicationRunner {

    /** 客户端未上报 notifyPath 时的默认失效通知路径（与 starter 固定值一致） */
    public static final String DEFAULT_NOTIFY_PATH = "/sso/janus/openApi/notify";

    private final SsoClientRegistryMapper registryMapper;
    private final SaSsoServerTemplate ssoServerTemplate;

    /**
     * 客户端自助注册（幂等 upsert，最新注册为准）：首次登记，重复注册覆盖元信息。
     *
     * @return 注册结果行（含生成的 id）
     */
    @Transactional
    public SsoClientRegistry register(SsoClientRegisterRequest req) {
        if (!StringUtils.hasText(req.getClient())) {
            throw ServiceException.badRequest("client 不能为空");
        }
        if (!StringUtils.hasText(req.getSecretKey())) {
            throw ServiceException.badRequest("secretKey 不能为空");
        }
        if (!StringUtils.hasText(req.getClientUrl()) || !req.getClientUrl().trim().startsWith("http")) {
            throw ServiceException.badRequest("clientUrl 必须为 http(s) 地址");
        }
        String client = req.getClient().trim();
        String clientUrl = req.getClientUrl().trim();
        String appName = StringUtils.hasText(req.getAppName()) ? req.getAppName().trim() : client;
        String notifyPath = StringUtils.hasText(req.getNotifyPath()) ? req.getNotifyPath().trim() : DEFAULT_NOTIFY_PATH;

        SsoClientRegistry existing = getByClient(client);
        SsoClientRegistry row;
        if (existing == null) {
            row = new SsoClientRegistry();
            row.setClient(client);
            row.setSecretKey(req.getSecretKey().trim());
            row.setClientUrl(clientUrl);
            row.setNotifyPath(notifyPath);
            row.setAppName(appName);
            row.setStatus(SsoClientRegistry.STATUS_AUTO_APPROVED);
            LocalDateTime now = LocalDateTime.now();
            row.setCreateTime(now);
            row.setUpdateTime(now);
            registryMapper.insert(row);
            log.info("[SSO] 客户端注册成功: client={}, clientUrl={}, notifyPath={}", client, clientUrl, notifyPath);
        } else {
            if (StringUtils.hasText(existing.getSecretKey())
                    && !existing.getSecretKey().equals(req.getSecretKey().trim())) {
                log.warn("[SSO] 客户端注册被拒绝: client={}, 密钥不匹配（防冒名覆盖）", client);
                throw ServiceException.forbidden("client 已存在且密钥不匹配");
            }
            existing.setSecretKey(req.getSecretKey().trim());
            existing.setClientUrl(clientUrl);
            existing.setNotifyPath(notifyPath);
            existing.setAppName(appName);
            existing.setUpdateTime(LocalDateTime.now());
            registryMapper.updateById(existing);
            row = existing;
            log.info("[SSO] 客户端重复注册（幂等覆盖）: client={}, clientUrl={}", client, clientUrl);
        }
        syncToRuntime(row);
        return row;
    }

    /**
     * 重置客户端签名密钥（管理端入口，记审计日志）。
     * 生成 32 位高强度随机密钥，落库并同步运行时配置。
     */
    @Transactional
    public String resetSecretKey(String id, String operator) {
        SsoClientRegistry row = registryMapper.selectById(id);
        if (row == null) {
            throw ServiceException.notFound("客户端不存在: " + id);
        }
        String newSecret = cn.dev33.satoken.util.SaFoxUtil.getRandomString(32);
        row.setSecretKey(newSecret);
        row.setUpdateTime(LocalDateTime.now());
        registryMapper.updateById(row);
        syncToRuntime(row);
        log.info("[SSO] 管理端重置客户端密钥成功: operator={}, client={}", operator, row.getClient());
        return newSecret;
    }

    /** 注册表全量列表（管理端查看） */
    public List<SsoClientRegistry> listAll() {
        return registryMapper.selectList(new LambdaQueryWrapper<SsoClientRegistry>()
                .orderByAsc(SsoClientRegistry::getId));
    }

    /**
     * 管理端列表（密钥脱敏）：出参为 {@link SsoClientAdminVO}，
     * 不含 secretKey 明文字段；明文查看走 {@link #getSecretKeyById(String, String)} 并记审计日志。
     */
    public List<SsoClientAdminVO> listForAdmin() {
        return listAll().stream().map(SsoClientAdminVO::from).toList();
    }

    /** 按 id 查看客户端签名密钥明文（管理端"查看密钥"入口，operator 仅用于审计日志） */
    public String getSecretKeyById(String id, String operator) {
        SsoClientRegistry row = registryMapper.selectById(id);
        if (row == null) {
            throw ServiceException.notFound("客户端不存在: " + id);
        }
        log.info("[SSO] 管理端查看客户端密钥: operator={}, client={}", operator, row.getClient());
        return row.getSecretKey();
    }

    /** 按应用标识查询（无则返回 null） */
    public SsoClientRegistry getByClient(String client) {
        return registryMapper.selectOne(new LambdaQueryWrapper<SsoClientRegistry>()
                .eq(SsoClientRegistry::getClient, client));
    }

    /**
     * 注册行 → Sa-Token 运行时 client 模型并 put 进 clients Map，立即生效。
     *
     * <p>allow-url 取 {clientUrl}/*（与原静态配置形状一致，匹配该应用一级 SSO 路由）；
     * isSlo/isPush 显式 true（框架按 Boolean 拆箱读取，null 即 NPE）；
     * serverUrl 取 clientUrl（SLO 推送 logoutCall 的目标基址）。</p>
     */
    public void syncToRuntime(SsoClientRegistry row) {
        SaSsoClientModel model = new SaSsoClientModel()
                .setClient(row.getClient())
                .setSecretKey(row.getSecretKey())
                .setAllowUrl(row.getClientUrl() + "/*")
                .setIsSlo(true)
                .setIsPush(true)
                .setServerUrl(row.getClientUrl());
        SaSsoServerConfig config = ssoServerTemplate.getServerConfig();
        Map<String, SaSsoClientModel> clients = config.getClients();
        if (clients == null) {
            // yml 未配置任何 clients 时绑定结果可能为 null，防御性初始化
            clients = new ConcurrentHashMap<>();
            config.setClients(clients);
        }
        clients.put(row.getClient(), model);
        log.info("[SSO] 运行时已同步客户端: client={}, allowUrl={}", row.getClient(), model.getAllowUrl());
    }

    /** 认证中心启动后从注册表全量恢复运行时 clients（同名覆盖静态配置） */
    @Override
    public void run(ApplicationArguments args) {
        List<SsoClientRegistry> rows = listAll();
        rows.forEach(this::syncToRuntime);
        log.info("[SSO] 注册表启动恢复完成: {} 个客户端", rows.size());
    }
}
