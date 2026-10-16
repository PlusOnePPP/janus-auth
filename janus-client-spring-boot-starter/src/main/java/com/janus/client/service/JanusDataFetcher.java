package com.janus.client.service;

import cn.dev33.satoken.sso.template.SaSsoClientUtil;
import cn.dev33.satoken.util.SaResult;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * getData 通用回源组件：apiType 参数化回源（roles-all、orgs-batch、users-batch、orgs-children、users-search、orgs-search）。
 *
 * <p>签名参数（client/sign/timestamp/nonce）由 Sa-Token 框架按 secret-key 自动追加。
 * 注意必须用单参重载 {@code getData(params)}：URL 取配置的 getDataUrl（默认 /sso/getData）。
 * 双参重载 getData(urlPath, params) 的第一个参数是 URL 路径而非 apiType。</p>
 *
 * @author ppp
 */
@RequiredArgsConstructor
public class JanusDataFetcher {

    private final ObjectMapper objectMapper;

    /**
     * 拉取指定 apiType 的字典列表数据（如 roles-all）。
     *
     * @param apiType roles-all
     * @param type    目标 bean 类型（字段与认证中心 VO 契约一致）
     */
    public <T> List<T> fetchList(String apiType, Class<T> type) {
        // 必须用可变 Map：Sa-Token buildCustomPathUrl 会向该 Map 追加签名参数
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("apiType", apiType);
        Object raw = SaSsoClientUtil.getData(params);
        return parseData(raw, type);
    }

    /**
     * 带额外参数拉取列表数据（如 org-members）。
     */
    public <T> List<T> fetchListWithParams(String apiType, Map<String, Object> extraParams, Class<T> type) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("apiType", apiType);
        if (extraParams != null) {
            params.putAll(extraParams);
        }
        Object raw = SaSsoClientUtil.getData(params);
        return parseData(raw, type);
    }

    /**
     * 拉取单个对象数据（如 org-leader）。
     */
    public <T> T fetchObject(String apiType, Map<String, Object> extraParams, Class<T> type) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("apiType", apiType);
        if (extraParams != null) {
            params.putAll(extraParams);
        }
        Object raw = SaSsoClientUtil.getData(params);
        JsonNode root = parseJsonRoot(raw);
        if (root.path("code").asInt(-1) != SaResult.CODE_SUCCESS) {
            throw new IllegalStateException("getData 回源失败: code=" + root.path("code").asInt()
                    + ", msg=" + root.path("msg").asText());
        }
        JsonNode data = root.path("data");
        if (data.isMissingNode() || data.isNull()) {
            return null;
        }
        return objectMapper.convertValue(data, type);
    }

    /**
     * 批量按 ID 列表拉取数据（分片回源，每批上限 500）。
     *
     * @param apiType users-batch / orgs-batch
     * @param ids     ID 列表
     * @param type    目标 bean 类型
     */
    public <T> List<T> fetchBatch(String apiType, Collection<String> ids, Class<T> type) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        List<String> validIds = ids.stream()
                .filter(id -> id != null && !id.isBlank())
                .distinct()
                .toList();
        if (validIds.isEmpty()) {
            return List.of();
        }
        List<T> result = new ArrayList<>();
        for (int from = 0; from < validIds.size(); from += 500) {
            List<String> shard = validIds.subList(from, Math.min(from + 500, validIds.size()));
            Map<String, Object> params = new LinkedHashMap<>();
            params.put("apiType", apiType);
            params.put("ids", String.join(",", shard));
            Object raw = SaSsoClientUtil.getData(params);
            result.addAll(parseData(raw, type));
        }
        return result;
    }

    /**
     * 按父 ID 级联拉取直接子级机构。
     *
     * @param parentId 父机构 ID（为 null 时认证中心返回顶级机构列表）
     * @param type     目标 bean 类型
     */
    public <T> List<T> fetchChildren(String parentId, Class<T> type) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("apiType", "orgs-children");
        if (parentId != null) {
            params.put("parentId", parentId);
        }
        Object raw = SaSsoClientUtil.getData(params);
        return parseData(raw, type);
    }

    /**
     * 分页搜索回源（委托服务端执行 SQL LIKE / LIMIT，避免拉全量）。
     *
     * @param apiType users-search / orgs-search
     * @param keyword 搜索关键字
     * @param page    页码（从 1 开始）
     * @param size    每页条数
     * @param type    目标 bean 类型
     */
    public <T> List<T> fetchSearch(String apiType, String keyword, int page, int size, Class<T> type) {
        return fetchSearchPage(apiType, keyword, page, size, type).getRecords();
    }

    /**
     * 分页搜索回源（含 total/page/size 完整分页元数据）。
     *
     * @param apiType users-search / orgs-search
     * @param keyword 搜索关键字
     * @param page    页码（从 1 开始）
     * @param size    每页条数
     * @param type    目标 bean 类型
     */
    public <T> com.janus.client.model.PageResult<T> fetchSearchPage(String apiType, String keyword, int page, int size, Class<T> type) {
        Map<String, Object> params = new LinkedHashMap<>();
        if (keyword != null && !keyword.isBlank()) {
            params.put("keyword", keyword);
        }
        return fetchPage(apiType, params, page, size, type);
    }

    /**
     * 通用分页取数（params 为业务过滤参数，null/空串值自动跳过；page/size 统一附加）。
     *
     * @param apiType users-search / orgs-search 等分页端点的 apiType
     * @param params  业务过滤参数（如 deptId、scope）
     */
    public <T> com.janus.client.model.PageResult<T> fetchPage(String apiType, Map<String, Object> params,
                                                              int page, int size, Class<T> type) {
        Map<String, Object> all = new LinkedHashMap<>();
        all.put("apiType", apiType);
        if (params != null) {
            params.forEach((k, v) -> {
                if (v != null && !"".equals(v)) {
                    all.put(k, v);
                }
            });
        }
        int safePage = Math.max(1, page);
        int safeSize = Math.max(1, size);
        all.put("page", safePage);
        all.put("size", safeSize);
        Object raw = SaSsoClientUtil.getData(all);
        return parsePageData(raw, safePage, safeSize, type);
    }

    /**
     * 解析 getData 响应（兼容纯数组 T[] 与分页对象结构 {total, page, size, users/orgs=T[]}）。
     */
    private <T> List<T> parseData(Object raw, Class<T> type) {
        JsonNode root = parseJsonRoot(raw);
        if (root.path("code").asInt(-1) != SaResult.CODE_SUCCESS) {
            throw new IllegalStateException("getData 回源失败: code=" + root.path("code").asInt()
                    + ", msg=" + root.path("msg").asText());
        }
        JsonNode data = root.path("data");
        if (data.isMissingNode() || data.isNull()) {
            return List.of();
        }
        JsonNode targetArray = data;
        if (data.isObject()) {
            if (data.has("users")) {
                targetArray = data.get("users");
            } else if (data.has("orgs")) {
                targetArray = data.get("orgs");
            } else if (data.has("records")) {
                targetArray = data.get("records");
            } else if (data.has("list")) {
                targetArray = data.get("list");
            }
        }
        List<T> items = new ArrayList<>();
        if (targetArray != null && targetArray.isArray()) {
            for (JsonNode item : targetArray) {
                items.add(objectMapper.convertValue(item, type));
            }
        }
        return items;
    }

    /**
     * 解析分页 getData 响应。
     */
    private <T> com.janus.client.model.PageResult<T> parsePageData(Object raw, int requestPage, int requestSize, Class<T> type) {
        JsonNode root = parseJsonRoot(raw);
        if (root.path("code").asInt(-1) != SaResult.CODE_SUCCESS) {
            throw new IllegalStateException("getData 回源失败: code=" + root.path("code").asInt()
                    + ", msg=" + root.path("msg").asText());
        }
        JsonNode data = root.path("data");
        if (data.isMissingNode() || data.isNull()) {
            return new com.janus.client.model.PageResult<>(0L, requestPage, requestSize, List.of());
        }

        if (data.isArray()) {
            List<T> items = new ArrayList<>();
            for (JsonNode item : data) {
                items.add(objectMapper.convertValue(item, type));
            }
            return new com.janus.client.model.PageResult<>((long) items.size(), requestPage, requestSize, items);
        }

        long total = data.path("total").asLong(0L);
        int page = data.path("page").asInt(requestPage);
        int size = data.path("size").asInt(requestSize);

        JsonNode targetArray = null;
        if (data.has("users")) {
            targetArray = data.get("users");
        } else if (data.has("orgs")) {
            targetArray = data.get("orgs");
        } else if (data.has("records")) {
            targetArray = data.get("records");
        } else if (data.has("list")) {
            targetArray = data.get("list");
        }

        List<T> items = new ArrayList<>();
        if (targetArray != null && targetArray.isArray()) {
            for (JsonNode item : targetArray) {
                items.add(objectMapper.convertValue(item, type));
            }
        }
        return new com.janus.client.model.PageResult<>(total, page, size, items);
    }

    private JsonNode parseJsonRoot(Object raw) {
        if (raw instanceof String json) {
            try {
                return objectMapper.readTree(json);
            } catch (JsonProcessingException e) {
                throw new IllegalStateException("getData 响应非合法 JSON: " + raw, e);
            }
        }
        return objectMapper.valueToTree(raw);
    }
}
