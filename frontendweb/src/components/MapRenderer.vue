<template>
  <div class="map-container" ref="container">
    <canvas ref="canvasEl"></canvas>

    <!-- 图例 -->
    <div class="legend glass-panel">
      <h4>🎨 色彩图例</h4>
      <div class="legend-item"><span class="box bg-road-main"></span>主干道 (白色)</div>
      <div class="legend-item"><span class="box bg-road-big"></span>大路 (灰色)</div>
      <div class="legend-item"><span class="box bg-road-small"></span>小路/山路 (深灰色)</div>
      <div class="legend-item"><span class="box bg-tile-ground"></span>草地 (山脚)</div>
      <div class="legend-item"><span class="box bg-tile-lake"></span>湖面</div>
      <div class="legend-item"><span class="box bg-tile-mountain"></span>山体 (山腰)</div>
      <div class="divider"></div>
      <div class="legend-item"><span class="box bg-plot-initial"></span>山脚地块</div>
      <div class="legend-item"><span class="box bg-plot-mountain"></span>山腰地块</div>
      <div class="legend-item"><span class="box bg-plot-lakeside"></span>湖畔地块</div>
      <div class="divider"></div>
      <div class="legend-item"><span class="box bg-b-room"></span>普通客房</div>
      <div class="legend-item"><span class="box bg-b-lux"></span>豪华套房</div>
      <div class="legend-item"><span class="box bg-b-rest"></span>餐厅</div>
      <div class="legend-item"><span class="box bg-b-spa"></span>温泉池</div>
      <div class="legend-item"><span class="box bg-b-view"></span>观景台</div>
      <div class="legend-item"><span class="box bg-b-park"></span>停车场</div>
      <div class="divider"></div>
      <div class="legend-item"><span class="circle bg-guest"></span>游客</div>
      <div class="legend-item"><span class="box bg-staff-front"></span>前台员工</div>
      <div class="legend-item"><span class="box bg-staff-clean"></span>保洁员工</div>
      <div class="legend-item"><span class="box bg-staff-chef"></span>厨师员工</div>
      <div class="legend-item"><span class="box bg-staff-maint"></span>维修员工</div>
      <div class="divider"></div>
      <div class="legend-item hint">🖱️ 滚轮缩放 / 拖拽平移</div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, onBeforeUnmount, watch } from 'vue';
import CanvasEngine from '../utils/CanvasEngine';

const props = defineProps({
  mapData: Object,
  guests: Array,
  staff: Array,
  buildings: Array,
  plots: Array
});

const container = ref(null);
const canvasEl = ref(null);
let engine = null;

onMounted(() => {
  engine = new CanvasEngine(canvasEl.value);

  const resize = () => {
    if (container.value) {
      engine.resize(container.value.clientWidth, container.value.clientHeight);
    }
  };
  window.addEventListener('resize', resize);
  resize();

  if (props.mapData) engine.setMap(props.mapData);
  if (props.guests) engine.setGuests(props.guests);
  if (props.staff) engine.setStaff(props.staff);
  if (props.buildings) engine.setBuildings(props.buildings);
  if (props.plots) engine.setPlots(props.plots);
});

onBeforeUnmount(() => {
  if (engine) engine.destroy();
});

watch(() => props.mapData, (newMap) => {
  if (engine) engine.setMap(newMap);
});

watch(() => props.guests, (newGuests) => {
  if (engine) engine.setGuests(newGuests);
}, { deep: true });

watch(() => props.staff, (newStaff) => {
  if (engine) engine.setStaff(newStaff);
}, { deep: true });

watch(() => props.buildings, (newBuildings) => {
  if (engine) engine.setBuildings(newBuildings);
}, { deep: true });

watch(() => props.plots, (newPlots) => {
  if (engine) engine.setPlots(newPlots);
}, { deep: true });

// 暴露给父组件：建造成功后定位新建筑 / 回到整图
defineExpose({
  focusOn: (x, y) => { if (engine) engine.focusOn(x, y); },
  resetView: () => { if (engine) engine.resetView(); }
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
.bg-tile-ground { background: #31402a; }
.bg-tile-lake { background: #1e5b78; }
.bg-tile-mountain { background: #7a5c34; }
.bg-plot-initial { background: rgba(34, 197, 94, 0.5); border: 1px solid #22c55e; }
.bg-plot-mountain { background: rgba(180, 83, 9, 0.7); border: 1px solid #b45309; }
.bg-plot-lakeside { background: rgba(14, 116, 144, 0.7); border: 1px solid #06b6d4; }
.bg-b-room { background: #4ade80; }
.bg-b-lux { background: #fbbf24; }
.bg-b-rest { background: #fb923c; }
.bg-b-spa { background: #22d3ee; }
.bg-b-view { background: #a78bfa; }
.bg-b-park { background: #94a3b8; }
.bg-guest { background: #e2e8f0; box-shadow: 0 0 6px #e2e8f0; }
.bg-staff-front { background: #38bdf8; }
.bg-staff-clean { background: #f472b6; }
.bg-staff-chef { background: #fb923c; }
.bg-staff-maint { background: #a78bfa; }
</style>
