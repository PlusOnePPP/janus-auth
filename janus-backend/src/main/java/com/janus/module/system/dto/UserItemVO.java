package com.janus.module.system.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 用户列表项（脱敏：不含密码哈希），用于用户管理表格。
 *
 * @author ppp
 */
@Data
public class UserItemVO {

    private String id;
    private String username;
    private String realName;
    private String avatar;
    private String homePath;
    private String deptId;
    /** 所属部门/机构名称 */
    private String deptName;
    private Integer status;
    private String remark;
    private LocalDateTime createTime;

    /** 关联角色 ID 列表 */
    private List<String> roleIds;

    /** 关联角色编码列表 */
    private List<String> roleCodes;

    /** 关联角色名称列表（中文） */
    private List<String> roleNames;

    /** 主职务 ID */
    private String positionId;

    /** 主职务名称 */
    private String positionName;

    /** 关联的所有部门及职务列表 */
    private List<UserDeptPositionVO> deptPositions;
}
