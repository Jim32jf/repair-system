package com.demo.repair.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "健康检查")
@RestController
@RequestMapping("/api")
public class PingController {

    @Operation(summary = "服务探活")
    @GetMapping("/ping")
    public String ping() {
        return "pong";
    }
}
