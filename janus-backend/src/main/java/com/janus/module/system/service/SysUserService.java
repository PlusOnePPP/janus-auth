package com.janus.module.system.service;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.janus.common.result.PageResult;
import com.janus.common.result.ServiceException;
import com.janus.module.sso.constant.ChangeReason;
import com.janus.module.sso.service.UserProfileChangePublisher;
import com.janus.module.system.dto.ChangePasswordRequest;
import com.janus.module.system.dto.ProfileUpdateRequest;
import com.janus.module.system.dto.UserItemVO;
import com.janus.module.system.dto.UserSaveRequest;
import com.janus.module.system.dto.UserDeptPositionDTO;
import com.janus.module.system.dto.UserDeptPositionVO;
import com.janus.module.system.entity.SysDept;
import com.janus.module.system.entity.SysPosition;
import com.janus.module.system.entity.SysRole;
import com.janus.module.system.entity.SysUser;
import com.janus.module.system.entity.SysUserDeptPosition;
import com.janus.module.system.entity.SysUserRole;
import com.janus.module.system.mapper.SysDeptMapper;
import com.janus.module.system.mapper.SysPositionMapper;
import com.janus.module.system.mapper.SysRoleMapper;
import com.janus.module.system.mapper.SysUserDeptPositionMapper;
import com.janus.module.system.mapper.SysUserMapper;
import com.janus.module.system.mapper.SysUserRoleMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 用户领域服务：用户查询、CRUD 与密码管理。
 *
 * @author ppp
 */
@Service
@RequiredArgsConstructor
public class SysUserService {

    private final SysUserMapper userMapper;
    private final SysDeptMapper deptMapper;
    private final SysRoleMapper roleMapper;
    private final SysUserRoleMapper userRoleMapper;
    private final SysPositionMapper positionMapper;
    private final SysUserDeptPositionMapper userDeptPositionMapper;
    private final BCryptPasswordEncoder passwordEncoder;
    private final UserProfileChangePublisher changePublisher;

    /** 内置超级管理员（V1 初始化，界面不可删除） */
    public static final String SUPER_ADMIN_USER_ID = "u0000000000000000000000000000001";

    /** 头像等上传文件的存储根目录（默认 ./uploads，可用 V_BEN_UPLOAD_DIR 覆盖） */
    @Value("${janus.auth.upload-dir:./uploads}")
    private String uploadDir;

    private static final long AVATAR_MAX_SIZE = 5 * 1024 * 1024L;
    private static final Set<String> AVATAR_EXTENSIONS = Set.of(".jpg", ".jpeg", ".png", ".webp", ".gif");

    /** 按ID查询用户 */
    public SysUser getById(String userId) {
        return userMapper.selectById(userId);
    }

    /** 用户角色编码列表（仅启用角色） */
    public List<String> getRoleCodes(String userId) {
        List<String> roleIds = userRoleMapper.selectList(new LambdaQueryWrapper<SysUserRole>()
                        .eq(SysUserRole::getUserId, userId)).stream()
                .map(SysUserRole::getRoleId).toList();
        if (roleIds.isEmpty()) {
            return List.of();
        }
        return roleMapper.selectList(new LambdaQueryWrapper<SysRole>()
                        .in(SysRole::getId, roleIds)
                        .eq(SysRole::getStatus, 1)).stream()
                .map(SysRole::getCode).toList();
    }

    /** 用户分页列表（脱敏，附带角色与部门名称，支持角色筛选与关键字模糊检索） */
    public PageResult<UserItemVO> listUsers(int page, int pageSize, String username, Integer status, String deptId, String roleId, String keyword) {
        LambdaQueryWrapper<SysUser> w = new LambdaQueryWrapper<SysUser>()
                .orderByDesc(SysUser::getId);
        if (StringUtils.hasText(username)) {
            w.like(SysUser::getUsername, username);
        }
        if (status != null) {
            w.eq(SysUser::getStatus, status);
        }
        if (StringUtils.hasText(deptId)) {
            w.eq(SysUser::getDeptId, deptId);
        }
        if (StringUtils.hasText(keyword)) {
            w.and(q -> q.like(SysUser::getUsername, keyword).or().like(SysUser::getRealName, keyword));
        }
        if (StringUtils.hasText(roleId)) {
            w.apply("id IN (SELECT user_id FROM sys_user_role WHERE role_id = {0})", roleId);
        }
        IPage<SysUser> p = userMapper.selectPage(new Page<>(page, pageSize), w);
        List<SysUser> records = p.getRecords();
        if (records.isEmpty()) {
            return new PageResult<>(List.of(), p.getTotal());
        }

        // 批量预查部门名称（消除 N+1）
        Set<String> deptIds = records.stream()
                .map(SysUser::getDeptId)
                .filter(StringUtils::hasText)
                .collect(Collectors.toSet());
        Map<String, String> deptNameMap = deptIds.isEmpty() ? Map.of() :
                deptMapper.selectBatchIds(deptIds).stream()
                        .collect(Collectors.toMap(SysDept::getId, SysDept::getName, (a, b) -> a));

        // 批量预查用户角色关联与编码（消除 N+1）
        List<String> uids = records.stream().map(SysUser::getId).toList();
        List<SysUserRole> userRoles = userRoleMapper.selectList(new LambdaQueryWrapper<SysUserRole>()
                .in(SysUserRole::getUserId, uids));
        Map<String, List<String>> userRoleIdsMap = userRoles.stream()
                .collect(Collectors.groupingBy(SysUserRole::getUserId,
                        Collectors.mapping(SysUserRole::getRoleId, Collectors.toList())));

        Set<String> allRoleIds = userRoles.stream().map(SysUserRole::getRoleId).collect(Collectors.toSet());
        List<SysRole> allRoles = allRoleIds.isEmpty() ? List.of() : roleMapper.selectBatchIds(allRoleIds);
        Map<String, String> roleCodeMap = (allRoles == null || allRoles.isEmpty()) ? Map.of() :
                allRoles.stream()
                        .filter(Objects::nonNull)
                        .collect(Collectors.toMap(SysRole::getId, SysRole::getCode, (a, b) -> a));
        Map<String, String> roleNameMap = (allRoles == null || allRoles.isEmpty()) ? Map.of() :
                allRoles.stream()
                        .filter(Objects::nonNull)
                        .collect(Collectors.toMap(SysRole::getId, SysRole::getName, (a, b) -> a));

        // 批量预查用户部门与职务任职（消除 N+1）
        List<SysUserDeptPosition> udpList = userDeptPositionMapper.selectList(new QueryWrapper<SysUserDeptPosition>()
                .in("user_id", uids));
        Map<String, List<SysUserDeptPosition>> userUdpMap = udpList.stream()
                .collect(Collectors.groupingBy(SysUserDeptPosition::getUserId));

        Set<String> allPosIds = udpList.stream().map(SysUserDeptPosition::getPositionId).filter(StringUtils::hasText).collect(Collectors.toSet());
        Set<String> allDeptIds = udpList.stream().map(SysUserDeptPosition::getDeptId).filter(StringUtils::hasText).collect(Collectors.toSet());
        allDeptIds.addAll(deptIds);

        Map<String, SysPosition> posMap = allPosIds.isEmpty() ? Map.of() :
                positionMapper.selectBatchIds(allPosIds).stream()
                        .filter(Objects::nonNull)
                        .collect(Collectors.toMap(SysPosition::getId, posItem -> posItem, (a, b) -> a));

        Map<String, String> allDeptNameMap = allDeptIds.isEmpty() ? Map.of() :
                deptMapper.selectBatchIds(allDeptIds).stream()
                        .filter(Objects::nonNull)
                        .collect(Collectors.toMap(SysDept::getId, SysDept::getName, (a, b) -> a));

        List<UserItemVO> items = records.stream().map(u -> {
            UserItemVO vo = new UserItemVO();
            vo.setId(u.getId());
            vo.setUsername(u.getUsername());
            vo.setRealName(u.getRealName());
            vo.setAvatar(u.getAvatar());
            vo.setHomePath(u.getHomePath());
            vo.setDeptId(u.getDeptId());
            vo.setDeptName(u.getDeptId() != null ? allDeptNameMap.get(u.getDeptId()) : null);
            vo.setStatus(u.getStatus());
            vo.setRemark(u.getRemark());
            vo.setCreateTime(u.getCreateTime());
            List<String> rIds = userRoleIdsMap.getOrDefault(u.getId(), List.of());
            vo.setRoleIds(rIds);
            vo.setRoleCodes(rIds.stream().map(roleCodeMap::get).filter(Objects::nonNull).toList());
            vo.setRoleNames(rIds.stream().map(roleNameMap::get).filter(Objects::nonNull).toList());

            List<SysUserDeptPosition> myUdps = userUdpMap.getOrDefault(u.getId(), List.of());
            List<UserDeptPositionVO> udpVOs = myUdps.stream().map(udp -> {
                UserDeptPositionVO item = new UserDeptPositionVO();
                item.setId(udp.getId());
                item.setUserId(udp.getUserId());
                item.setUsername(u.getUsername());
                item.setRealName(u.getRealName());
                item.setDeptId(udp.getDeptId());
                item.setDeptName(allDeptNameMap.get(udp.getDeptId()));
                item.setPositionId(udp.getPositionId());
                SysPosition pos = posMap.get(udp.getPositionId());
                if (pos != null) {
                    item.setPositionName(pos.getName());
                    item.setPositionCode(pos.getCode());
                    item.setLevelWeight(pos.getLevelWeight());
                }
                item.setIsMain(udp.getIsMain() != null && udp.getIsMain() == 1);
                item.setIsLeader(udp.getIsLeader() != null && udp.getIsLeader() == 1);
                return item;
            }).toList();
            vo.setDeptPositions(udpVOs);

            // 回填主职务
            udpVOs.stream().filter(i -> Boolean.TRUE.equals(i.getIsMain())).findFirst().ifPresent(mainItem -> {
                vo.setPositionId(mainItem.getPositionId());
                vo.setPositionName(mainItem.getPositionName());
            });

            return vo;
        }).toList();

        return new PageResult<>(items, p.getTotal());
    }

    /** 用户分页列表（脱敏，附带角色） */
    public PageResult<UserItemVO> listUsers(int page, int pageSize, String username, Integer status, String deptId) {
        return listUsers(page, pageSize, username, status, deptId, null, null);
    }

    /** 新建用户 */
    @Transactional
    public String createUser(UserSaveRequest req) {
        if (!StringUtils.hasText(req.getUsername())) {
            throw ServiceException.badRequest("登录名不能为空");
        }
        if (!StringUtils.hasText(req.getPassword())) {
            throw ServiceException.badRequest("密码不能为空");
        }
        long dup = userMapper.selectCount(new LambdaQueryWrapper<SysUser>().eq(SysUser::getUsername, req.getUsername()));
        if (dup > 0) {
            throw ServiceException.badRequest("登录名已存在");
        }
        // 预校验部门与职务
        List<UserDeptPositionDTO> validUdps = normalizeAndValidateDeptPositions(req.getDeptPositions(), req.getDeptId());
        String mainDeptId = validUdps.stream().filter(u -> Boolean.TRUE.equals(u.getIsMain())).findFirst().map(UserDeptPositionDTO::getDeptId).orElse(null);

        SysUser user = new SysUser();
        user.setUsername(req.getUsername());
        user.setPasswordHash(passwordEncoder.encode(req.getPassword()));
        user.setRealName(req.getRealName());
        user.setAvatar(req.getAvatar());
        user.setHomePath(req.getHomePath());
        user.setDeptId(mainDeptId);
        user.setStatus(req.getStatus() == null ? 1 : req.getStatus());
        user.setRemark(req.getRemark());
        user.setCreateTime(LocalDateTime.now());
        user.setUpdateTime(LocalDateTime.now());
        userMapper.insert(user);

        // 绑定部门及职务
        saveDeptPositions(user.getId(), validUdps);

        bindRoles(user.getId(), req.getRoleIds());
        if (user.getId() != null) {
            publishProfileChanged(List.of(user.getId()), ChangeReason.CREATE_USER);
        }
        return user.getId();
    }

    /** 更新用户 */
    @Transactional
    public void updateUser(UserSaveRequest req) {
        if (req.getId() == null) {
            throw ServiceException.badRequest("用户 ID 不能为空");
        }
        SysUser user = userMapper.selectById(req.getId());
        if (user == null) {
            throw ServiceException.badRequest("用户不存在");
        }
        String oldUsername = user.getUsername();
        String oldRealName = user.getRealName();
        String oldDeptId = user.getDeptId();
        Integer oldStatus = user.getStatus();

        if (StringUtils.hasText(req.getUsername())) {
            long dup = userMapper.selectCount(new LambdaQueryWrapper<SysUser>()
                    .eq(SysUser::getUsername, req.getUsername()).ne(SysUser::getId, req.getId()));
            if (dup > 0) {
                throw ServiceException.badRequest("登录名已存在");
            }
            user.setUsername(req.getUsername());
        }
        if (StringUtils.hasText(req.getPassword())) {
            user.setPasswordHash(passwordEncoder.encode(req.getPassword()));
        }
        if (StringUtils.hasText(req.getRealName())) {
            user.setRealName(req.getRealName());
        }
        if (req.getAvatar() != null) {
            user.setAvatar(req.getAvatar());
        }
        if (req.getHomePath() != null) {
            user.setHomePath(req.getHomePath());
        }
        boolean deptPositionsChanged = false;
        if (req.getDeptPositions() != null) {
            String mainDeptId = bindDeptPositions(user.getId(), req.getDeptPositions(), req.getDeptId());
            if (!Objects.equals(user.getDeptId(), mainDeptId)) {
                user.setDeptId(mainDeptId);
            }
            deptPositionsChanged = true;
        } else if (StringUtils.hasText(req.getDeptId()) && !req.getDeptId().equals(user.getDeptId())) {
            String mainDeptId = bindDeptPositions(user.getId(), null, req.getDeptId());
            user.setDeptId(mainDeptId);
            deptPositionsChanged = true;
        }

        if (req.getStatus() != null) {
            user.setStatus(req.getStatus());
        }
        if (req.getRemark() != null) {
            user.setRemark(req.getRemark());
        }
        user.setUpdateTime(LocalDateTime.now());
        userMapper.updateById(user);

        boolean rolesChanged = false;
        // 仅当请求携带角色列表时才重新绑定（空列表=清空，null=保持不变）
        if (req.getRoleIds() != null) {
            List<String> currentRoleIds = userRoleMapper.selectList(
                    new LambdaQueryWrapper<SysUserRole>().eq(SysUserRole::getUserId, user.getId()))
                    .stream().map(SysUserRole::getRoleId).toList();
            rolesChanged = !new HashSet<>(currentRoleIds).equals(new HashSet<>(req.getRoleIds()));
            if (rolesChanged) {
                bindRoles(user.getId(), req.getRoleIds());
            }
        }

        // 仅当影响客户端 UserInfoVO 契约的字段发生变化时才发布变更事件
        boolean briefAffected = !Objects.equals(oldUsername, user.getUsername())
                || !Objects.equals(oldRealName, user.getRealName())
                || !Objects.equals(oldDeptId, user.getDeptId())
                || !Objects.equals(oldStatus, user.getStatus())
                || rolesChanged
                || deptPositionsChanged;

        if (briefAffected) {
            publishProfileChanged(List.of(user.getId()), ChangeReason.UPDATE_USER);
            if (user.getStatus() != null && user.getStatus() == 0) {
                // 立即注销该用户在认证中心的中央会话，彻底消除 401 循环跳转
                StpUtil.logout(user.getId());
            }
        }
    }

    /** 删除用户（禁止删除自己和内置超级管理员） */
    @Transactional
    public void deleteUser(String id) {
        if (id == null) {
            throw ServiceException.badRequest("用户 ID 不能为空");
        }
        String loginId = StpUtil.getLoginIdAsString();
        if (id.equals(loginId)) {
            throw ServiceException.badRequest("不能删除当前登录账号");
        }
        if (SUPER_ADMIN_USER_ID.equals(id)) {
            throw ServiceException.badRequest("超级管理员为内置账号，不允许删除");
        }
        userRoleMapper.delete(new LambdaQueryWrapper<SysUserRole>().eq(SysUserRole::getUserId, id));
        userDeptPositionMapper.delete(new LambdaQueryWrapper<SysUserDeptPosition>().eq(SysUserDeptPosition::getUserId, id));
        userMapper.deleteById(id);
        StpUtil.logout(id);
        publishProfileChanged(List.of(id), ChangeReason.DELETE_USER);
    }

    /**
     * 绑定用户部门及职务关联关系
     *
     * @param userId         用户 ID
     * @param dtoList        部门任职列表
     * @param fallbackDeptId 若没有传列表时的降级主部门 ID
     * @return 主部门 ID
     */
    private List<UserDeptPositionDTO> normalizeAndValidateDeptPositions(List<UserDeptPositionDTO> dtoList, String fallbackDeptId) {
        if ((dtoList == null || dtoList.isEmpty()) && !StringUtils.hasText(fallbackDeptId)) {
            throw ServiceException.badRequest("用户必须分配至少一个部门及职务（所属机构不能为空）");
        }

        List<UserDeptPositionDTO> list = dtoList;
        if (list == null || list.isEmpty()) {
            // 兼容旧调用：分配默认普通员工
            UserDeptPositionDTO single = new UserDeptPositionDTO();
            single.setDeptId(fallbackDeptId);
            single.setPositionId("p0000000000000000000000000000008"); // 默认普通员工
            single.setIsMain(true);
            single.setIsLeader(false);
            list = List.of(single);
        }

        Set<String> deptSet = new HashSet<>();
        UserDeptPositionDTO mainDto = null;
        for (UserDeptPositionDTO dto : list) {
            if (!StringUtils.hasText(dto.getDeptId())) {
                throw ServiceException.badRequest("部门 ID 不能为空");
            }
            if (!StringUtils.hasText(dto.getPositionId())) {
                throw ServiceException.badRequest("职务不能为空，用户在每个部门必须拥有一个职务");
            }
            if (!deptSet.add(dto.getDeptId())) {
                throw ServiceException.badRequest("同一用户不能重复添加相同部门");
            }
            if (deptMapper.selectById(dto.getDeptId()) == null) {
                throw ServiceException.badRequest("所属机构不存在: " + dto.getDeptId());
            }
            if (positionMapper != null && StringUtils.hasText(dto.getPositionId())) {
                if (positionMapper.selectById(dto.getPositionId()) == null && dtoList != null && !dtoList.isEmpty()) {
                    throw ServiceException.badRequest("指定的职务不存在: " + dto.getPositionId());
                }
            }
            if (Boolean.TRUE.equals(dto.getIsMain())) {
                if (mainDto != null) {
                    throw ServiceException.badRequest("用户只能有一个主部门");
                }
                mainDto = dto;
            }
        }

        if (mainDto == null) {
            if (list.size() == 1) {
                mainDto = list.get(0);
                mainDto.setIsMain(true);
            } else {
                throw ServiceException.badRequest("必须指定且只能指定一个主部门");
            }
        }

        return list;
    }

    private void saveDeptPositions(String userId, List<UserDeptPositionDTO> list) {
        userDeptPositionMapper.delete(new QueryWrapper<SysUserDeptPosition>()
                .eq("user_id", userId));

        for (UserDeptPositionDTO dto : list) {
            SysUserDeptPosition entity = new SysUserDeptPosition();
            entity.setUserId(userId);
            entity.setDeptId(dto.getDeptId());
            entity.setPositionId(dto.getPositionId());
            entity.setIsMain(Boolean.TRUE.equals(dto.getIsMain()) ? 1 : 0);
            entity.setIsLeader(Boolean.TRUE.equals(dto.getIsLeader()) ? 1 : 0);
            entity.setCreateTime(LocalDateTime.now());
            userDeptPositionMapper.insert(entity);
        }
    }

    private String bindDeptPositions(String userId, List<UserDeptPositionDTO> dtoList, String fallbackDeptId) {
        List<UserDeptPositionDTO> list = normalizeAndValidateDeptPositions(dtoList, fallbackDeptId);
        saveDeptPositions(userId, list);
        return list.stream().filter(u -> Boolean.TRUE.equals(u.getIsMain())).findFirst().map(UserDeptPositionDTO::getDeptId).orElse(fallbackDeptId);
    }

    /** 获取指定用户的部门与职务明细 */
    public List<UserDeptPositionVO> getUserDeptPositions(String userId) {
        List<SysUserDeptPosition> list = userDeptPositionMapper.selectList(new LambdaQueryWrapper<SysUserDeptPosition>()
                .eq(SysUserDeptPosition::getUserId, userId));
        if (list.isEmpty()) {
            return List.of();
        }
        Set<String> deptIds = list.stream().map(SysUserDeptPosition::getDeptId).collect(Collectors.toSet());
        Set<String> posIds = list.stream().map(SysUserDeptPosition::getPositionId).collect(Collectors.toSet());
        Map<String, String> deptNameMap = deptMapper.selectBatchIds(deptIds).stream()
                .collect(Collectors.toMap(SysDept::getId, SysDept::getName, (a, b) -> a));
        Map<String, SysPosition> posMap = positionMapper.selectBatchIds(posIds).stream()
                .collect(Collectors.toMap(SysPosition::getId, p -> p, (a, b) -> a));

        return list.stream().map(udp -> {
            UserDeptPositionVO vo = new UserDeptPositionVO();
            vo.setId(udp.getId());
            vo.setUserId(udp.getUserId());
            vo.setDeptId(udp.getDeptId());
            vo.setDeptName(deptNameMap.get(udp.getDeptId()));
            vo.setPositionId(udp.getPositionId());
            SysPosition pos = posMap.get(udp.getPositionId());
            if (pos != null) {
                vo.setPositionName(pos.getName());
                vo.setPositionCode(pos.getCode());
                vo.setLevelWeight(pos.getLevelWeight());
            }
            vo.setIsMain(udp.getIsMain() != null && udp.getIsMain() == 1);
            vo.setIsLeader(udp.getIsLeader() != null && udp.getIsLeader() == 1);
            return vo;
        }).toList();
    }

    /** 当前用户更新自己的资料（个人中心-基本设置：姓名/简介） */
    public void updateProfile(ProfileUpdateRequest req) {
        String loginId = StpUtil.getLoginIdAsString();
        SysUser user = userMapper.selectById(loginId);
        if (user == null) {
            throw ServiceException.badRequest("用户不存在");
        }
        boolean realNameChanged = StringUtils.hasText(req.getRealName())
                && !Objects.equals(req.getRealName(), user.getRealName());
        if (StringUtils.hasText(req.getRealName())) {
            user.setRealName(req.getRealName());
        }
        // intro 允许清空（传空串视为清空）
        user.setIntro(req.getIntro());
        user.setUpdateTime(LocalDateTime.now());
        userMapper.updateById(user);
        // 只有 realName 真正变更才发布通知（intro 不在客户端缓存契约中）
        if (realNameChanged) {
            publishProfileChanged(List.of(loginId), ChangeReason.UPDATE_PROFILE);
        }
    }

    /**
     * 当前用户上传头像：校验类型与大小，落盘到 {upload-dir}/avatar/，
     * 更新 sys_user.avatar 并返回可公开访问的 URL（/avatar/file/{filename}）。
     */
    public String saveAvatar(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw ServiceException.badRequest("请选择头像文件");
        }
        if (file.getSize() > AVATAR_MAX_SIZE) {
            throw ServiceException.badRequest("头像文件不能超过 5MB");
        }
        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            throw ServiceException.badRequest("仅支持图片文件");
        }
        String original = file.getOriginalFilename();
        String ext = original == null ? "" : original.substring(original.lastIndexOf('.')).toLowerCase(Locale.ROOT);
        if (!AVATAR_EXTENSIONS.contains(ext)) {
            throw ServiceException.badRequest("仅支持 jpg/png/webp/gif 格式");
        }
        String loginId = StpUtil.getLoginIdAsString();
        String filename = "u" + loginId + "-" + System.currentTimeMillis() + ext;
        try {
            Path dir = Paths.get(uploadDir, "avatar").toAbsolutePath().normalize();
            Files.createDirectories(dir);
            file.transferTo(dir.resolve(filename).toFile());
        } catch (IOException e) {
            throw new ServiceException(HttpStatus.INTERNAL_SERVER_ERROR, "头像保存失败");
        }
        String url = "/api/admin/avatar/file/" + filename;
        applyAvatar(loginId, url);
        return url;
    }

    /** 更新用户头像字段（头像不在客户端缓存契约中，不发通知） */
    private void applyAvatar(String userId, String avatarUrl) {
        SysUser user = userMapper.selectById(userId);
        if (user == null) {
            throw ServiceException.badRequest("用户不存在");
        }
        user.setAvatar(avatarUrl);
        user.setUpdateTime(LocalDateTime.now());
        userMapper.updateById(user);
    }

    /** 当前用户修改自己的密码（密码不在客户端缓存契约中，不发通知） */
    public void changePassword(ChangePasswordRequest req) {
        if (!StringUtils.hasText(req.getOldPassword())
                || !StringUtils.hasText(req.getNewPassword())
                || !StringUtils.hasText(req.getConfirmPassword())) {
            throw ServiceException.badRequest("密码项不能为空");
        }
        if (!req.getNewPassword().equals(req.getConfirmPassword())) {
            throw ServiceException.badRequest("两次输入的新密码不一致");
        }
        String loginId = StpUtil.getLoginIdAsString();
        SysUser user = userMapper.selectById(loginId);
        if (user == null || !passwordEncoder.matches(req.getOldPassword(), user.getPasswordHash())) {
            throw ServiceException.badRequest("原密码不正确");
        }
        user.setPasswordHash(passwordEncoder.encode(req.getNewPassword()));
        user.setUpdateTime(LocalDateTime.now());
        userMapper.updateById(user);
    }

    /** 管理员重置某用户密码（密码不在客户端缓存契约中，不发通知） */
    @Transactional
    public void resetPassword(String id, String newPassword) {
        if (id == null) {
            throw ServiceException.badRequest("用户 ID 不能为空");
        }
        if (!StringUtils.hasText(newPassword)) {
            throw ServiceException.badRequest("新密码不能为空");
        }
        SysUser user = userMapper.selectById(id);
        if (user == null) {
            throw ServiceException.badRequest("用户不存在");
        }
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        user.setUpdateTime(LocalDateTime.now());
        userMapper.updateById(user);
    }

    // ----------------------------------------------------------------- 私有方法

    /** 发布用户信息变更事件（记录变更流水并在事务提交后异步推送失效通知给所有已注册客户端） */
    private void publishProfileChanged(List<String> uids, String reason) {
        changePublisher.publishUsers(uids, reason);
    }

    /** 替换用户-角色关联 */
    private void bindRoles(String userId, List<String> roleIds) {
        userRoleMapper.delete(new LambdaQueryWrapper<SysUserRole>().eq(SysUserRole::getUserId, userId));
        if (roleIds == null || roleIds.isEmpty()) {
            return;
        }
        for (String roleId : roleIds) {
            SysUserRole ur = new SysUserRole();
            ur.setUserId(userId);
            ur.setRoleId(roleId);
            userRoleMapper.insert(ur);
        }
    }
}
