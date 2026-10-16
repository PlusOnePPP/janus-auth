package com.janus.module.sso.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 增量对账响应 DTO。
 *
 * @author ppp
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChangesResponse {

    /** 服务端当前最大流水序号（客户端推进游标用） */
    private Long latestSeq;

    /** true 表示客户端 since 落后于最早保留流水，客户端应清空并全量重同步 */
    private boolean resync;

    /** 变更流水列表（按 seq 升序） */
    private List<SsoChangeLogItem> changes;
}
