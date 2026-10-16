package com.janus.module.system.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 通知消息实体。
 *
 * @author ppp
 */
@Data
@TableName("sys_notice")
public class SysNotice {

    /** 主键：32 位 UUID */
    @TableId(type = IdType.ASSIGN_UUID)
    private String id;
    private String title;
    private String message;
    private String avatar;
    private String link;
    private Integer isRead;
    private String userId;
    private String roleId;
    private String type;
    private LocalDateTime createTime;
}
