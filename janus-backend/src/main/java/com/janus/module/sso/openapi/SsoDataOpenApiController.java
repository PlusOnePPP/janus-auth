package com.janus.module.sso.openapi;

import cn.dev33.satoken.context.SaHolder;
import cn.dev33.satoken.sso.processor.SaSsoServerProcessor;
import cn.dev33.satoken.util.SaResult;
import com.janus.module.sso.service.SsoChangeLogService;
import com.janus.module.sso.service.SsoOrgBriefService;
import com.janus.module.sso.service.SsoPositionBriefService;
import com.janus.module.sso.service.SsoRoleBriefService;
import com.janus.module.sso.service.SsoUserBriefService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.List;

/**
 * OpenApi：SSO 数据回源与增量对账接口（对外提供给已注册业务系统调用）。
 *
 * <p>端点：{@code GET /sso/janus/openApi/getData}、{@code GET /sso/janus/openApi/changes}、
 * {@code GET /sso/janus/openApi/users-search}、{@code GET /sso/janus/openApi/orgs-search}、
 * {@code GET /sso/janus/openApi/orgs-batch}、{@code GET /sso/janus/openApi/orgs-children}。
 * 客户端 SDK 带 client + HMAC 签名 HTTP 调用。</p>
 *
 * @author Starry
 */
@RestController
@RequiredArgsConstructor
public class SsoDataOpenApiController {

    private final SsoUserBriefService ssoUserBriefService;
    private final SsoOrgBriefService ssoOrgBriefService;
    private final SsoRoleBriefService ssoRoleBriefService;
    private final SsoPositionBriefService ssoPositionBriefService;
    private final com.janus.module.system.service.SysDeptService sysDeptService;
    private final SsoChangeLogService ssoChangeLogService;

    /**
     * 增量变更对账接口（客户端周期性或启动补拉对账）。
     *
     * <p>端点：{@code GET /sso/janus/openApi/changes?since={seq}&limit={n}}。
     * 复用 getData 客户端签名校验：client + sign + timestamp + nonce。</p>
     */
    @GetMapping("/sso/janus/openApi/changes")
    public Object getChanges(@RequestParam(name = "since", defaultValue = "0") long since,
                             @RequestParam(name = "limit", defaultValue = "200") int limit) {
        checkClientSign();
        return SaResult.ok().setData(ssoChangeLogService.listSince(since, limit));
    }

    /**
     * 用户分页搜索端点（十万级按需检索，仅返回 status=1 启用用户，不暴露角色与权限码）。
     *
     * <p>带 {@code deptId} 时按机构维度查询：{@code scope=direct}（默认）仅直属用户，
     * {@code scope=subtree} 含全部下级机构用户（递归 CTE）；此时 keyword 忽略。</p>
     */
    @GetMapping("/sso/janus/openApi/users-search")
    public Object searchUsers(@RequestParam(required = false) String keyword,
                              @RequestParam(required = false) String deptId,
                              @RequestParam(required = false, defaultValue = "direct") String scope,
                              @RequestParam(defaultValue = "1") int page,
                              @RequestParam(defaultValue = "50") int size) {
        checkClientSign();
        if (deptId != null && !deptId.isBlank()) {
            return SaResult.ok().setData(
                    ssoUserBriefService.searchUsersByDept(deptId, scope, keyword, page, size));
        }
        return SaResult.ok().setData(ssoUserBriefService.searchUsers(keyword, page, size));
    }

    /**
     * 机构分页搜索端点（万级机构按需检索）。
     */
    @GetMapping("/sso/janus/openApi/orgs-search")
    public Object searchOrgs(@RequestParam(required = false) String keyword,
                             @RequestParam(defaultValue = "1") int page,
                             @RequestParam(defaultValue = "50") int size) {
        checkClientSign();
        return SaResult.ok().setData(ssoOrgBriefService.searchOrgs(keyword, page, size));
    }

    /**
     * 机构批量回源端点（单次上限 500）。
     */
    @GetMapping("/sso/janus/openApi/orgs-batch")
    public Object getOrgsBatch(@RequestParam(required = false) String ids) {
        checkClientSign();
        return SaResult.ok().setData(ssoOrgBriefService.getOrgsByIds(parseIds(ids)));
    }

    /**
     * 机构层级查询端点（查询直接下级子机构列表）。
     */
    @GetMapping("/sso/janus/openApi/orgs-children")
    public Object getOrgsChildren(@RequestParam(required = false) String parentId) {
        checkClientSign();
        return SaResult.ok().setData(ssoOrgBriefService.getChildren(parentId));
    }

    /**
     * 职务全量查询端点（获取当前所有启用职务，按权重降序）。
     */
    @GetMapping("/sso/janus/openApi/positions-all")
    public Object getPositionsAll() {
        checkClientSign();
        return SaResult.ok().setData(ssoPositionBriefService.listAll());
    }

    /**
     * 职务批量回源端点。
     */
    @GetMapping("/sso/janus/openApi/positions-batch")
    public Object getPositionsBatch(@RequestParam(required = false) String ids) {
        checkClientSign();
        return SaResult.ok().setData(ssoPositionBriefService.getPositionsByIds(parseIds(ids)));
    }

    /**
     * 部门老大查询端点（获取指定部门负责人）。
     */
    @GetMapping("/sso/janus/openApi/org-leader")
    public Object getOrgLeader(@RequestParam String deptId) {
        checkClientSign();
        return SaResult.ok().setData(ssoPositionBriefService.getOrgLeader(deptId));
    }

    /**
     * 部门成员任职详情查询端点。
     */
    @GetMapping("/sso/janus/openApi/org-members")
    public Object getOrgMembers(@RequestParam String deptId) {
        checkClientSign();
        return SaResult.ok().setData(sysDeptService.getDeptMembers(deptId));
    }

    @RequestMapping("/sso/janus/openApi/getData")
    public Object getData(String apiType, String loginId, String ids, String parentId,
                          String deptId,
                          @RequestParam(required = false) String scope,
                          @RequestParam(required = false) String keyword,
                          @RequestParam(required = false, defaultValue = "1") Integer page,
                          @RequestParam(required = false, defaultValue = "50") Integer size) {
        checkClientSign();

        int p = page == null ? 1 : page;
        int s = size == null ? 50 : size;

        return switch (apiType == null ? "" : apiType) {
            case "userinfo" -> SaResult.ok().setData(
                    ssoUserBriefService.getUserBrief(parseId(loginId, "loginId")));
            case "users-batch" -> SaResult.ok().setData(
                    ssoUserBriefService.getUserBriefs(parseIds(ids)));
            case "users-search" -> org.springframework.util.StringUtils.hasText(deptId)
                    ? SaResult.ok().setData(
                            ssoUserBriefService.searchUsersByDept(deptId, scope, keyword, p, s))
                    : SaResult.ok().setData(
                            ssoUserBriefService.searchUsers(keyword, p, s));
            case "orgs-batch" -> SaResult.ok().setData(ssoOrgBriefService.getOrgsByIds(parseIds(ids)));
            case "orgs-children" -> SaResult.ok().setData(ssoOrgBriefService.getChildren(parentId));
            case "orgs-search" -> SaResult.ok().setData(
                    ssoOrgBriefService.searchOrgs(keyword, p, s));
            case "roles-all" -> SaResult.ok().setData(ssoRoleBriefService.listAll());
            case "positions-all" -> SaResult.ok().setData(ssoPositionBriefService.listAll());
            case "positions-batch" -> SaResult.ok().setData(ssoPositionBriefService.getPositionsByIds(parseIds(ids)));
            case "org-leader" -> SaResult.ok().setData(ssoPositionBriefService.getOrgLeader(deptId));
            case "org-members" -> SaResult.ok().setData(sysDeptService.getDeptMembers(deptId));
            default -> SaResult.error("未知 apiType: " + apiType);
        };
    }

    private void checkClientSign() {
        String client = SaHolder.getRequest().getHeader("client");
        if (client == null) {
            client = SaHolder.getRequest().getParam("client");
        }
        SaSsoServerProcessor.instance.ssoServerTemplate
                .getSignTemplate(client).checkRequest(SaHolder.getRequest());
    }

    private String parseId(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("参数 " + name + " 不能为空");
        }
        return value.trim();
    }

    private List<String> parseIds(String ids) {
        if (ids == null || ids.isBlank()) {
            return List.of();
        }
        return Arrays.stream(ids.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }
}
