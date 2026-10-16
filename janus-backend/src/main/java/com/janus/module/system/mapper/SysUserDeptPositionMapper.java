package com.janus.module.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.janus.module.system.entity.SysUserDeptPosition;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * 用户部门职务关联 Mapper。
 *
 * @author ppp
 */
@Mapper
public interface SysUserDeptPositionMapper extends BaseMapper<SysUserDeptPosition> {

    /**
     * 查询部门成员任职详情（包含人员姓名、登录名、职务名称、职务编码、职级权重）
     */
    @Select("SELECT udp.id, udp.user_id, u.username, u.real_name, udp.dept_id, "
            + "udp.position_id, p.name AS position_name, p.code AS position_code, "
            + "p.level_weight, udp.is_main, udp.is_leader, udp.create_time "
            + "FROM sys_user_dept_position udp "
            + "JOIN sys_user u ON udp.user_id = u.id "
            + "JOIN sys_position p ON udp.position_id = p.id "
            + "WHERE udp.dept_id = #{deptId} AND u.status = 1 "
            + "ORDER BY udp.is_leader DESC, p.level_weight DESC, udp.create_time ASC")
    List<Map<String, Object>> selectDeptMembersWithPosition(@Param("deptId") String deptId);

    /**
     * 批量查询部门的老大（每个部门选取 is_leader DESC, level_weight DESC 的首位）
     */
    @Select("<script>"
            + "SELECT DISTINCT ON (udp.dept_id) udp.dept_id, udp.user_id, u.real_name, u.username, "
            + "p.id AS position_id, p.name AS position_name, p.level_weight "
            + "FROM sys_user_dept_position udp "
            + "JOIN sys_user u ON udp.user_id = u.id "
            + "JOIN sys_position p ON udp.position_id = p.id "
            + "WHERE udp.dept_id IN <foreach collection='deptIds' item='d' open='(' separator=',' close=')'>#{d}</foreach> "
            + "AND u.status = 1 "
            + "ORDER BY udp.dept_id, udp.is_leader DESC, p.level_weight DESC, udp.create_time ASC"
            + "</script>")
    List<Map<String, Object>> selectDeptLeaders(@Param("deptIds") Collection<String> deptIds);
}
