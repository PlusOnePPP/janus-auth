package com.janus.client.service;

import cn.dev33.satoken.SaManager;
import cn.dev33.satoken.http.SaHttpTemplate;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.janus.client.model.DeptInfoVO;
import com.janus.client.model.PageResult;
import com.janus.client.model.UserInfoVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class JanusDataFetcherTest {

    private ObjectMapper objectMapper;
    private JanusDataFetcher fetcher;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        fetcher = new JanusDataFetcher(objectMapper);
        cn.dev33.satoken.sso.SaSsoManager.getClientConfig().setServerUrl("http://localhost:8080");
        cn.dev33.satoken.sso.SaSsoManager.getClientConfig().setSecretKey("test-secret");
    }

    @Test
    @DisplayName("parseData: 成功解析分页对象格式响应（users-search: data={total, page, size, users}）")
    void parseData_objectPayload_usersSearch() {
        String json = """
            {
              "code": 200,
              "msg": "ok",
              "data": {
                "total": 128,
                "page": 1,
                "size": 50,
                "users": [
                  {
                    "uid": "u1",
                    "username": "zhangsan",
                    "realName": "张三",
                    "deptId": "d1",
                    "deptName": "研发部",
                    "exists": true
                  }
                ]
              }
            }
            """;

        SaManager.setSaHttpTemplate(new SaHttpTemplate() {
            @Override
            public String get(String url) {
                return json;
            }
            @Override
            public String postByFormData(String url, java.util.Map<String, Object> formData) {
                return json;
            }
        });

        List<UserInfoVO> list = fetcher.fetchSearch("users-search", "zhang", 1, 50, UserInfoVO.class);
        assertThat(list).hasSize(1);
        assertThat(list.get(0).getUid()).isEqualTo("u1");
        assertThat(list.get(0).getRealName()).isEqualTo("张三");

        PageResult<UserInfoVO> page = fetcher.fetchSearchPage("users-search", "zhang", 1, 50, UserInfoVO.class);
        assertThat(page.getTotal()).isEqualTo(128L);
        assertThat(page.getPage()).isEqualTo(1);
        assertThat(page.getSize()).isEqualTo(50);
        assertThat(page.getRecords()).hasSize(1);
        assertThat(page.getRecords().get(0).getUsername()).isEqualTo("zhangsan");
    }

    @Test
    @DisplayName("parseData: 成功解析机构分页格式响应（orgs-search: data={total, page, size, orgs}）")
    void parseData_objectPayload_orgsSearch() {
        String json = """
            {
              "code": 200,
              "msg": "ok",
              "data": {
                "total": 15,
                "page": 1,
                "size": 20,
                "orgs": [
                  {
                    "deptId": "d1",
                    "deptName": "财务部",
                    "parentId": "0",
                    "status": 1
                  }
                ]
              }
            }
            """;

        SaManager.setSaHttpTemplate(new SaHttpTemplate() {
            @Override
            public String get(String url) {
                return json;
            }
            @Override
            public String postByFormData(String url, java.util.Map<String, Object> formData) {
                return json;
            }
        });

        List<DeptInfoVO> list = fetcher.fetchSearch("orgs-search", "财务", 1, 20, DeptInfoVO.class);
        assertThat(list).hasSize(1);
        assertThat(list.get(0).getDeptId()).isEqualTo("d1");
        assertThat(list.get(0).getDeptName()).isEqualTo("财务部");

        PageResult<DeptInfoVO> page = fetcher.fetchSearchPage("orgs-search", "财务", 1, 20, DeptInfoVO.class);
        assertThat(page.getTotal()).isEqualTo(15L);
        assertThat(page.getRecords()).hasSize(1);
    }

    @Test
    @DisplayName("parseData: 兼容纯数组格式（orgs-children / users-batch）")
    void parseData_arrayPayload() {
        String json = """
            {
              "code": 200,
              "msg": "ok",
              "data": [
                {
                  "deptId": "d2",
                  "deptName": "运维部",
                  "parentId": "0",
                  "status": 1
                }
              ]
            }
            """;

        SaManager.setSaHttpTemplate(new SaHttpTemplate() {
            @Override
            public String get(String url) {
                return json;
            }
            @Override
            public String postByFormData(String url, java.util.Map<String, Object> formData) {
                return json;
            }
        });

        List<DeptInfoVO> list = fetcher.fetchChildren("0", DeptInfoVO.class);
        assertThat(list).hasSize(1);
        assertThat(list.get(0).getDeptName()).isEqualTo("运维部");
    }
}
