import type { Recordable } from '@vben/types';

import { requestClient } from '#/api/request';

export namespace SystemPositionApi {
  export interface SystemPosition {
    [key: string]: any;
    id: string;
    name: string;
    code: string;
    levelWeight: number;
    sort: number;
    status: 0 | 1;
    remark?: string;
    createTime?: string;
    updateTime?: string;
  }
}

/**
 * 职务列表（按职级权重降序排列）
 */
async function getPositionList(params?: Recordable<any>) {
  return requestClient.get<SystemPositionApi.SystemPosition[]>('/admin/system/position/list', {
    params,
  });
}

/**
 * 职务详情
 */
async function getPositionById(id: string) {
  return requestClient.get<SystemPositionApi.SystemPosition>(`/admin/system/position/${id}`);
}

/**
 * 新增职务
 */
async function createPosition(data: Recordable<any>) {
  return requestClient.post<SystemPositionApi.SystemPosition>('/admin/system/position', data);
}

/**
 * 编辑职务
 */
async function updatePosition(id: string, data: Recordable<any>) {
  return requestClient.put<SystemPositionApi.SystemPosition>(`/admin/system/position/${id}`, data);
}

/**
 * 删除职务
 */
async function deletePosition(id: string) {
  return requestClient.delete(`/admin/system/position/${id}`);
}

/**
 * 修改职务状态
 */
async function changePositionStatus(id: string, status: number) {
  return requestClient.put(`/admin/system/position/${id}/status`, {}, {
    params: { status },
  });
}

export {
  changePositionStatus,
  createPosition,
  deletePosition,
  getPositionById,
  getPositionList,
  updatePosition,
};
