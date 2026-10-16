package com.janus.module.sso.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.janus.module.sso.entity.SsoChangeLog;
import org.apache.ibatis.annotations.Mapper;

/**
 * SSO 用户数据变更流水 Mapper。
 *
 * @author ppp
 */
@Mapper
public interface SsoChangeLogMapper extends BaseMapper<SsoChangeLog> {
}
