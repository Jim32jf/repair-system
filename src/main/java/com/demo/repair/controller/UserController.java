package com.demo.repair.controller;

import com.demo.repair.common.PageResult;
import com.demo.repair.common.RequireRole;
import com.demo.repair.common.Result;
import com.demo.repair.dto.UserCreateDTO;
import com.demo.repair.dto.UserQueryDTO;
import com.demo.repair.dto.UserUpdateDTO;
import com.demo.repair.service.UserService;
import com.demo.repair.vo.UserVO;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;
import java.util.List;

@Tag(name = "用户管理")
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
@RequireRole("ADMIN")
public class UserController {

    private final UserService userService;

    @Operation(summary = "分页查询用户")
    @GetMapping("/page")
    public Result<PageResult<UserVO>> page(UserQueryDTO query) {
        return Result.success(userService.page(query));
    }

    @Operation(summary = "新增用户")
    @PostMapping
    public Result<Void> create(@RequestBody @Valid UserCreateDTO dto) {
        userService.create(dto);
        return Result.success();
    }

    @Operation(summary = "修改用户")
    @PutMapping
    public Result<Void> update(@RequestBody @Valid UserUpdateDTO dto) {
        userService.update(dto);
        return Result.success();
    }

    @Operation(summary = "删除用户")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        userService.delete(id);
        return Result.success();
    }

    @Operation(summary = "启用/禁用用户")
    @PutMapping("/{id}/status")
    public Result<Void> updateStatus(@PathVariable Long id, @RequestParam Integer status) {
        userService.updateStatus(id, status);
        return Result.success();
    }

    @Operation(summary = "重置密码为 123456")
    @PutMapping("/{id}/reset-password")
    public Result<Void> resetPassword(@PathVariable Long id) {
        userService.resetPassword(id);
        return Result.success();
    }

    @Operation(summary = "维修工列表（派单用）")
    @GetMapping("/repairers")
    public Result<List<UserVO>> repairers() {
        return Result.success(userService.listRepairers());
    }
}
