package com.demo.repair.controller;

import com.demo.repair.common.RequireRole;
import com.demo.repair.common.Result;
import com.demo.repair.dto.CategoryDTO;
import com.demo.repair.entity.DeviceCategory;
import com.demo.repair.service.CategoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;
import java.util.List;

@Tag(name = "设备分类")
@RestController
@RequestMapping("/api/categories")
@RequiredArgsConstructor
public class CategoryController {

    private final CategoryService categoryService;

    @Operation(summary = "分类列表")
    @GetMapping("/list")
    public Result<List<DeviceCategory>> list() {
        return Result.success(categoryService.list());
    }

    @Operation(summary = "新增分类")
    @PostMapping
    @RequireRole("ADMIN")
    public Result<Void> create(@RequestBody @Valid CategoryDTO dto) {
        categoryService.create(dto);
        return Result.success();
    }

    @Operation(summary = "修改分类")
    @PutMapping
    @RequireRole("ADMIN")
    public Result<Void> update(@RequestBody @Valid CategoryDTO dto) {
        categoryService.update(dto);
        return Result.success();
    }

    @Operation(summary = "删除分类")
    @DeleteMapping("/{id}")
    @RequireRole("ADMIN")
    public Result<Void> delete(@PathVariable Long id) {
        categoryService.delete(id);
        return Result.success();
    }
}
