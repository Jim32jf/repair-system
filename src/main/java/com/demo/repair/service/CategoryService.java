package com.demo.repair.service;

import com.demo.repair.dto.CategoryDTO;
import com.demo.repair.vo.CategoryVO;

import java.util.List;

public interface CategoryService {

    List<CategoryVO> list();

    void create(CategoryDTO dto);

    void update(CategoryDTO dto);

    void delete(Long id);
}
