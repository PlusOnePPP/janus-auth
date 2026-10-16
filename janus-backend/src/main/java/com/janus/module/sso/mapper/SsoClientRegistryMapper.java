package com.janus.module.sso.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.janus.module.sso.entity.SsoClientRegistry;
import org.apache.ibatis.annotations.Mapper;

/**
 * SSO 客户端注册表 Mapper。
 *
 * @author ppp
 */
@Mapper
public interface SsoClientRegistryMapper extends BaseMapper<SsoClientRegistry> {
}
