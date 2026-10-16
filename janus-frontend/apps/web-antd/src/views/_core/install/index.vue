<script lang="ts" setup>
import { ref } from 'vue';
import { useRouter } from 'vue-router';
import {
  Button,
  Card,
  Col,
  Form,
  FormItem,
  Input,
  message,
  Row,
  Steps,
  Step,
  Tag,
  Upload,
} from 'ant-design-vue';
import { LOGIN_PATH } from '@vben/constants';
import { initInstallApi, uploadLogoApi } from '#/api/system/site';
import { useSiteStore } from '#/store';

defineOptions({ name: 'InstallWizard' });

const router = useRouter();
const siteStore = useSiteStore();

const currentStep = ref(0);
const submitting = ref(false);
const uploadingLogo = ref(false);

interface FormState {
  systemName: string;
  systemDesc: string;
  logoUrl: string;
  logoDarkUrl: string;
  faviconUrl: string;
  loginTheme: string;
  copyright: string;
}

const formState = ref<FormState>({
  systemName: '企业统一认证中心',
  systemDesc: '安全、高速、企业级统一认证与单点登录中心',
  logoUrl: '',
  logoDarkUrl: '',
  faviconUrl: '',
  loginTheme: 'default',
  copyright: `© ${new Date().getFullYear()} 统一认证中心. All Rights Reserved.`,
});

async function handleCustomUpload(options: any) {
  const { file, onSuccess, onError } = options;
  uploadingLogo.value = true;
  try {
    const res = await uploadLogoApi(file as File);
    formState.value.logoUrl = res.url;
    if (!formState.value.logoDarkUrl) {
      formState.value.logoDarkUrl = res.url;
    }
    message.success('Logo 上传成功');
    onSuccess(res, file);
  } catch (err: any) {
    message.error(err?.message || 'Logo 上传失败');
    onError(err);
  } finally {
    uploadingLogo.value = false;
  }
}

function nextStep() {
  if (currentStep.value === 0) {
    if (!formState.value.systemName.trim()) {
      message.warning('请输入系统名称');
      return;
    }
  }
  if (currentStep.value < 2) {
    currentStep.value += 1;
  }
}

function prevStep() {
  if (currentStep.value > 0) {
    currentStep.value -= 1;
  }
}

async function handleCompleteInstall() {
  submitting.value = true;
  try {
    await initInstallApi({
      systemName: formState.value.systemName.trim(),
      systemDesc: formState.value.systemDesc.trim(),
      logoUrl: formState.value.logoUrl,
      logoDarkUrl: formState.value.logoUrl,
      faviconUrl: formState.value.faviconUrl,
      loginTheme: formState.value.loginTheme,
      copyright: formState.value.copyright,
      extraSettings: JSON.stringify({
        installedFrom: 'web-installer',
        initTheme: formState.value.loginTheme,
      }),
    });

    message.success('恭喜！系统初始化安装完成，正在跳转登录页...', 1.5);
    // 立即就地更新本地 store 安装标记
    siteStore.siteConfig.installed = true;
    siteStore.isLoaded = true;
    await siteStore.fetchSiteConfig(true);

    // 解析登录页目标路径并执行 replace 跳转，清空当前 install 历史记录且完整载入新主题
    const targetUrl = router.resolve(LOGIN_PATH).href;
    setTimeout(() => {
      window.location.replace(targetUrl);
    }, 600);
  } catch (err: any) {
    message.error(err?.message || '安装初始化失败，请稍后重试');
    submitting.value = false;
  }
}
</script>

<template>
  <div class="install-container">
    <!-- 动态科技背景微粒光晕 -->
    <div class="bg-mesh-decorations">
      <div class="glow-sphere sphere-1"></div>
      <div class="glow-sphere sphere-2"></div>
      <div class="glow-sphere sphere-3"></div>
    </div>

    <!-- 主窗口卡片 -->
    <div class="install-card-wrapper">
      <div class="install-header">
        <div class="brand-badge">
          <span class="pulse-dot"></span>
          <span>系统首次启动向导</span>
        </div>
        <h1 class="install-title">初始化系统部署配置</h1>
        <p class="install-subtitle">
          欢迎使用 Janus 统一认证与单点登录中心，完成以下基础设置后系统将正式进入就绪状态。
        </p>
      </div>

      <!-- 步骤指示器 -->
      <div class="steps-container">
        <Steps :current="currentStep" size="small">
          <Step title="基本信息" description="系统名称与描述" />
          <Step title="品牌视觉" description="Logo 与图标" />
          <Step title="完成安装" description="确认并生效" />
        </Steps>
      </div>

      <div class="step-content">
        <!-- Step 0: 基本信息 -->
        <div v-show="currentStep === 0" class="step-pane">
          <div class="pane-intro">
            <h3>第一步：设置系统基本信息</h3>
            <p>这些信息将呈现在浏览器标签栏、系统页眉、页脚版权及系统对外通告中。</p>
          </div>
          <Form layout="vertical" class="form-body">
            <FormItem label="系统名称" required>
              <Input
                v-model:value="formState.systemName"
                placeholder="例如：企业统一认证中心 / 星云单点登录平台"
                size="large"
              />
            </FormItem>
            <FormItem label="系统副标题 / Slogan">
              <Input
                v-model:value="formState.systemDesc"
                placeholder="例如：安全、高效的企业级统一登录中心"
                size="large"
              />
            </FormItem>
            <FormItem label="页脚版权说明">
              <Input
                v-model:value="formState.copyright"
                placeholder="例如：© 2026 统一认证中心. All Rights Reserved."
                size="large"
              />
            </FormItem>
          </Form>
        </div>

        <!-- Step 1: 品牌视觉 Logo -->
        <div v-show="currentStep === 1" class="step-pane">
          <div class="pane-intro">
            <h3>第二步：品牌视觉与 Logo 定制</h3>
            <p>上传企业 Logo，系统将在登录页与管理后台自动进行自适应渲染。</p>
          </div>
          <Row :gutter="24" class="logo-config-row">
            <Col :span="12">
              <Card class="logo-preview-card" :bordered="false">
                <template #title>Logo 实时预览</template>
                <div class="logo-preview-box">
                  <img
                    v-if="formState.logoUrl"
                    :src="formState.logoUrl"
                    alt="Logo Preview"
                    class="preview-img"
                  />
                  <div v-else class="empty-logo-tip">
                    <div class="empty-icon">🖼️</div>
                    <div>暂未上传 Logo（将使用默认矢量样式）</div>
                  </div>
                </div>
                <div class="upload-actions mt-4">
                  <Upload
                    :show-upload-list="false"
                    :custom-request="handleCustomUpload"
                    accept="image/png,image/jpeg,image/svg+xml,image/webp"
                  >
                    <Button :loading="uploadingLogo" type="primary" ghost>
                      点击上传图片 Logo
                    </Button>
                  </Upload>
                </div>
              </Card>
            </Col>
            <Col :span="12">
              <Form layout="vertical">
                <FormItem label="Logo 图片地址（可手动输入或上传）">
                  <Input
                    v-model:value="formState.logoUrl"
                    placeholder="https://... 或点击左侧按钮上传"
                    size="large"
                  />
                </FormItem>
                <FormItem label="浏览器 Favicon 地址（可选）">
                  <Input
                    v-model:value="formState.faviconUrl"
                    placeholder="留空则使用默认 favicon.ico"
                    size="large"
                  />
                </FormItem>
              </Form>
            </Col>
          </Row>
        </div>

        <!-- Step 2: 确认并完成安装 -->
        <div v-show="currentStep === 2" class="step-pane">
          <div class="pane-intro">
            <h3>第三步：确认配置并完成安装</h3>
            <p>请核对您的系统初始配置，点击「完成安装并立即进入」将正式生效！</p>
          </div>
          <div class="summary-list">
            <div class="summary-item">
              <span class="summary-label">系统名称</span>
              <span class="summary-val">{{ formState.systemName }}</span>
            </div>
            <div class="summary-item">
              <span class="summary-label">系统描述</span>
              <span class="summary-val">{{ formState.systemDesc || '（未设置）' }}</span>
            </div>
            <div class="summary-item">
              <span class="summary-label">品牌 Logo</span>
              <span class="summary-val">{{ formState.logoUrl || '使用默认矢量图标' }}</span>
            </div>
            <div class="summary-item">
              <span class="summary-label">版权声明</span>
              <span class="summary-val">{{ formState.copyright }}</span>
            </div>
          </div>
        </div>
      </div>

      <!-- 底部操作导航条 -->
      <div class="install-footer">
        <Button v-if="currentStep > 0" size="large" @click="prevStep">上一步</Button>
        <div class="flex-1"></div>
        <Button
          v-if="currentStep < 2"
          type="primary"
          size="large"
          class="next-btn"
          @click="nextStep"
        >
          下一步
        </Button>
        <Button
          v-else
          type="primary"
          size="large"
          class="complete-btn"
          :loading="submitting"
          @click="handleCompleteInstall"
        >
          完成安装并立即开启
        </Button>
      </div>
    </div>
  </div>
</template>

<style scoped>
.install-container {
  position: relative;
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 32px 16px;
  background-color: #0b1120;
  overflow: hidden;
}

.bg-mesh-decorations {
  position: absolute;
  inset: 0;
  pointer-events: none;
  overflow: hidden;
}

.glow-sphere {
  position: absolute;
  border-radius: 9999px;
  filter: blur(120px);
  opacity: 0.35;
}

.sphere-1 {
  width: 500px;
  height: 500px;
  background: #3b82f6;
  top: -100px;
  left: -100px;
}

.sphere-2 {
  width: 600px;
  height: 600px;
  background: #6366f1;
  bottom: -150px;
  right: -100px;
}

.sphere-3 {
  width: 400px;
  height: 400px;
  background: #06b6d4;
  top: 35%;
  left: 45%;
}

.install-card-wrapper {
  position: relative;
  z-index: 10;
  width: 100%;
  max-width: 880px;
  background: rgba(255, 255, 255, 0.95);
  backdrop-filter: blur(24px);
  border-radius: 20px;
  box-shadow: 0 25px 60px -15px rgba(0, 0, 0, 0.4), 0 0 0 1px rgba(255, 255, 255, 0.1);
  padding: 40px;
}

.install-header {
  text-align: center;
  margin-bottom: 28px;
}

.brand-badge {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 6px 14px;
  background: #e0f2fe;
  color: #0284c7;
  border-radius: 9999px;
  font-size: 13px;
  font-weight: 600;
  margin-bottom: 12px;
}

.pulse-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: #0284c7;
  animation: pulse 2s cubic-bezier(0.4, 0, 0.6, 1) infinite;
}

@keyframes pulse {
  0%, 100% { opacity: 1; transform: scale(1); }
  50% { opacity: 0.4; transform: scale(1.3); }
}

.install-title {
  font-size: 26px;
  font-weight: 700;
  color: #0f172a;
  margin: 0 0 8px;
}

.install-subtitle {
  color: #64748b;
  font-size: 14px;
  margin: 0;
}

.steps-container {
  margin: 24px 0 32px;
  padding: 0 16px;
}

.step-content {
  min-height: 320px;
}

.pane-intro {
  margin-bottom: 20px;
}

.pane-intro h3 {
  font-size: 18px;
  font-weight: 600;
  color: #1e293b;
  margin-bottom: 4px;
}

.pane-intro p {
  font-size: 13px;
  color: #64748b;
  margin: 0;
}

.logo-preview-box {
  min-height: 120px;
  display: flex;
  align-items: center;
  justify-content: center;
  border: 2px dashed #cbd5e1;
  border-radius: 12px;
  background: #f8fafc;
  padding: 16px;
}

.preview-img {
  max-height: 80px;
  max-width: 100%;
  object-fit: contain;
}

.empty-logo-tip {
  text-align: center;
  color: #94a3b8;
  font-size: 13px;
}

.empty-icon {
  font-size: 32px;
  margin-bottom: 6px;
}

.theme-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 16px;
}

.theme-card {
  border: 2px solid #e2e8f0;
  border-radius: 14px;
  overflow: hidden;
  cursor: pointer;
  transition: all 0.25s ease;
  background: #ffffff;
}

.theme-card:hover {
  transform: translateY(-3px);
  border-color: #93c5fd;
  box-shadow: 0 10px 20px -5px rgba(59, 130, 246, 0.15);
}

.theme-card.active {
  border-color: #2563eb;
  box-shadow: 0 0 0 2px rgba(37, 99, 235, 0.2), 0 12px 24px -5px rgba(37, 99, 235, 0.2);
}

.theme-preview-box {
  position: relative;
  width: 100%;
  height: 140px;
  background: #f1f5f9;
  overflow: hidden;
}

.theme-screenshot-img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  transition: transform 0.3s ease;
}

.theme-card:hover .theme-screenshot-img {
  transform: scale(1.03);
}

.theme-overlay {
  position: absolute;
  top: 10px;
  left: 10px;
  right: 10px;
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  pointer-events: none;
}

.theme-badge {
  background: rgba(0, 0, 0, 0.65);
  backdrop-filter: blur(8px);
  color: #ffffff;
  font-size: 11px;
  font-weight: 500;
  padding: 3px 8px;
  border-radius: 6px;
}

.selected-check {
  width: 26px;
  height: 26px;
  background: #2563eb;
  color: white;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-weight: bold;
  font-size: 14px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.25);
}

.theme-info {
  padding: 14px;
}

.theme-name {
  font-size: 15px;
  font-weight: 600;
  color: #1e293b;
  margin-bottom: 4px;
}

.theme-desc {
  font-size: 12px;
  color: #64748b;
  line-height: 1.5;
  margin-bottom: 10px;
  min-height: 36px;
}

.summary-list {
  background: #f8fafc;
  border-radius: 14px;
  padding: 18px 24px;
  display: flex;
  flex-direction: column;
  gap: 14px;
  border: 1px solid #e2e8f0;
}

.summary-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-size: 14px;
}

.summary-label {
  color: #64748b;
  font-weight: 500;
}

.summary-val {
  color: #0f172a;
  font-weight: 500;
}

.install-footer {
  display: flex;
  align-items: center;
  margin-top: 32px;
  padding-top: 20px;
  border-top: 1px solid #e2e8f0;
}

.next-btn, .complete-btn {
  padding-left: 28px;
  padding-right: 28px;
  font-weight: 600;
}
</style>
