package com.janus.module.system.dto;

import lombok.Data;

import java.util.List;

/**
 * 角色用户分配 / 移除请求。
 *
 * @author ppp
 */
@Data
public class RoleUserAssignRequest {

    /** 待绑定或待移除的用户 ID 列表 */
    private List<String> userIds;
}
