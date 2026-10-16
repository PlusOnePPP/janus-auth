<script lang="ts" setup>
import type { VersionInfo } from '#/api/system/version';

import { ref } from 'vue';

import { useVbenModal } from '@vben/common-ui';

/** 弹窗数据：由调用方通过 setData(versionInfo) 传入 */
const info = ref<VersionInfo>();

const [Modal, modalApi] = useVbenModal({
  footer: false,
  onOpenChange(isOpen) {
    if (isOpen) {
      info.value = modalApi.getData<VersionInfo>();
    }
  },
});

/** 正文行渲染模型：解析后的最小 Markdown 行（无需引入 markdown 渲染库） */
interface RenderLine {
  bold: boolean;
  text: string;
  type: 'bullet' | 'text';
}

/** 把 CHANGELOG 正文解析为可渲染行：`- x` 转列表项，`**x**` 整行加粗 */
function parseContent(content: string): RenderLine[] {
  return content
    .split('\n')
    .map((raw) => raw.trim())
    .filter((line) => line.length > 0)
    .map((line): RenderLine => {
      if (line.startsWith('- ')) {
        return { bold: false, text: line.slice(2), type: 'bullet' };
      }
      const bold = /^\*\*.+\*\*$/.test(line);
      return {
        bold,
        text: bold ? line.slice(2, -2) : line,
        type: 'text',
      };
    });
}
</script>

<template>
  <Modal class="w-[560px]" title="版本更新日志">
    <div v-if="info" class="max-h-[60vh] overflow-y-auto pr-1">
      <div
        v-for="(entry, index) in info.entries"
        :key="entry.version"
        :class="index > 0 ? 'border-border border-t' : ''"
        class="py-3"
      >
        <div class="flex items-center gap-2">
          <span
            class="text-primary bg-primary/10 rounded px-1.5 py-0.5 text-xs font-semibold"
          >
            v{{ entry.version }}
          </span>
          <span class="text-muted-foreground text-xs">{{ entry.date }}</span>
          <span v-if="index === 0" class="text-primary ml-auto text-xs">
            当前版本
          </span>
        </div>
        <p v-if="entry.title" class="mt-1.5 text-sm font-medium">
          {{ entry.title }}
        </p>
        <ul class="mt-1 space-y-1">
          <template v-for="(line, i) in parseContent(entry.content)" :key="i">
            <li
              v-if="line.type === 'bullet'"
              class="text-muted-foreground flex gap-2 text-sm leading-6"
            >
              <span class="mt-[9px] size-1 shrink-0 rounded-full bg-current"></span>
              <span>{{ line.text }}</span>
            </li>
            <li
              v-else
              class="text-sm leading-6"
              :class="line.bold ? 'font-semibold' : 'text-muted-foreground'"
            >
              {{ line.text }}
            </li>
          </template>
        </ul>
      </div>
      <div
        v-if="!info.entries || info.entries.length === 0"
        class="text-muted-foreground py-6 text-center text-sm"
      >
        暂无版本信息
      </div>
    </div>
  </Modal>
</template>
