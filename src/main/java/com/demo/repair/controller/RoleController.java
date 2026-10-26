package com.demo.repair.controller;

import com.demo.repair.common.RequireRole;
import com.demo.repair.common.Result;
import com.demo.repair.entity.SysRole;
import com.demo.repair.service.RoleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "角色管理")
@RestController
@RequestMapping("/api/roles")
@RequiredArgsConstructor
@RequireRole("ADMIN")
public class RoleController {

    private final RoleService roleService;

    @Operation(summary = "角色列表")
    @GetMapping("/list")
    public Result<List<SysRole>> list() {
        return Result.success(roleService.list());
    }
}
