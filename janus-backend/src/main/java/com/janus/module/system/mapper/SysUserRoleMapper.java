package com.janus.module.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.janus.module.system.entity.SysUserRole;
import org.apache.ibatis.annotations.Mapper;

/**
 * 用户-角色关联 Mapper。
 *
 * @author ppp
 */
@Mapper
public interface SysUserRoleMapper extends BaseMapper<SysUserRole> {
}
