import type { Recordable } from '@vben/types';

import { requestClient } from '#/api/request';

export namespace SystemMenuApi {
  export interface SystemMenu {
    [key: string]: any;
    id: string;
    pid: string;
    name: string;
    type: string;
    path: string;
    component?: string;
    redirect?: string;
    authCode?: string;
    icon?: string;
    status: 0 | 1;
    sort?: number;
    /** 后端以 JSON 字符串返回，前端按需解析为对象 */
    meta?: any;
    createTime?: string;
    updateTime?: string;
  }

  export const MenuTypeOptions = [
    { label: '目录', value: 'catalog' },
    { label: '菜单', value: 'menu' },
    { label: '按钮', value: 'button' },
    { label: '内嵌', value: 'embedded' },
    { label: '外链', value: 'link' },
  ];
}

/**
 * 菜单扁平列表
 */
async function getMenuList() {
  return requestClient.get<SystemMenuApi.SystemMenu[]>('/admin/system/menu/list');
}

/**
 * 菜单树（上级菜单选择用，不含按钮，meta 已解析为对象）
 */
async function getMenuTree() {
  return requestClient.get<any[]>('/admin/system/menu/tree');
}

/**
 * 权限分配树（角色授权勾选用，含按钮型，meta 已解析为对象）
 */
async function getPermissionMenuTree() {
  return requestClient.get<any[]>('/admin/system/menu/tree/permission');
}

async function isMenuNameExists(name: string, id?: string) {
  const params: Recordable<any> = { name };
  if (id != null) {
    params.id = id;
  }
  return requestClient.get<boolean>('/admin/system/menu/name-exists', { params });
}

async function isMenuPathExists(path: string, id?: string) {
  const params: Recordable<any> = { path };
  if (id != null) {
    params.id = id;
  }
  return requestClient.get<boolean>('/admin/system/menu/path-exists', { params });
}

async function createMenu(data: Recordable<any>) {
  return requestClient.post<string>('/admin/system/menu', data);
}

async function updateMenu(id: string, data: Recordable<any>) {
  return requestClient.put(`/admin/system/menu/${id}`, data);
}

async function deleteMenu(id: string) {
  return requestClient.delete(`/admin/system/menu/${id}`);
}

export {
  createMenu,
  deleteMenu,
  getMenuList,
  getMenuTree,
  getPermissionMenuTree,
  isMenuNameExists,
  isMenuPathExists,
  updateMenu,
};
