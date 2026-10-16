<script lang="ts" setup>
import type {
  OnActionClickParams,
  VxeTableGridOptions,
} from '#/adapter/vxe-table';
import type { SystemPositionApi } from '#/api/system/position';

import { Page, useVbenDrawer } from '@vben/common-ui';
import { Plus } from '@vben/icons';

import { Button, Switch, Tag, message } from 'ant-design-vue';

import { useVbenVxeGrid } from '#/adapter/vxe-table';
import {
  changePositionStatus,
  deletePosition,
  getPositionList,
} from '#/api/system/position';

import { usePositionColumns, usePositionGridFormSchema } from './data';
import Form from './modules/form.vue';

const [FormDrawer, formDrawerApi] = useVbenDrawer({
  connectedComponent: Form,
  destroyOnClose: true,
});

const [Grid, gridApi] = useVbenVxeGrid({
  formOptions: {
    schema: usePositionGridFormSchema(),
    submitOnChange: true,
  },
  gridOptions: {
    columns: usePositionColumns(onActionClick),
    height: 'auto',
    keepSource: true,
    proxyConfig: {
      ajax: {
        query: async (_params, formValues) => {
          const params: Record<string, any> = {};
          if (formValues?.keyword?.trim()) {
            params.keyword = formValues.keyword.trim();
          }
          const res = await getPositionList(params);
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
  } as VxeTableGridOptions<SystemPositionApi.SystemPosition>,
});

function onActionClick(e: OnActionClickParams<SystemPositionApi.SystemPosition>) {
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

function onEdit(row: SystemPositionApi.SystemPosition) {
  formDrawerApi.setData(row).open();
}

function onDelete(row: SystemPositionApi.SystemPosition) {
  deletePosition(row.id)
    .then(() => {
      message.success(`删除职务 ${row.name} 成功`);
      onRefresh();
    })
    .catch(() => {});
}

async function onStatusChange(row: SystemPositionApi.SystemPosition, checked: boolean) {
  const newStatus = checked ? 1 : 0;
  try {
    await changePositionStatus(row.id, newStatus);
    row.status = newStatus;
    message.success('修改状态成功');
  } catch {
    // 失败重置
  }
}

function onRefresh() {
  gridApi.query();
}

function onCreate() {
  formDrawerApi.setData({}).open();
}

function getWeightColor(weight: number) {
  if (weight >= 80) return 'purple';
  if (weight >= 60) return 'blue';
  if (weight >= 40) return 'cyan';
  return 'default';
}
</script>
<template>
  <Page auto-content-height>
    <FormDrawer @success="onRefresh" />
    <Grid table-title="职务管理">
      <template #toolbar-tools>
        <Button type="primary" @click="onCreate">
          <Plus class="size-5" />
          新增职务
        </Button>
      </template>

      <template #code="{ row }">
        <Tag>{{ row.code }}</Tag>
      </template>

      <template #levelWeight="{ row }">
        <Tag :color="getWeightColor(row.levelWeight)">
          权重: {{ row.levelWeight }}
        </Tag>
      </template>

      <template #status="{ row }">
        <Switch
          :checked="row.status === 1"
          checked-children="启用"
          un-checked-children="停用"
          @change="(val) => onStatusChange(row, Boolean(val))"
        />
      </template>
    </Grid>
  </Page>
</template>
