package com.demo.repair.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.demo.repair.common.BusinessException;
import com.demo.repair.dto.CategoryDTO;
import com.demo.repair.entity.DeviceCategory;
import com.demo.repair.entity.RepairOrder;
import com.demo.repair.mapper.DeviceCategoryMapper;
import com.demo.repair.mapper.RepairOrderMapper;
import com.demo.repair.service.CategoryService;
import com.demo.repair.vo.CategoryVO;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class CategoryServiceImpl implements CategoryService {

    private static final String CACHE_KEY = "repair:category:list";

    private static final long CACHE_TTL_MINUTES = 60;

    private final DeviceCategoryMapper categoryMapper;

    private final RepairOrderMapper orderMapper;

    private final StringRedisTemplate redisTemplate;

    private final ObjectMapper objectMapper;

    @Override
    public List<CategoryVO> list() {
        // 1. 先查缓存；Redis 异常时降级到数据库，不影响业务
        try {
            String cached = redisTemplate.opsForValue().get(CACHE_KEY);
            if (StrUtil.isNotBlank(cached)) {
                return objectMapper.readValue(cached, new TypeReference<List<CategoryVO>>() {
                });
            }
        } catch (Exception e) {
            log.warn("读取分类缓存失败，降级查询数据库: {}", e.getMessage());
        }

        // 2. 缓存未命中，查询数据库并转换为 VO（不暴露 deleted 等内部字段）
        List<DeviceCategory> entities = categoryMapper.selectList(new LambdaQueryWrapper<DeviceCategory>()
                .orderByAsc(DeviceCategory::getSort)
                .orderByAsc(DeviceCategory::getId));
        List<CategoryVO> list = entities.stream().map(entity -> {
            CategoryVO vo = new CategoryVO();
            BeanUtil.copyProperties(entity, vo);
            return vo;
        }).collect(Collectors.toList());

        // 3. 回写缓存，设置 60 分钟过期
        try {
            redisTemplate.opsForValue().set(
                    CACHE_KEY,
                    objectMapper.writeValueAsString(list),
                    CACHE_TTL_MINUTES,
                    TimeUnit.MINUTES
            );
        } catch (Exception e) {
            log.warn("写入分类缓存失败: {}", e.getMessage());
        }
        return list;
    }

    @Override
    public void create(CategoryDTO dto) {
        DeviceCategory category = new DeviceCategory();
        category.setName(dto.getName());
        category.setSort(dto.getSort() == null ? 0 : dto.getSort());
        categoryMapper.insert(category);
        clearCache();
    }

    @Override
    public void update(CategoryDTO dto) {
        if (dto.getId() == null) {
            throw new BusinessException("分类ID不能为空");
        }
        if (categoryMapper.selectById(dto.getId()) == null) {
            throw new BusinessException(404, "分类不存在");
        }

        DeviceCategory update = new DeviceCategory();
        update.setId(dto.getId());
        update.setName(dto.getName());
        update.setSort(dto.getSort());
        categoryMapper.updateById(update);
        clearCache();
    }

    @Override
    public void delete(Long id) {
        if (categoryMapper.selectById(id) == null) {
            throw new BusinessException(404, "分类不存在");
        }
        Long orderCount = orderMapper.selectCount(new LambdaQueryWrapper<RepairOrder>()
                .eq(RepairOrder::getCategoryId, id));
        if (orderCount > 0) {
            throw new BusinessException("该分类下存在工单，无法删除");
        }
        categoryMapper.deleteById(id);
        clearCache();
    }

    /**
     * 分类数据变更后删除缓存，下次查询自动回源重建
     */
    private void clearCache() {
        try {
            redisTemplate.delete(CACHE_KEY);
        } catch (Exception e) {
            log.warn("清除分类缓存失败: {}", e.getMessage());
        }
    }
}
