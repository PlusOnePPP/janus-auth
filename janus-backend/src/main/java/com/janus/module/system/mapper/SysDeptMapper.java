package com.janus.module.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.janus.module.system.entity.SysDept;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * 部门 Mapper。
 *
 * @author Starry
 */
@Mapper
public interface SysDeptMapper extends BaseMapper<SysDept> {

    /**
     * 按父机构集合分组统计直属子机构数（orgs-children 树节点展开箭头判断用）。
     *
     * <p>返回行结构：pid / child_count（键按小写规约读取，兼容各库列名大小写折叠）。</p>
     */
    @Select("<script>SELECT pid, COUNT(*) AS child_count FROM sys_dept"
            + " WHERE pid IN <foreach collection='pids' item='p' open='(' separator=',' close=')'>#{p}</foreach>"
            + " GROUP BY pid</script>")
    List<Map<String, Object>> countChildrenGroupByPid(@Param("pids") Collection<String> pids);
}
