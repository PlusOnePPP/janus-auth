import type { Recordable } from '@vben/types';

import { requestClient } from '#/api/request';

export namespace SystemRoleApi {
  export interface SystemRole {
    [key: string]: any;
    id: string;
    name: string;
    code: string;
    status: 0 | 1;
    remark?: string;
    createBy?: string;
    creatorName?: string;
    createTime?: string;
    userCount?: number;
  }
}

/**
 * 分页角色列表：返回 { items, total }
 */
async function getRoleList(params: Recordable<any>) {
  return requestClient.get<{
    items: SystemRoleApi.SystemRole[];
    total: number;
  }>('/admin/system/role/list', { params });
}

async function createRole(data: Recordable<any>) {
  return requestClient.post<string>('/admin/system/role', data);
}

async function updateRole(id: string, data: Recordable<any>) {
  return requestClient.put(`/admin/system/role/${id}`, data);
}

async function deleteRole(id: string) {
  return requestClient.delete(`/admin/system/role/${id}`);
}

async function getRoleMenus(id: string) {
  return requestClient.get<string[]>(`/admin/system/role/${id}/menus`);
}

async function assignRoleMenus(id: string, data: { menuIds: string[] }) {
  return requestClient.post(`/admin/system/role/${id}/menus`, data);
}

/** 查询角色下关联的用户列表 */
async function getRoleUsers(roleId: string, params: Recordable<any>) {
  return requestClient.get<{
    items: any[];
    total: number;
  }>(`/admin/system/role/${roleId}/users`, { params });
}

/** 向角色增量添加用户，返回实际新增用户数 */
async function addRoleUsers(roleId: string, userIds: string[]) {
  return requestClient.post<number>(`/admin/system/role/${roleId}/users`, { userIds });
}

/** 从角色移除用户，返回实际移除用户数 */
async function removeRoleUsers(roleId: string, userIds: string[]) {
  return requestClient.delete<number>(`/admin/system/role/${roleId}/users`, {
    data: { userIds },
  });
}

/** 导出角色列表 Excel */
async function exportRoleList(params?: Recordable<any>) {
  const blob = await requestClient.download<Blob>('/admin/system/role/export', { params });
  const url = URL.createObjectURL(blob);
  const a = document.createElement('a');
  a.href = url;
  a.download = '角色列表.xlsx';
  a.click();
  URL.revokeObjectURL(url);
}

export {
  addRoleUsers,
  assignRoleMenus,
  createRole,
  deleteRole,
  exportRoleList,
  getRoleList,
  getRoleMenus,
  getRoleUsers,
  removeRoleUsers,
  updateRole,
};
