import { requestClient } from '#/api/request';

export namespace SsoClientApi {
  export interface Client {
    id: string;
    appName: string;
    client: string;
    clientUrl: string;
    notifyPath: string;
    status: string;
    /** 脱敏后的签名密钥（如 abcd****5678），明文需调 getSsoClientSecretKey 按需获取 */
    secretKeyMasked: string;
    createTime: string;
    updateTime: string;
  }
}

/** 客户端注册列表（secretKey 脱敏） */
async function getSsoClientList() {
  return requestClient.get<SsoClientApi.Client[]>('/admin/system/sso-client/list');
}

/** 查看客户端签名密钥明文（服务端记录审计日志） */
async function getSsoClientSecretKey(id: string) {
  return requestClient.get<string>(`/admin/system/sso-client/${id}/secret-key`);
}

export { getSsoClientList, getSsoClientSecretKey };
