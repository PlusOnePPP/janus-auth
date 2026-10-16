package com.janus.module.system.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.annotation.SaCheckRole;
import cn.dev33.satoken.annotation.SaMode;
import com.janus.common.result.R;
import com.janus.module.system.annotation.Log;
import com.janus.module.system.dto.DeptSaveRequest;
import com.janus.module.system.service.SysDeptService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 部门管理接口（/system/dept/**）。
 *
 * <p>鉴权模型：list 作为用户管理页的部门过滤器，向持有「查看用户」码的用户开放；
 * 增删改仍需 super/admin 角色。</p>
 *
 * @author ppp
 */
@RestController
@RequestMapping("/api/admin/system/dept")
@RequiredArgsConstructor
public class SystemDeptController {

    private final SysDeptService deptService;

    /** GET /system/dept/list：部门列表（扁平），用户页过滤器，需「查看用户」码 */
    @SaCheckPermission("AC_1000000")
    @GetMapping("/list")
    public R<?> list(@RequestParam(required = false) String keyword,
                     @RequestParam(required = false) String pid) {
        return R.ok(deptService.list(keyword, pid));
    }

    /** GET /system/dept/{id}/members：部门下成员及职务列表 */
    @SaCheckPermission("AC_1000000")
    @GetMapping("/{id}/members")
    public R<?> getMembers(@PathVariable String id) {
        return R.ok(deptService.getDeptMembers(id));
    }

    /** POST /system/dept：新建部门（仅 super/admin） */
    @SaCheckRole(value = {"super", "admin"}, mode = SaMode.OR)
    @Log(module = "部门管理", description = "新增部门")
    @PostMapping
    public R<String> create(@RequestBody DeptSaveRequest req) {
        return R.ok(deptService.create(req));
    }

    /** PUT /system/dept/{id}：更新部门（仅 super/admin） */
    @SaCheckRole(value = {"super", "admin"}, mode = SaMode.OR)
    @Log(module = "部门管理", description = "编辑部门")
    @PutMapping("/{id}")
    public R<Void> update(@PathVariable String id, @RequestBody DeptSaveRequest req) {
        if (req.getId() == null) {
            req.setId(id);
        }
        deptService.update(req);
        return R.ok();
    }

    /** DELETE /system/dept/{id}：删除部门（仅 super/admin） */
    @SaCheckRole(value = {"super", "admin"}, mode = SaMode.OR)
    @Log(module = "部门管理", description = "删除部门")
    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable String id) {
        deptService.remove(id);
        return R.ok();
    }
}
