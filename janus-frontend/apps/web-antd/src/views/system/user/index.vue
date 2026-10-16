<script lang="ts" setup>
import type { Recordable } from '@vben/types';

import type {
  OnActionClickParams,
  VxeTableGridOptions,
} from '#/adapter/vxe-table';
import type { SystemUserApi } from '#/api/system/user';

import { computed, onMounted, ref, useTemplateRef, watch } from 'vue';

import { useAccess } from '@vben/access';
import { Page, useVbenDrawer } from '@vben/common-ui';
import { Download, Plus, Search } from '@vben/icons';

import { useElementSize } from '@vueuse/core';
import { Button, Empty, Input, message, Modal, Spin, Tag, Tooltip, Tree } from 'ant-design-vue';

import { useVbenVxeGrid } from '#/adapter/vxe-table';
import { getDeptList, ROOT_DEPT_ID } from '#/api/system/dept';
import { deleteUser, exportUserList, getUserList, updateUser } from '#/api/system/user';

import { useUserColumns, useUserGridFormSchema } from './data';
import Form from './modules/form.vue';

const { hasAccessByCodes } = useAccess();

const selectedDeptId = ref<string>('');
const selectedDeptName = ref<string>('');
const selectedKeys = ref<string[]>([]);
const expandedKeys = ref<string[]>([]);
const treeData = ref<any[]>([]);
const treeLoading = ref(false);
const deptSearchText = ref('');
const isSearching = ref(false);
const deptMap = new Map<string, string>();

const treeContainerRef = useTemplateRef<HTMLDivElement>('treeContainerRef');
const { height: treeContainerHeight } = useElementSize(treeContainerRef);
const virtualHeight = computed(() => {
  const h = Math.floor(treeContainerHeight.value);
  return h > 100 ? h : 400;
});

const tableTitle = computed(() => {
  if (selectedDeptName.value) {
    return `用户管理（${selectedDeptName.value}）`;
  }
  return '用户管理';
});

async function loadRootDepts() {
  treeLoading.value = true;
  try {
    const roots = await getDeptList({ pid: '0' });
    roots.forEach((r: any) => deptMap.set(String(r.id), r.name));
    const firstRoot = roots[0];
    if (roots.length === 1 && firstRoot && firstRoot.hasChildren) {
      const children = await getDeptList({ pid: firstRoot.id });
      children.forEach((c: any) => deptMap.set(String(c.id), c.name));
      firstRoot.children = children.map((c: any) => ({
        id: c.id,
        name: c.name,
        isLeaf: !c.hasChildren,
        children: c.hasChildren ? [] : undefined,
      }));
      expandedKeys.value = [firstRoot.id];
    }
    treeData.value = roots.map((d: any) => ({
      id: d.id,
      name: d.name,
      isLeaf: !d.hasChildren,
      children: d.children || (d.hasChildren ? [] : undefined),
    }));
  } catch (error) {
    console.error('加载部门树失败:', error);
  } finally {
    treeLoading.value = false;
  }
}

function onLoadData(treeNode: any) {
  return new Promise<void>((resolve) => {
    const raw = treeNode.dataRef || treeNode;
    if (raw.children && raw.children.length > 0) {
      resolve();
      return;
    }
    getDeptList({ pid: raw.id })
      .then((children: any[]) => {
        if (children && children.length > 0) {
          children.forEach((c: any) => deptMap.set(String(c.id), c.name));
          raw.children = children.map((c) => ({
            id: c.id,
            name: c.name,
            isLeaf: !c.hasChildren,
            children: c.hasChildren ? [] : undefined,
          }));
        } else {
          raw.isLeaf = true;
        }
        treeData.value = [...treeData.value];
        resolve();
      })
      .catch(() => {
        resolve();
      });
  });
}

let searchTimer: null | ReturnType<typeof setTimeout> = null;
watch(deptSearchText, (val) => {
  if (searchTimer) clearTimeout(searchTimer);
  const q = val.trim();
  if (!q) {
    isSearching.value = false;
    loadRootDepts();
    return;
  }
  searchTimer = setTimeout(async () => {
    treeLoading.value = true;
    isSearching.value = true;
    try {
      const results = await getDeptList({ keyword: q });
      results.forEach((d: any) => deptMap.set(String(d.id), d.name));
      treeData.value = results.map((d: any) => ({
        id: d.id,
        name: d.name,
        isLeaf: true,
      }));
    } finally {
      treeLoading.value = false;
    }
  }, 300);
});

function onTreeSelect(keys: any[], info: any) {
  const id = keys[0] ? String(keys[0]) : '';
  selectedDeptId.value = id;
  selectedKeys.value = id ? [id] : [];
  selectedDeptName.value = info?.node?.name || deptMap.get(id) || '';
  gridApi.query();
}

function clearDept() {
  selectedDeptId.value = '';
  selectedKeys.value = [];
  selectedDeptName.value = '';
  gridApi.query();
}

onMounted(() => {
  loadRootDepts();
});

const [FormDrawer, formDrawerApi] = useVbenDrawer({
  connectedComponent: Form,
  destroyOnClose: true,
});

const [Grid, gridApi] = useVbenVxeGrid({
  formOptions: {
    schema: useUserGridFormSchema(),
    submitOnChange: true,
  },
  gridOptions: {
    columns: useUserColumns(onActionClick, onStatusChange),
    height: 'auto',
    keepSource: true,
    proxyConfig: {
      ajax: {
        query: async ({ page }, formValues) => {
          const params: Recordable<any> = {
            page: page.currentPage,
            pageSize: page.pageSize,
            ...formValues,
          };
          if (selectedDeptId.value) {
            params.deptId = selectedDeptId.value;
          }
          return await getUserList(params);
        },
      },
    },
    rowConfig: { keyField: 'id' },
    toolbarConfig: {
      custom: true,
      export: false,
      refresh: true,
      search: true,
      zoom: true,
    },
  } as VxeTableGridOptions<SystemUserApi.SystemUser>,
});

function onActionClick(e: OnActionClickParams<SystemUserApi.SystemUser>) {
  switch (e.code) {
    case 'delete': {
      onDelete(e.row);
      break;
    }
    case 'edit': {
      onEdit(e.row);
      break;
    }
  }
}

function confirm(content: string, title: string) {
  return new Promise<boolean>((resolve, reject) => {
    Modal.confirm({
      title,
      content,
      okText: '确定',
      cancelText: '取消',
      onOk: () => resolve(true),
      onCancel: () => reject(new Error('已取消')),
    });
  });
}

async function onStatusChange(newStatus: number, row: SystemUserApi.SystemUser) {
  const statusText = newStatus === 1 ? '启用' : '禁用';
  try {
    await confirm(
      `确定将【${row.username}】的状态切换为【${statusText}】吗？`,
      '切换状态',
    );
    await updateUser(row.id, { status: newStatus });
    return true;
  } catch {
    return false;
  }
}

function onEdit(row: SystemUserApi.SystemUser) {
  formDrawerApi.setData(row).open();
}

function onDelete(row: SystemUserApi.SystemUser) {
  // 删除确认已由操作列 CellOperation 的 Popconfirm 完成，此处直接删除，避免双重确认
  deleteUser(row.id)
    .then(() => {
      message.success(`删除 ${row.username} 成功`);
      onRefresh();
    })
    .catch(() => {});
}

function onRefresh() {
  gridApi.query();
}

function onCreate() {
  formDrawerApi
    .setData({
      deptId: selectedDeptId.value || ROOT_DEPT_ID,
    })
    .open();
}

async function onExport() {
  try {
    const params: Recordable<any> = {};
    if (selectedDeptId.value) {
      params.deptId = selectedDeptId.value;
    }
    await exportUserList(params);
    message.success('导出成功');
  } catch {
    message.error('导出失败');
  }
}
</script>
<template>
  <Page auto-content-height>
    <FormDrawer @success="onRefresh" />
    <div class="flex size-full gap-4 overflow-hidden">
      <!-- 左侧：部门机构树面板 -->
      <div
        class="flex w-[260px] shrink-0 flex-col h-full overflow-hidden rounded-md border border-border bg-card p-3 shadow-xs"
      >
        <div class="mb-3 flex shrink-0 items-center justify-between border-b border-border pb-2.5">
          <span class="font-semibold text-foreground">部门列表</span>
          <span
            v-if="selectedDeptId"
            class="cursor-pointer text-xs text-primary hover:underline"
            @click="clearDept"
          >
            重置筛选
          </span>
        </div>

        <div class="mb-2 shrink-0">
          <Input
            v-model:value="deptSearchText"
            placeholder="搜索部门"
            allow-clear
            size="small"
          >
            <template #prefix>
              <Search class="size-3.5 text-gray-400" />
            </template>
          </Input>
        </div>

        <div class="mb-2 shrink-0">
          <Button
            :type="!selectedDeptId ? 'primary' : 'default'"
            size="small"
            class="w-full text-left"
            @click="clearDept"
          >
            全部部门
          </Button>
        </div>

        <!-- 树容器限制高度，滚动条限制在容器内，开启虚拟滚动与懒加载 -->
        <div ref="treeContainerRef" class="flex-1 min-h-0 overflow-hidden pr-1">
          <Spin :spinning="treeLoading">
            <Empty
              v-if="treeData.length === 0"
              class="py-8"
              description="暂无匹配部门"
            />
            <Tree
              v-else
              :tree-data="treeData"
              :load-data="isSearching ? undefined : onLoadData"
              :virtual="true"
              :height="virtualHeight"
              :field-names="{ title: 'name', key: 'id', children: 'children' }"
              :selected-keys="selectedKeys"
              :expanded-keys="expandedKeys"
              block-node
              @select="onTreeSelect"
              @expand="(keys: any[]) => (expandedKeys = keys)"
            />
          </Spin>
        </div>
      </div>

      <!-- 右侧：用户列表表格 -->
      <div class="flex-1 min-w-0 h-full overflow-hidden">
        <Grid :table-title="tableTitle">
          <template #toolbar-tools>
            <Button v-if="hasAccessByCodes(['AC_100010'])" type="primary" @click="onCreate">
              <Plus class="size-5" />
              新增用户
            </Button>
            <Button v-if="hasAccessByCodes(['AC_1000000'])" @click="onExport">
              <Download class="size-5" />
              导出Excel
            </Button>
          </template>

          <template #deptPositions="{ row }">
            <div class="flex flex-wrap items-center gap-1.5 py-1">
              <!-- 主部门与职务 -->
              <span v-if="row.deptName || row.positionName" class="inline-flex items-center gap-1">
                <span class="font-medium text-foreground">{{ row.deptName || '未分配部门' }}</span>
                <Tag color="blue">{{ row.positionName || '未设职务' }}</Tag>
              </span>
              <span v-else class="text-muted-foreground text-xs">-</span>

              <!-- 兼职部门 (+N 气泡展示) -->
              <template v-if="row.deptPositions && row.deptPositions.filter((d: any) => !d.isMain).length > 0">
                <Tooltip>
                  <template #title>
                    <div class="space-y-1 py-0.5 text-xs">
                      <div class="font-semibold text-gray-200">兼职部门及职务：</div>
                      <div
                        v-for="sub in row.deptPositions.filter((d: any) => !d.isMain)"
                        :key="sub.id || sub.deptId"
                        class="flex items-center gap-2"
                      >
                        <span>• {{ sub.deptName }}</span>
                        <span class="text-blue-300">[{{ sub.positionName || '成员' }}]</span>
                        <span v-if="sub.isLeader" class="text-amber-300 text-[10px]">(负责人)</span>
                      </div>
                    </div>
                  </template>
                  <Tag color="cyan" class="cursor-pointer">
                    +{{ row.deptPositions.filter((d: any) => !d.isMain).length }} 兼职
                  </Tag>
                </Tooltip>
              </template>
            </div>
          </template>
        </Grid>
      </div>
    </div>
  </Page>
</template>
