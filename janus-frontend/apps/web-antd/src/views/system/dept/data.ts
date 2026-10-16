import type { VbenFormSchema } from '#/adapter/form';
import type { OnActionClickFn, VxeTableGridColumns } from '#/adapter/vxe-table';
import type { SystemDeptApi } from '#/api/system/dept';
import { getDeptTree } from '#/api/system/dept';

export function useDeptFormSchema(): VbenFormSchema[] {
  return [
    {
      component: 'Input',
      fieldName: 'name',
      label: '部门名称',
      rules: 'required',
    },
    {
      component: 'ApiTreeSelect',
      fieldName: 'pid',
      label: '上级部门',
      help: '留空表示顶级部门',
      componentProps: {
        class: 'w-full',
        style: { width: '100%' },
        api: getDeptTree,
        labelField: 'name',
        valueField: 'id',
        childrenField: 'children',
        treeDefaultExpandAll: true,
        showSearch: true,
        treeNodeFilterProp: 'label',
        placeholder: '请选择上级部门（留空为顶级部门）',
        allowClear: true,
        popupMatchSelectWidth: true,
        dropdownMatchSelectWidth: true,
        dropdownStyle: { maxHeight: '360px', overflow: 'auto' },
      },
    },
    {
      component: 'Textarea',
      fieldName: 'remark',
      label: '备注',
    },
  ];
}

export function useDeptGridFormSchema(): VbenFormSchema[] {
  return [
    {
      component: 'Input',
      fieldName: 'keyword',
      label: '部门名称',
    },
  ];
}

export function useDeptColumns(
  onActionClick: OnActionClickFn<SystemDeptApi.SystemDept>,
): VxeTableGridColumns<SystemDeptApi.SystemDept> {
  return [
    {
      field: 'name',
      title: '部门名称',
      treeNode: true,
      minWidth: 200,
      align: 'left',
    },
    {
      field: 'leaderName',
      title: '部门负责人 (老大)',
      minWidth: 160,
      slots: { default: 'leader' },
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
      width: 200,
      fixed: 'right',
      align: 'center',
      cellRender: {
        name: 'CellOperation',
        attrs: {
          nameField: 'name',
          nameTitle: '部门名称',
          onClick: onActionClick,
        },
        options: [
          { code: 'append', text: '新增下级' },
          'edit',
          'delete',
        ],
      },
    },
  ];
}
