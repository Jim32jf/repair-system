package com.demo.repair.controller;

import com.demo.repair.common.PageResult;
import com.demo.repair.common.RequireRole;
import com.demo.repair.common.Result;
import com.demo.repair.dto.EvaluationDTO;
import com.demo.repair.dto.OrderAssignDTO;
import com.demo.repair.dto.OrderCreateDTO;
import com.demo.repair.dto.OrderQueryDTO;
import com.demo.repair.service.OrderService;
import com.demo.repair.vo.OrderDetailVO;
import com.demo.repair.vo.OrderVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;

@Tag(name = "报修工单")
@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    @Operation(summary = "提交报修（学生）")
    @PostMapping
    @RequireRole("STUDENT")
    public Result<Void> create(@RequestBody @Valid OrderCreateDTO dto) {
        orderService.create(dto);
        return Result.success();
    }

    @Operation(summary = "工单分页（按角色自动过滤）")
    @GetMapping("/page")
    public Result<PageResult<OrderVO>> page(OrderQueryDTO query) {
        return Result.success(orderService.page(query));
    }

    @Operation(summary = "工单详情（含流转记录和评价）")
    @GetMapping("/{id}")
    public Result<OrderDetailVO> detail(@PathVariable Long id) {
        return Result.success(orderService.detail(id));
    }

    @Operation(summary = "派单（管理员）")
    @PutMapping("/assign")
    @RequireRole("ADMIN")
    public Result<Void> assign(@RequestBody @Valid OrderAssignDTO dto) {
        orderService.assign(dto);
        return Result.success();
    }

    @Operation(summary = "开始处理（维修工）")
    @PutMapping("/{id}/start")
    @RequireRole("REPAIRER")
    public Result<Void> start(@PathVariable Long id) {
        orderService.start(id);
        return Result.success();
    }

    @Operation(summary = "完成工单（维修工）")
    @PutMapping("/{id}/finish")
    @RequireRole("REPAIRER")
    public Result<Void> finish(@PathVariable Long id) {
        orderService.finish(id);
        return Result.success();
    }

    @Operation(summary = "评价（学生）")
    @PostMapping("/evaluate")
    @RequireRole("STUDENT")
    public Result<Void> evaluate(@RequestBody @Valid EvaluationDTO dto) {
        orderService.evaluate(dto);
        return Result.success();
    }
}
