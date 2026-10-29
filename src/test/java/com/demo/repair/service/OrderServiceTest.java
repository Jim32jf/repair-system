package com.demo.repair.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.demo.repair.common.BusinessException;
import com.demo.repair.common.LoginUser;
import com.demo.repair.common.OrderStatus;
import com.demo.repair.common.UserContext;
import com.demo.repair.dto.EvaluationDTO;
import com.demo.repair.dto.OrderAssignDTO;
import com.demo.repair.dto.OrderCreateDTO;
import com.demo.repair.entity.OrderLog;
import com.demo.repair.entity.RepairOrder;
import com.demo.repair.entity.SysRole;
import com.demo.repair.entity.SysUser;
import com.demo.repair.mapper.DeviceCategoryMapper;
import com.demo.repair.mapper.EvaluationMapper;
import com.demo.repair.mapper.OrderLogMapper;
import com.demo.repair.mapper.RepairOrderMapper;
import com.demo.repair.mapper.SysRoleMapper;
import com.demo.repair.mapper.SysUserMapper;
import com.demo.repair.service.impl.OrderServiceImpl;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 工单状态机与权限校验单元测试（纯 Mockito，不依赖数据库）
 */
@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock
    private RepairOrderMapper orderMapper;

    @Mock
    private OrderLogMapper logMapper;

    @Mock
    private EvaluationMapper evaluationMapper;

    @Mock
    private SysUserMapper userMapper;

    @Mock
    private SysRoleMapper roleMapper;

    @Mock
    private DeviceCategoryMapper categoryMapper;

    @InjectMocks
    private OrderServiceImpl orderService;

    /**
     * LambdaUpdateWrapper 需要实体的 lambda 列缓存，脱离 Spring 容器时要手动预热，
     * 否则报 "can not find lambda cache"
     */
    @BeforeAll
    static void warmUpLambdaCache() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, RepairOrder.class);
    }

    @AfterEach
    void clearContext() {
        UserContext.clear();
    }

    private RepairOrder buildOrder(Long id, int status, Long studentId, Long repairerId) {
        RepairOrder order = new RepairOrder();
        order.setId(id);
        order.setStatus(status);
        order.setStudentId(studentId);
        order.setRepairerId(repairerId);
        return order;
    }

    @Test
    @DisplayName("提交报修：落库为待派单并记录流转日志")
    void createPersistsOrderAndLog() {
        UserContext.set(new LoginUser(2L, "student", "STUDENT"));

        OrderCreateDTO dto = new OrderCreateDTO();
        dto.setCategoryId(1L);
        dto.setDeviceName("笔记本");
        dto.setLocation("3号楼201");
        dto.setDescription("开不了机");

        orderService.create(dto);

        ArgumentCaptor<RepairOrder> captor = ArgumentCaptor.forClass(RepairOrder.class);
        verify(orderMapper).insert(captor.capture());
        RepairOrder saved = captor.getValue();
        assertEquals(OrderStatus.PENDING_ASSIGN, saved.getStatus());
        assertEquals(2L, saved.getStudentId());
        assertTrue(saved.getOrderNo().startsWith("RO"));
        verify(logMapper).insert(any(OrderLog.class));
    }

    @Test
    @DisplayName("派单成功：条件更新命中并写日志")
    void assignSuccess() {
        UserContext.set(new LoginUser(1L, "admin", "ADMIN"));
        when(orderMapper.selectById(10L)).thenReturn(buildOrder(10L, OrderStatus.PENDING_ASSIGN, 2L, null));

        SysUser repairer = new SysUser();
        repairer.setId(3L);
        repairer.setRoleId(2L);
        repairer.setRealName("李师傅");
        when(userMapper.selectById(3L)).thenReturn(repairer);

        SysRole role = new SysRole();
        role.setRoleCode("REPAIRER");
        when(roleMapper.selectById(2L)).thenReturn(role);

        when(orderMapper.update(isNull(), any())).thenReturn(1);

        OrderAssignDTO dto = new OrderAssignDTO();
        dto.setOrderId(10L);
        dto.setRepairerId(3L);
        orderService.assign(dto);

        verify(logMapper).insert(any(OrderLog.class));
    }

    @Test
    @DisplayName("派单被拒：非待派单状态直接抛业务异常，不执行更新")
    void assignRejectedWhenNotPending() {
        UserContext.set(new LoginUser(1L, "admin", "ADMIN"));
        when(orderMapper.selectById(10L)).thenReturn(buildOrder(10L, OrderStatus.ASSIGNED, 2L, 3L));

        OrderAssignDTO dto = new OrderAssignDTO();
        dto.setOrderId(10L);
        dto.setRepairerId(3L);

        BusinessException e = assertThrows(BusinessException.class, () -> orderService.assign(dto));
        assertEquals(400, e.getCode());
        verify(orderMapper, never()).update(any(), any());
    }

    @Test
    @DisplayName("派单并发冲突：条件更新0行时提示状态已变更")
    void assignConflictDetectedByConditionalUpdate() {
        UserContext.set(new LoginUser(1L, "admin", "ADMIN"));
        when(orderMapper.selectById(10L)).thenReturn(buildOrder(10L, OrderStatus.PENDING_ASSIGN, 2L, null));

        SysUser repairer = new SysUser();
        repairer.setId(3L);
        repairer.setRoleId(2L);
        repairer.setRealName("李师傅");
        when(userMapper.selectById(3L)).thenReturn(repairer);

        SysRole role = new SysRole();
        role.setRoleCode("REPAIRER");
        when(roleMapper.selectById(2L)).thenReturn(role);

        when(orderMapper.update(isNull(), any())).thenReturn(0);

        OrderAssignDTO dto = new OrderAssignDTO();
        dto.setOrderId(10L);
        dto.setRepairerId(3L);

        BusinessException e = assertThrows(BusinessException.class, () -> orderService.assign(dto));
        assertTrue(e.getMessage().contains("状态已变更"));
        verify(logMapper, never()).insert(any(OrderLog.class));
    }

    @Test
    @DisplayName("完成维修：非指派维修工操作被拒（403）")
    void finishRejectedForOtherRepairer() {
        UserContext.set(new LoginUser(99L, "other", "REPAIRER"));
        when(orderMapper.selectById(10L)).thenReturn(buildOrder(10L, OrderStatus.PROCESSING, 2L, 3L));

        BusinessException e = assertThrows(BusinessException.class, () -> orderService.finish(10L));
        assertEquals(403, e.getCode());
    }

    @Test
    @DisplayName("评价：非本人工单被拒（403）")
    void evaluateRejectedForOtherStudent() {
        UserContext.set(new LoginUser(88L, "other", "STUDENT"));
        when(orderMapper.selectById(10L)).thenReturn(buildOrder(10L, OrderStatus.FINISHED, 2L, 3L));

        EvaluationDTO dto = new EvaluationDTO();
        dto.setOrderId(10L);
        dto.setScore(5);
        dto.setContent("很好");

        BusinessException e = assertThrows(BusinessException.class, () -> orderService.evaluate(dto));
        assertEquals(403, e.getCode());
    }

    @Test
    @DisplayName("评价：未完成的工单不允许评价")
    void evaluateRejectedWhenNotFinished() {
        UserContext.set(new LoginUser(2L, "student", "STUDENT"));
        when(orderMapper.selectById(10L)).thenReturn(buildOrder(10L, OrderStatus.PROCESSING, 2L, 3L));

        EvaluationDTO dto = new EvaluationDTO();
        dto.setOrderId(10L);
        dto.setScore(5);
        dto.setContent("很好");

        BusinessException e = assertThrows(BusinessException.class, () -> orderService.evaluate(dto));
        assertEquals(400, e.getCode());
        verify(evaluationMapper, never()).insert(any());
    }
}
