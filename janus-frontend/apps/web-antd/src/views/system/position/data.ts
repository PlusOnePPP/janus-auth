import type { VbenFormSchema } from '#/adapter/form';
import type { OnActionClickFn, VxeTableGridColumns } from '#/adapter/vxe-table';
import type { SystemPositionApi } from '#/api/system/position';

export function usePositionFormSchema(): VbenFormSchema[] {
  return [
    {
      component: 'Input',
      fieldName: 'name',
      label: '职务名称',
      rules: 'required',
      componentProps: {
        placeholder: '请输入职务名称（如：一级部门经理）',
      },
    },
    {
      component: 'Input',
      fieldName: 'code',
      label: '职务编码',
      rules: 'required',
      componentProps: {
        placeholder: '请输入唯一编码（如：DEPT_MGR_1）',
      },
    },
    {
      component: 'InputNumber',
      fieldName: 'levelWeight',
      label: '职级权重',
      rules: 'required',
      help: '数值越大级别越高；部门内按此权重自动判定谁是部门老大',
      defaultValue: 20,
      componentProps: {
        min: 0,
        max: 9999,
        placeholder: '请输入职级权重（如：80）',
        style: { width: '100%' },
      },
    },
    {
      component: 'InputNumber',
      fieldName: 'sort',
      label: '排序序号',
      defaultValue: 0,
      componentProps: {
        min: 0,
        max: 9999,
        style: { width: '100%' },
      },
    },
    {
      component: 'RadioGroup',
      fieldName: 'status',
      label: '状态',
      defaultValue: 1,
      componentProps: {
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
      componentProps: {
        rows: 3,
        placeholder: '请输入备注说明',
      },
    },
  ];
}

export function usePositionGridFormSchema(): VbenFormSchema[] {
  return [
    {
      component: 'Input',
      fieldName: 'keyword',
      label: '职务检索',
      componentProps: {
        placeholder: '输入职务名称或编码',
      },
    },
  ];
}

export function usePositionColumns(
  onActionClick: OnActionClickFn<SystemPositionApi.SystemPosition>,
): VxeTableGridColumns<SystemPositionApi.SystemPosition> {
  return [
    {
      field: 'name',
      title: '职务名称',
      minWidth: 160,
      align: 'left',
    },
    {
      field: 'code',
      title: '职务编码',
      minWidth: 140,
      slots: { default: 'code' },
    },
    {
      field: 'levelWeight',
      title: '职级权重 (层级高低)',
      minWidth: 160,
      slots: { default: 'levelWeight' },
    },
    {
      field: 'status',
      title: '状态',
      width: 100,
      slots: { default: 'status' },
    },
    {
      field: 'sort',
      title: '排序',
      width: 80,
    },
    {
      field: 'remark',
      title: '备注',
      minWidth: 160,
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
      width: 140,
      fixed: 'right',
      align: 'center',
      cellRender: {
        name: 'CellOperation',
        attrs: {
          nameField: 'name',
          nameTitle: '职务名称',
          onClick: onActionClick,
        },
        options: [
          'edit',
          'delete',
        ],
      },
    },
  ];
}
