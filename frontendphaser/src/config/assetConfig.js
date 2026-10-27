// 资源配置表与等轴测纹理管理 (预留资产库结构，当前优先使用纯净等轴测颜色格子)

/**
 * 当前渲染模式开关：
 * 'COLOR_BLOCK'   - 纯净高对比度等轴测颜色格子与立体积木 (当前主推模式，信息层级分明，专注 UI 功能)
 * 'ASSET_TEXTURE' - 真实等轴测 PNG 图片素材模式 (预留，随时可无缝切换)
 */
export const RENDER_MODE = 'COLOR_BLOCK';

/**
 * 纯净等轴测颜色格子与积木高保真调色板 (对齐现代拟态数字沙盘)
 */
export const ColorPalette = {
    TERRAIN: {
        TOWN: {
            top: 0x334155,        // slate-700
            sideLeft: 0x1e293b,
            sideRight: 0x0f172a,
            stroke: 0x64748b,
            label: '城',
            fullName: '城镇街区'
        },
        LAKE: {
            top: 0x0284c7,        // sky-600 (碧蓝水光)
            sideLeft: 0x0369a1,
            sideRight: 0x075985,
            stroke: 0x38bdf8,
            label: '湖',
            fullName: '湖区水面'
        },
        MOUNTAIN: {
            top: 0x78350f,        // amber-900 (岩石深褐)
            sideLeft: 0x451a03,
            sideRight: 0x270f03,
            stroke: 0xd97706,
            label: '山',
            fullName: '险峻山地'
        },
        PLAIN: {
            top: 0x15803d,        // emerald-700 (自然草甸绿)
            sideLeft: 0x166534,
            sideRight: 0x14532d,
            stroke: 0x4ade80,
            label: '原',
            fullName: '自然草甸'
        },
        BUILDABLE: {
            top: 0x0f172a,        // slate-900 (深色平整工程地基)
            sideLeft: 0x0b1120,
            sideRight: 0x070b14,
            stroke: 0x10b981,     // 亮绿边界发光
            label: '+',
            fullName: '可建空地'
        }
    },
    BUILDING: {
        CONSTRUCTION: {
            top: 0xf59e0b,        // 施工警戒暖橙
            sideLeft: 0xd97706,
            sideRight: 0xb45309,
            stroke: 0xfef08a,
            tag: '🚧 施工中'
        },
        // 住宿-基础系 (标准客房)
        ACCOMMODATION_BASE: {
            top: 0x4f46e5,        // indigo-600
            sideLeft: 0x4338ca,
            sideRight: 0x3730a3,
            stroke: 0xa5b4fc,
            badge: '基'
        },
        // 住宿-亲子系 (家庭亲子房 / 亲子套房)
        ACCOMMODATION_FAMILY: {
            top: 0xe11d48,        // rose-600
            sideLeft: 0xbe123c,
            sideRight: 0x9f1239,
            stroke: 0xfda4af,
            badge: '家'
        },
        // 住宿-商务系 (商务行政房 / 行政套房)
        ACCOMMODATION_BUSINESS: {
            top: 0x0d9488,        // teal-600
            sideLeft: 0x0f766e,
            sideRight: 0x115e59,
            stroke: 0x5eead4,
            badge: '商'
        },
        // 住宿-终极合并系 (亲子合院 / 商务行政馆)
        ACCOMMODATION_MERGE: {
            top: 0xb45309,        // amber-700
            sideLeft: 0x92400e,
            sideRight: 0x78350f,
            stroke: 0xfde047,
            badge: '合'
        },
        // 配套体验建筑 (风味餐厅 / 温泉泡池 / 观景台)
        SUPPORT: {
            top: 0x7c3aed,        // violet-600
            sideLeft: 0x6d28d9,
            sideRight: 0x5b21b6,
            stroke: 0xc4b5fd,
            badge: '配'
        }
    }
};

/**
 * 获取建筑对应的立体颜色积木主题
 * @param {object} form 形态对象
 * @param {boolean} isUnderConstruction 是否在建
 */
export function getBuildingColorTheme(form, isUnderConstruction) {
    if (isUnderConstruction) {
        return ColorPalette.BUILDING.CONSTRUCTION;
    }
    const cat = (form?.category || 'ACCOMMODATION').toUpperCase();
    if (cat === 'SUPPORT') {
        return ColorPalette.BUILDING.SUPPORT;
    }
    const line = (form?.line || '').toUpperCase();
    const id = (form?.id || '').toUpperCase();
    if (line === 'MERGE' || id.includes('MERGED')) {
        return ColorPalette.BUILDING.ACCOMMODATION_MERGE;
    }
    if (line === 'FAMILY' || id.includes('FAMILY')) {
        return ColorPalette.BUILDING.ACCOMMODATION_FAMILY;
    }
    if (line === 'BUSINESS' || id.includes('BUSINESS')) {
        return ColorPalette.BUILDING.ACCOMMODATION_BUSINESS;
    }
    return ColorPalette.BUILDING.ACCOMMODATION_BASE;
}

export const AssetKeys = {
    TILES: {
        BUILDABLE: 'kenney_tile_buildable',      // 可建造空地
        PLAIN: 'kenney_tile_plain',              // 平原
        PLAIN_FLOWER: 'kenney_tile_plain_flower',
        LAKE: 'kenney_tile_lake',                // 湖区
        LAKE_DEEP: 'kenney_tile_lake_deep',
        MOUNTAIN: 'kenney_tile_mountain',        // 山地
        MOUNTAIN_PEAK: 'kenney_tile_mountain_peak',
        TOWN: 'kenney_tile_town',                // 城镇 / 城区
        TOWN_STREET: 'kenney_tile_town_street',
        HOVER: 'iso_cursor_hover',               // 悬浮高亮选框
        SELECTED: 'iso_cursor_selected',         // 选中高亮选框
        BUILD_MARKER: 'iso_build_marker',        // 可建造空地 '+' 动态指示标记
    },
    BUILDINGS: {
        SCAFFOLD: 'kenney_bld_scaffold',         // 施工脚手架
        // Kenney City Kit (Suburban) 21 款等轴测郊区独栋/联排洋房/公馆建筑
        SUBURBAN_A: 'kenney_suburban_a',
        SUBURBAN_B: 'kenney_suburban_b',
        SUBURBAN_C: 'kenney_suburban_c',
        SUBURBAN_D: 'kenney_suburban_d',
        SUBURBAN_E: 'kenney_suburban_e',
        SUBURBAN_F: 'kenney_suburban_f',
        SUBURBAN_G: 'kenney_suburban_g',
        SUBURBAN_H: 'kenney_suburban_h',
        SUBURBAN_I: 'kenney_suburban_i',
        SUBURBAN_J: 'kenney_suburban_j',
        SUBURBAN_K: 'kenney_suburban_k',
        SUBURBAN_L: 'kenney_suburban_l',
        SUBURBAN_M: 'kenney_suburban_m',
        SUBURBAN_N: 'kenney_suburban_n',
        SUBURBAN_O: 'kenney_suburban_o',
        SUBURBAN_P: 'kenney_suburban_p',
        SUBURBAN_Q: 'kenney_suburban_q',
        SUBURBAN_R: 'kenney_suburban_r',
        SUBURBAN_S: 'kenney_suburban_s',
        SUBURBAN_T: 'kenney_suburban_t',
        SUBURBAN_U: 'kenney_suburban_u',
        STANDARD_ROOM: 'kenney_suburban_a',
    },
    PARTICLES: {
        SPARKLE: 'particle_sparkle',
    }
};

export const SUBURBAN_KEYS = ['a','b','c','d','e','f','g','h','i','j','k','l','m','n','o','p','q','r','s','t','u'];

/**
 * Kenney 静态素材文件相对路径列表 (存放于 public/assets/kenney/ 下)
 */
export const KenneyFilePaths = {
    [AssetKeys.TILES.BUILDABLE]: 'assets/kenney/tiles/buildable_soil.png',
    [AssetKeys.TILES.PLAIN]: 'assets/kenney/tiles/grass_plain.png',
    [AssetKeys.TILES.PLAIN_FLOWER]: 'assets/kenney/tiles/grass_plain_flower.png',
    [AssetKeys.TILES.LAKE]: 'assets/kenney/tiles/water_lake.png',
    [AssetKeys.TILES.LAKE_DEEP]: 'assets/kenney/tiles/water_deep.png',
    [AssetKeys.TILES.MOUNTAIN]: 'assets/kenney/tiles/mountain_rock.png',
    [AssetKeys.TILES.MOUNTAIN_PEAK]: 'assets/kenney/tiles/mountain_peak.png',
    [AssetKeys.TILES.TOWN]: 'assets/kenney/tiles/town_cobble.png',
    [AssetKeys.TILES.TOWN_STREET]: 'assets/kenney/tiles/town_street.png',
    
    [AssetKeys.BUILDINGS.SCAFFOLD]: 'assets/kenney/buildings/bld_scaffold.png',

    // 21 款 Suburban 郊区建筑立绘 (@2x.png 高保真等轴测立绘)
    ...Object.fromEntries(SUBURBAN_KEYS.map(k => [
        `kenney_suburban_${k}`,
        `assets/kenney/suburban/building-type-${k}@2x.png`
    ]))
};

/**
 * 根据后端地形枚举 (TOWN / PLAIN / MOUNTAIN / LAKE) 或可建状态匹配对应 Tile Key
 * @param {boolean} buildable 是否为可建造空地
 * @param {string} terrain 地形类型
 * @param {number} x 列
 * @param {number} y 行
 * @returns {string}
 */
export function getTerrainTextureKey(buildable, terrain, x = 0, y = 0) {
    if (buildable) {
        return AssetKeys.TILES.BUILDABLE;
    }
    const t = (terrain || 'PLAIN').toUpperCase();
    if (t.includes('TOWN') || t.includes('CITY')) {
        return (x + y) % 2 === 0 ? AssetKeys.TILES.TOWN : AssetKeys.TILES.TOWN_STREET;
    }
    if (t.includes('LAKE') || t.includes('WATER')) {
        return (x + y) % 2 === 0 ? AssetKeys.TILES.LAKE : AssetKeys.TILES.LAKE_DEEP;
    }
    if (t.includes('MOUNTAIN') || t.includes('ROCK') || t.includes('HILL')) {
        return (x + y) % 2 === 0 ? AssetKeys.TILES.MOUNTAIN : AssetKeys.TILES.MOUNTAIN_PEAK;
    }
    // 默认为平原
    return (x + y) % 2 === 0 ? AssetKeys.TILES.PLAIN : AssetKeys.TILES.PLAIN_FLOWER;
}

/**
 * 各建筑形态与各等级的 Suburban 模型专属映射池 (随机分布分配)
 */
const FORM_LEVEL_MAP = {
    STANDARD_ROOM: { 1: 'a', 2: 'c', 3: 'j' },
    FAMILY_ROOM: { 1: 'e', 2: 'd', 3: 'k' },
    FAMILY_SUITE: { 1: 'f', 2: 'h', 3: 'l' },
    BUSINESS_ROOM: { 1: 't', 2: 'b', 3: 'q' },
    BUSINESS_SUITE: { 1: 'i', 2: 'o', 3: 'r' },
    MERGED_FAMILY_VILLA: { 1: 'g', 2: 'm', 3: 's' },
    MERGED_BUSINESS_HALL: { 1: 'n', 2: 'p', 3: 'u' },
    RESTAURANT: { 1: 's', 2: 'u', 3: 'g' },
    HOT_SPRING_POOL: { 1: 'k', 2: 'b', 3: 'o' },
    VIEWING_PLATFORM: { 1: 'c', 2: 'd', 3: 'f' }
};

/**
 * 根据建筑形态 ID、星级及实例唯一标识确定性随机匹配 City Kit: Suburban 建筑立绘
 * @param {string} formId 形态代码
 * @param {number} level 星级
 * @param {string} buildingId 建筑实例 ID (用于多实例丰富度散列)
 * @returns {string}
 */
export function getBuildingTextureKey(formId, level = 1, buildingId = '') {
    const key = (formId || 'STANDARD_ROOM').toUpperCase();
    const cleanLevel = Math.max(1, Math.min(3, Number(level) || 1));

    // 优先从设计映射池中分配
    if (FORM_LEVEL_MAP[key]?.[cleanLevel]) {
        const typeChar = FORM_LEVEL_MAP[key][cleanLevel];
        return `kenney_suburban_${typeChar}`;
    }

    // 确定性随机散列兜底：任意未定义形态或等级均离散随机分配在 21 款建筑中
    let hash = 0;
    const str = `${key}_lv${cleanLevel}_${buildingId}`;
    for (let i = 0; i < str.length; i++) {
        hash = (hash * 31 + str.charCodeAt(i)) & 0xffffffff;
    }
    const idx = Math.abs(hash) % SUBURBAN_KEYS.length;
    return `kenney_suburban_${SUBURBAN_KEYS[idx]}`;
}
