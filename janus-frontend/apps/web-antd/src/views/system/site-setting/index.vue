<script lang="ts" setup>
import { onMounted, reactive, ref } from 'vue';
import { Page } from '@vben/common-ui';
import {
  Button,
  Card,
  Col,
  Divider,
  Form,
  FormItem,
  Input,
  message,
  Row,
  TabPane,
  Tabs,
  Upload,
} from 'ant-design-vue';
import {
  getAdminSiteSettingApi,
  updateAdminSiteSettingApi,
  uploadAdminLogoApi,
  type SysSiteSetting,
} from '#/api/system/site';
import { useSiteStore } from '#/store';

defineOptions({ name: 'SystemSiteSetting' });

const siteStore = useSiteStore();
const activeTab = ref('basic');
const loading = ref(false);
const saving = ref(false);
const uploadingLogo = ref(false);

const formState = reactive<SysSiteSetting>({
  systemName: '',
  systemDesc: '',
  logoUrl: '',
  logoDarkUrl: '',
  faviconUrl: '',
  loginTheme: 'default',
  copyright: '',
  extraSettings: '{}',
});

async function loadSetting() {
  loading.value = true;
  try {
    const res = await getAdminSiteSettingApi();
    if (res) {
      formState.systemName = res.systemName || '';
      formState.systemDesc = res.systemDesc || '';
      formState.logoUrl = res.logoUrl || '';
      formState.logoDarkUrl = res.logoDarkUrl || '';
      formState.faviconUrl = res.faviconUrl || '';
      formState.loginTheme = res.loginTheme || 'classic-split';
      formState.copyright = res.copyright || '';
      formState.extraSettings = res.extraSettings || '{}';
      formState.installed = res.installed;
      formState.installedTime = res.installedTime;
    }
  } catch (err: any) {
    message.error(err?.message || '获取系统配置失败');
  } finally {
    loading.value = false;
  }
}

async function handleSave() {
  if (!formState.systemName.trim()) {
    message.warning('系统名称不能为空');
    return;
  }

  // 校验 extraSettings 是否符合 JSON 格式
  if (formState.extraSettings) {
    try {
      JSON.parse(formState.extraSettings);
    } catch {
      message.error('扩展设置必须是合法的 JSON 格式字符串');
      return;
    }
  }

  // 同步保持深色 logo 一致
  formState.logoDarkUrl = formState.logoUrl;

  saving.value = true;
  try {
    await updateAdminSiteSettingApi(formState);
    message.success('系统配置保存成功！');
    // 同步刷新全局 Store
    await siteStore.fetchSiteConfig(true);
  } catch (err: any) {
    message.error(err?.message || '保存配置失败');
  } finally {
    saving.value = false;
  }
}

async function handleUploadLogo(options: any) {
  const { file, onSuccess, onError } = options;
  uploadingLogo.value = true;
  try {
    const res = await uploadAdminLogoApi(file as File);
    formState.logoUrl = res.url;
    formState.logoDarkUrl = res.url;
    message.success('Logo 上传成功');
    onSuccess(res, file);
  } catch (err: any) {
    message.error(err?.message || 'Logo 上传失败');
    onError(err);
  } finally {
    uploadingLogo.value = false;
  }
}

onMounted(() => {
  loadSetting();
});
</script>

<template>
  <Page
    description="管理与自定义全站名称、品牌 Logo、登录页视觉主题及扩展参数配置"
    title="系统配置 (站点与品牌)"
  >
    <Card :bordered="false" class="site-setting-card" :loading="loading">
      <Tabs v-model:activeKey="activeTab">
        <!-- 标签 1：基本信息 -->
        <TabPane key="basic" tab="平台基本信息">
          <div class="tab-pane-box">
            <Form layout="vertical" class="max-w-3xl">
              <FormItem label="系统名称" required extra="呈现在浏览器页面标题、系统主导航栏和登录门户顶部">
                <Input
                  v-model:value="formState.systemName"
                  placeholder="例如：统一认证中心 / 星云统一登录平台"
                  size="large"
                />
              </FormItem>

              <FormItem label="系统宣传描述 / Slogan" extra="展示在登录页引导区域与欢迎横幅">
                <Input
                  v-model:value="formState.systemDesc"
                  placeholder="例如：安全、高效的企业级统一认证与单点登录中心"
                  size="large"
                />
              </FormItem>

              <FormItem label="页脚版权所有声明" extra="展示在管理后台底部与登录页底部">
                <Input
                  v-model:value="formState.copyright"
                  placeholder="例如：© 2026 统一认证中心. All Rights Reserved."
                  size="large"
                />
              </FormItem>

              <FormItem label="浏览器 Favicon 图标 URL" extra="浏览器标签页左侧展示的小图标">
                <Input
                  v-model:value="formState.faviconUrl"
                  placeholder="例如：/favicon.ico 或 https://..."
                />
              </FormItem>

              <div v-if="formState.installedTime" class="install-info-badge">
                <span>安装状态：已完成安装向导</span>
                <span>部署时间：{{ formState.installedTime }}</span>
              </div>
            </Form>
          </div>
        </TabPane>

        <!-- 标签 2：品牌 Logo -->
        <TabPane key="logo" tab="品牌 Logo">
          <div class="tab-pane-box">
            <Card title="系统品牌 Logo" :bordered="true" class="logo-box-card max-w-2xl">
              <div class="logo-preview-area light-bg">
                <img
                  v-if="formState.logoUrl"
                  :src="formState.logoUrl"
                  alt="Logo Preview"
                  class="preview-image"
                />
                <div v-else class="text-gray-400 text-sm">暂无自定义 Logo（使用系统默认图标）</div>
              </div>
              <div class="upload-bar mt-4 flex items-center gap-3">
                <Upload
                  :show-upload-list="false"
                  :custom-request="handleUploadLogo"
                  accept="image/png,image/jpeg,image/svg+xml,image/webp"
                >
                  <Button :loading="uploadingLogo" type="primary" ghost>上传 Logo 图片</Button>
                </Upload>
                <Input
                  v-model:value="formState.logoUrl"
                  placeholder="或直接粘贴 Logo 图片 URL"
                  class="flex-1"
                />
              </div>
            </Card>
          </div>
        </TabPane>

        <!-- 标签 3：扩展配置 -->
        <TabPane key="extra" tab="高级与扩展参数">
          <div class="tab-pane-box">
            <div class="mb-4 text-sm text-gray-500">
              为后续业务功能预留的全局配置存储槽（支持合法的 JSON 格式），便于平滑扩展各种定制开关与字段。
            </div>
            <Form layout="vertical" class="max-w-3xl">
              <FormItem label="扩展参数 (JSON 字符串)" extra="支持后期平滑扩展，比如备案号、自定义外链等">
                <Input.TextArea
                  v-model:value="formState.extraSettings"
                  :rows="8"
                  placeholder="{ &quot;icp&quot;: &quot;京ICP备xxxx号&quot; }"
                  class="font-mono text-sm"
                />
              </FormItem>
            </Form>
          </div>
        </TabPane>
      </Tabs>

      <Divider />
      <div class="save-toolbar flex justify-end gap-3">
        <Button size="large" @click="loadSetting">重置更改</Button>
        <Button
          type="primary"
          size="large"
          class="save-btn px-8"
          :loading="saving"
          @click="handleSave"
        >
          保存并应用配置
        </Button>
      </div>
    </Card>
  </Page>
</template>

<style scoped>
.site-setting-card {
  border-radius: 12px;
}

.tab-pane-box {
  padding: 16px 0 24px;
}

.install-info-badge {
  display: flex;
  gap: 20px;
  background: #f0fdf4;
  border: 1px solid #bbf7d0;
  color: #166534;
  padding: 10px 16px;
  border-radius: 8px;
  font-size: 13px;
  margin-top: 16px;
}

.logo-preview-area {
  height: 140px;
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;
  padding: 12px;
}

.light-bg {
  background: #f8fafc;
  border: 2px dashed #e2e8f0;
}

.dark-bg {
  background: #0f172a;
  border: 2px dashed #334155;
}

.preview-image {
  max-height: 100px;
  max-width: 100%;
  object-fit: contain;
}

.save-btn {
  font-weight: 600;
}
</style>
