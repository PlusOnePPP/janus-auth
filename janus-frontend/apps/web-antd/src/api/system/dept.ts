import type { Recordable } from '@vben/types';

import { requestClient } from '#/api/request';

/** 内置根机构 ID（V1 初始化数据） */
export const ROOT_DEPT_ID = 'd0000000000000000000000000000001';

export namespace SystemDeptApi {
  export interface SystemDept {
    [key: string]: any;
    id: string;
    pid?: string;
    name: string;
    status: 0 | 1;
    remark?: string;
    createTime?: string;
  }
}

/**
 * 部门列表（扁平数组）
 */
async function getDeptList(params?: Recordable<any>) {
  return requestClient.get<SystemDeptApi.SystemDept[]>('/admin/system/dept/list', {
    params,
  });
}

/**
 * 将扁平部门数组构建为树形结构
 */
function buildDeptTree(list: SystemDeptApi.SystemDept[] = []): any[] {
  const map = new Map<string, any>();
  const roots: any[] = [];
  list.forEach((d) => map.set(d.id, { ...d, children: [] }));
  list.forEach((d) => {
    const node = map.get(d.id);
    if (d.pid && d.pid !== '0' && map.has(d.pid)) {
      map.get(d.pid).children.push(node);
    } else {
      roots.push(node);
    }
  });
  return roots;
}

/**
 * 获取部门树形数据
 */
async function getDeptTree(): Promise<any[]> {
  const list = await getDeptList();
  return buildDeptTree(list);
}

async function createDept(data: Recordable<any>) {
  return requestClient.post<string>('/admin/system/dept', data);
}

async function updateDept(id: string, data: Recordable<any>) {
  return requestClient.put(`/admin/system/dept/${id}`, data);
}

async function deleteDept(id: string) {
  return requestClient.delete(`/admin/system/dept/${id}`);
}

/**
 * 获取部门下成员及职务明细
 */
async function getDeptMembers(id: string) {
  return requestClient.get<any[]>(`/admin/system/dept/${id}/members`);
}

export { buildDeptTree, createDept, deleteDept, getDeptList, getDeptMembers, getDeptTree, updateDept };
