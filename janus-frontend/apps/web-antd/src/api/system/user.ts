import type { Recordable } from '@vben/types';

import { requestClient } from '#/api/request';

export namespace SystemUserApi {
  export interface SystemUser {
    [key: string]: any;
    id: string;
    username: string;
    realName?: string;
    avatar?: string;
    homePath?: string;
    deptId?: string;
    status: 0 | 1;
    remark?: string;
    createTime?: string;
    roleIds?: string[];
    roleCodes?: string[];
    roleNames?: string[];
  }
}

/**
 * 分页用户列表：返回 { items, total }
 */
async function getUserList(params: Recordable<any>) {
  return requestClient.get<{
    items: SystemUserApi.SystemUser[];
    total: number;
  }>('/admin/system/user/list', { params });
}

async function createUser(data: Recordable<any>) {
  return requestClient.post<string>('/admin/system/user', data);
}

async function updateUser(id: string, data: Recordable<any>) {
  return requestClient.put(`/admin/system/user/${id}`, data);
}

async function deleteUser(id: string) {
  return requestClient.delete(`/admin/system/user/${id}`);
}

async function resetUserPassword(id: string, newPassword: string) {
  return requestClient.post(`/admin/system/user/${id}/reset-password`, {}, {
    params: { newPassword },
  });
}

/** 导出用户列表 Excel */
async function exportUserList(params?: Recordable<any>) {
  const blob = await requestClient.download<Blob>('/admin/system/user/export', { params });
  const url = URL.createObjectURL(blob);
  const a = document.createElement('a');
  a.href = url;
  a.download = '用户列表.xlsx';
  a.click();
  URL.revokeObjectURL(url);
}

/** 获取指定用户的部门与职务列表 */
async function getUserDeptPositions(id: string) {
  return requestClient.get<any[]>(`/admin/system/user/${id}/dept-positions`);
}

export {
  createUser,
  deleteUser,
  exportUserList,
  getUserDeptPositions,
  getUserList,
  resetUserPassword,
  updateUser,
};
