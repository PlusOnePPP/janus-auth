<script lang="ts" setup>
import type { SystemRoleApi } from '#/api/system/role';

import { computed, onMounted, ref, watch } from 'vue';

import { Page, useVbenDrawer } from '@vben/common-ui';
import {
  Download,
  Plus,
  RotateCw,
  Search,
} from '@vben/icons';
import { $t } from '@vben/locales';

import {
  Button,
  Empty,
  Input,
  message,
  Pagination,
  Popconfirm,
  Space,
  Spin,
  Table,
  Tabs,
  Tag,
  Tree,
} from 'ant-design-vue';

import dayjs from 'dayjs';

import { useAccess } from '@vben/access';
import { getPermissionMenuTree } from '#/api/system/menu';
import {
  assignRoleMenus,
  deleteRole,
  exportRoleList,
  getRoleList,
  getRoleMenus,
  getRoleUsers,
  removeRoleUsers,
} from '#/api/system/role';

import Form from './modules/form.vue';
import UserAddModal from './modules/user-add-modal.vue';

const { hasAccessByCodes } = useAccess();

// ----------------------------------------------------------------------
// 抽屉与弹窗状态
// ----------------------------------------------------------------------
const [FormDrawer, formDrawerApi] = useVbenDrawer({
  connectedComponent: Form,
  destroyOnClose: true,
});

const userAddModalOpen = ref(false);

// ----------------------------------------------------------------------
// 角色列表状态（左侧）
// ----------------------------------------------------------------------
const rolesLoading = ref(false);
const roleList = ref<SystemRoleApi.SystemRole[]>([]);
const roleSearchText = ref('');
const activeRoleId = ref<string>('');

const filteredRoles = computed(() => {
  const q = roleSearchText.value.trim().toLowerCase();
  if (!q) return roleList.value;
  return roleList.value.filter(
    (r) =>
      r.name?.toLowerCase().includes(q) ||
      r.code?.toLowerCase().includes(q) ||
      r.creatorName?.toLowerCase().includes(q),
  );
});

function formatDateTime(time?: string) {
  if (!time) return '-';
  const d = dayjs(time);
  return d.isValid() ? d.format('YYYY-MM-DD HH:mm') : time;
}

function formatFullDateTime(time?: string) {
  if (!time) return '-';
  const d = dayjs(time);
  return d.isValid() ? d.format('YYYY-MM-DD HH:mm:ss') : time;
}

const activeRole = computed(() => {
  return roleList.value.find((r) => r.id === activeRoleId.value);
});

// ----------------------------------------------------------------------
// 右侧 Tab 状态
// ----------------------------------------------------------------------
const activeTab = ref<'permissions' | 'users'>('permissions');

// ----------------------------------------------------------------------
// Tab 1: 权限分配
// ----------------------------------------------------------------------
const menuLoading = ref(false);
const savingMenus = ref(false);
const rawMenuTree = ref<any[]>([]);
const checkedMenuKeys = ref<string[]>([]);
const halfCheckedMenuKeys = ref<string[]>([]);
const expandedMenuKeys = ref<string[]>([]);
const originalMenuIds = ref<string[]>([]);

function translateMenuTree(nodes: any[]): any[] {
  return nodes.map((node) => {
    const translated: any = {
      ...node,
      key: node.id,
      title:
        node.meta?.title && typeof node.meta.title === 'string'
          ? $t(node.meta.title as any)
          : node.name,
    };
    if (Array.isArray(node.children) && node.children.length > 0) {
      translated.children = translateMenuTree(node.children);
    }
    return translated;
  });
}

const labeledMenuTree = computed(() => translateMenuTree(rawMenuTree.value));

function getAllKeys(nodes: any[]): string[] {
  const keys: string[] = [];
  function traverse(list: any[]) {
    for (const node of list) {
      keys.push(node.id);
      if (node.children?.length) {
        traverse(node.children);
      }
    }
  }
  traverse(nodes);
  return keys;
}

function getLeafKeys(nodes: any[]): string[] {
  const leaves: string[] = [];
  function traverse(list: any[]) {
    for (const node of list) {
      if (node.children && node.children.length > 0) {
        traverse(node.children);
      } else {
        leaves.push(node.id);
      }
    }
  }
  traverse(nodes);
  return leaves;
}

async function ensureMenuTree() {
  if (rawMenuTree.value.length === 0) {
    try {
      rawMenuTree.value = await getPermissionMenuTree();
      expandedMenuKeys.value = getAllKeys(rawMenuTree.value);
    } catch (error) {
      console.error('加载菜单树失败:', error);
    }
  }
}

async function loadRoleMenus(roleId: string) {
  if (!roleId) return;
  menuLoading.value = true;
  try {
    await ensureMenuTree();
    const assignedIds = await getRoleMenus(roleId);
    originalMenuIds.value = assignedIds || [];

    // 对于 Ant Design a-tree，仅把叶子节点传给 checkedKeys，父节点会自动变为半选或全选
    const leafKeys = getLeafKeys(rawMenuTree.value);
    checkedMenuKeys.value = originalMenuIds.value.filter((id) =>
      leafKeys.includes(id),
    );
    halfCheckedMenuKeys.value = [];
  } catch (error) {
    console.error('加载角色菜单失败:', error);
  } finally {
    menuLoading.value = false;
  }
}

function onMenuCheck(checked: any, info: any) {
  checkedMenuKeys.value = Array.isArray(checked) ? checked : checked.checked;
  halfCheckedMenuKeys.value = info.halfCheckedKeys || [];
}

async function saveRoleMenus() {
  if (!activeRole.value) return;
  savingMenus.value = true;
  try {
    const allIds = Array.from(
      new Set([...checkedMenuKeys.value, ...halfCheckedMenuKeys.value]),
    );
    await assignRoleMenus(activeRole.value.id, { menuIds: allIds });
    message.success('保存菜单授权成功');
    originalMenuIds.value = allIds;
  } catch (error) {
    console.error('保存菜单授权失败:', error);
  } finally {
    savingMenus.value = false;
  }
}

function resetRoleMenus() {
  const leafKeys = getLeafKeys(rawMenuTree.value);
  checkedMenuKeys.value = originalMenuIds.value.filter((id) =>
    leafKeys.includes(id),
  );
  halfCheckedMenuKeys.value = [];
}

function expandAllMenus() {
  expandedMenuKeys.value = getAllKeys(rawMenuTree.value);
}

function collapseAllMenus() {
  expandedMenuKeys.value = [];
}

function selectAllMenus() {
  checkedMenuKeys.value = getLeafKeys(rawMenuTree.value);
  halfCheckedMenuKeys.value = [];
}

function deselectAllMenus() {
  checkedMenuKeys.value = [];
  halfCheckedMenuKeys.value = [];
}

// ----------------------------------------------------------------------
// Tab 2: 角色用户
// ----------------------------------------------------------------------
const roleUsersLoading = ref(false);
const roleUsersList = ref<any[]>([]);
const roleUsersTotal = ref(0);
const roleUsersPage = ref(1);
const roleUsersPageSize = ref(10);
const roleUsersKeyword = ref('');
const selectedUserIds = ref<string[]>([]);

async function loadRoleUsers(roleId?: string) {
  const targetId = roleId || activeRole.value?.id;
  if (!targetId) return;
  roleUsersLoading.value = true;
  try {
    const res = await getRoleUsers(targetId, {
      page: roleUsersPage.value,
      pageSize: roleUsersPageSize.value,
      keyword: roleUsersKeyword.value.trim() || undefined,
    });
    roleUsersList.value = res.items || [];
    roleUsersTotal.value = res.total || 0;
  } catch (error) {
    console.error('加载角色用户失败:', error);
  } finally {
    roleUsersLoading.value = false;
  }
}

function onSearchRoleUsers() {
  roleUsersPage.value = 1;
  loadRoleUsers();
}

function onRoleUsersPageChange(newPage: number, newPageSize: number) {
  roleUsersPage.value = newPage;
  roleUsersPageSize.value = newPageSize;
  loadRoleUsers();
}

const userRowSelection = computed(() => ({
  selectedRowKeys: selectedUserIds.value,
  onChange: (keys: (string | number)[]) => {
    selectedUserIds.value = keys.map(String);
  },
}));

async function onRemoveSingleUser(userId: string, username: string) {
  if (!activeRole.value) return;
  try {
    const removedCount = await removeRoleUsers(activeRole.value.id, [userId]);
    if (removedCount > 0) {
      message.success(`已将用户【${username}】从当前角色中移除`);
    } else {
      message.info('该用户已不在当前角色中');
    }
    await loadRoleUsers();
    await loadRoles(activeRole.value.id);
  } catch (error) {
    console.error('移除用户失败:', error);
  }
}

async function onBatchRemoveUsers() {
  if (!activeRole.value || selectedUserIds.value.length === 0) return;
  try {
    const removedCount = await removeRoleUsers(activeRole.value.id, selectedUserIds.value);
    if (removedCount > 0) {
      message.success(`已成功移除 ${removedCount} 位用户`);
    } else {
      message.info('所选用户已不在当前角色中');
    }
    selectedUserIds.value = [];
    await loadRoleUsers();
    await loadRoles(activeRole.value.id);
  } catch (error) {
    console.error('批量移除失败:', error);
  }
}

const userColumns = [
  {
    title: '用户名',
    dataIndex: 'username',
    key: 'username',
    width: 140,
  },
  {
    title: '姓名',
    dataIndex: 'realName',
    key: 'realName',
    width: 120,
  },
  {
    title: '所属部门',
    dataIndex: 'deptName',
    key: 'deptName',
    width: 140,
  },
  {
    title: '状态',
    dataIndex: 'status',
    key: 'status',
    width: 90,
  },
  {
    title: '操作',
    key: 'operation',
    width: 100,
    align: 'center' as const,
  },
];

// ----------------------------------------------------------------------
// 角色加载与操作
// ----------------------------------------------------------------------
async function loadRoles(preferActiveId?: string) {
  rolesLoading.value = true;
  try {
    const res = await getRoleList({ page: 1, pageSize: 1000 });
    roleList.value = res.items || [];
    if (roleList.value.length > 0) {
      if (
        preferActiveId &&
        roleList.value.some((r) => r.id === preferActiveId)
      ) {
        activeRoleId.value = preferActiveId;
      } else if (
        !activeRoleId.value ||
        !roleList.value.some((r) => r.id === activeRoleId.value)
      ) {
        activeRoleId.value = roleList.value[0]?.id || '';
      }
    } else {
      activeRoleId.value = '';
    }
  } catch (error) {
    console.error('加载角色列表失败:', error);
  } finally {
    rolesLoading.value = false;
  }
}

function selectRole(role: SystemRoleApi.SystemRole) {
  if (activeRoleId.value === role.id) return;
  activeRoleId.value = role.id;
  // activeRoleId 变更后会由 watch 自动触发 loadRoleMenus 和 loadRoleUsers，避免重复请求 (修复 P1-2)
}

function onCreateRole() {
  formDrawerApi.setData({}).open();
}

function onEditRole(role?: SystemRoleApi.SystemRole) {
  if (!role) return;
  formDrawerApi.setData(role).open();
}

function onDeleteRole(role?: SystemRoleApi.SystemRole) {
  if (!role) return;
  if (role.code === 'super') {
    message.warning('超级管理员角色不可删除');
    return;
  }
  deleteRole(role.id)
    .then(() => {
      message.success(`删除角色【${role.name}】成功`);
      loadRoles();
    })
    .catch(() => {});
}


function onFormSuccess(savedId?: string) {
  loadRoles(savedId);
}

async function onExport() {
  try {
    await exportRoleList();
    message.success('导出成功');
  } catch {
    message.error('导出失败');
  }
}

// ----------------------------------------------------------------------
// 监听选中角色变化：单一数据流驱动子视图刷新，彻底解决重复请求 (修复 P1-2)
// ----------------------------------------------------------------------
watch(
  () => activeRoleId.value,
  (newId) => {
    if (newId) {
      selectedUserIds.value = [];
      roleUsersPage.value = 1;
      loadRoleMenus(newId);
      loadRoleUsers(newId);
    }
  },
  { immediate: false },
);

onMounted(async () => {
  await loadRoles();
  // loadRoles 内自动初始化 activeRoleId，由上面的 watch 驱动加载菜单和用户
});
</script>

<template>
  <Page auto-content-height>
    <!-- 编辑/新建抽屉 -->
    <FormDrawer @success="onFormSuccess" />

    <!-- 添加用户弹窗 -->
    <UserAddModal
      v-model:open="userAddModalOpen"
      :role="activeRole"
      @success="() => { loadRoleUsers(); loadRoles(activeRoleId); }"
    />

    <!-- 顶部操作条 -->
    <div class="mb-3 flex shrink-0 items-center justify-between">
      <div class="text-base font-semibold text-gray-800 dark:text-gray-100">
        角色管理
      </div>
      <Space>
        <Button v-if="hasAccessByCodes(['AC_1000001'])" @click="onExport">
          <Download class="size-4" />
          导出Excel
        </Button>
        <Button
          v-if="hasAccessByCodes(['AC_1000002'])"
          type="primary"
          @click="onCreateRole"
        >
          <Plus class="size-4" />
          新增角色
        </Button>
      </Space>
    </div>

    <!-- 左右分栏主体布局（全 flex 自适应，彻底移除脆弱的硬编码 calc 高度） -->
    <div class="flex h-[calc(100vh-180px)] min-h-[500px] gap-4">
      <!-- 左侧：角色列表面板 -->
      <div
        class="flex w-[320px] shrink-0 flex-col overflow-hidden rounded-xl border border-gray-200 bg-white p-3 shadow-xs dark:border-gray-800 dark:bg-gray-900"
      >
        <div class="mb-3 shrink-0">
          <Input
            v-model:value="roleSearchText"
            placeholder="搜索角色名称 / 编码"
            allow-clear
          >
            <template #prefix>
              <Search class="size-4 text-gray-400" />
            </template>
          </Input>
        </div>

        <div class="flex-1 min-h-0 overflow-y-auto pr-1">
          <Spin :spinning="rolesLoading">
            <div
              v-if="filteredRoles.length === 0"
              class="py-12 text-center text-sm text-gray-400"
            >
              未找到匹配角色
            </div>
            <div
              v-for="role in filteredRoles"
              :key="role.id"
              class="group mb-2 cursor-pointer rounded-lg border p-3 transition-all duration-150"
              :class="[
                activeRoleId === role.id
                  ? 'border-primary bg-primary/5 shadow-xs dark:border-primary dark:bg-primary/10'
                  : 'border-gray-100 bg-white hover:border-gray-300 hover:bg-gray-50/80 dark:border-gray-800 dark:bg-gray-900 dark:hover:border-gray-700 dark:hover:bg-gray-800/60',
              ]"
              @click="selectRole(role)"
            >
              <div class="flex items-center justify-between">
                <div class="flex items-center gap-2">
                  <span
                    class="size-2 rounded-full"
                    :class="role.status === 1 ? 'bg-emerald-500' : 'bg-gray-300 dark:bg-gray-600'"
                  />
                  <span class="font-medium text-gray-800 dark:text-gray-100">
                    {{ role.name }}
                  </span>
                </div>
                <Tag
                  :color="activeRoleId === role.id ? 'blue' : 'default'"
                  class="mr-0"
                >
                  {{ role.userCount || 0 }} 人
                </Tag>
              </div>

              <div class="mt-2 flex items-center justify-between text-xs text-gray-400">
                <span class="font-mono">{{ role.code }}</span>
                <span v-if="role.remark" class="line-clamp-1 max-w-[140px]" :title="role.remark">
                  {{ role.remark }}
                </span>
              </div>

              <div class="mt-1 flex items-center justify-between text-xs text-gray-400">
                <span class="truncate max-w-[110px]" :title="role.creatorName || role.createBy || '-'">
                  创建人：{{ role.creatorName || role.createBy || '-' }}
                </span>
                <span>{{ formatDateTime(role.createTime) }}</span>
              </div>
            </div>
          </Spin>
        </div>
      </div>

      <!-- 右侧：当前选中角色详情与操作区 -->
      <div
        class="flex flex-1 min-h-0 flex-col overflow-hidden rounded-xl border border-gray-200 bg-white p-4 shadow-xs dark:border-gray-800 dark:bg-gray-900"
      >
        <template v-if="activeRole">
          <!-- 角色信息头部 Banner -->
          <div
            class="mb-3 flex shrink-0 items-start justify-between border-b border-gray-100 pb-3 dark:border-gray-800"
          >
            <div>
              <div class="flex items-center gap-3">
                <span class="text-lg font-bold text-gray-900 dark:text-gray-100">
                  {{ activeRole.name }}
                </span>
                <Tag color="cyan">{{ activeRole.code }}</Tag>
                <Tag :color="activeRole.status === 1 ? 'success' : 'default'">
                  {{ activeRole.status === 1 ? '启用' : '停用' }}
                </Tag>
              </div>
              <div class="mt-1 flex flex-wrap items-center gap-x-4 gap-y-1 text-xs text-gray-500 dark:text-gray-400">
                <span>备注：{{ activeRole.remark || '暂无备注说明' }}</span>
                <span>创建人：{{ activeRole.creatorName || activeRole.createBy || '-' }}</span>
                <span>创建时间：{{ formatFullDateTime(activeRole.createTime) }}</span>
              </div>
            </div>

            <Space>
              <Button
                v-if="hasAccessByCodes(['AC_1000002'])"
                size="small"
                @click="onEditRole(activeRole)"
              >
                编辑角色
              </Button>
              <Popconfirm
                v-if="hasAccessByCodes(['AC_1000002']) && activeRole.code !== 'super'"
                title="确定删除该角色吗？"
                ok-text="确定"
                cancel-text="取消"
                @confirm="onDeleteRole(activeRole)"
              >
                <Button danger size="small">
                  删除角色
                </Button>
              </Popconfirm>
            </Space>
          </div>

          <!-- Tabs: 权限分配 / 角色用户 -->
          <Tabs v-model:active-key="activeTab" class="flex flex-1 min-h-0 flex-col overflow-hidden" type="line">
            <!-- Tab 1: 权限分配 -->
            <Tabs.TabPane key="permissions" tab="权限分配" class="flex flex-1 min-h-0 flex-col overflow-hidden">
              <div class="flex flex-1 min-h-0 flex-col overflow-hidden">
                <!-- 权限操作工具栏 -->
                <div class="mb-2 flex shrink-0 items-center justify-between border-b pb-2 dark:border-gray-800">
                  <Space size="small">
                    <Button size="small" @click="expandAllMenus">展开全部</Button>
                    <Button size="small" @click="collapseAllMenus">折叠全部</Button>
                    <Button size="small" @click="selectAllMenus">全选</Button>
                    <Button size="small" @click="deselectAllMenus">全不选</Button>
                  </Space>
                  <Space>
                    <Button size="small" @click="resetRoleMenus">重置</Button>
                    <Button
                      v-if="hasAccessByCodes(['AC_1000002'])"
                      type="primary"
                      size="small"
                      :loading="savingMenus"
                      @click="saveRoleMenus"
                    >
                      保存授权
                    </Button>
                  </Space>
                </div>

                <!-- 菜单权限树 -->
                <div class="flex-1 min-h-0 overflow-y-auto rounded-lg border border-gray-100 p-2 dark:border-gray-800">
                  <Spin :spinning="menuLoading">
                    <Tree
                      v-model:checked-keys="checkedMenuKeys"
                      v-model:expanded-keys="expandedMenuKeys"
                      checkable
                      :tree-data="labeledMenuTree"
                      :check-strictly="false"
                      @check="onMenuCheck"
                    />
                  </Spin>
                </div>
              </div>
            </Tabs.TabPane>

            <!-- Tab 2: 角色用户 -->
            <Tabs.TabPane
              key="users"
              :tab="`角色用户 (${activeRole.userCount || 0})`"
              class="flex flex-1 min-h-0 flex-col overflow-hidden"
            >
              <div class="flex flex-1 min-h-0 flex-col overflow-hidden">
                <!-- 用户管理工具栏 -->
                <div class="mb-3 flex shrink-0 items-center justify-between gap-3">
                  <div class="flex items-center gap-2">
                    <Input.Search
                      v-model:value="roleUsersKeyword"
                      placeholder="搜索用户名 / 姓名"
                      class="w-[240px]"
                      allow-clear
                      @search="onSearchRoleUsers"
                    />
                    <Button
                      v-if="hasAccessByCodes(['AC_1000002'])"
                      type="primary"
                      @click="userAddModalOpen = true"
                    >
                      <Plus class="size-4" />
                      添加用户
                    </Button>
                    <Popconfirm
                      v-if="hasAccessByCodes(['AC_1000002'])"
                      :disabled="selectedUserIds.length === 0"
                      :title="`确定将所选 ${selectedUserIds.length} 位用户移出该角色吗？`"
                      ok-text="确定"
                      cancel-text="取消"
                      @confirm="onBatchRemoveUsers"
                    >
                      <Button danger :disabled="selectedUserIds.length === 0">
                        批量移除 ({{ selectedUserIds.length }})
                      </Button>
                    </Popconfirm>
                  </div>
                  <Button @click="loadRoleUsers()">
                    <RotateCw class="size-4" />
                    刷新
                  </Button>
                </div>

                <!-- 角色用户表格 -->
                <div class="flex-1 min-h-0 overflow-auto rounded-lg border border-gray-100 dark:border-gray-800">
                  <Table
                    :columns="userColumns"
                    :data-source="roleUsersList"
                    row-key="id"
                    :loading="roleUsersLoading"
                    :pagination="false"
                    :row-selection="userRowSelection"
                    size="middle"
                    :scroll="{ y: 420 }"
                  >
                    <template #bodyCell="{ column, record, text }">
                      <template v-if="column.key === 'realName'">
                        {{ text || '-' }}
                      </template>
                      <template v-else-if="column.key === 'deptName'">
                        {{ text || record.deptId || '-' }}
                      </template>
                      <template v-else-if="column.key === 'status'">
                        <Tag :color="text === 1 ? 'success' : 'default'">
                          {{ text === 1 ? '启用' : '停用' }}
                        </Tag>
                      </template>
                      <template v-else-if="column.key === 'operation'">
                        <Popconfirm
                          v-if="hasAccessByCodes(['AC_1000002'])"
                          :title="`确定将【${record.username}】从该角色中移除吗？`"
                          ok-text="确定"
                          cancel-text="取消"
                          @confirm="onRemoveSingleUser(record.id, record.username)"
                        >
                          <Button type="link" danger size="small">
                            移除
                          </Button>
                        </Popconfirm>
                      </template>
                    </template>
                  </Table>
                </div>

                <!-- 分页 -->
                <div class="mt-3 flex shrink-0 justify-end">
                  <Pagination
                    v-model:current="roleUsersPage"
                    v-model:page-size="roleUsersPageSize"
                    :total="roleUsersTotal"
                    show-size-changer
                    size="small"
                    :show-total="(t) => `共 ${t} 位成员`"
                    @change="onRoleUsersPageChange"
                  />
                </div>
              </div>
            </Tabs.TabPane>
          </Tabs>
        </template>

        <div v-else class="flex flex-1 items-center justify-center">
          <Empty description="请在左侧选择或创建一个角色" />
        </div>
      </div>
    </div>
  </Page>
</template>
