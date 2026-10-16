package com.janus.module.system.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("sys_job")
public class SysJob {
    /** 主键：32 位 UUID */
    @TableId(type = IdType.ASSIGN_UUID)
    private String id;
    private String name;
    private String groupName;
    private String invokeTarget;
    private String cron;
    private Integer status;
    private String remark;
    private LocalDateTime createTime;
}
