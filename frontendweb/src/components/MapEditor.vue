<template>
  <div class="map-editor-container">
    <!-- 顶部工具栏 -->
    <div class="editor-header glass-panel">
      <div class="header-left">
        <button class="btn-back" @click="$emit('close')">⬅️ 返回监控</button>
        <h2>🎨 城市地图可视化编辑器 (201x201)</h2>
      </div>

      <div class="header-center">
        <!-- 撤销按钮 -->
        <button 
          class="btn-action btn-undo" 
          @click="undo" 
          :disabled="historyStack.length === 0"
          title="撤销上一步操作 (Ctrl+Z / Cmd+Z)"
        >
          ↩️ 撤销 ({{ historyStack.length }})
        </button>

        <div class="map-name-input">
          <label>地图名称：</label>
          <input type="text" v-model="mapName" placeholder="例如：我的自建都市中心" />
        </div>
        <button class="btn-action btn-save" @click="saveMap" :disabled="isSaving">
          💾 {{ currentMapId ? '更新地图' : '保存为新地图' }}
        </button>
        <button v-if="currentMapId" class="btn-action btn-new" @click="resetToNewMap">
          ➕ 新建空白地图
        </button>
      </div>

      <div class="header-right">
        <label>载入已有地图：</label>
        <select v-model="selectedMapIdToLoad" @change="loadSelectedMap" class="map-select">
          <option value="">-- 选择地图载入 --</option>
          <option v-for="m in savedMaps" :key="m.id" :value="m.id">
            {{ m.name }} (道路数: {{ m.roadCount }})
          </option>
        </select>
        <button v-if="currentMapId" class="btn-action btn-delete" @click="deleteCurrentMap">
          🗑️ 删除
        </button>
      </div>
    </div>

    <!-- 中部主工作区：左侧工具箱 + 中间 Canvas 画布 + 右侧说明统计 -->
    <div class="editor-workspace">
      <!-- 左侧画笔工具箱 -->
      <div class="toolbox glass-panel">
        <h3>🖌️ 绘图工具</h3>
        
        <!-- 绘制模式切换：画笔 vs 直线 -->
        <div class="tool-group">
          <div class="group-title">绘制方式</div>
          <div class="mode-selector">
            <button 
              class="mode-btn" 
              :class="{ active: drawMode === 'brush' }" 
              @click="drawMode = 'brush'"
            >
              🖌️ 自由涂抹
            </button>
            <button 
              class="mode-btn" 
              :class="{ active: drawMode === 'line' }" 
              @click="drawMode = 'line'"
            >
              📏 直线拉取
            </button>
          </div>
        </div>

        <div class="tool-group">
          <div class="group-title">道路层级 (骑手可通行)</div>
          <button 
            class="tool-btn" 
            :class="{ active: currentTool === 1 }" 
            @click="setTool(1, 3)"
          >
            <span class="tool-color c-main"></span>
            🛣️ 主干道 (3格宽, 9.0格/秒)
          </button>
          <button 
            class="tool-btn" 
            :class="{ active: currentTool === 2 }" 
            @click="setTool(2, 2)"
          >
            <span class="tool-color c-big"></span>
            🚗 大路 (2格宽, 6.5格/秒)
          </button>
          <button 
            class="tool-btn" 
            :class="{ active: currentTool === 3 }" 
            @click="setTool(3, 1)"
          >
            <span class="tool-color c-small"></span>
            🚲 小路 (1格宽, 4.0格/秒)
          </button>
        </div>

        <div class="tool-group">
          <div class="group-title">建筑与功能区</div>
          <button 
            class="tool-btn" 
            :class="{ active: currentTool === 6 }" 
            @click="setTool(6, 3)"
          >
            <span class="tool-color c-com-high"></span>
            🏬 高密商业区 (产出商家)
          </button>
          <button 
            class="tool-btn" 
            :class="{ active: currentTool === 7 }" 
            @click="setTool(7, 3)"
          >
            <span class="tool-color c-com-low"></span>
            🏢 低密商业区 (产出商家)
          </button>
          <button 
            class="tool-btn" 
            :class="{ active: currentTool === 4 }" 
            @click="setTool(4, 3)"
          >
            <span class="tool-color c-res-high"></span>
            🏙️ 高密住宅区 (送餐目标)
          </button>
          <button 
            class="tool-btn" 
            :class="{ active: currentTool === 5 }" 
            @click="setTool(5, 3)"
          >
            <span class="tool-color c-res-low"></span>
            🏠 低密住宅区 (送餐目标)
          </button>
          <button 
            class="tool-btn" 
            :class="{ active: currentTool === 0 }" 
            @click="setTool(0, 3)"
          >
            <span class="tool-color c-eraser"></span>
            🧹 橡皮擦 (涂抹为基底)
          </button>
        </div>

        <div class="tool-group">
          <div class="group-title">笔刷尺寸</div>
          <div class="size-selector">
            <button 
              v-for="s in [1, 3, 5]" 
              :key="s" 
              class="size-btn" 
              :class="{ active: brushSize === s }" 
              @click="brushSize = s"
            >
              {{ s }}x{{ s }}
            </button>
          </div>
        </div>

        <div class="tool-group quick-actions">
          <div class="group-title">快捷预设与操作</div>
          <button class="btn-quick" @click="quickDrawCenterCross">✝️ 铺设中心十字干道</button>
          <button class="btn-quick" @click="clearAllGrid">🗑️ 清空所有道路</button>
        </div>
      </div>

      <!-- 中间 Canvas 画布 -->
      <div class="canvas-wrapper" ref="canvasWrapper">
        <canvas ref="canvasEl"></canvas>
        <div class="canvas-hint">
          <span>
            🖱️ {{ drawMode === 'line' ? '按住左键拖拽拉直线 (松开落笔)' : '左键拖拽自由涂抹' }} | 
            右键/中键拖动画布 | 滚轮缩放视图 ({{ Math.round(scale * 100) }}%) | 
            快捷键: <strong>Ctrl+Z / Cmd+Z</strong> 撤销
          </span>
          <span class="cursor-coord">光标: ({{ hoverCoord.x }}, {{ hoverCoord.y }})</span>
        </div>
      </div>

      <!-- 右侧统计与规则说明 -->
      <div class="info-sidebar glass-panel">
        <h3>📊 地图数据分析</h3>
        <div class="stat-card">
          <div class="stat-row">
            <span>地图网格：</span>
            <span class="val">201 × 201 (40,401 格)</span>
          </div>
          <div class="stat-row">
            <span>🛣️ 主干道格子：</span>
            <span class="val text-warning">{{ roadStats.main }}</span>
          </div>
          <div class="stat-row">
            <span>🚗 大路格子：</span>
            <span class="val text-accent">{{ roadStats.big }}</span>
          </div>
          <div class="stat-row">
            <span>🚲 小路格子：</span>
            <span class="val">{{ roadStats.small }}</span>
          </div>
          <div class="stat-row">
            <span>🛣️ 道路总格子：</span>
            <span class="val text-success">{{ roadStats.totalRoads }}</span>
          </div>
          <div class="stat-row">
            <span>🏢 临路商业区 (商家位)：</span>
            <span class="val text-warning">{{ roadStats.roadsideCom }}</span>
          </div>
          <div class="stat-row">
            <span>🏠 临路住宅区 (送餐位)：</span>
            <span class="val text-success">{{ roadStats.roadsideRes }}</span>
          </div>
        </div>

        <div class="rules-tip">
          <h4>💡 高效绘制技巧：</h4>
          <ul>
            <li><strong>直线拉取</strong>：切换至【📏 直线拉取】，可以非常轻松地一笔拉出贯穿全城笔直的主干道！</li>
            <li><strong>随时撤销</strong>：画错随时点击【↩️ 撤销】或按 <strong>Ctrl+Z</strong> 回退。</li>
            <li><strong>临路原则</strong>：商业区与住宅区只要与道路紧邻，系统即可自动抽取作为商家与送餐点！</li>
            <li><strong>骑手出生</strong>：骑手启动时会在您绘制的所有马路格子上随机分散出生。</li>
          </ul>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, onUnmounted } from 'vue';

const emit = defineEmits(['close', 'mapSaved']);

const SIZE = 201;
const GRID_MIN = -100;

// 工具状态
const currentTool = ref(1); // 1: 主干道, 2: 大路, 3: 小路, 6: 高密商业, 7: 低密商业, 4: 高密住宅, 5: 低密住宅, 0: 橡皮
const drawMode = ref('brush'); // 'brush': 自由画笔, 'line': 直线工具
const brushSize = ref(3);

// 撤销历史快照栈 (最多 30 步)
const historyStack = ref([]);

const mapName = ref('自建城市地图');
const currentMapId = ref(null);
const savedMaps = ref([]);
const selectedMapIdToLoad = ref('');
const isSaving = ref(false);

const canvasWrapper = ref(null);
const canvasEl = ref(null);
let ctx = null;

// 201x201 网格数据
const grid = ref(Array.from({ length: SIZE }, () => Array(SIZE).fill(5))); // 默认全低密住宅(5)

// 视图与拖拽
const scale = ref(0.8);
const offsetX = ref(0);
const offsetY = ref(0);
const isDrawing = ref(false);
const isPanning = ref(false);
const lastMouse = { x: 0, y: 0 };
const hoverCoord = reactive({ x: 0, y: 0 });

// 直线绘制状态
const isLineDrawing = ref(false);
const lineStart = reactive({ gridX: 0, gridY: 0 });
const lineEnd = reactive({ gridX: 0, gridY: 0 });

const TILE_COLORS = {
  0: '#0f172a', // 橡皮擦基底
  1: '#ffffff', // 主干道 (白色)
  2: '#94a3b8', // 大路 (灰色)
  3: '#475569', // 小路 (深灰色)
  4: '#15803d', // 高密住宅 (深绿)
  5: '#86efac', // 低密住宅 (浅绿)
  6: '#6b21a8', // 高密商业 (深紫)
  7: '#c084fc'  // 低密商业 (浅紫)
};

const setTool = (tool, defaultBrush = 3) => {
  currentTool.value = tool;
  brushSize.value = defaultBrush;
};

// 保存历史快照
const pushHistory = () => {
  if (historyStack.value.length >= 30) {
    historyStack.value.shift();
  }
  historyStack.value.push(grid.value.map(row => [...row]));
};

// 执行撤销
const undo = () => {
  if (historyStack.value.length > 0) {
    grid.value = historyStack.value.pop();
    render();
  }
};

// 键盘快捷键监听 (Ctrl+Z / Cmd+Z)
const handleKeyDown = (e) => {
  if ((e.ctrlKey || e.metaKey) && (e.key === 'z' || e.key === 'Z')) {
    e.preventDefault();
    undo();
  }
};

// 统计
const roadStats = computed(() => {
  let main = 0, big = 0, small = 0;
  let roadsideCom = 0, roadsideRes = 0;
  const g = grid.value;

  for (let i = 0; i < SIZE; i++) {
    for (let j = 0; j < SIZE; j++) {
      const t = g[i][j];
      if (t === 1) main++;
      else if (t === 2) big++;
      else if (t === 3) small++;
    }
  }

  // 临路计算
  for (let i = 0; i < SIZE; i++) {
    for (let j = 0; j < SIZE; j++) {
      const t = g[i][j];
      if (t === 6 || t === 7 || t === 4 || t === 5) {
        let hasAdj = false;
        for (let di = -1; di <= 1; di++) {
          for (let dj = -1; dj <= 1; dj++) {
            if (di === 0 && dj === 0) continue;
            const ni = i + di, nj = j + dj;
            if (ni >= 0 && ni < SIZE && nj >= 0 && nj < SIZE) {
              const nt = g[ni][nj];
              if (nt === 1 || nt === 2 || nt === 3) {
                hasAdj = true;
                break;
              }
            }
          }
          if (hasAdj) break;
        }
        if (hasAdj) {
          if (t === 6 || t === 7) roadsideCom++;
          if (t === 4 || t === 5) roadsideRes++;
        }
      }
    }
  }

  return {
    main,
    big,
    small,
    totalRoads: main + big + small,
    roadsideCom,
    roadsideRes
  };
});

// 加载地图列表
const fetchSavedMaps = async () => {
  try {
    const res = await fetch('http://localhost:8081/api/maps');
    if (res.ok) {
      savedMaps.value = await res.json();
    }
  } catch (e) {
    console.error('加载地图列表失败:', e);
  }
};

// 载入指定地图
const loadSelectedMap = async () => {
  if (!selectedMapIdToLoad.value) return;
  try {
    const res = await fetch(`http://localhost:8081/api/maps/${selectedMapIdToLoad.value}`);
    if (res.ok) {
      const data = await res.json();
      pushHistory();
      currentMapId.value = data.id;
      mapName.value = data.name;
      grid.value = data.grid;
      render();
    }
  } catch (e) {
    alert('加载地图详情失败: ' + e.message);
  }
};

const resetToNewMap = () => {
  pushHistory();
  currentMapId.value = null;
  mapName.value = '新建自建地图';
  selectedMapIdToLoad.value = '';
  grid.value = Array.from({ length: SIZE }, () => Array(SIZE).fill(5));
  quickDrawCenterCross();
  render();
};

const saveMap = async () => {
  if (!mapName.value.trim()) {
    alert('请输入地图名称！');
    return;
  }
  isSaving.value = true;
  try {
    const payload = {
      name: mapName.value.trim(),
      grid: grid.value
    };
    let res;
    if (currentMapId.value) {
      res = await fetch(`http://localhost:8081/api/maps/${currentMapId.value}`, {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(payload)
      });
    } else {
      res = await fetch('http://localhost:8081/api/maps', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(payload)
      });
    }

    if (res.ok) {
      const data = await res.json();
      currentMapId.value = data.id;
      alert(`🎉 地图《${data.name}》保存成功！可在启动模拟时直接选用。`);
      await fetchSavedMaps();
      emit('mapSaved', data);
    } else {
      alert('保存失败，请检查后端服务');
    }
  } catch (e) {
    alert('保存地图发生异常: ' + e.message);
  } finally {
    isSaving.value = false;
  }
};

const deleteCurrentMap = async () => {
  if (!currentMapId.value) return;
  if (!confirm(`确定要删除地图《${mapName.value}》吗？`)) return;

  try {
    const res = await fetch(`http://localhost:8081/api/maps/${currentMapId.value}`, {
      method: 'DELETE'
    });
    if (res.ok) {
      alert('地图已删除');
      await fetchSavedMaps();
      resetToNewMap();
    }
  } catch (e) {
    alert('删除地图失败: ' + e.message);
  }
};

// 快捷铺设中心十字干道
const quickDrawCenterCross = () => {
  pushHistory();
  const center = 100;
  // 纵向
  for (let i = 0; i < SIZE; i++) {
    for (let offset = -1; offset <= 1; offset++) {
      grid.value[i][center + offset] = 1;
    }
  }
  // 横向
  for (let j = 0; j < SIZE; j++) {
    for (let offset = -1; offset <= 1; offset++) {
      grid.value[center + offset][j] = 1;
    }
  }
  // 放置几个商业区
  for (let i = 80; i <= 95; i++) {
    for (let j = 80; j <= 95; j++) {
      if (grid.value[i][j] !== 1) grid.value[i][j] = 6;
    }
  }
  for (let i = 105; i <= 120; i++) {
    for (let j = 105; j <= 120; j++) {
      if (grid.value[i][j] !== 1) grid.value[i][j] = 7;
    }
  }
  render();
};

const clearAllGrid = () => {
  if (confirm('确定要清空所有道路并重置为低密度住宅基底吗？')) {
    pushHistory();
    grid.value = Array.from({ length: SIZE }, () => Array(SIZE).fill(5));
    render();
  }
};

// ================= Bresenham 直线算法 =================
const getBresenhamPoints = (x0, y0, x1, y1) => {
  const points = [];
  const dx = Math.abs(x1 - x0);
  const dy = Math.abs(y1 - y0);
  const sx = (x0 < x1) ? 1 : -1;
  const sy = (y0 < y1) ? 1 : -1;
  let err = dx - dy;

  let currX = x0;
  let currY = y0;

  while (true) {
    points.push({ x: currX, y: currY });
    if (currX === x1 && currY === y1) break;
    const e2 = 2 * err;
    if (e2 > -dy) { err -= dy; currX += sx; }
    if (e2 < dx) { err += dx; currY += sy; }
  }
  return points;
};

// ================= 画布渲染与交互 =================
const tileSize = 8; // 每个网格像素基础大小

const initCanvas = () => {
  const canvas = canvasEl.value;
  const wrapper = canvasWrapper.value;
  if (!canvas || !wrapper) return;

  canvas.width = wrapper.clientWidth;
  canvas.height = wrapper.clientHeight;
  ctx = canvas.getContext('2d', { alpha: false });

  // 居中画布
  const totalW = SIZE * tileSize * scale.value;
  offsetX.value = (canvas.width - totalW) / 2;
  offsetY.value = (canvas.height - totalW) / 2;

  render();
};

const render = () => {
  if (!ctx || !canvasEl.value) return;
  const canvas = canvasEl.value;

  // 清空背景
  ctx.fillStyle = '#0f172a';
  ctx.fillRect(0, 0, canvas.width, canvas.height);

  ctx.save();
  ctx.translate(offsetX.value, offsetY.value);
  ctx.scale(scale.value, scale.value);

  const g = grid.value;
  const tSize = tileSize;

  // 绘制 201x201 网格
  for (let i = 0; i < SIZE; i++) {
    for (let j = 0; j < SIZE; j++) {
      const tile = g[i][j];
      ctx.fillStyle = TILE_COLORS[tile] || '#1e382b';
      ctx.fillRect(i * tSize, j * tSize, tSize, tSize);
    }
  }

  // 绘制网格外边框
  ctx.strokeStyle = 'rgba(255, 255, 255, 0.15)';
  ctx.lineWidth = 1;
  ctx.strokeRect(0, 0, SIZE * tSize, SIZE * tSize);

  // 标出 (0,0) 逻辑中心点
  ctx.fillStyle = '#ffffff';
  ctx.beginPath();
  ctx.arc(100 * tSize + tSize/2, 100 * tSize + tSize/2, 4, 0, Math.PI * 2);
  ctx.fill();

  // 如果处于直线拉取模式且正在拖拽中，绘制半透明高亮直线预览
  if (drawMode.value === 'line' && isLineDrawing.value) {
    const linePts = getBresenhamPoints(lineStart.gridX, lineStart.gridY, lineEnd.gridX, lineEnd.gridY);
    const radius = Math.floor(brushSize.value / 2);
    const previewColor = TILE_COLORS[currentTool.value === 0 ? 0 : currentTool.value] || '#ff4757';
    
    ctx.fillStyle = previewColor;
    ctx.globalAlpha = 0.65;
    const drawnSet = new Set();

    for (const pt of linePts) {
      for (let di = -radius; di <= radius; di++) {
        for (let dj = -radius; dj <= radius; dj++) {
          const ni = pt.x + di;
          const nj = pt.y + dj;
          if (ni >= 0 && ni < SIZE && nj >= 0 && nj < SIZE) {
            const key = `${ni},${nj}`;
            if (!drawnSet.has(key)) {
              drawnSet.add(key);
              ctx.fillRect(ni * tSize, nj * tSize, tSize, tSize);
            }
          }
        }
      }
    }

    // 绘制起点到终点的白色引导线
    ctx.globalAlpha = 0.9;
    ctx.strokeStyle = '#ffffff';
    ctx.lineWidth = 2;
    ctx.beginPath();
    ctx.moveTo(lineStart.gridX * tSize + tSize/2, lineStart.gridY * tSize + tSize/2);
    ctx.lineTo(lineEnd.gridX * tSize + tSize/2, lineEnd.gridY * tSize + tSize/2);
    ctx.stroke();

    ctx.globalAlpha = 1.0;
  }

  ctx.restore();
};

const applyBrushToPoint = (gridX, gridY) => {
  const radius = Math.floor(brushSize.value / 2);
  const targetTile = currentTool.value === 0 ? 5 : currentTool.value;
  let modified = false;

  for (let di = -radius; di <= radius; di++) {
    for (let dj = -radius; dj <= radius; dj++) {
      const ni = gridX + di;
      const nj = gridY + dj;
      if (ni >= 0 && ni < SIZE && nj >= 0 && nj < SIZE) {
        if (grid.value[ni][nj] !== targetTile) {
          grid.value[ni][nj] = targetTile;
          modified = true;
        }
      }
    }
  }
  return modified;
};

const applyBrush = (gridX, gridY) => {
  if (applyBrushToPoint(gridX, gridY)) {
    render();
  }
};

const applyLine = (x0, y0, x1, y1) => {
  const points = getBresenhamPoints(x0, y0, x1, y1);
  let modified = false;
  for (const pt of points) {
    if (applyBrushToPoint(pt.x, pt.y)) {
      modified = true;
    }
  }
  if (modified) {
    render();
  }
};

const getGridCoordsFromMouse = (e) => {
  const rect = canvasEl.value.getBoundingClientRect();
  const mouseX = e.clientX - rect.left;
  const mouseY = e.clientY - rect.top;

  const worldX = (mouseX - offsetX.value) / scale.value;
  const worldY = (mouseY - offsetY.value) / scale.value;

  const gridX = Math.floor(worldX / tileSize);
  const gridY = Math.floor(worldY / tileSize);

  return { gridX, gridY };
};

const onMouseDown = (e) => {
  if (e.button === 0) { // 左键
    const { gridX, gridY } = getGridCoordsFromMouse(e);
    if (gridX >= 0 && gridX < SIZE && gridY >= 0 && gridY < SIZE) {
      pushHistory(); // 记录快照以支持撤销
      if (drawMode.value === 'line') {
        isLineDrawing.value = true;
        lineStart.gridX = gridX;
        lineStart.gridY = gridY;
        lineEnd.gridX = gridX;
        lineEnd.gridY = gridY;
        render();
      } else {
        isDrawing.value = true;
        applyBrush(gridX, gridY);
      }
    }
  } else if (e.button === 1 || e.button === 2) { // 中键或右键平移
    isPanning.value = true;
    lastMouse.x = e.clientX;
    lastMouse.y = e.clientY;
  }
};

const onMouseMove = (e) => {
  const { gridX, gridY } = getGridCoordsFromMouse(e);
  hoverCoord.x = gridX + GRID_MIN;
  hoverCoord.y = gridY + GRID_MIN;

  if (drawMode.value === 'line' && isLineDrawing.value) {
    lineEnd.gridX = Math.max(0, Math.min(SIZE - 1, gridX));
    lineEnd.gridY = Math.max(0, Math.min(SIZE - 1, gridY));
    render();
  } else if (drawMode.value === 'brush' && isDrawing.value) {
    if (gridX >= 0 && gridX < SIZE && gridY >= 0 && gridY < SIZE) {
      applyBrush(gridX, gridY);
    }
  } else if (isPanning.value) {
    const dx = e.clientX - lastMouse.x;
    const dy = e.clientY - lastMouse.y;
    offsetX.value += dx;
    offsetY.value += dy;
    lastMouse.x = e.clientX;
    lastMouse.y = e.clientY;
    render();
  }
};

const onMouseUp = (e) => {
  if (drawMode.value === 'line' && isLineDrawing.value) {
    isLineDrawing.value = false;
    applyLine(lineStart.gridX, lineStart.gridY, lineEnd.gridX, lineEnd.gridY);
  }
  isDrawing.value = false;
  isPanning.value = false;
};

const onWheel = (e) => {
  e.preventDefault();
  const zoomFactor = e.deltaY < 0 ? 1.1 : 0.9;
  const newScale = Math.min(Math.max(scale.value * zoomFactor, 0.2), 3.0);
  
  // 保持鼠标指向点为缩放中心
  const rect = canvasEl.value.getBoundingClientRect();
  const mouseX = e.clientX - rect.left;
  const mouseY = e.clientY - rect.top;

  offsetX.value = mouseX - (mouseX - offsetX.value) * (newScale / scale.value);
  offsetY.value = mouseY - (mouseY - offsetY.value) * (newScale / scale.value);
  scale.value = newScale;

  render();
};

const onContextMenu = (e) => {
  e.preventDefault(); // 阻止默认右键菜单
};

onMounted(() => {
  fetchSavedMaps();
  initCanvas();

  const canvas = canvasEl.value;
  if (canvas) {
    canvas.addEventListener('mousedown', onMouseDown);
    window.addEventListener('mousemove', onMouseMove);
    window.addEventListener('mouseup', onMouseUp);
    canvas.addEventListener('wheel', onWheel, { passive: false });
    canvas.addEventListener('contextmenu', onContextMenu);
  }
  window.addEventListener('keydown', handleKeyDown);
  window.addEventListener('resize', initCanvas);
});

onUnmounted(() => {
  const canvas = canvasEl.value;
  if (canvas) {
    canvas.removeEventListener('mousedown', onMouseDown);
    window.removeEventListener('mousemove', onMouseMove);
    window.removeEventListener('mouseup', onMouseUp);
    canvas.removeEventListener('wheel', onWheel);
    canvas.removeEventListener('contextmenu', onContextMenu);
  }
  window.removeEventListener('keydown', handleKeyDown);
  window.removeEventListener('resize', initCanvas);
});
</script>

<style scoped>
.map-editor-container {
  display: flex;
  flex-direction: column;
  height: 100vh;
  width: 100vw;
  background: #0b0f19;
  color: #fff;
  overflow: hidden;
}

.editor-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 10px 20px;
  background: rgba(15, 23, 42, 0.85);
  border-bottom: 1px solid rgba(255, 255, 255, 0.1);
  z-index: 10;
  gap: 15px;
}

.header-left {
  display: flex;
  align-items: center;
  gap: 12px;
}

.header-left h2 {
  font-size: 1.1rem;
  margin: 0;
}

.btn-back {
  padding: 6px 12px;
  background: rgba(255, 255, 255, 0.1);
  border: 1px solid rgba(255, 255, 255, 0.2);
  border-radius: 6px;
  color: #fff;
  cursor: pointer;
  font-weight: 600;
  transition: all 0.2s;
}
.btn-back:hover {
  background: rgba(255, 255, 255, 0.2);
}

.header-center {
  display: flex;
  align-items: center;
  gap: 10px;
}

.btn-undo {
  background: rgba(245, 158, 11, 0.15);
  border: 1px solid rgba(245, 158, 11, 0.4);
  color: #fbbf24;
}
.btn-undo:hover:not(:disabled) {
  background: rgba(245, 158, 11, 0.3);
}
.btn-undo:disabled {
  opacity: 0.4;
  cursor: not-allowed;
  border-color: rgba(255, 255, 255, 0.1);
  color: #888;
}

.map-name-input {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
}

.map-name-input input {
  padding: 6px 10px;
  background: rgba(0, 0, 0, 0.4);
  border: 1px solid rgba(255, 255, 255, 0.2);
  border-radius: 6px;
  color: #fff;
  font-size: 13px;
  width: 180px;
}

.btn-action {
  padding: 6px 14px;
  border-radius: 6px;
  font-size: 13px;
  font-weight: bold;
  border: none;
  cursor: pointer;
  transition: all 0.2s;
}

.btn-save {
  background: #10b981;
  color: #fff;
}
.btn-save:hover:not(:disabled) {
  background: #059669;
}

.btn-new {
  background: rgba(59, 130, 246, 0.2);
  border: 1px solid rgba(59, 130, 246, 0.5);
  color: #93c5fd;
}

.header-right {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
}

.map-select {
  padding: 6px 10px;
  background: rgba(0, 0, 0, 0.4);
  border: 1px solid rgba(255, 255, 255, 0.2);
  border-radius: 6px;
  color: #fff;
  font-size: 13px;
}

.btn-delete {
  background: rgba(239, 68, 68, 0.2);
  border: 1px solid rgba(239, 68, 68, 0.4);
  color: #f87171;
}

/* 主工作区 */
.editor-workspace {
  display: flex;
  flex: 1;
  min-height: 0;
  position: relative;
  overflow: hidden;
}

.toolbox {
  width: 250px;
  display: flex;
  flex-direction: column;
  gap: 12px;
  padding: 14px;
  overflow-y: auto;
  border-right: 1px solid rgba(255, 255, 255, 0.1);
  background: rgba(15, 23, 42, 0.6);
}

.toolbox h3 {
  margin: 0;
  font-size: 1rem;
  padding-bottom: 6px;
  border-bottom: 1px solid rgba(255, 255, 255, 0.1);
}

.tool-group {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.group-title {
  font-size: 11px;
  color: #94a3b8;
  font-weight: bold;
  text-transform: uppercase;
}

.mode-selector {
  display: flex;
  gap: 6px;
}
.mode-btn {
  flex: 1;
  padding: 7px;
  background: rgba(255, 255, 255, 0.06);
  border: 1px solid rgba(255, 255, 255, 0.15);
  border-radius: 6px;
  color: #cbd5e1;
  font-size: 12px;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.2s;
}
.mode-btn.active {
  background: #3b82f6;
  border-color: #60a5fa;
  color: #fff;
}

.tool-btn {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 10px;
  background: rgba(255, 255, 255, 0.05);
  border: 1px solid transparent;
  border-radius: 6px;
  color: #e2e8f0;
  font-size: 12px;
  cursor: pointer;
  text-align: left;
  transition: all 0.2s;
}

.tool-btn:hover {
  background: rgba(255, 255, 255, 0.12);
}

.tool-btn.active {
  background: rgba(59, 130, 246, 0.25);
  border-color: #60a5fa;
  color: #93c5fd;
  font-weight: bold;
}

.tool-color {
  width: 14px;
  height: 14px;
  border-radius: 3px;
  display: inline-block;
}
.c-main { background: #ffffff; border: 1px solid #cbd5e1; }
.c-big { background: #94a3b8; }
.c-small { background: #475569; }
.c-com-high { background: #6b21a8; }
.c-com-low { background: #c084fc; }
.c-res-high { background: #15803d; }
.c-res-low { background: #86efac; border: 1px solid #22c55e; }
.c-eraser { background: #0f172a; border: 1px dashed #aaa; }

.size-selector {
  display: flex;
  gap: 6px;
}
.size-btn {
  flex: 1;
  padding: 6px;
  background: rgba(255, 255, 255, 0.05);
  border: 1px solid rgba(255, 255, 255, 0.1);
  border-radius: 4px;
  color: #fff;
  font-size: 12px;
  cursor: pointer;
}
.size-btn.active {
  background: #3b82f6;
  border-color: #60a5fa;
  font-weight: bold;
}

.btn-quick {
  padding: 8px;
  background: rgba(255, 255, 255, 0.05);
  border: 1px dashed rgba(255, 255, 255, 0.2);
  border-radius: 6px;
  color: #cbd5e1;
  font-size: 12px;
  cursor: pointer;
  text-align: center;
}
.btn-quick:hover {
  background: rgba(255, 255, 255, 0.1);
}

/* 画布区域 */
.canvas-wrapper {
  flex: 1;
  position: relative;
  overflow: hidden;
  background: #020617;
  cursor: crosshair;
}

canvas {
  width: 100%;
  height: 100%;
  display: block;
}

.canvas-hint {
  position: absolute;
  bottom: 12px;
  left: 14px;
  right: 14px;
  display: flex;
  justify-content: space-between;
  background: rgba(15, 23, 42, 0.85);
  padding: 6px 14px;
  border-radius: 20px;
  font-size: 12px;
  color: #94a3b8;
  pointer-events: none;
  border: 1px solid rgba(255, 255, 255, 0.1);
}
.cursor-coord {
  color: #38bdf8;
  font-family: monospace;
  font-weight: bold;
}

/* 右侧面板 */
.info-sidebar {
  width: 260px;
  padding: 14px;
  display: flex;
  flex-direction: column;
  gap: 14px;
  border-left: 1px solid rgba(255, 255, 255, 0.1);
  background: rgba(15, 23, 42, 0.6);
  overflow-y: auto;
}

.info-sidebar h3 {
  margin: 0;
  font-size: 1rem;
  padding-bottom: 6px;
  border-bottom: 1px solid rgba(255, 255, 255, 0.1);
}

.stat-card {
  display: flex;
  flex-direction: column;
  gap: 8px;
  background: rgba(0, 0, 0, 0.3);
  padding: 12px;
  border-radius: 8px;
  font-size: 12px;
}

.stat-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.val {
  font-family: monospace;
  font-weight: bold;
}

.text-warning { color: #facc15; }
.text-accent { color: #38bdf8; }
.text-success { color: #4ade80; }

.rules-tip {
  background: rgba(59, 130, 246, 0.1);
  border: 1px solid rgba(59, 130, 246, 0.3);
  padding: 12px;
  border-radius: 8px;
  font-size: 12px;
}
.rules-tip h4 {
  margin: 0 0 6px 0;
  color: #60a5fa;
  font-size: 13px;
}
.rules-tip ul {
  margin: 0;
  padding-left: 16px;
  color: #cbd5e1;
  display: flex;
  flex-direction: column;
  gap: 6px;
}
</style>
