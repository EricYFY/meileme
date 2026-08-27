package com.meileme.backend.repository;

import com.meileme.backend.model.Merchant;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 商家存储库接口
 * 当前 MVP 阶段使用内存存储 (模拟 Redis 或 MySQL)，后续可轻松替换为 Spring Data JPA 或 Redis Template。
 */
@Repository
public class MerchantRepository {
    private final List<Merchant> merchants = new ArrayList<>();

    public void save(Merchant merchant) {
        merchants.removeIf(m -> m.getId().equals(merchant.getId()));
        merchants.add(merchant);
    }

    public List<Merchant> findAll() {
        return new ArrayList<>(merchants);
    }

    public Optional<Merchant> findById(String id) {
        return merchants.stream().filter(m -> m.getId().equals(id)).findFirst();
    }
    
    public void clear() {
        merchants.clear();
    }
}
