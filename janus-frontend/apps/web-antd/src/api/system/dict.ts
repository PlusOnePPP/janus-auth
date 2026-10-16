import type { Recordable } from '@vben/types';

import { requestClient } from '#/api/request';

export namespace DictApi {
  export interface DictType {
    id: string;
    name: string;
    code: string;
    status: 0 | 1;
    remark?: string;
    createTime?: string;
    updateTime?: string;
  }

  export interface DictData {
    id: string;
    typeId: string;
    label: string;
    value: string;
    sort: number;
    status: 0 | 1;
    cssClass?: string;
    remark?: string;
    createTime?: string;
  }
}

// 字典类型
async function getDictTypeList(params: Recordable<any>) {
  return requestClient.get<{ items: DictApi.DictType[]; total: number }>(
    '/admin/system/dict/type/list',
    { params },
  );
}

async function isDictCodeExists(code: string, id?: string) {
  const params: Recordable<any> = { code };
  if (id != null) params.id = id;
  return requestClient.get<boolean>('/admin/system/dict/type/code-exists', { params });
}

async function createDictType(data: Recordable<any>) {
  return requestClient.post<string>('/admin/system/dict/type', data);
}

async function updateDictType(id: string, data: Recordable<any>) {
  return requestClient.put(`/admin/system/dict/type/${id}`, data);
}

async function deleteDictType(id: string) {
  return requestClient.delete(`/admin/system/dict/type/${id}`);
}

// 字典数据
async function getDictDataList(typeId: string) {
  return requestClient.get<DictApi.DictData[]>('/admin/system/dict/data/list', {
    params: { typeId },
  });
}

async function getDictDataByCode(code: string) {
  return requestClient.get<DictApi.DictData[]>(`/admin/system/dict/data/code/${code}`);
}

async function createDictData(data: Recordable<any>) {
  return requestClient.post<string>('/admin/system/dict/data', data);
}

async function updateDictData(id: string, data: Recordable<any>) {
  return requestClient.put(`/admin/system/dict/data/${id}`, data);
}

async function deleteDictData(id: string) {
  return requestClient.delete(`/admin/system/dict/data/${id}`);
}

export {
  createDictData,
  createDictType,
  deleteDictData,
  deleteDictType,
  getDictDataByCode,
  getDictDataList,
  getDictTypeList,
  isDictCodeExists,
  updateDictData,
  updateDictType,
};
