<script lang="ts" setup>
import type { SystemPositionApi } from '#/api/system/position';

import { computed, nextTick, ref } from 'vue';

import { useVbenDrawer, useVbenForm } from '@vben/common-ui';

import { createPosition, updatePosition } from '#/api/system/position';

import { usePositionFormSchema } from '../data';

const emits = defineEmits(['success']);
const formData = ref<SystemPositionApi.SystemPosition>();
const id = ref<string>();

const [Form, formApi] = useVbenForm({
  schema: usePositionFormSchema(),
  showDefaultActions: false,
  commonConfig: { componentProps: { class: 'w-full' } },
});

const [Drawer, drawerApi] = useVbenDrawer({
  async onConfirm() {
    const { valid } = await formApi.validate();
    if (!valid) return;
    const values = await formApi.getValues();
    delete values.id;
    if (!id.value && values.status == null) {
      values.status = 1;
    }
    drawerApi.lock();
    (id.value ? updatePosition(id.value, values) : createPosition(values))
      .then(() => {
        emits('success');
        drawerApi.close();
      })
      .catch(() => drawerApi.unlock());
  },
  async onOpenChange(isOpen) {
    if (isOpen) {
      const data = drawerApi.getData<SystemPositionApi.SystemPosition>();
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
        formApi.setValues({ ...data });
      }
    }
  },
});

const title = computed(() => (id.value ? '编辑职务' : '新增职务'));
</script>
<template>
  <Drawer :title="title">
    <Form />
  </Drawer>
</template>
