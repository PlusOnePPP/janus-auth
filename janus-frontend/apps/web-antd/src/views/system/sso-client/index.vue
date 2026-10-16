<script lang="ts" setup>
import type { SsoClientApi } from '#/api/system/sso-client';

import { h, reactive, ref } from 'vue';

import { Page } from '@vben/common-ui';

import {
  Button,
  Input,
  message,
  Select,
  Space,
  Table,
  Tag,
  Tooltip,
} from 'ant-design-vue';

import { getSsoClientList, getSsoClientSecretKey } from '#/api/system/sso-client';

defineOptions({ name: 'SsoClient' });

const loading = ref(false);
const allRows = ref<SsoClientApi.Client[]>([]);
const currentPage = ref(1);
const pageSize = ref(10);
const searchForm = reactive({
  keyword: '',
  status: undefined as string | undefined,
});

// 密钥明文查看状态（仅当前会话内存中，刷新即失；明文不落 localStorage）
const revealedKeys = reactive(new Map<string, string>());
const revealingIds = reactive(new Set<string>());

const statusMeta: Record<string, { color: string; text: string }> = {
  AUTO_APPROVED: { color: 'green', text: '已注册' },
  PENDING: { color: 'orange', text: '待审核' },
  REJECTED: { color: 'red', text: '已拒绝' },
};

/** 前端过滤：应用名/标识关键字 + 状态（客户端数量小，无需后端分页） */
const filteredRows = ref<SsoClientApi.Client[]>([]);

function applyFilter() {
  const kw = searchForm.keyword.trim().toLowerCase();
  filteredRows.value = allRows.value.filter((row) => {
    const hitKw =
      !kw ||
      row.client.toLowerCase().includes(kw) ||
      (row.appName || '').toLowerCase().includes(kw);
    const hitStatus = !searchForm.status || row.status === searchForm.status;
    return hitKw && hitStatus;
  });
}

const pagedRows = ref<SsoClientApi.Client[]>([]);

function applyPage() {
  const start = (currentPage.value - 1) * pageSize.value;
  pagedRows.value = filteredRows.value.slice(start, start + pageSize.value);
}

function handleFilterChange() {
  currentPage.value = 1;
  applyFilter();
  applyPage();
}

function handlePageChange(page: number, size: number) {
  currentPage.value = page;
  pageSize.value = size;
  applyPage();
}

const columns = [
  { title: '应用名', dataIndex: 'appName', width: 150, ellipsis: true },
  { title: '应用标识', dataIndex: 'client', width: 170, ellipsis: true },
  { title: '回调地址', dataIndex: 'clientUrl', width: 230, ellipsis: true },
  { title: '通知路径', dataIndex: 'notifyPath', width: 190, ellipsis: true },
  {
    title: '状态',
    dataIndex: 'status',
    width: 90,
    customRender: ({ text }: any) => {
      const meta = statusMeta[text] || { color: 'default', text: text || '-' };
      return h(Tag, { color: meta.color }, () => meta.text);
    },
  },
  {
    title: '签名密钥',
    dataIndex: 'secretKeyMasked',
    width: 260,
    customRender: ({ record }: any) => renderSecretColumn(record),
  },
  { title: '注册时间', dataIndex: 'createTime', width: 165 },
  { title: '更新时间', dataIndex: 'updateTime', width: 165 },
];

/** 密钥列：默认掩码；查看后展示明文并附复制按钮 */
function renderSecretColumn(record: SsoClientApi.Client) {
  const revealed = revealedKeys.has(record.id);
  const text = revealed ? revealedKeys.get(record.id) : record.secretKeyMasked;
  return h('span', { class: 'flex items-center gap-1' }, [
    h(
      Tooltip,
      { title: text },
      () => h('code', { class: 'font-mono text-xs' }, text),
    ),
    h(
      Button,
      {
        type: 'link',
        size: 'small',
        loading: revealingIds.has(record.id),
        onClick: () => toggleSecret(record),
      },
      () => (revealed ? '隐藏' : '查看'),
    ),
    revealed
      ? h(
          Button,
          { type: 'link', size: 'small', onClick: () => copySecret(record) },
          () => '复制',
        )
      : null,
  ]);
}

async function toggleSecret(row: SsoClientApi.Client) {
  if (revealedKeys.has(row.id)) {
    revealedKeys.delete(row.id);
    return;
  }
  revealingIds.add(row.id);
  try {
    revealedKeys.set(row.id, await getSsoClientSecretKey(row.id));
  } finally {
    revealingIds.delete(row.id);
  }
}

async function copySecret(row: SsoClientApi.Client) {
  const plain = revealedKeys.get(row.id);
  if (!plain) return;
  try {
    await copyText(plain);
    message.success('密钥已复制到剪贴板');
  } catch {
    message.error('复制失败，请手动选择复制');
  }
}

/** 剪贴板复制：https 走 Clipboard API，http 降级 execCommand */
async function copyText(text: string) {
  if (navigator.clipboard && window.isSecureContext) {
    await navigator.clipboard.writeText(text);
    return;
  }
  const ta = document.createElement('textarea');
  ta.value = text;
  ta.style.position = 'fixed';
  ta.style.opacity = '0';
  document.body.appendChild(ta);
  ta.select();
  document.execCommand('copy');
  document.body.removeChild(ta);
}

async function loadData() {
  loading.value = true;
  try {
    allRows.value = (await getSsoClientList()) || [];
    handleFilterChange();
  } finally {
    loading.value = false;
  }
}

loadData();
</script>

<template>
  <Page auto-content-height>
    <div class="overflow-hidden">
      <!-- 搜索栏 -->
      <div class="mb-4 flex flex-wrap items-center gap-2">
        <Input
          v-model:value="searchForm.keyword"
          placeholder="应用名 / 应用标识"
          style="width: 220px"
          allow-clear
          @press-enter="handleFilterChange"
        />
        <Select
          v-model:value="searchForm.status"
          placeholder="状态"
          style="width: 120px"
          allow-clear
          :options="[
            { label: '已注册', value: 'AUTO_APPROVED' },
            { label: '待审核', value: 'PENDING' },
            { label: '已拒绝', value: 'REJECTED' },
          ]"
          @change="handleFilterChange"
        />
        <Space>
          <Button type="primary" @click="handleFilterChange">搜索</Button>
          <Button
            @click="
              () => {
                searchForm.keyword = '';
                searchForm.status = undefined;
                handleFilterChange();
              }
            "
          >
            重置
          </Button>
          <Button @click="loadData">刷新</Button>
        </Space>
      </div>

      <Table
        :loading="loading"
        :data-source="pagedRows"
        :columns="columns"
        :pagination="{
          current: currentPage,
          pageSize: pageSize,
          total: filteredRows.length,
          showSizeChanger: true,
          showTotal: (t: number) => `共 ${t} 条`,
          onChange: handlePageChange,
        }"
        :scroll="{ x: 1200 }"
        row-key="id"
        size="small"
      />
    </div>
  </Page>
</template>
