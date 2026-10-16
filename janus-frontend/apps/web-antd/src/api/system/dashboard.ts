import { requestClient } from '#/api/request';

export namespace DashboardApi {
  export interface OverviewData {
    totalUsers: number;
    activeUsers: number;
    disabledUsers: number;
    totalRoles: number;
    totalDepts: number;
    totalMenus: number;
  }

  export interface TrendItem {
    month: string;
    count: number;
  }

  export interface DistributionItem {
    name: string;
    value: number;
  }
}

/** 概览统计 */
export async function getOverviewApi() {
  return requestClient.get<DashboardApi.OverviewData>('/admin/dashboard/overview');
}

/** 用户增长趋势 */
export async function getUserTrendsApi() {
  return requestClient.get<DashboardApi.TrendItem[]>('/admin/dashboard/user-trends');
}

/** 角色分布 */
export async function getRoleDistributionApi() {
  return requestClient.get<DashboardApi.DistributionItem[]>(
    '/admin/dashboard/role-distribution',
  );
}

/** 部门用户分布 */
export async function getDeptDistributionApi() {
  return requestClient.get<DashboardApi.DistributionItem[]>(
    '/admin/dashboard/dept-distribution',
  );
}
