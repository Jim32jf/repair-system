package com.demo.repair.controller;

import com.demo.repair.common.LoginUser;
import com.demo.repair.common.Result;
import com.demo.repair.common.UserContext;
import com.demo.repair.dto.LoginDTO;
import com.demo.repair.service.AuthService;
import com.demo.repair.vo.LoginVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;

@Tag(name = "认证")
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @Operation(summary = "登录")
    @PostMapping("/login")
    public Result<LoginVO> login(@RequestBody @Valid LoginDTO dto) {
        return Result.success(authService.login(dto));
    }

    @Operation(summary = "当前登录用户")
    @GetMapping("/me")
    public Result<LoginVO> me() {
        LoginUser user = UserContext.get();
        LoginVO vo = new LoginVO();
        vo.setUserId(user.getUserId());
        vo.setUsername(user.getUsername());
        vo.setRoleCode(user.getRoleCode());
        return Result.success(vo);
    }
}
