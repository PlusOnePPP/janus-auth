import type { Recordable } from '@vben/types';
import { requestClient } from '#/api/request';

async function getConfigList(params: Recordable<any>) {
  return requestClient.get<{ items: any[]; total: number }>('/admin/system/config/list', { params });
}
async function getConfigByKey(key: string) {
  return requestClient.get<string>(`/admin/system/config/key/${key}`);
}
async function createConfig(data: Recordable<any>) {
  return requestClient.post<string>('/admin/system/config', data);
}
async function updateConfig(id: string, data: Recordable<any>) {
  return requestClient.put(`/admin/system/config/${id}`, data);
}
async function deleteConfig(id: string) {
  return requestClient.delete(`/admin/system/config/${id}`);
}
async function isConfigKeyExists(key: string, id?: string) {
  const params: Recordable<any> = { key };
  if (id != null) params.id = id;
  return requestClient.get<boolean>('/admin/system/config/key-exists', { params });
}

export { createConfig, deleteConfig, getConfigByKey, getConfigList, isConfigKeyExists, updateConfig };
