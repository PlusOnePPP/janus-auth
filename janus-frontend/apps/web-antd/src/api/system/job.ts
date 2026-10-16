import type { Recordable } from '@vben/types';
import { requestClient } from '#/api/request';

async function getJobList() {
  return requestClient.get<any[]>('/admin/system/job/list');
}
async function createJob(data: Recordable<any>) {
  return requestClient.post<string>('/admin/system/job', data);
}
async function updateJob(id: string, data: Recordable<any>) {
  return requestClient.put(`/admin/system/job/${id}`, data);
}
async function deleteJob(id: string) {
  return requestClient.delete(`/admin/system/job/${id}`);
}
async function toggleJob(id: string) {
  return requestClient.put(`/admin/system/job/${id}/toggle`);
}

export { createJob, deleteJob, getJobList, toggleJob, updateJob };
