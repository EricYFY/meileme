export default class CanvasEngine {
    constructor(canvasElement) {
        this.canvas = canvasElement;
        this.ctx = this.canvas.getContext('2d', { alpha: false });
        
        // 视图状态
        this.scale = 1.0;
        this.offsetX = 0;
        this.offsetY = 0;
        this.isDragging = false;
        this.dragMoved = false; // 区分拖拽和点击
        this.lastMouse = { x: 0, y: 0 };
        
        // 数据
        this.mapData = null;
        this.tileSize = 20;
        
        this.riders = new Map();
        this.orders = new Map();
        
        // 选中高亮状态
        this.selectedOrderId = null;
        this.onOrderSelected = null; // 回调：通知 Vue 层
        
        this._bindEvents();
        
        this.running = true;
        this._renderLoop();
    }

    _bindEvents() {
        this.canvas.addEventListener('mousedown', (e) => {
            this.isDragging = true;
            this.dragMoved = false;
            this.lastMouse = { x: e.clientX, y: e.clientY };
            this.canvas.style.cursor = 'grabbing';
        });

        this.canvas.addEventListener('mousemove', (e) => {
            if (!this.isDragging) return;
            const dx = e.clientX - this.lastMouse.x;
            const dy = e.clientY - this.lastMouse.y;
            if (Math.abs(dx) > 2 || Math.abs(dy) > 2) this.dragMoved = true;
            this.offsetX += dx;
            this.offsetY += dy;
            this.lastMouse = { x: e.clientX, y: e.clientY };
        });

        this.canvas.addEventListener('mouseup', (e) => {
            this.isDragging = false;
            this.canvas.style.cursor = 'grab';
            
            // 如果没有拖拽过，视为点击事件
            if (!this.dragMoved) {
                this._handleClick(e);
            }
        });

        this.canvas.addEventListener('mouseleave', () => {
            this.isDragging = false;
            this.canvas.style.cursor = 'grab';
        });

        this.canvas.addEventListener('wheel', (e) => {
            e.preventDefault();
            const zoomAmount = e.deltaY > 0 ? 0.9 : 1.1;
            
            const rect = this.canvas.getBoundingClientRect();
            const mouseX = e.clientX - rect.left;
            const mouseY = e.clientY - rect.top;
            
            const worldX = (mouseX - this.offsetX) / this.scale;
            const worldY = (mouseY - this.offsetY) / this.scale;
            
            this.scale *= zoomAmount;
            this.scale = Math.max(0.1, Math.min(this.scale, 5.0));
            
            this.offsetX = mouseX - worldX * this.scale;
            this.offsetY = mouseY - worldY * this.scale;
        });
    }

    _handleClick(e) {
        if (!this.mapData) return;
        
        const rect = this.canvas.getBoundingClientRect();
        const canvasX = e.clientX - rect.left;
        const canvasY = e.clientY - rect.top;
        
        // 屏幕坐标 → 世界坐标 (格子坐标)
        const worldX = (canvasX - this.offsetX) / this.scale;
        const worldY = (canvasY - this.offsetY) / this.scale;
        const gridX = Math.floor(worldX / this.tileSize) + this.mapData.min;
        const gridY = Math.floor(worldY / this.tileSize) + this.mapData.min;
        
        // 检查是否点中了某个订单的取餐点或送餐点
        let clickedOrderId = null;
        this.orders.forEach((order, id) => {
            const px = Math.round(order.pickupLocation.x);
            const py = Math.round(order.pickupLocation.y);
            const dx = Math.round(order.deliveryLocation.x);
            const dy = Math.round(order.deliveryLocation.y);
            
            if ((Math.abs(gridX - px) <= 1 && Math.abs(gridY - py) <= 1) ||
                (Math.abs(gridX - dx) <= 1 && Math.abs(gridY - dy) <= 1)) {
                clickedOrderId = id;
            }
        });
        
        this.selectedOrderId = clickedOrderId;
        if (this.onOrderSelected) {
            this.onOrderSelected(clickedOrderId);
        }
    }

    // 外部调用：从侧边栏点击订单时选中
    selectOrder(orderId) {
        this.selectedOrderId = orderId;
    }

    setMap(mapData) {
        this.mapData = mapData;
        if (!mapData) {
            this.clear();
            return;
        }
        const totalSize = (mapData.max - mapData.min + 1) * this.tileSize;
        this.offsetX = (this.canvas.width - totalSize * this.scale) / 2;
        this.offsetY = (this.canvas.height - totalSize * this.scale) / 2;
    }

    clear() {
        this.mapData = null;
        this.riders.clear();
        this.orders.clear();
        this.selectedOrderId = null;
    }

    updateRiders(ridersList) {
        ridersList.forEach(r => {
            if (this.riders.has(r.id)) {
                const existing = this.riders.get(r.id);
                existing.target = r.currentPosition;
                existing.status = r.status;
                existing.currentOrderId = r.currentOrderId;
            } else {
                this.riders.set(r.id, {
                    id: r.id,
                    current: { ...r.currentPosition },
                    target: { ...r.currentPosition },
                    status: r.status,
                    currentOrderId: r.currentOrderId
                });
            }
        });
    }

    addOrder(order) {
        this.orders.set(order.id, order);
    }
    
    updateOrderStatus(orderId, status) {
        if (this.orders.has(orderId)) {
            this.orders.get(orderId).status = status;
        }
    }

    removeOrder(orderId) {
        this.orders.delete(orderId);
        if (this.selectedOrderId === orderId) {
            this.selectedOrderId = null;
        }
    }

    resize(width, height) {
        this.canvas.width = width;
        this.canvas.height = height;
    }

    destroy() {
        this.running = false;
    }

    _getColor(tileType) {
        switch(tileType) {
            case 0: return '#0f172a';
            case 1: return '#ffffff'; // 主干道 (白色)
            case 2: return '#94a3b8'; // 大路 (灰色)
            case 3: return '#475569'; // 小路 (深灰色)
            case 4: return '#15803d'; // 高密度住宅 (深绿)
            case 5: return '#86efac'; // 低密度住宅 (浅绿)
            case 6: return '#6b21a8'; // 高密度商业 (深紫)
            case 7: return '#c084fc'; // 低密度商业 (浅紫)
            default: return '#000000';
        }
    }

    _renderLoop() {
        if (!this.running) return;
        
        // 1. Lerp 插值
        this.riders.forEach(r => {
            r.current.x += (r.target.x - r.current.x) * 0.3;
            r.current.y += (r.target.y - r.current.y) * 0.3;
        });

        // 2. 清空背景
        this.ctx.fillStyle = '#0f172a';
        this.ctx.fillRect(0, 0, this.canvas.width, this.canvas.height);

        this.ctx.save();
        this.ctx.translate(this.offsetX, this.offsetY);
        this.ctx.scale(this.scale, this.scale);

        // 3. 绘制地图瓦片
        if (this.mapData) {
            const grid = this.mapData.grid;
            const size = grid.length;
            
            for (let i = 0; i < size; i++) {
                for (let j = 0; j < size; j++) {
                    const tile = grid[i][j];
                    if (tile === 0) continue;
                    
                    this.ctx.fillStyle = this._getColor(tile);
                    this.ctx.fillRect(i * this.tileSize, j * this.tileSize, this.tileSize - 1, this.tileSize - 1);
                }
            }

            // 商家标记 (橘色圆点)
            this.mapData.merchants.forEach(m => {
                const px = (m.x - this.mapData.min) * this.tileSize;
                const py = (m.y - this.mapData.min) * this.tileSize;
                
                this.ctx.fillStyle = '#f97316';
                this.ctx.shadowBlur = 10;
                this.ctx.shadowColor = '#f97316';
                this.ctx.beginPath();
                this.ctx.arc(px + this.tileSize/2, py + this.tileSize/2, this.tileSize * 0.5, 0, Math.PI * 2);
                this.ctx.fill();
                this.ctx.shadowBlur = 0;
            });
        }

        // 4. 绘制选中订单的高亮连线和区域
        if (this.selectedOrderId && this.mapData) {
            const order = this.orders.get(this.selectedOrderId);
            if (order) {
                const min = this.mapData.min;
                const pickupPx = (order.pickupLocation.x - min) * this.tileSize + this.tileSize/2;
                const pickupPy = (order.pickupLocation.y - min) * this.tileSize + this.tileSize/2;
                const deliveryPx = (order.deliveryLocation.x - min) * this.tileSize + this.tileSize/2;
                const deliveryPy = (order.deliveryLocation.y - min) * this.tileSize + this.tileSize/2;
                
                // 取餐点高亮圈 (橘色圈)
                this.ctx.strokeStyle = '#f97316';
                this.ctx.lineWidth = 3;
                this.ctx.shadowBlur = 20;
                this.ctx.shadowColor = '#f97316';
                this.ctx.beginPath();
                this.ctx.arc(pickupPx, pickupPy, this.tileSize * 1.5, 0, Math.PI * 2);
                this.ctx.stroke();
                
                // 取餐点标签
                this.ctx.fillStyle = '#f97316';
                this.ctx.font = `bold ${this.tileSize * 0.7}px Inter, sans-serif`;
                this.ctx.fillText('取餐', pickupPx - this.tileSize * 0.7, pickupPy - this.tileSize * 1.8);
                
                // 送餐点高亮圈 (深绿圈)
                this.ctx.strokeStyle = '#15803d';
                this.ctx.shadowColor = '#15803d';
                this.ctx.beginPath();
                this.ctx.arc(deliveryPx, deliveryPy, this.tileSize * 1.5, 0, Math.PI * 2);
                this.ctx.stroke();
                
                // 送餐点标签
                this.ctx.fillStyle = '#15803d';
                this.ctx.fillText('送达', deliveryPx - this.tileSize * 0.7, deliveryPy - this.tileSize * 1.8);
                
                // 连线 (虚线)
                this.ctx.setLineDash([8, 4]);
                this.ctx.strokeStyle = 'rgba(255, 255, 255, 0.5)';
                this.ctx.lineWidth = 2;
                this.ctx.shadowBlur = 0;
                this.ctx.beginPath();
                this.ctx.moveTo(pickupPx, pickupPy);
                this.ctx.lineTo(deliveryPx, deliveryPy);
                this.ctx.stroke();
                this.ctx.setLineDash([]);
                
                // 找到负责这个订单的骑手并高亮
                this.riders.forEach(r => {
                    if (r.currentOrderId === this.selectedOrderId) {
                        const riderPx = (r.current.x - min) * this.tileSize + this.tileSize/2;
                        const riderPy = (r.current.y - min) * this.tileSize + this.tileSize/2;
                        
                        // 骑手高亮圈
                        this.ctx.strokeStyle = r.status === 1 ? '#38bdf8' : '#1d4ed8';
                        this.ctx.lineWidth = 3;
                        this.ctx.shadowBlur = 25;
                        this.ctx.shadowColor = this.ctx.strokeStyle;
                        this.ctx.beginPath();
                        this.ctx.arc(riderPx, riderPy, this.tileSize * 1.8, 0, Math.PI * 2);
                        this.ctx.stroke();
                        
                        this.ctx.fillStyle = '#ffffff';
                        this.ctx.fillText('骑手', riderPx - this.tileSize * 0.7, riderPy - this.tileSize * 2.2);
                        
                        this.ctx.shadowBlur = 0;
                        
                        // 骑手到取餐/送餐点的连线
                        this.ctx.setLineDash([4, 4]);
                        this.ctx.strokeStyle = 'rgba(255, 255, 255, 0.6)';
                        this.ctx.lineWidth = 1.5;
                        this.ctx.beginPath();
                        this.ctx.moveTo(riderPx, riderPy);
                        if (order.status <= 1) {
                            this.ctx.lineTo(pickupPx, pickupPy);
                        } else {
                            this.ctx.lineTo(deliveryPx, deliveryPy);
                        }
                        this.ctx.stroke();
                        this.ctx.setLineDash([]);
                    }
                });
            }
        }

        // 5. 绘制所有订单
        if (this.mapData) {
            const min = this.mapData.min;
            this.orders.forEach((order, id) => {
                // 取餐点标记 (新订单: 红色；取餐中: 浅蓝色)
                if (order.status <= 1) {
                    const px = (order.pickupLocation.x - min) * this.tileSize;
                    const py = (order.pickupLocation.y - min) * this.tileSize;
                    
                    const isSelected = id === this.selectedOrderId;
                    this.ctx.fillStyle = order.status === 0 ? '#ef4444' : '#38bdf8';
                    
                    if (isSelected) {
                        this.ctx.shadowBlur = 20;
                        this.ctx.shadowColor = this.ctx.fillStyle;
                    } else {
                        // 闪烁效果
                        const alpha = Math.sin(Date.now() / 200) * 0.3 + 0.7;
                        this.ctx.globalAlpha = alpha;
                        this.ctx.shadowBlur = 8;
                        this.ctx.shadowColor = this.ctx.fillStyle;
                    }
                    
                    this.ctx.fillRect(px + 3, py + 3, this.tileSize - 6, this.tileSize - 6);
                    this.ctx.globalAlpha = 1.0;
                    this.ctx.shadowBlur = 0;
                }
                
                // 送餐点标记 (配送中: 深蓝色)
                if (order.status === 2) {
                    const dx = (order.deliveryLocation.x - min) * this.tileSize;
                    const dy = (order.deliveryLocation.y - min) * this.tileSize;
                    
                    this.ctx.fillStyle = '#1d4ed8';
                    this.ctx.shadowBlur = 6;
                    this.ctx.shadowColor = '#1d4ed8';
                    this.ctx.fillRect(dx + 4, dy + 4, this.tileSize - 8, this.tileSize - 8);
                    this.ctx.shadowBlur = 0;
                }
            });
        }

        // 6. 绘制骑手 (空闲: 绿色，接单中: 浅蓝，配送中: 深蓝)
        if (this.mapData) {
            const min = this.mapData.min;
            this.riders.forEach(r => {
                const px = (r.current.x - min) * this.tileSize + this.tileSize/2;
                const py = (r.current.y - min) * this.tileSize + this.tileSize/2;
                
                let color = '#22c55e'; // 空闲 (绿色)
                if (r.status === 1) color = '#38bdf8'; // 接单中/取餐中 (浅蓝色)
                if (r.status === 2) color = '#1d4ed8'; // 配送中 (深蓝色)

                this.ctx.fillStyle = color;
                this.ctx.shadowBlur = 15;
                this.ctx.shadowColor = color;
                
                this.ctx.beginPath();
                this.ctx.arc(px, py, this.tileSize * 0.6, 0, Math.PI * 2);
                this.ctx.fill();
                
                this.ctx.shadowBlur = 0;
            });
        }

        this.ctx.restore();

        requestAnimationFrame(() => this._renderLoop());
    }
}
