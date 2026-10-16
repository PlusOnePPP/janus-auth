package com.janus.module.sso.service;

import com.janus.module.sso.model.SsoRoleBriefVO;
import com.janus.module.system.mapper.SysRoleMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 角色信息全量组装服务：/sso/getData roles-all 回源接口的取数实现。
 *
 * @author ppp
 */
@Service
@RequiredArgsConstructor
public class SsoRoleBriefService {

    private final SysRoleMapper roleMapper;

    /** 全量角色列表（含停用，客户端按 status 自行判断） */
    public List<SsoRoleBriefVO> listAll() {
        return roleMapper.selectList(null).stream()
                .map(SsoRoleBriefVO::of)
                .toList();
    }
}
