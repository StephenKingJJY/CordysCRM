import { FormDesignKeyEnum } from '@lib/shared/enums/formDesignEnum';

import { OrderRouteEnum } from '@/enums/routeEnum';

import { DEFAULT_LAYOUT } from '../base';
import type { AppRouteRecordRaw } from '../types';

const order: AppRouteRecordRaw = {
  path: '/order',
  name: OrderRouteEnum.ORDER,
  redirect: '/order/index',
  component: DEFAULT_LAYOUT,
  meta: { permissions: ['ORDER:READ'] },
  children: [
    {
      path: 'create',
      name: OrderRouteEnum.ORDER_CREATE,
      component: () => import('@/components/business/crm-form-create/index.vue'),
      props: { formKey: FormDesignKeyEnum.ORDER },
      meta: { locale: 'mobileOrder.create', permissions: ['ORDER:ADD'], depth: 3 },
    },
    {
      path: 'index',
      name: OrderRouteEnum.ORDER_INDEX,
      component: () => import('@/views/order/index.vue'),
      meta: { locale: 'mobileOrder.title', permissions: ['ORDER:READ'], depth: 2 },
    },
    {
      path: 'detail',
      name: OrderRouteEnum.ORDER_DETAIL,
      component: () => import('@/views/order/detail.vue'),
      meta: { locale: 'mobileOrder.detail', permissions: ['ORDER:READ'], depth: 3 },
    },
  ],
};

export default order;
