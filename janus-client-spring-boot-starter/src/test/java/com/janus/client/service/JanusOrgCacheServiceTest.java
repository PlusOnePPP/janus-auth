package com.janus.client.service;

import com.janus.client.config.JanusClientProperties;
import com.janus.client.model.DeptInfoVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JanusOrgCacheServiceTest {

    @Mock
    private JanusDataFetcher fetcher;

    private JanusClientProperties properties;
    private JanusOrgCacheService orgCacheService;

    @BeforeEach
    void setUp() {
        properties = new JanusClientProperties();
        properties.getCache().setOrgMaxSize(500);
        orgCacheService = new JanusOrgCacheService(properties, fetcher);
    }

    @Test
    @DisplayName("getById: 缓存未命中时调用 orgs-batch 回源并缓存，第二次直接命中")
    void getById_missAndHit() {
        DeptInfoVO vo = new DeptInfoVO();
        vo.setDeptId("dept-1");
        vo.setDeptName("研发部");

        when(fetcher.fetchBatch(eq("orgs-batch"), eq(List.of("dept-1")), eq(DeptInfoVO.class)))
                .thenReturn(List.of(vo));

        // 第一次读取：未命中，回源
        DeptInfoVO r1 = orgCacheService.getById("dept-1");
        assertThat(r1).isNotNull();
        assertThat(r1.getDeptName()).isEqualTo("研发部");

        // 第二次读取：命中 LRU 缓存，不应再回源
        DeptInfoVO r2 = orgCacheService.getById("dept-1");
        assertThat(r2).isNotNull();
        assertThat(r2.getDeptName()).isEqualTo("研发部");

        verify(fetcher, times(1)).fetchBatch(eq("orgs-batch"), any(), eq(DeptInfoVO.class));
    }

    @Test
    @DisplayName("getChildren: 级联回源直接子部门并写入 LRU 缓存")
    void getChildren_fetchAndCache() {
        DeptInfoVO child1 = new DeptInfoVO();
        child1.setDeptId("dept-10");
        child1.setDeptName("后端组");

        when(fetcher.fetchChildren(eq("dept-1"), eq(DeptInfoVO.class)))
                .thenReturn(List.of(child1));

        List<DeptInfoVO> children = orgCacheService.getChildren("dept-1");
        assertThat(children).hasSize(1);
        assertThat(children.get(0).getDeptName()).isEqualTo("后端组");

        // 验证回填到 LRU：后续直接 getById("dept-10") 不回源
        DeptInfoVO cached = orgCacheService.getById("dept-10");
        assertThat(cached).isNotNull();
        assertThat(cached.getDeptName()).isEqualTo("后端组");
        verify(fetcher, never()).fetchBatch(eq("orgs-batch"), any(), any());
    }

    @Test
    @DisplayName("invalidateAll: 精确踢出指定部门，不影响其他缓存项")
    void invalidateAll_precise() {
        DeptInfoVO d1 = new DeptInfoVO();
        d1.setDeptId("d1");
        d1.setDeptName("部门1");

        DeptInfoVO d2 = new DeptInfoVO();
        d2.setDeptId("d2");
        d2.setDeptName("部门2");

        when(fetcher.fetchBatch(eq("orgs-batch"), eq(List.of("d1")), eq(DeptInfoVO.class)))
                .thenReturn(List.of(d1));
        when(fetcher.fetchBatch(eq("orgs-batch"), eq(List.of("d2")), eq(DeptInfoVO.class)))
                .thenReturn(List.of(d2));

        orgCacheService.getById("d1");
        orgCacheService.getById("d2");
        assertThat(orgCacheService.stats().get("size")).isEqualTo(2L);

        // 仅失效 d1
        int removed = orgCacheService.invalidateAll(List.of("d1"));
        assertThat(removed).isEqualTo(1);

        // d2 依然命中，d1 重新回源
        orgCacheService.getById("d2");
        verify(fetcher, times(1)).fetchBatch(eq("orgs-batch"), eq(List.of("d2")), eq(DeptInfoVO.class));

        orgCacheService.getById("d1");
        verify(fetcher, times(2)).fetchBatch(eq("orgs-batch"), eq(List.of("d1")), eq(DeptInfoVO.class));
    }
}
