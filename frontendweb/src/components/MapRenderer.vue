<template>
  <div class="map-container" ref="container">
    <canvas ref="canvasEl"></canvas>
    
    <!-- 图例 -->
    <div class="legend glass-panel">
      <h4>🎨 色彩图例</h4>
      <div class="legend-item"><span class="box bg-road-main"></span>主干道 (白色)</div>
      <div class="legend-item"><span class="box bg-road-big"></span>大路 (灰色)</div>
      <div class="legend-item"><span class="box bg-road-small"></span>小路 (深灰色)</div>
      <div class="legend-item"><span class="circle bg-merchant"></span>商家 (橘色)</div>
      <div class="legend-item"><span class="box bg-com-high"></span>高密商业区 (深紫)</div>
      <div class="legend-item"><span class="box bg-com-low"></span>低密商业区 (浅紫)</div>
      <div class="legend-item"><span class="box bg-res-high"></span>高密住宅区 (深绿)</div>
      <div class="legend-item"><span class="box bg-res-low"></span>低密住宅区 (浅绿)</div>
      <div class="divider"></div>
      <div class="legend-item"><span class="circle bg-rider-idle"></span>骑手: 空闲 (绿色)</div>
      <div class="legend-item"><span class="circle bg-rider-pickup"></span>骑手: 接单中 (浅蓝)</div>
      <div class="legend-item"><span class="circle bg-rider-delivery"></span>骑手: 配送中 (深蓝)</div>
      <div class="legend-item"><span class="box bg-order-new"></span>新订单 (红色)</div>
      <div class="divider"></div>
      <div class="legend-item hint">💡 点击订单查看详情</div>
      <div class="legend-item hint">🖱️ 滚轮缩放 / 拖拽平移</div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, onBeforeUnmount, watch } from 'vue';
import CanvasEngine from '../utils/CanvasEngine';

const props = defineProps({
  mapData: Object,
  riders: Array,
  orders: Object,
  selectedOrderId: String
});

const emit = defineEmits(['orderSelected']);

const container = ref(null);
const canvasEl = ref(null);
let engine = null;

onMounted(() => {
  engine = new CanvasEngine(canvasEl.value);
  
  // 当地图上点击订单时，通知父组件
  engine.onOrderSelected = (orderId) => {
    emit('orderSelected', orderId);
  };
  
  const resize = () => {
    if (container.value) {
      engine.resize(container.value.clientWidth, container.value.clientHeight);
    }
  };
  window.addEventListener('resize', resize);
  resize();

  if (props.mapData) engine.setMap(props.mapData);
});

onBeforeUnmount(() => {
  if (engine) engine.destroy();
});

watch(() => props.mapData, (newMap) => {
  if (engine) engine.setMap(newMap);
});

watch(() => props.riders, (newRiders) => {
  if (engine && newRiders) engine.updateRiders(newRiders);
}, { deep: true });

watch(() => props.orders, (newOrders) => {
  if (engine) {
    Object.values(newOrders).forEach(o => engine.addOrder(o));
    engine.orders.forEach((_, id) => {
      if (!newOrders[id]) engine.removeOrder(id);
    });
  }
}, { deep: true });

// 从侧边栏点击订单时，同步高亮到 Canvas
watch(() => props.selectedOrderId, (newId) => {
  if (engine) engine.selectOrder(newId);
});
</script>

<style scoped>
.map-container {
  width: 100%;
  height: 100%;
  position: relative;
  background-color: #0f172a;
  overflow: hidden;
  border-radius: 16px;
}

canvas {
  display: block;
  width: 100%;
  height: 100%;
  cursor: grab;
}

.legend {
  position: absolute;
  bottom: 20px;
  left: 20px;
  padding: 15px;
  display: flex;
  flex-direction: column;
  gap: 8px;
  font-size: 12px;
  color: #94a3b8;
  pointer-events: none;
}

.legend h4 {
  margin: 0 0 5px 0;
  color: #fff;
  font-size: 14px;
}

.legend-item {
  display: flex;
  align-items: center;
  gap: 8px;
}
.legend-item.hint {
  font-size: 11px;
  color: #64748b;
}

.box { width: 12px; height: 12px; border-radius: 2px; }
.circle { width: 12px; height: 12px; border-radius: 50%; }
.divider { height: 1px; background: rgba(255,255,255,0.1); margin: 4px 0; }

.bg-road-main { background: #ffffff; border: 1px solid #cbd5e1; }
.bg-road-big { background: #94a3b8; }
.bg-road-small { background: #475569; }
.bg-merchant { background: #f97316; box-shadow: 0 0 6px #f97316; }
.bg-com-high { background: #6b21a8; }
.bg-com-low { background: #c084fc; }
.bg-res-high { background: #15803d; }
.bg-res-low { background: #86efac; border: 1px solid #22c55e; }
.bg-rider-idle { background: #22c55e; box-shadow: 0 0 6px #22c55e; }
.bg-rider-pickup { background: #38bdf8; box-shadow: 0 0 6px #38bdf8; }
.bg-rider-delivery { background: #1d4ed8; box-shadow: 0 0 6px #1d4ed8; }
.bg-order-new { background: #ef4444; box-shadow: 0 0 6px #ef4444; }
</style>
