package com.janus.module.system.controller;

import cn.dev33.satoken.annotation.SaCheckRole;
import cn.dev33.satoken.annotation.SaMode;
import com.janus.common.result.R;
import com.janus.module.system.annotation.Log;
import com.janus.module.system.entity.SysJob;
import com.janus.module.system.mapper.SysJobMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 定时任务管理（/system/job/**）。
 * 注：当前为 CRUD 管理，实际调度需集成 Quartz 或 Spring Scheduling。
 *
 * @author ppp
 */
@RestController
@RequestMapping("/api/admin/system/job")
@RequiredArgsConstructor
@SaCheckRole(value = {"super", "admin"}, mode = SaMode.OR)
public class SysJobController {

    private final SysJobMapper jobMapper;

    @GetMapping("/list")
    public R<List<SysJob>> list() {
        return R.ok(jobMapper.selectList(null));
    }

    @Log(module = "定时任务", description = "新增任务")
    @PostMapping
    public R<String> create(@RequestBody SysJob job) {
        jobMapper.insert(job);
        return R.ok(job.getId());
    }

    @Log(module = "定时任务", description = "编辑任务")
    @PutMapping("/{id}")
    public R<Void> update(@PathVariable String id, @RequestBody SysJob job) {
        if (job.getId() == null) job.setId(id);
        jobMapper.updateById(job);
        return R.ok();
    }

    @Log(module = "定时任务", description = "删除任务")
    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable String id) {
        jobMapper.deleteById(id);
        return R.ok();
    }

    @Log(module = "定时任务", description = "切换任务状态")
    @PutMapping("/{id}/toggle")
    public R<Void> toggle(@PathVariable String id) {
        SysJob job = jobMapper.selectById(id);
        if (job != null) {
            job.setStatus(job.getStatus() == 1 ? 0 : 1);
            jobMapper.updateById(job);
        }
        return R.ok();
    }
}
