<template>
  <div class="h-full overflow-auto" :class="props.class">
    <van-pull-refresh v-model="refreshing" @refresh="handleRefresh">
      <van-empty v-if="list.length === 0 && !loading && !error" :description="t('common.noData')" />
      <van-list
        v-model:loading="loading"
        v-model:error="error"
        :error-text="props.errorText ?? t('common.listLoadErrorTip')"
        :finished="finished"
        :finished-text="list.length === 0 ? '' : t('common.listFinishedTip')"
        class="flex flex-col"
        :class="`gap-[${itemGap}px]`"
        @load="loadList"
      >
        <template v-for="item in list" :key="item.id">
          <slot name="item" :item="item"></slot>
        </template>
      </van-list>
    </van-pull-refresh>
  </div>
</template>

<script setup lang="ts">
  import { closeToast, showLoadingToast, showSuccessToast } from 'vant';
  import { cloneDeep } from 'lodash-es';

  import { useI18n } from '@lib/shared/hooks/useI18n';
  import { CommonList } from '@lib/shared/models/common';

  const props = defineProps<{
    keyword?: string;
    class?: string;
    listParams?: Record<string, any>;
    itemGap?: number;
    noPageNation?: boolean;
    errorText?: string;
    isReturnNativeResponse?: boolean;
    loadListApi?: (...args: any) => Promise<CommonList<Record<string, any>> | Record<string, any>>;
    transform?: (item: any, optionMap?: Record<string, any[]>) => Record<string, any>;
    closeInitLoad?: boolean; // 关闭首次加载
    notShowLoadingToast?: boolean;
  }>();
  const emit = defineEmits<{
    (e: 'refresh'): void;
  }>();

  const { t } = useI18n();

  const loading = defineModel<boolean>('loading', {
    default: false,
  });
  const error = ref(false);
  const refreshing = ref(false);
  const finished = ref(false);
  const list = defineModel<any[]>('modelValue', {
    default: [],
  });
  const currentPage = ref(0);

  const code = ref(0); // 状态码

  const originData = ref<any[]>([]);
  const searchKeyword = ref('');
  let generation = 0;
  let inFlight = false;
  async function loadList(refresh = false) {
    if (props.closeInitLoad) return;
    if (!refresh && inFlight) return;
    const requestGeneration = refresh ? ++generation : generation;
    const page = refresh ? 1 : currentPage.value + 1;
    if (page === 1) searchKeyword.value = (props.listParams?.keyword ?? props.keyword ?? '').trim();
    inFlight = true;
    try {
      if (!props.loadListApi) {
        list.value = props.transform ? list.value.map((e: any) => props.transform!(e)) : list.value;
        originData.value = cloneDeep(list.value);
        finished.value = true;
        return;
      }

      loading.value = true;
      if (refresh) {
        list.value = [];
        currentPage.value = 0;
        finished.value = false;
      }
      if (page === 1 && !refreshing.value && !props.notShowLoadingToast) {
        showLoadingToast(t('common.loading'));
      }
      const res = await props.loadListApi({
        ...props.listParams,
        keyword: searchKeyword.value,
        pageSize: 20,
        current: page,
      });
      if (requestGeneration !== generation) return;
      if (props?.isReturnNativeResponse) {
        code.value = res.data.code;
      }
      const data = props?.isReturnNativeResponse ? res.data.data : res;
      const resList = props.noPageNation && !data.list ? data : data.list || [];
      const dataList = props.transform
        ? resList.map((e: Record<string, any>) => props.transform!(e, data.optionMap))
        : resList;
      if (refresh) {
        list.value = dataList;
      } else {
        list.value = list.value.concat(dataList);
      }
      currentPage.value = page;
      finished.value = props.noPageNation || resList.length === 0 || data.total <= page * 20;
      error.value = false;
      originData.value = cloneDeep(list.value);
    } catch (_error: any) {
      if (requestGeneration !== generation) return;
      // eslint-disable-next-line no-console
      console.log(_error);
      error.value = true;
      if (props?.isReturnNativeResponse) {
        code.value = _error.code;
      }
    } finally {
      if (requestGeneration === generation) {
        inFlight = false;
        loading.value = false;
        refreshing.value = false;
        closeToast();
      }
    }
  }

  function filterListByKeyword(keywordKeys: string | string[]) {
    // Paged sources must search the full server-side dataset, not the loaded page.
    if (props.loadListApi && !props.noPageNation) {
      return loadList(true);
    }
    if (!props.keyword?.trim()?.length) {
      if (props.loadListApi) {
        loadList(true);
      } else {
        list.value = originData.value;
      }
      return;
    }
    const lowerCaseVal = props.keyword.trim()?.toLowerCase();
    const keys = Array.isArray(keywordKeys) ? keywordKeys : [keywordKeys];
    list.value = originData.value.filter((item) => {
      return keys.some((key) => item[key]?.toString().toLowerCase().includes(lowerCaseVal));
    });
  }

  async function handleRefresh() {
    refreshing.value = true;
    await loadList(true);
    showSuccessToast(t('common.refreshSuccess'));
    emit('refresh');
  }

  defineExpose({
    loadList,
    filterListByKeyword,
    code,
  });
</script>

<style lang="less" scoped></style>
