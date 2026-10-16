import type { Recordable, UserInfo } from '@vben/types';

import { ref } from 'vue';
import { useRouter } from 'vue-router';

import { LOGIN_PATH } from '@vben/constants';
import { preferences } from '@vben/preferences';
import { resetAllStores, useAccessStore, useUserStore } from '@vben/stores';

import { notification } from 'ant-design-vue';
import { defineStore } from 'pinia';

import { getAccessCodesApi, getUserInfoApi, loginApi, logoutApi } from '#/api';
import { $t } from '#/locales';

export const useAuthStore = defineStore('auth', () => {
  const accessStore = useAccessStore();
  const userStore = useUserStore();
  const router = useRouter();

  const loginLoading = ref(false);

  /**
   * 异步处理登录操作
   * Asynchronously handle the login process
   * @param params 登录表单数据
   */
  async function authLogin(
    params: Recordable<any>,
    onSuccess?: () => Promise<void> | void,
  ) {
    let userInfo: null | UserInfo = null;
    try {
      loginLoading.value = true;
      const { accessToken } = await loginApi(params);
      userInfo = await finishLogin(accessToken, onSuccess);
    } finally {
      loginLoading.value = false;
    }

    return {
      userInfo,
    };
  }

  /** 持有 accessToken 后完成登录收尾（拉用户信息、清过期标记、跳首页 / 回跳） */
  async function finishLogin(
    accessToken: string,
    onSuccess?: () => Promise<void> | void,
  ): Promise<null | UserInfo> {
    if (!accessToken) {
      return null;
    }
    accessStore.setAccessToken(accessToken);

    const [userInfo, accessCodes] = await Promise.all([
      fetchUserInfo(),
      getAccessCodesApi(),
    ]);

    userStore.setUserInfo(userInfo);
    accessStore.setAccessCodes(accessCodes);

    if (accessStore.loginExpired) {
      accessStore.setLoginExpired(false);
    } else if (onSuccess) {
      await onSuccess?.();
    } else {
      // SSO 外跳：redirect 指向外部系统（如业务系统的 /sso/login?ticket 回跳链路）时，
      // 必须整页跳转而非 SPA 路由（vue-router 无法解析外部绝对地址，会拼成 /auth/http://…）
      const rawRedirect = router.currentRoute.value.query?.redirect as
        | string
        | undefined;
      const external = rawRedirect ? safeDecode(rawRedirect) : undefined;
      if (external?.startsWith('http')) {
        window.location.href = external;
        return userInfo;
      }
      await router.push(userInfo.homePath || preferences.app.defaultHomePath);
    }

    if (userInfo?.realName) {
      notification.success({
        description: `${$t('authentication.loginSuccessDesc')}:${userInfo?.realName}`,
        duration: 3,
        message: $t('authentication.loginSuccess'),
      });
    }
    return userInfo;
  }

  async function logout(redirect: boolean = true) {
    try {
      await logoutApi();
    } catch {
      // 不做任何处理
    }
    resetAllStores();
    accessStore.setLoginExpired(false);

    // 回登录页带上当前路由地址
    await router.replace({
      path: LOGIN_PATH,
      query: redirect
        ? {
            redirect: encodeURIComponent(router.currentRoute.value.fullPath),
          }
        : {},
    });
  }

  async function fetchUserInfo() {
    const userInfo = await getUserInfoApi();
    userStore.setUserInfo(userInfo);
    return userInfo;
  }

  function $reset() {
    loginLoading.value = false;
  }

  /** 容错解码 redirect 参数（非法编码时原样返回，避免 decodeURIComponent 抛异常） */
  function safeDecode(value: string): string {
    try {
      return decodeURIComponent(value);
    } catch {
      return value;
    }
  }

  return {
    $reset,
    authLogin,
    fetchUserInfo,
    finishLogin,
    loginLoading,
    logout,
  };
});
