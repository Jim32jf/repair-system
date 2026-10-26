package com.demo.repair.service;

import com.demo.repair.dto.CategoryDTO;
import com.demo.repair.entity.DeviceCategory;

import java.util.List;

public interface CategoryService {

    List<DeviceCategory> list();

    void create(CategoryDTO dto);

    void update(CategoryDTO dto);

    void delete(Long id);
}
