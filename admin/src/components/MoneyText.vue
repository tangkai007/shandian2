<script setup lang="ts">
import { computed } from 'vue';
import { fenToYuan } from '@/domain/money';

/** 金额展示：整数分入参，千分位 + 单位 + tabular-nums（S1b §11.1）。 */
const props = defineProps<{ value: number; withSymbol?: boolean }>();

const text = computed(() =>
  fenToYuan(props.value, props.withSymbol === undefined ? {} : { withSymbol: props.withSymbol }),
);
</script>

<template>
  <span class="adm-money adm-num">{{ text }}</span>
</template>
