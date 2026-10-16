<script lang="ts" setup>
import type { SystemRoleApi } from '#/api/system/role';
import type { SystemUserApi } from '#/api/system/user';

import { computed, ref, watch } from 'vue';

import { Tree } from '@vben/common-ui';
import {
  Button,
  Input,
  message,
  Modal,
  Pagination,
  Table,
  Tag,
} from 'ant-design-vue';

import { getDeptList } from '#/api/system/dept';
import { addRoleUsers } from '#/api/system/role';
import { getUserList } from '#/api/system/user';

const props = defineProps<{
  open: boolean;
  role?: SystemRoleApi.SystemRole;
}>();

const emit = defineEmits<{
  'success': [];
  'update:open': [value: boolean];
}>();

const loading = ref(false);
const submitting = ref(false);
const userList = ref<SystemUserApi.SystemUser[]>([]);
const total = ref(0);
const page = ref(1);
const pageSize = ref(10);
const keyword = ref('');
const selectedDeptId = ref<string>('');
const deptList = ref<any[]>([]);

// 勾选的用户 IDs
const selectedRowKeys = ref<string[]>([]);

// 构建部门树
function buildDeptTree(list: any[]) {
  const map = new Map<string, any>();
  const roots: any[] = [];
  list.forEach((d) => map.set(d.id, { ...d, children: [] }));
  list.forEach((d) => {
    const node = map.get(d.id);
    if (d.pid && d.pid !== '0' && map.has(d.pid)) {
      map.get(d.pid).children.push(node);
    } else {
      roots.push(node);
    }
  });
  return roots;
}

const deptTree = computed(() => buildDeptTree(deptList.value));

function getDeptName(deptId: any): string {
  if (!deptId) return '-';
  const dept = deptList.value.find((d) => String(d.id) === String(deptId));
  return dept?.name || String(deptId);
}

async function loadDepts() {
  try {
    deptList.value = await getDeptList();
  } catch (error) {
    console.error('加载部门列表失败:', error);
  }
}

async function loadUsers() {
  if (!props.open) return;
  loading.value = true;
  try {
    const res = await getUserList({
      page: page.value,
      pageSize: pageSize.value,
      keyword: keyword.value.trim() || undefined,
      deptId: selectedDeptId.value || undefined,
    });
    userList.value = res.items || [];
    total.value = res.total || 0;
  } catch (error) {
    console.error('加载用户失败:', error);
  } finally {
    loading.value = false;
  }
}

function onSearch() {
  page.value = 1;
  loadUsers();
}

function selectDept(item: any) {
  const id = item?.value?.id ?? item?.id;
  selectedDeptId.value = id == null ? '' : String(id);
  page.value = 1;
  loadUsers();
}

function clearDept() {
  selectedDeptId.value = '';
  page.value = 1;
  loadUsers();
}

function onPageChange(newPage: number, newPageSize: number) {
  page.value = newPage;
  pageSize.value = newPageSize;
  loadUsers();
}

function isUserInCurrentRole(record: Record<string, any>): boolean {
  return Boolean(props.role?.id && Array.isArray(record?.roleIds) && record.roleIds.includes(props.role.id));
}

const rowSelection = computed(() => ({
  selectedRowKeys: selectedRowKeys.value,
  onChange: (keys: (string | number)[]) => {
    selectedRowKeys.value = keys.map(String);
  },
  getCheckboxProps: (record: Record<string, any>) => ({
    disabled: isUserInCurrentRole(record),
  }),
}));

async function handleConfirm() {
  if (!props.role?.id) return;
  if (selectedRowKeys.value.length === 0) {
    message.warning('请至少勾选一位用户');
    return;
  }
  submitting.value = true;
  try {
    const addedCount = await addRoleUsers(props.role.id, selectedRowKeys.value);
    if (addedCount > 0) {
      message.success(`成功添加 ${addedCount} 位用户到「${props.role.name}」`);
    } else {
      message.info('所选用户已全部属于当前角色');
    }
    emit('success');
    handleCancel();
  } catch (error) {
    console.error('添加用户失败:', error);
  } finally {
    submitting.value = false;
  }
}

function handleCancel() {
  emit('update:open', false);
}

watch(
  () => props.open,
  (val) => {
    if (val) {
      selectedRowKeys.value = [];
      keyword.value = '';
      selectedDeptId.value = '';
      page.value = 1;
      if (deptList.value.length === 0) {
        loadDepts();
      }
      loadUsers();
    }
  },
);

const columns = [
  {
    title: '用户名',
    dataIndex: 'username',
    key: 'username',
    width: 140,
  },
  {
    title: '真实姓名',
    dataIndex: 'realName',
    key: 'realName',
    width: 120,
  },
  {
    title: '所属部门',
    dataIndex: 'deptId',
    key: 'deptId',
    width: 130,
  },
  {
    title: '已有角色',
    dataIndex: 'roleNames',
    key: 'roleNames',
  },
];
</script>

<template>
  <Modal
    :open="open"
    :title="`添加用户到「${role?.name || ''}」`"
    width="900px"
    :confirm-loading="submitting"
    destroy-on-close
    @cancel="handleCancel"
    @ok="handleConfirm"
  >
    <div class="flex h-[520px] gap-4 pt-2">
      <!-- 左侧：部门树 -->
      <div class="flex w-[220px] shrink-0 flex-col rounded-lg border p-3">
        <div class="mb-2 text-xs font-semibold text-gray-500 dark:text-gray-400">
          按部门筛选
        </div>
        <Button
          :type="selectedDeptId === '' ? 'primary' : 'default'"
          size="small"
          class="mb-2 w-full text-left"
          @click="clearDept"
        >
          全部部门
        </Button>
        <div class="flex-1 overflow-y-auto">
          <Tree
            :tree-data="deptTree"
            label-field="name"
            value-field="id"
            children-field="children"
            :default-expanded-level="2"
            @select="selectDept"
          />
        </div>
      </div>

      <!-- 右侧：用户列表 -->
      <div class="flex flex-1 flex-col overflow-hidden">
        <div class="mb-3 flex items-center justify-between gap-2">
          <Input.Search
            v-model:value="keyword"
            placeholder="搜索用户名 / 真实姓名"
            class="max-w-[280px]"
            allow-clear
            @search="onSearch"
          />
          <span class="text-xs text-gray-500">
            已勾选 <b class="text-primary text-sm">{{ selectedRowKeys.length }}</b> 位候选人
          </span>
        </div>

        <div class="flex-1 overflow-auto">
          <Table
            :columns="columns"
            :data-source="userList"
            row-key="id"
            :loading="loading"
            :pagination="false"
            :row-selection="rowSelection"
            size="small"
            :scroll="{ y: 360 }"
          >
            <template #bodyCell="{ column, record, text }">
              <template v-if="column.key === 'realName'">
                {{ text || '-' }}
              </template>
              <template v-else-if="column.key === 'deptId'">
                {{ record.deptName || getDeptName(record.deptId) }}
              </template>
              <template v-else-if="column.key === 'roleNames' || column.key === 'roleCodes'">
                <div class="flex flex-wrap items-center gap-1">
                  <Tag v-if="isUserInCurrentRole(record)" color="orange">已在当前角色</Tag>
                  <Tag
                    v-for="(name, idx) in (record.roleNames && record.roleNames.length ? record.roleNames : record.roleCodes || [])"
                    :key="idx"
                  >
                    {{ name }}
                  </Tag>
                  <span
                    v-if="!isUserInCurrentRole(record) && !(record.roleNames?.length || record.roleCodes?.length)"
                    class="text-gray-400"
                  >
                    -
                  </span>
                </div>
              </template>
            </template>
          </Table>
        </div>

        <div class="mt-3 flex justify-end">
          <Pagination
            v-model:current="page"
            v-model:page-size="pageSize"
            :total="total"
            show-size-changer
            size="small"
            :show-total="(t) => `共 ${t} 条`"
            @change="onPageChange"
          />
        </div>
      </div>
    </div>
  </Modal>
</template>
