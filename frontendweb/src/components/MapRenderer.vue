<template>
  <div class="map-container" ref="container">
    <canvas ref="canvasEl"></canvas>
    
    <!-- 图例 -->
    <div class="legend glass-panel">
      <h4>图例</h4>
      <div class="legend-item"><span class="box bg-blue-main"></span>主干道 (3格宽)</div>
      <div class="legend-item"><span class="box bg-blue-big"></span>大路 (2格宽)</div>
      <div class="legend-item"><span class="box bg-blue-small"></span>小路 (1格宽)</div>
      <div class="legend-item"><span class="circle bg-merchant"></span>商家</div>
      <div class="legend-item"><span class="box bg-com"></span>商业区</div>
      <div class="legend-item"><span class="box bg-res"></span>住宅区</div>
      <div class="divider"></div>
      <div class="legend-item"><span class="circle bg-rider-idle"></span>骑手 (空闲)</div>
      <div class="legend-item"><span class="circle bg-rider-pickup"></span>骑手 (取餐中)</div>
      <div class="legend-item"><span class="circle bg-rider-delivery"></span>骑手 (送餐中)</div>
      <div class="legend-item"><span class="box bg-order"></span>新订单</div>
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

.bg-blue-main { background: #3b82f6; }
.bg-blue-big { background: #2563eb; }
.bg-blue-small { background: #1e40af; }
.bg-merchant { background: #f59e0b; box-shadow: 0 0 5px #f59e0b; }
.bg-com { background: #7c3aed; }
.bg-res { background: #4b5563; }
.bg-rider-idle { background: #22c55e; box-shadow: 0 0 5px #22c55e; }
.bg-rider-pickup { background: #f97316; box-shadow: 0 0 5px #f97316; }
.bg-rider-delivery { background: #0ea5e9; box-shadow: 0 0 5px #0ea5e9; }
.bg-order { background: #ef4444; box-shadow: 0 0 5px #ef4444; }
</style>
