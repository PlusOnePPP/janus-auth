package com.janus.module.system.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 操作日志实体。
 *
 * @author ppp
 */
@Data
@TableName("sys_operation_log")
public class SysOperationLog {

    /** 主键：32 位 UUID */
    @TableId(type = IdType.ASSIGN_UUID)
    private String id;
    private String userId;
    private String username;
    private String module;
    private String description;
    private String method;
    private String requestUrl;
    private String requestMethod;
    private String requestParams;
    private String ip;
    private Integer status;
    private String errorMsg;
    private Long costTime;
    private LocalDateTime createTime;
}
