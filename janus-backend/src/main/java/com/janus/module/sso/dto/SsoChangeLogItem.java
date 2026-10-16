package com.janus.module.sso.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 单条变更流水条目 DTO。
 *
 * @author ppp
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SsoChangeLogItem {

    /** 变更序号（游标 seq） */
    private Long seq;

    /** 受影响用户 ID（逗号分隔） */
    private String uids;

    /** 受影响机构 ID（逗号分隔） */
    private String deptIds;

    /** 受影响角色 ID（逗号分隔） */
    private String roleIds;

    /** 变更原因 */
    private String reason;
}
