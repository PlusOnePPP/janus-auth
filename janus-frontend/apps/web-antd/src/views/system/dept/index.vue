<script lang="ts" setup>
import type {
  OnActionClickParams,
  VxeTableGridOptions,
} from '#/adapter/vxe-table';
import type { SystemDeptApi } from '#/api/system/dept';

import { Page, useVbenDrawer } from '@vben/common-ui';
import { Plus } from '@vben/icons';

import { Button, Tag, message } from 'ant-design-vue';

import { useVbenVxeGrid } from '#/adapter/vxe-table';
import { deleteDept, getDeptList } from '#/api/system/dept';

import { useDeptColumns, useDeptGridFormSchema } from './data';
import Form from './modules/form.vue';

const [FormDrawer, formDrawerApi] = useVbenDrawer({
  connectedComponent: Form,
  destroyOnClose: true,
});

const [Grid, gridApi] = useVbenVxeGrid({
  formOptions: {
    schema: useDeptGridFormSchema(),
    submitOnChange: true,
  },
  gridOptions: {
    columns: useDeptColumns(onActionClick),
    height: 'auto',
    keepSource: true,
    proxyConfig: {
      ajax: {
        query: async (_params, formValues) => {
          if (formValues?.keyword?.trim()) {
            const res = await getDeptList({ keyword: formValues.keyword.trim() });
            res.forEach((d: any) => {
              d.id = String(d.id);
              d.pid = d.pid != null && d.pid !== '' ? String(d.pid) : '0';
            });
            return { items: res, total: res.length };
          }
          const res = await getDeptList({ pid: '0' });
          res.forEach((d: any) => {
            d.id = String(d.id);
            d.pid = d.pid != null && d.pid !== '' ? String(d.pid) : '0';
          });
          return { items: res, total: res.length };
        },
      },
    },
    rowConfig: { keyField: 'id' },
    pagerConfig: { enabled: false },
    toolbarConfig: {
      custom: true,
      export: false,
      refresh: true,
      search: true,
      zoom: true,
    },
    treeConfig: {
      parentField: 'pid',
      rowField: 'id',
      lazy: true,
      hasChild: 'hasChildren',
      trigger: 'cell',
      indent: 16,
      loadMethod: async ({ row }) => {
        const children = await getDeptList({ pid: row.id });
        children.forEach((d: any) => {
          d.id = String(d.id);
          d.pid = String(d.pid);
        });
        return children;
      },
    },
  } as VxeTableGridOptions<SystemDeptApi.SystemDept>,
});

function onActionClick(e: OnActionClickParams<SystemDeptApi.SystemDept>) {
  switch (e.code) {
    case 'append': {
      onAppend(e.row);
      break;
    }
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

function onEdit(row: SystemDeptApi.SystemDept) {
  formDrawerApi.setData(row).open();
}

function onAppend(row: SystemDeptApi.SystemDept) {
  formDrawerApi.setData({ pid: row.id }).open();
}

function onDelete(row: SystemDeptApi.SystemDept) {
  // 删除确认已由操作列 CellOperation 的 Popconfirm 完成，此处直接删除，避免双重确认
  deleteDept(row.id)
    .then(() => {
      message.success(`删除 ${row.name} 成功`);
      onRefresh();
    })
    .catch(() => {});
}

function onRefresh() {
  gridApi.query();
}

function onCreate() {
  formDrawerApi.setData({}).open();
}
</script>
<template>
  <Page auto-content-height>
    <FormDrawer @success="onRefresh" />
    <Grid table-title="部门管理">
      <template #toolbar-tools>
        <Button @click="gridApi.grid?.setAllTreeExpand(true)">
          展开全部
        </Button>
        <Button @click="gridApi.grid?.setAllTreeExpand(false)">
          折叠全部
        </Button>
        <Button type="primary" @click="onCreate">
          <Plus class="size-5" />
          新增部门
        </Button>
      </template>

      <template #leader="{ row }">
        <span v-if="row.leaderName" class="inline-flex items-center gap-1.5">
          <span class="font-medium text-foreground">{{ row.leaderName }}</span>
          <Tag color="blue">{{ row.leaderPositionName || '负责人' }}</Tag>
        </span>
        <span v-else class="text-muted-foreground text-xs">暂无负责人</span>
      </template>
    </Grid>
  </Page>
</template>

<style scoped>
:deep(.vxe-body--column.col--tree-node) {
  cursor: pointer;
  user-select: none;
}

:deep(.vxe-body--column.col--tree-node .vxe-tree-cell) {
  padding-left: 1.35em;
}

:deep(.vxe-body--column.col--tree-node:hover .vxe-tree-cell) {
  color: hsl(var(--primary));
  transition: color 0.15s ease;
}
</style>
