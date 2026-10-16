package com.janus.module.sso.model;

import com.janus.module.system.entity.SysDept;
import lombok.Data;

/**
 * 机构（部门）信息 VO：/sso/janus/openApi orgs-batch 与 orgs-search 回源返回的数据结构。
 *
 * <p>与 starter 侧 {@code DeptInfoVO} 字段一一对应。</p>
 *
 * @author ppp
 */
@Data
public class SsoOrgBriefVO {

    /** 部门 ID */
    private String deptId;

    /** 父部门 ID（根为 '0'） */
    private String parentId;

    /** 部门名称 */
    private String deptName;

    /** 状态：0 停用 / 1 启用 */
    private Integer status;

    /** 备注 */
    private String remark;

    /** 直属启用用户数（orgs-children 层级查询返回，供树节点人数徽标；其他端点为 null） */
    private Long userCount;

    /** 直属子机构数（orgs-children 层级查询返回，供树节点展开箭头判断；其他端点为 null） */
    private Long childCount;

    /** 部门负责人/老大用户 ID */
    private String leaderUserId;

    /** 部门负责人/老大姓名 */
    private String leaderName;

    /** 部门负责人/老大职务名称 */
    private String leaderPositionName;

    public static SsoOrgBriefVO of(SysDept dept) {
        SsoOrgBriefVO vo = new SsoOrgBriefVO();
        vo.setDeptId(dept.getId());
        vo.setParentId(dept.getPid());
        vo.setDeptName(dept.getName());
        vo.setStatus(dept.getStatus());
        vo.setRemark(dept.getRemark());
        return vo;
    }
}
