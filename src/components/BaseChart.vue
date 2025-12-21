<script setup lang="ts">
import { ref, onMounted, onUnmounted, watch } from 'vue'
import * as echarts from 'echarts'
import { useResizeObserver } from '@vueuse/core'

const props = defineProps<{
  options: any
  height?: string
  width?: string
}>()

const chartRef = ref<HTMLElement | null>(null)
let chartInstance: echarts.ECharts | null = null

const initChart = () => {
  if (chartRef.value) {
    chartInstance = echarts.init(chartRef.value)
    chartInstance.setOption(props.options)
  }
}

watch(() => props.options, (newOptions) => {
  chartInstance?.setOption(newOptions, true)
}, { deep: true })

useResizeObserver(chartRef, () => {
  chartInstance?.resize()
})

onMounted(() => {
  initChart()
})

onUnmounted(() => {
  chartInstance?.dispose()
})
</script>

<template>
  <div ref="chartRef" :style="{ height: height || '300px', width: width || '100%' }"></div>
</template>
