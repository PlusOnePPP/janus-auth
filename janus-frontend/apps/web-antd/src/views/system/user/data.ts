import type { VbenFormSchema } from '#/adapter/form';
import type { OnActionClickFn, VxeTableGridColumns } from '#/adapter/vxe-table';
import type { SystemUserApi } from '#/api/system/user';

/** 内置超级管理员登录名（界面不可删除，后端同步兜底校验） */
const SUPER_ADMIN_USERNAME = 'janus';

export function useUserFormSchema(): VbenFormSchema[] {
  return [
    {
      component: 'Input',
      fieldName: 'username',
      label: '登录账号',
      rules: 'required',
    },
    {
      component: 'InputPassword',
      fieldName: 'password',
      label: '密码',
      help: '新建时必填；编辑时留空表示不修改',
    },
    {
      component: 'Input',
      fieldName: 'realName',
      label: '真实姓名',
    },
    {
      component: 'RadioGroup',
      fieldName: 'status',
      label: '状态',
      defaultValue: 1,
      componentProps: {
        buttonStyle: 'solid',
        optionType: 'button',
        options: [
          { label: '启用', value: 1 },
          { label: '停用', value: 0 },
        ],
      },
    },
    {
      component: 'Textarea',
      fieldName: 'remark',
      label: '备注',
    },
  ];
}

export function useUserGridFormSchema(): VbenFormSchema[] {
  return [
    {
      component: 'Input',
      fieldName: 'username',
      label: '登录账号',
    },
    {
      component: 'Select',
      fieldName: 'status',
      label: '状态',
      componentProps: {
        allowClear: true,
        options: [
          { label: '启用', value: 1 },
          { label: '停用', value: 0 },
        ],
      },
    },
  ];
}

export function useUserColumns(
  onActionClick: OnActionClickFn<SystemUserApi.SystemUser>,
  onStatusChange?: (newStatus: any, row: SystemUserApi.SystemUser) => PromiseLike<boolean | undefined>,
): VxeTableGridColumns<SystemUserApi.SystemUser> {
  return [
    {
      field: 'username',
      title: '登录账号',
      width: 160,
    },
    {
      field: 'realName',
      title: '真实姓名',
      width: 140,
    },
    {
      field: 'roleNames',
      title: '角色',
      formatter: ({ row }) =>
        (row.roleNames && row.roleNames.length ? row.roleNames : row.roleCodes || []).join('，') || '-',
      minWidth: 140,
    },
    {
      field: 'deptPositions',
      title: '部门与职务',
      minWidth: 200,
      slots: { default: 'deptPositions' },
    },
    {
      field: 'status',
      title: '状态',
      width: 100,
      cellRender: {
        name: onStatusChange ? 'CellSwitch' : 'CellTag',
        attrs: { beforeChange: onStatusChange },
      },
    },
    {
      field: 'remark',
      title: '备注',
      minWidth: 140,
    },
    {
      field: 'createTime',
      title: '创建时间',
      width: 180,
      formatter: 'formatDateTime',
    },
    {
      field: 'operation',
      title: '操作',
      width: 160,
      fixed: 'right',
      align: 'center',
      cellRender: {
        name: 'CellOperation',
        attrs: {
          nameField: 'username',
          nameTitle: '登录账号',
          onClick: onActionClick,
        },
        // 按钮级权限：与后端 @SaCheckPermission 同一套码
        options: [
          { code: 'edit', accessCode: 'AC_100020' },
          {
            code: 'delete',
            accessCode: 'AC_100030',
            // 内置超级管理员不允许删除（按钮隐藏，后端亦有兜底校验）
            show: (row: SystemUserApi.SystemUser) =>
              row.username !== SUPER_ADMIN_USERNAME,
          },
        ],
      },
    },
  ];
}
