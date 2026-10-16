package com.janus.module.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.janus.module.system.entity.SysLoginLog;
import org.apache.ibatis.annotations.Mapper;

/**
 * 登录日志 Mapper。
 *
 * @author ppp
 */
@Mapper
public interface SysLoginLogMapper extends BaseMapper<SysLoginLog> {
}
