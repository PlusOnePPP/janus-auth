<script lang="ts" setup>
import type { SystemPositionApi } from '#/api/system/position';
import type { SystemUserApi } from '#/api/system/user';

import { computed, nextTick, onMounted, ref } from 'vue';

import { useVbenDrawer, useVbenForm } from '@vben/common-ui';
import { Plus } from '@vben/icons';

import {
  Button,
  Card,
  Checkbox,
  message,
  Radio,
  Select,
  TreeSelect,
} from 'ant-design-vue';

import { getDeptTree, ROOT_DEPT_ID } from '#/api/system/dept';
import { getPositionList } from '#/api/system/position';
import {
  createUser,
  getUserDeptPositions,
  updateUser,
} from '#/api/system/user';

import { useUserFormSchema } from '../data';

interface DeptPositionItem {
  deptId?: string;
  positionId?: string;
  isMain: boolean;
  isLeader: boolean;
}

const emits = defineEmits(['success']);
const formData = ref<SystemUserApi.SystemUser>();
const id = ref<string>();

const deptTreeData = ref<any[]>([]);
const positionOptions = ref<SystemPositionApi.SystemPosition[]>([]);
const deptPositionList = ref<DeptPositionItem[]>([]);

const [Form, formApi] = useVbenForm({
  schema: useUserFormSchema(),
  showDefaultActions: false,
  commonConfig: { componentProps: { class: 'w-full' } },
});

onMounted(async () => {
  try {
    const [tree, positions] = await Promise.all([
      getDeptTree(),
      getPositionList(),
    ]);
    deptTreeData.value = tree;
    positionOptions.value = positions;
  } catch {
    // 容错
  }
});

function getDefaultPositionId() {
  const staff = positionOptions.value.find((p) => p.code === 'STAFF');
  return staff ? staff.id : positionOptions.value[positionOptions.value.length - 1]?.id || '';
}

function addDeptPositionRow() {
  deptPositionList.value.push({
    deptId: undefined,
    positionId: getDefaultPositionId(),
    isMain: deptPositionList.value.length === 0,
    isLeader: false,
  });
}

function removeDeptPositionRow(index: number) {
  if (deptPositionList.value.length <= 1) {
    message.warning('用户必须保留至少一个部门及职务');
    return;
  }
  const removed = deptPositionList.value.splice(index, 1)[0];
  if (removed?.isMain && deptPositionList.value.length > 0) {
    deptPositionList.value[0]!.isMain = true;
  }
}

function setMainDept(index: number) {
  deptPositionList.value.forEach((item, i) => {
    item.isMain = i === index;
  });
}

const [Drawer, drawerApi] = useVbenDrawer({
  async onConfirm() {
    const { valid } = await formApi.validate();
    if (!valid) return;

    if (deptPositionList.value.length === 0) {
      message.error('请至少添加一个部门及职务');
      return;
    }

    const deptSet = new Set<string>();
    let mainCount = 0;

    for (let i = 0; i < deptPositionList.value.length; i++) {
      const item = deptPositionList.value[i]!;
      if (!item.deptId) {
        message.error(`第 ${i + 1} 行未选择部门`);
        return;
      }
      if (!item.positionId) {
        message.error(`第 ${i + 1} 行未选择职务`);
        return;
      }
      if (deptSet.has(item.deptId)) {
        message.error('不能重复添加相同的部门');
        return;
      }
      deptSet.add(item.deptId);
      if (item.isMain) {
        mainCount++;
      }
    }

    if (mainCount !== 1) {
      message.error('必须且只能指定一个主部门');
      return;
    }

    const values = await formApi.getValues();
    delete values.roleIds;

    if (id.value && !values.password) {
      delete values.password;
    }

    values.deptPositions = deptPositionList.value.map((item) => ({
      deptId: item.deptId,
      positionId: item.positionId,
      isMain: item.isMain,
      isLeader: item.isLeader,
    }));

    drawerApi.lock();
    (id.value ? updateUser(id.value, values) : createUser(values))
      .then(() => {
        emits('success');
        drawerApi.close();
      })
      .catch(() => drawerApi.unlock());
  },
  async onOpenChange(isOpen) {
    if (isOpen) {
      const data = drawerApi.getData<SystemUserApi.SystemUser>();
      formApi.resetForm();

      if (positionOptions.value.length === 0 || deptTreeData.value.length === 0) {
        try {
          const [tree, positions] = await Promise.all([
            getDeptTree(),
            getPositionList(),
          ]);
          deptTreeData.value = tree;
          positionOptions.value = positions;
        } catch {
          // ignore
        }
      }

      if (data?.id) {
        formData.value = data;
        id.value = data.id;
        // 获取现有部门职务
        try {
          const udps = await getUserDeptPositions(data.id);
          if (udps && udps.length > 0) {
            deptPositionList.value = udps.map((u: any) => ({
              deptId: u.deptId,
              positionId: u.positionId,
              isMain: Boolean(u.isMain),
              isLeader: Boolean(u.isLeader),
            }));
          } else {
            deptPositionList.value = [
              {
                deptId: data.deptId || ROOT_DEPT_ID,
                positionId: getDefaultPositionId(),
                isMain: true,
                isLeader: false,
              },
            ];
          }
        } catch {
          deptPositionList.value = [
            {
              deptId: data.deptId || ROOT_DEPT_ID,
              positionId: getDefaultPositionId(),
              isMain: true,
              isLeader: false,
            },
          ];
        }
      } else {
        formData.value = undefined;
        id.value = undefined;
        deptPositionList.value = [
          {
            deptId: ROOT_DEPT_ID,
            positionId: getDefaultPositionId(),
            isMain: true,
            isLeader: false,
          },
        ];
      }
      await nextTick();
      if (data) {
        formApi.setValues(data);
      }
    }
  },
});

const title = computed(() => (id.value ? '编辑用户' : '新增用户'));
</script>
<template>
  <Drawer :title="title">
    <Form />

    <!-- 部门与职务设置卡片 -->
    <Card size="small" class="mt-4 border-dashed" title="部门与职务配置">
      <template #extra>
        <Button size="small" type="link" @click="addDeptPositionRow">
          <Plus class="size-3.5 mr-1 inline" />
          添加兼职部门
        </Button>
      </template>

      <div class="space-y-3">
        <div
          v-for="(item, idx) in deptPositionList"
          :key="idx"
          class="flex flex-col gap-2 p-2.5 rounded-md bg-muted/30 border border-border/50 text-xs"
        >
          <div class="flex items-center justify-between font-medium">
            <span class="text-foreground">任职 #{{ idx + 1 }}</span>
            <div class="flex items-center gap-3">
              <label class="flex items-center gap-1 cursor-pointer">
                <Radio
                  :checked="item.isMain"
                  @change="() => setMainDept(idx)"
                />
                <span :class="item.isMain ? 'text-primary font-semibold' : 'text-muted-foreground'">
                  主部门
                </span>
              </label>

              <label class="flex items-center gap-1 cursor-pointer">
                <Checkbox v-model:checked="item.isLeader" />
                <span class="text-muted-foreground">部门负责人 (老大)</span>
              </label>

              <Button
                v-if="deptPositionList.length > 1"
                danger
                size="small"
                type="link"
                @click="() => removeDeptPositionRow(idx)"
              >
                移除
              </Button>
            </div>
          </div>

          <div class="grid grid-cols-1 md:grid-cols-2 gap-2">
            <div>
              <div class="text-[11px] text-muted-foreground mb-1">所属部门:</div>
              <TreeSelect
                v-model:value="item.deptId"
                :tree-data="deptTreeData"
                :field-names="{ label: 'name', value: 'id', children: 'children' }"
                placeholder="请选择部门"
                tree-default-expand-all
                show-search
                tree-node-filter-prop="name"
                class="w-full"
              />
            </div>

            <div>
              <div class="text-[11px] text-muted-foreground mb-1">所任职务:</div>
              <Select
                v-model:value="item.positionId"
                placeholder="请选择职务"
                class="w-full"
              >
                <Select.Option
                  v-for="p in positionOptions"
                  :key="p.id"
                  :value="p.id"
                >
                  {{ p.name }} (权重: {{ p.levelWeight }})
                </Select.Option>
              </Select>
            </div>
          </div>
        </div>
      </div>
    </Card>
  </Drawer>
</template>
