// Phaser 3 游戏主配置
import BootScene from '../scenes/BootScene.js';
import MainScene from '../scenes/MainScene.js';

export const phaserConfig = {
    type: Phaser.AUTO,
    parent: 'phaser-container',
    width: window.innerWidth,
    height: window.innerHeight,
    scale: {
        mode: Phaser.Scale.RESIZE,
        autoCenter: Phaser.Scale.CENTER_BOTH,
    },
    render: {
        pixelArt: false,
        antialias: true,
    },
    scene: [BootScene, MainScene],
};
