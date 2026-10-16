package com.janus.module.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.janus.module.system.entity.SysOperationLog;
import org.apache.ibatis.annotations.Mapper;

/**
 * 操作日志 Mapper。
 *
 * @author ppp
 */
@Mapper
public interface SysOperationLogMapper extends BaseMapper<SysOperationLog> {
}
