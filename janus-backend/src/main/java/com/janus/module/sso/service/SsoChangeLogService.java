package com.janus.module.sso.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.janus.module.sso.dto.ChangesResponse;
import com.janus.module.sso.dto.SsoChangeLogItem;
import com.janus.module.sso.entity.SsoChangeLog;
import com.janus.module.sso.mapper.SsoChangeLogMapper;
import com.janus.module.sso.util.NotifySignUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

/**
 * SSO 用户数据变更流水服务：
 * <ol>
 *   <li>业务变更事务内同步写入流水；</li>
 *   <li>提供客户端增量对账（since/limit）；</li>
 *   <li>定时清理过期流水。</li>
 * </ol>
 *
 * @author ppp
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SsoChangeLogService {

    private final SsoChangeLogMapper changeLogMapper;

    @Value("${janus.notify.change-log-retention-days:7}")
    private int retentionDays;

    /**
     * 事务内追加一条变更流水并返回全局自增 seq。
     */
    @Transactional
    public long append(Collection<String> uids, Collection<String> deptIds,
                       Collection<String> roleIds, String reason) {
        String uidsStr = String.join(",", NotifySignUtil.sortIds(uids));
        String deptIdsStr = String.join(",", NotifySignUtil.sortIds(deptIds));
        String roleIdsStr = String.join(",", NotifySignUtil.sortIds(roleIds));

        SsoChangeLog row = new SsoChangeLog();
        row.setUids(uidsStr);
        row.setDeptIds(deptIdsStr);
        row.setRoleIds(roleIdsStr);
        row.setReason(reason == null ? "" : reason);
        row.setCreatedAt(LocalDateTime.now());
        changeLogMapper.insert(row);
        long id = row.getId() != null ? row.getId() : 0L;
        log.debug("[SSO] 追加变更流水: seq={}, reason={}, uids={}, deptIds={}, roleIds={}",
                id, reason, uidsStr, deptIdsStr, roleIdsStr);
        return id;
    }

    /**
     * 增量对账拉取：查询 id > since 的流水，升序返回最多 limit 条。
     *
     * @param since 客户端上次处理的最大 seq（0 = 首次或未知）
     * @param limit 本次拉取最大条数（默认 200，上限 500）
     */
    public ChangesResponse listSince(long since, int limit) {
        int effectiveLimit = Math.min(Math.max(limit, 1), 500);

        SsoChangeLog latest = changeLogMapper.selectOne(new LambdaQueryWrapper<SsoChangeLog>()
                .orderByDesc(SsoChangeLog::getId)
                .last("OFFSET 0 ROWS FETCH NEXT 1 ROWS ONLY"));

        if (latest == null) {
            return ChangesResponse.builder()
                    .latestSeq(0L)
                    .resync(false)
                    .changes(List.of())
                    .build();
        }

        long latestSeq = latest.getId();

        // since 为 0 表示客户端游标未知或首次连入，直接通知全量重同步
        if (since <= 0) {
            return ChangesResponse.builder()
                    .latestSeq(latestSeq)
                    .resync(true)
                    .changes(List.of())
                    .build();
        }

        SsoChangeLog earliest = changeLogMapper.selectOne(new LambdaQueryWrapper<SsoChangeLog>()
                .orderByAsc(SsoChangeLog::getId)
                .last("OFFSET 0 ROWS FETCH NEXT 1 ROWS ONLY"));

        // 若 since 小于表中最旧记录，说明客户端错过的流水已在清理周期中被 purge，无法补全，需全量重拉
        if (earliest != null && since < earliest.getId()) {
            log.warn("[SSO] 客户端 since={} 小于最早保留流水 minId={}，触发 resync", since, earliest.getId());
            return ChangesResponse.builder()
                    .latestSeq(latestSeq)
                    .resync(true)
                    .changes(List.of())
                    .build();
        }

        List<SsoChangeLog> rows = changeLogMapper.selectList(new LambdaQueryWrapper<SsoChangeLog>()
                .gt(SsoChangeLog::getId, since)
                .orderByAsc(SsoChangeLog::getId)
                .last("OFFSET 0 ROWS FETCH NEXT " + effectiveLimit + " ROWS ONLY"));

        List<SsoChangeLogItem> items = rows.stream()
                .map(r -> SsoChangeLogItem.builder()
                        .seq(r.getId())
                        .uids(r.getUids())
                        .deptIds(r.getDeptIds())
                        .roleIds(r.getRoleIds())
                        .reason(r.getReason())
                        .build())
                .toList();

        return ChangesResponse.builder()
                .latestSeq(latestSeq)
                .resync(false)
                .changes(items)
                .build();
    }

    /**
     * 当前库中最新（最大）seq，无记录返回 0。
     */
    public long latestSeq() {
        SsoChangeLog latest = changeLogMapper.selectOne(new LambdaQueryWrapper<SsoChangeLog>()
                .orderByDesc(SsoChangeLog::getId)
                .last("OFFSET 0 ROWS FETCH NEXT 1 ROWS ONLY"));
        return latest == null ? 0L : latest.getId();
    }

    /**
     * 清理指定时间之前的过期流水。
     */
    @Transactional
    public int purgeBefore(LocalDateTime threshold) {
        if (threshold == null) {
            return 0;
        }
        int deleted = changeLogMapper.delete(new LambdaQueryWrapper<SsoChangeLog>()
                .lt(SsoChangeLog::getCreatedAt, threshold));
        if (deleted > 0) {
            log.info("[SSO] 清理过期变更流水完成: 阈值={}, 清除条数={}", threshold, deleted);
        }
        return deleted;
    }

    /**
     * 每日凌晨定时清理过期流水（默认保留 7 天）。
     */
    @Scheduled(cron = "${janus.notify.change-log-purge-cron:0 30 3 * * ?}")
    public void scheduledPurge() {
        if (retentionDays <= 0) {
            return;
        }
        LocalDateTime threshold = LocalDateTime.now().minusDays(retentionDays);
        purgeBefore(threshold);
    }
}
