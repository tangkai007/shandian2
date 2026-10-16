import { ref, type Ref } from 'vue';
import { ApiError } from '@/api/client';
import { ErrorCodes } from '@/types/contract';

export interface AsyncTask<T> {
  data: Ref<T | null>;
  loading: Ref<boolean>;
  error: Ref<ApiError | null>;
  run: (loader: () => Promise<T>) => Promise<void>;
}

/** 页面级异步状态（加载 / 空 / 错误），与 `PageState` 组件配套。 */
export function useAsync<T>(initial: T | null = null): AsyncTask<T> {
  const data = ref(initial) as Ref<T | null>;
  const loading = ref(false);
  const error = ref<ApiError | null>(null);

  async function run(loader: () => Promise<T>): Promise<void> {
    loading.value = true;
    error.value = null;
    try {
      data.value = await loader();
    } catch (caught) {
      error.value =
        caught instanceof ApiError
          ? caught
          : new ApiError(ErrorCodes.systemError, caught instanceof Error ? caught.message : String(caught));
    } finally {
      loading.value = false;
    }
  }

  return { data, loading, error, run };
}
