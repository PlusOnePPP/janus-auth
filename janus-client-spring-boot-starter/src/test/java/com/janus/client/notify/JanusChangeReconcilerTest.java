package com.janus.client.notify;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.janus.client.config.JanusClientProperties;
import com.janus.client.service.JanusInvalidateService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayDeque;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class JanusChangeReconcilerTest {

    private JanusClientProperties properties;

    @Mock
    private JanusInvalidateService invalidateService;

    private ObjectMapper objectMapper;

    /** 可测试子类：覆写 fetchChanges，避免对 Sa-Token 静态工具类的依赖 */
    static class TestableJanusChangeReconciler extends JanusChangeReconciler {
        private final Queue<ReconcileResult> responses = new ArrayDeque<>();
        private final AtomicInteger fetchCount = new AtomicInteger(0);

        public TestableJanusChangeReconciler(JanusClientProperties properties,
                                             JanusInvalidateService invalidateService,
                                             ObjectMapper objectMapper) {
            super(properties, invalidateService, objectMapper);
        }

        public void enqueueResponse(ReconcileResult resp) {
            responses.add(resp);
        }

        @Override
        protected ReconcileResult fetchChanges(long since, int limit) {
            fetchCount.incrementAndGet();
            return responses.poll();
        }

        public int getFetchCount() {
            return fetchCount.get();
        }
    }

    private TestableJanusChangeReconciler reconciler;

    @BeforeEach
    void setUp() {
        properties = new JanusClientProperties();
        properties.setClient("test-app");
        properties.setSecretKey("test-secret");
        properties.setReconcileEnabled(true);
        properties.setReconcileIntervalSeconds(300);

        objectMapper = new ObjectMapper();
        reconciler = new TestableJanusChangeReconciler(properties, invalidateService, objectMapper);
    }

    @Test
    @DisplayName("R1 水位先于快照：冷启动先取基线水位，再拉全量快照，游标锁定基线")
    void coldStart_watermarkBeforeSnapshot() {
        JanusChangeReconciler.ReconcileResult initResp = new JanusChangeReconciler.ReconcileResult();
        initResp.setLatestSeq(100L);
        initResp.setResync(false);
        initResp.setChanges(List.of());
        reconciler.enqueueResponse(initResp);

        assertThat(reconciler.isBaselineAligned()).isFalse();
        assertThat(reconciler.getLastSeq()).isEqualTo(0L);

        reconciler.reconcile();

        // 验证：调用了全量快照同步，基线被锁定为 100，对齐状态为 true
        verify(invalidateService, times(1)).fullResync();
        assertThat(reconciler.getLastSeq()).isEqualTo(100L);
        assertThat(reconciler.isBaselineAligned()).isTrue();
    }

    @Test
    @DisplayName("R2 流水表空时（latestSeq=0）：冷启动仍标记基线对齐，下轮对账不重复全量拉取")
    void coldStart_whenTableEmpty_alignsWithoutInfiniteLoop() {
        // 第一轮：服务端流水表为空，返回 latestSeq=0
        JanusChangeReconciler.ReconcileResult emptyResp = new JanusChangeReconciler.ReconcileResult();
        emptyResp.setLatestSeq(0L);
        emptyResp.setResync(false);
        emptyResp.setChanges(List.of());
        reconciler.enqueueResponse(emptyResp);

        reconciler.reconcile();

        verify(invalidateService, times(1)).fullResync();
        assertThat(reconciler.getLastSeq()).isEqualTo(0L);
        assertThat(reconciler.isBaselineAligned()).isTrue();

        // 第二轮：5 分钟后增量对账，服务端仍返回空增量
        JanusChangeReconciler.ReconcileResult nextEmptyResp = new JanusChangeReconciler.ReconcileResult();
        nextEmptyResp.setLatestSeq(0L);
        nextEmptyResp.setResync(false);
        nextEmptyResp.setChanges(List.of());
        reconciler.enqueueResponse(nextEmptyResp);

        reconciler.reconcile();

        // 验证：第二轮不再调用 fullResync()！消除了每 5 分钟重复全量的问题
        verify(invalidateService, times(1)).fullResync();
    }

    @Test
    @DisplayName("增量对账：追平增量变更并推进游标")
    void incrementalReconcile_appliesChanges_andAdvancesSeq() {
        // 初始已对齐
        reconciler.setBaselineAligned(true);
        reconciler.setLastSeq(100L);

        JanusChangeReconciler.ReconcileResult resp = new JanusChangeReconciler.ReconcileResult();
        resp.setLatestSeq(102L);
        resp.setResync(false);

        JanusChangeReconciler.ChangeItem item1 = new JanusChangeReconciler.ChangeItem();
        item1.setSeq(101L);
        item1.setUids("u1,u2");
        item1.setDeptIds("d1");
        item1.setRoleIds("");

        JanusChangeReconciler.ChangeItem item2 = new JanusChangeReconciler.ChangeItem();
        item2.setSeq(102L);
        item2.setUids("u3");
        item2.setDeptIds("");
        item2.setRoleIds("r1");

        resp.setChanges(List.of(item1, item2));
        reconciler.enqueueResponse(resp);

        reconciler.reconcile();

        // 验证整批变更被聚合去重后一次性分发本地失效
        verify(invalidateService, times(1)).apply(
                eq(List.of("u1", "u2", "u3")),
                eq(List.of("d1")),
                eq(List.of("r1")),
                eq(false), eq(false));
        // 游标推进至 102
        assertThat(reconciler.getLastSeq()).isEqualTo(102L);
    }

    @Test
    @DisplayName("流水清理兜底：服务端返回 resync=true 时触发全量重同步并更新游标")
    void incrementalReconcile_whenResyncTrue_triggersFullResync() {
        reconciler.setBaselineAligned(true);
        reconciler.setLastSeq(10L);

        JanusChangeReconciler.ReconcileResult resp = new JanusChangeReconciler.ReconcileResult();
        resp.setLatestSeq(500L);
        resp.setResync(true);
        resp.setChanges(List.of());
        reconciler.enqueueResponse(resp);

        reconciler.reconcile();

        verify(invalidateService, times(1)).fullResync();
        assertThat(reconciler.getLastSeq()).isEqualTo(500L);
    }

    @Test
    @DisplayName("R3 开关语义：关闭对账时，注册回调不触发对账")
    void reconcileDisabled_registrationIgnored() {
        properties.setReconcileEnabled(false);

        reconciler.onClientRegistered();

        // 验证没有发起任何 HTTP 调用
        assertThat(reconciler.getFetchCount()).isEqualTo(0);
        verify(invalidateService, never()).fullResync();
    }
}
