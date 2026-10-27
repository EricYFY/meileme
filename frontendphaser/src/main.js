// Phaser 3 独立前端入口文件
import Phaser from 'phaser';
import { phaserConfig } from './config/gameConfig.js';
import UIManager from './ui/uiManager.js';
import './ui/styles/gameUi.css';

// 启动 Phaser 3 游戏实例
const game = new Phaser.Game(phaserConfig);

// 启动 UI 覆盖层
const overlayEl = document.getElementById('ui-overlay');
const uiManager = new UIManager(overlayEl);

console.log('🎮 美了么·等轴测度假村 Phaser 前端已就绪！');
