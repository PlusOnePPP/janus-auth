import { requestClient } from '#/api/request';

async function getServerInfo() {
  return requestClient.get<any>('/admin/system/monitor/server');
}

export { getServerInfo };
