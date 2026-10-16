package com.janus.module.system.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.annotation.SaCheckRole;
import cn.dev33.satoken.annotation.SaMode;
import com.janus.common.result.R;
import com.janus.module.system.annotation.Log;
import com.janus.module.system.dto.PositionItemVO;
import com.janus.module.system.dto.PositionSaveRequest;
import com.janus.module.system.entity.SysPosition;
import com.janus.module.system.service.SysPositionService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 职务管理接口（/api/admin/system/position/**）。
 *
 * @author ppp
 */
@RestController
@RequestMapping("/api/admin/system/position")
@RequiredArgsConstructor
public class SystemPositionController {

    private final SysPositionService positionService;

    /** GET /list：职务列表 */
    @GetMapping("/list")
    public R<List<PositionItemVO>> list(@RequestParam(required = false) String keyword,
                                        @RequestParam(required = false) Integer status) {
        return R.ok(positionService.listPositions(keyword, status));
    }

    /** GET /{id}：职务详情 */
    @GetMapping("/{id}")
    public R<SysPosition> getById(@PathVariable String id) {
        return R.ok(positionService.getById(id));
    }

    /** POST /：新增职务 */
    @SaCheckRole(value = {"super", "admin"}, mode = SaMode.OR)
    @Log(module = "职务管理", description = "新增职务")
    @PostMapping
    public R<SysPosition> create(@RequestBody PositionSaveRequest req) {
        return R.ok(positionService.create(req));
    }

    /** PUT /{id}：更新职务 */
    @SaCheckRole(value = {"super", "admin"}, mode = SaMode.OR)
    @Log(module = "职务管理", description = "编辑职务")
    @PutMapping("/{id}")
    public R<SysPosition> update(@PathVariable String id, @RequestBody PositionSaveRequest req) {
        if (req.getId() == null) {
            req.setId(id);
        }
        return R.ok(positionService.update(req));
    }

    /** DELETE /{id}：删除职务 */
    @SaCheckRole(value = {"super", "admin"}, mode = SaMode.OR)
    @Log(module = "职务管理", description = "删除职务")
    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable String id) {
        positionService.delete(id);
        return R.ok();
    }

    /** PUT /{id}/status：修改职务状态 */
    @SaCheckRole(value = {"super", "admin"}, mode = SaMode.OR)
    @Log(module = "职务管理", description = "修改职务状态")
    @PutMapping("/{id}/status")
    public R<Void> changeStatus(@PathVariable String id, @RequestParam Integer status) {
        positionService.changeStatus(id, status);
        return R.ok();
    }
}
