package com.janus.module.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.janus.module.system.entity.SysUser;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * 用户 Mapper。
 *
 * @author ppp
 */
@Mapper
public interface SysUserMapper extends BaseMapper<SysUser> {

    /**
     * 递归查询指定机构（含全部下级机构）下的启用用户（SQL:2016 递归 CTE，分页）。
     *
     * <p>十万级用户 / 万级机构下替代应用层大 IN 集合，由数据库完成子树半连接。</p>
     */
    @Select("<script>"
            + "WITH RECURSIVE sub(dept_id) AS ("
            + "    SELECT id FROM sys_dept WHERE id = #{deptId}"
            + "    UNION ALL"
            + "    SELECT d.id FROM sys_dept d INNER JOIN sub s ON d.pid = s.dept_id"
            + ")"
            + "SELECT u.* FROM sys_user u"
            + " WHERE u.status = 1 AND u.dept_id IN (SELECT dept_id FROM sub)"
            + " <if test='keyword != null and keyword != \"\"'>"
            + "   AND (u.username LIKE CONCAT('%', #{keyword}, '%') OR u.real_name LIKE CONCAT('%', #{keyword}, '%'))"
            + " </if>"
            + " ORDER BY u.id"
            + " OFFSET #{offset} ROWS FETCH NEXT #{limit} ROWS ONLY"
            + "</script>")
    List<SysUser> selectByDeptSubtree(@Param("deptId") String deptId,
                                      @Param("keyword") String keyword,
                                      @Param("offset") long offset,
                                      @Param("limit") int limit);

    /** 递归子树用户总数（与 {@link #selectByDeptSubtree} 同条件） */
    @Select("<script>"
            + "WITH RECURSIVE sub(dept_id) AS ("
            + "    SELECT id FROM sys_dept WHERE id = #{deptId}"
            + "    UNION ALL"
            + "    SELECT d.id FROM sys_dept d INNER JOIN sub s ON d.pid = s.dept_id"
            + ")"
            + "SELECT COUNT(*) FROM sys_user u"
            + " WHERE u.status = 1 AND u.dept_id IN (SELECT dept_id FROM sub)"
            + " <if test='keyword != null and keyword != \"\"'>"
            + "   AND (u.username LIKE CONCAT('%', #{keyword}, '%') OR u.real_name LIKE CONCAT('%', #{keyword}, '%'))"
            + " </if>"
            + "</script>")
    long countByDeptSubtree(@Param("deptId") String deptId, @Param("keyword") String keyword);

    /**
     * 按机构集合分组统计启用用户数（机构树节点人数徽标用）。
     *
     * <p>返回行结构：dept_id / user_count（键按小写规约读取，兼容各库列名大小写折叠）。</p>
     */
    @Select("<script>SELECT dept_id, COUNT(*) AS user_count FROM sys_user"
            + " WHERE status = 1 AND dept_id IN"
            + " <foreach collection='deptIds' item='id' open='(' separator=',' close=')'>#{id}</foreach>"
            + " GROUP BY dept_id</script>")
    List<Map<String, Object>> countUsersGroupByDept(@Param("deptIds") Collection<String> deptIds);
}
