// 预加载场景：加载等轴测指示符与运行环境 (预留外部素材加载)
import { AssetKeys, RENDER_MODE, KenneyFilePaths } from '../config/assetConfig.js';
import { ISO_TILE_WIDTH, ISO_TILE_HEIGHT } from '../utils/isoUtils.js';

export default class BootScene extends Phaser.Scene {
    constructor() {
        super({ key: 'BootScene' });
    }

    preload() {
        const { width, height } = this.scale;
        
        // 渲染加载中文字
        this.add.text(width / 2, height / 2, '正在构筑等轴测度假村...', {
            fontSize: '18px',
            color: '#e2e8f0',
            fontFamily: 'system-ui, -apple-system, sans-serif'
        }).setOrigin(0.5);

        // 预留模式：若开启真实图片素材模式且配置了文件路径，才触发网络加载
        if (RENDER_MODE === 'ASSET_TEXTURE' && typeof KenneyFilePaths === 'object') {
            for (const [key, path] of Object.entries(KenneyFilePaths)) {
                this.load.image(key, path);
            }
        }
    }

    create() {
        const W = ISO_TILE_WIDTH;
        const H = ISO_TILE_HEIGHT;
        const halfW = W / 2;
        const halfH = H / 2;

        // 生成悬浮菱形线框
        if (!this.textures.exists(AssetKeys.TILES.HOVER)) {
            const canvas = this.textures.createCanvas(AssetKeys.TILES.HOVER, W, H);
            const ctx = canvas.getContext();
            ctx.beginPath();
            ctx.moveTo(halfW, 2);
            ctx.lineTo(W - 2, halfH);
            ctx.lineTo(halfW, H - 2);
            ctx.lineTo(2, halfH);
            ctx.closePath();
            ctx.fillStyle = 'rgba(56, 189, 248, 0.25)';
            ctx.fill();
            ctx.strokeStyle = '#38bdf8';
            ctx.lineWidth = 2.5;
            ctx.stroke();
            canvas.refresh();
        }

        // 生成选定菱形线框
        if (!this.textures.exists(AssetKeys.TILES.SELECTED)) {
            const canvas = this.textures.createCanvas(AssetKeys.TILES.SELECTED, W, H);
            const ctx = canvas.getContext();
            ctx.beginPath();
            ctx.moveTo(halfW, 2);
            ctx.lineTo(W - 2, halfH);
            ctx.lineTo(halfW, H - 2);
            ctx.lineTo(2, halfH);
            ctx.closePath();
            ctx.fillStyle = 'rgba(251, 191, 36, 0.35)';
            ctx.fill();
            ctx.strokeStyle = '#fbbf24';
            ctx.lineWidth = 3;
            ctx.stroke();
            canvas.refresh();
        }

        // 生成可建造指示牌 (+) 贴图
        if (!this.textures.exists(AssetKeys.TILES.BUILD_MARKER)) {
            const canvas = this.textures.createCanvas(AssetKeys.TILES.BUILD_MARKER, 48, 48);
            const ctx = canvas.getContext();
            ctx.fillStyle = 'rgba(15, 23, 42, 0.88)';
            ctx.beginPath();
            ctx.arc(24, 24, 18, 0, Math.PI * 2);
            ctx.fill();
            ctx.strokeStyle = '#f59e0b';
            ctx.lineWidth = 2.5;
            ctx.stroke();
            ctx.strokeStyle = '#fde047';
            ctx.lineWidth = 3.5;
            ctx.lineCap = 'round';
            // 十字 +
            ctx.beginPath();
            ctx.moveTo(15, 24);
            ctx.lineTo(33, 24);
            ctx.stroke();
            ctx.beginPath();
            ctx.moveTo(24, 15);
            ctx.lineTo(24, 33);
            ctx.stroke();
            canvas.refresh();
        }

        // 闪星粒子
        if (!this.textures.exists(AssetKeys.PARTICLES.SPARKLE)) {
            const canvas = this.textures.createCanvas(AssetKeys.PARTICLES.SPARKLE, 16, 16);
            const ctx = canvas.getContext();
            ctx.fillStyle = '#fde047';
            ctx.beginPath();
            ctx.arc(8, 8, 5, 0, Math.PI * 2);
            ctx.fill();
            canvas.refresh();
        }

        // 进入主场景
        this.scene.start('MainScene');
    }
}
