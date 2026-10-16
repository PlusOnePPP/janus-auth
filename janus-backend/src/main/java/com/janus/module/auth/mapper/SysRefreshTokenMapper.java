package com.janus.module.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.janus.module.auth.entity.SysRefreshToken;
import org.apache.ibatis.annotations.Mapper;

/**
 * 刷新令牌 Mapper。
 *
 * @author ppp
 */
@Mapper
public interface SysRefreshTokenMapper extends BaseMapper<SysRefreshToken> {
}
