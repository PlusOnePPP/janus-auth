<script lang="ts" setup>
import type { SystemDeptApi } from '#/api/system/dept';

import { computed, nextTick, ref } from 'vue';

import { useVbenDrawer, useVbenForm } from '@vben/common-ui';

import { createDept, updateDept } from '#/api/system/dept';

import { useDeptFormSchema } from '../data';

const emits = defineEmits(['success']);
const formData = ref<SystemDeptApi.SystemDept>();
const id = ref<string>();

const [Form, formApi] = useVbenForm({
  schema: useDeptFormSchema(),
  showDefaultActions: false,
  commonConfig: { componentProps: { class: 'w-full' } },
});

const [Drawer, drawerApi] = useVbenDrawer({
  async onConfirm() {
    const { valid } = await formApi.validate();
    if (!valid) return;
    const values = await formApi.getValues();
    if (values.pid == null || values.pid === '') {
      values.pid = '0';
    }
    delete values.id;
    if (!id.value && values.status == null) {
      values.status = 1;
    }
    drawerApi.lock();
    (id.value ? updateDept(id.value, values) : createDept(values))
      .then(() => {
        emits('success');
        drawerApi.close();
      })
      .catch(() => drawerApi.unlock());
  },
  async onOpenChange(isOpen) {
    if (isOpen) {
      const data = drawerApi.getData<SystemDeptApi.SystemDept>();
      formApi.resetForm();
      if (data?.id) {
        formData.value = data;
        id.value = data.id;
      } else {
        formData.value = undefined;
        id.value = undefined;
      }
      await nextTick();
      if (data) {
        const initialValues: Record<string, any> = { ...data };
        if (initialValues.pid === '0') {
          delete initialValues.pid;
        }
        formApi.setValues(initialValues);
      }
    }
  },
});

const title = computed(() => (id.value ? '编辑部门' : '新增部门'));
</script>
<template>
  <Drawer :title="title">
    <Form />
  </Drawer>
</template>
