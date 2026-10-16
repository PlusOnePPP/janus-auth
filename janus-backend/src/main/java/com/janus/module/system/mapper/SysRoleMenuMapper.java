package com.janus.module.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.janus.module.system.entity.SysRoleMenu;
import org.apache.ibatis.annotations.Mapper;

/**
 * 角色-菜单关联 Mapper。
 *
 * @author ppp
 */
@Mapper
public interface SysRoleMenuMapper extends BaseMapper<SysRoleMenu> {
}
