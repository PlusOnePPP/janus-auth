<script lang="ts" setup>
import type { SystemRoleApi } from '#/api/system/role';

import { computed, nextTick, ref } from 'vue';

import { useVbenDrawer, useVbenForm } from '@vben/common-ui';

import { createRole, updateRole } from '#/api/system/role';
import { useRoleFormSchema } from '../data';

const emits = defineEmits<{
  success: [id?: string];
}>();

const id = ref<string>();

const [Form, formApi] = useVbenForm({
  schema: useRoleFormSchema(),
  showDefaultActions: false,
  commonConfig: { componentProps: { class: 'w-full' } },
});

const [Drawer, drawerApi] = useVbenDrawer({
  async onConfirm() {
    const { valid } = await formApi.validate();
    if (!valid) return;
    const values = await formApi.getValues();
    delete values.id;
    drawerApi.lock();
    try {
      let savedId = id.value;
      if (id.value) {
        await updateRole(id.value, values);
      } else {
        savedId = (await createRole(values)) as string;
      }
      emits('success', savedId);
      drawerApi.close();
    } catch {
      drawerApi.unlock();
    }
  },
  async onOpenChange(isOpen) {
    if (isOpen) {
      const data = drawerApi.getData<SystemRoleApi.SystemRole>();
      formApi.resetForm();
      const isSuper = data?.code === 'super';
      formApi.updateSchema([
        {
          fieldName: 'code',
          disabled: isSuper,
        },
        {
          fieldName: 'status',
          disabled: isSuper,
        },
      ]);
      if (data?.id) {
        id.value = data.id;
      } else {
        id.value = undefined;
      }
      await nextTick();
      if (data?.id) {
        formApi.setValues(data);
      }
    }
  },
});

const title = computed(() => (id.value ? '编辑角色' : '新增角色'));
</script>

<template>
  <Drawer :title="title">
    <Form />
  </Drawer>
</template>
