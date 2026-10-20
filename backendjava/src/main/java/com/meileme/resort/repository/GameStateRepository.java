package com.meileme.resort.repository;

import com.meileme.resort.model.GameState;
import org.springframework.stereotype.Repository;

/**
 * 内存态游戏仓储：单例持有当前局的 {@link GameState}。
 *
 * <p>本期不引入独立数据库：静态配置走配置类、运行时状态走内存、逐日日志内存环形保留 ≤365 天；
 * 每次开局会整体清空上一局残留，避免跨局数据污染。</p>
 */
@Repository
public class GameStateRepository {

    private volatile GameState state;

    /** 保存当前局状态 */
    public void save(GameState state) {
        this.state = state;
    }

    /** 取当前局状态（未开局返回 null） */
    public GameState get() {
        return state;
    }

    /** 是否存在对局 */
    public boolean exists() {
        return state != null;
    }

    /** 清空当前局 */
    public void clear() {
        this.state = null;
    }
}
