package com.demo.repair.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.date.DateUtil;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.demo.repair.common.BusinessException;
import com.demo.repair.common.OrderStatus;
import com.demo.repair.common.PageResult;
import com.demo.repair.common.UserContext;
import com.demo.repair.dto.EvaluationDTO;
import com.demo.repair.dto.OrderAssignDTO;
import com.demo.repair.dto.OrderCreateDTO;
import com.demo.repair.dto.OrderQueryDTO;
import com.demo.repair.entity.DeviceCategory;
import com.demo.repair.entity.Evaluation;
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
import com.demo.repair.service.OrderService;
import com.demo.repair.vo.OrderDetailVO;
import com.demo.repair.vo.OrderLogVO;
import com.demo.repair.vo.OrderVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class OrderServiceImpl implements OrderService {

    private final RepairOrderMapper orderMapper;

    private final OrderLogMapper logMapper;

    private final EvaluationMapper evaluationMapper;

    private final SysUserMapper userMapper;

    private final SysRoleMapper roleMapper;

    private final DeviceCategoryMapper categoryMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void create(OrderCreateDTO dto) {
        RepairOrder order = new RepairOrder();
        order.setOrderNo(generateOrderNo());
        order.setStudentId(UserContext.getUserId());
        order.setCategoryId(dto.getCategoryId());
        order.setDeviceName(dto.getDeviceName());
        order.setLocation(dto.getLocation());
        order.setDescription(dto.getDescription());
        order.setImageUrl(dto.getImageUrl());
        order.setStatus(OrderStatus.PENDING_ASSIGN);
        orderMapper.insert(order);

        addLog(order.getId(), UserContext.getUserId(), "提交报修", null);
    }

    @Override
    public PageResult<OrderVO> page(OrderQueryDTO query) {
        Page<RepairOrder> page = new Page<>(query.getPageNum(), query.getPageSize());
        LambdaQueryWrapper<RepairOrder> wrapper = new LambdaQueryWrapper<>();

        // 按角色限制可见范围：学生只看自己的，维修工只看派给自己的，管理员看全部
        String roleCode = UserContext.getRoleCode();
        Long userId = UserContext.getUserId();
        if ("STUDENT".equals(roleCode)) {
            wrapper.eq(RepairOrder::getStudentId, userId);
        } else if ("REPAIRER".equals(roleCode)) {
            wrapper.eq(RepairOrder::getRepairerId, userId);
        }

        wrapper.eq(query.getStatus() != null, RepairOrder::getStatus, query.getStatus())
                .and(StrUtil.isNotBlank(query.getKeyword()), w -> w
                        .like(RepairOrder::getOrderNo, query.getKeyword())
                        .or()
                        .like(RepairOrder::getDeviceName, query.getKeyword())
                        .or()
                        .like(RepairOrder::getLocation, query.getKeyword()))
                .orderByDesc(RepairOrder::getCreateTime);

        IPage<RepairOrder> orderPage = orderMapper.selectPage(page, wrapper);
        return PageResult.of(orderPage.getTotal(), convert(orderPage.getRecords()));
    }

    @Override
    public OrderDetailVO detail(Long id) {
        RepairOrder order = getOrder(id);
        checkViewPermission(order);

        OrderDetailVO vo = new OrderDetailVO();
        BeanUtil.copyProperties(convert(Collections.singletonList(order)).get(0), vo);

        List<OrderLog> logs = logMapper.selectList(new LambdaQueryWrapper<OrderLog>()
                .eq(OrderLog::getOrderId, id)
                .orderByAsc(OrderLog::getCreateTime));
        Set<Long> operatorIds = logs.stream()
                .map(OrderLog::getOperatorId)
                .collect(Collectors.toSet());
        Map<Long, String> operatorNameMap = queryUserNameMap(operatorIds);
        vo.setLogs(logs.stream().map(log -> {
            OrderLogVO logVO = new OrderLogVO();
            BeanUtil.copyProperties(log, logVO);
            logVO.setOperatorName(operatorNameMap.get(log.getOperatorId()));
            return logVO;
        }).collect(Collectors.toList()));

        vo.setEvaluation(evaluationMapper.selectOne(new LambdaQueryWrapper<Evaluation>()
                .eq(Evaluation::getOrderId, id)));
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void assign(OrderAssignDTO dto) {
        RepairOrder order = getOrder(dto.getOrderId());
        if (order.getStatus() != OrderStatus.PENDING_ASSIGN) {
            throw new BusinessException("当前状态不允许派单");
        }

        SysUser repairer = userMapper.selectById(dto.getRepairerId());
        if (repairer == null) {
            throw new BusinessException(404, "维修工不存在");
        }
        SysRole role = roleMapper.selectById(repairer.getRoleId());
        if (role == null || !"REPAIRER".equals(role.getRoleCode())) {
            throw new BusinessException("该用户不是维修工");
        }

        RepairOrder update = new RepairOrder();
        update.setId(order.getId());
        update.setRepairerId(repairer.getId());
        update.setStatus(OrderStatus.ASSIGNED);
        orderMapper.updateById(update);

        addLog(order.getId(), UserContext.getUserId(), "派单", "分配给：" + repairer.getRealName());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void start(Long id) {
        RepairOrder order = getOrder(id);
        checkRepairerPermission(order);
        if (order.getStatus() != OrderStatus.ASSIGNED) {
            throw new BusinessException("当前状态不允许开始处理");
        }

        RepairOrder update = new RepairOrder();
        update.setId(order.getId());
        update.setStatus(OrderStatus.PROCESSING);
        orderMapper.updateById(update);

        addLog(order.getId(), UserContext.getUserId(), "开始处理", null);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void finish(Long id) {
        RepairOrder order = getOrder(id);
        checkRepairerPermission(order);
        if (order.getStatus() != OrderStatus.PROCESSING) {
            throw new BusinessException("当前状态不允许完成");
        }

        RepairOrder update = new RepairOrder();
        update.setId(order.getId());
        update.setStatus(OrderStatus.FINISHED);
        update.setFinishTime(LocalDateTime.now());
        orderMapper.updateById(update);

        addLog(order.getId(), UserContext.getUserId(), "完成维修", null);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void evaluate(EvaluationDTO dto) {
        RepairOrder order = getOrder(dto.getOrderId());
        if (!UserContext.getUserId().equals(order.getStudentId())) {
            throw new BusinessException(403, "只能评价自己的工单");
        }
        if (order.getStatus() != OrderStatus.FINISHED) {
            throw new BusinessException("当前状态不允许评价");
        }
        Long count = evaluationMapper.selectCount(new LambdaQueryWrapper<Evaluation>()
                .eq(Evaluation::getOrderId, order.getId()));
        if (count > 0) {
            throw new BusinessException("该工单已评价");
        }

        Evaluation evaluation = new Evaluation();
        evaluation.setOrderId(order.getId());
        evaluation.setScore(dto.getScore());
        evaluation.setContent(dto.getContent());
        evaluationMapper.insert(evaluation);

        RepairOrder update = new RepairOrder();
        update.setId(order.getId());
        update.setStatus(OrderStatus.EVALUATED);
        orderMapper.updateById(update);

        addLog(order.getId(), UserContext.getUserId(), "评价", dto.getScore() + " 星");
    }

    // ==================== 私有方法 ====================

    private RepairOrder getOrder(Long id) {
        RepairOrder order = orderMapper.selectById(id);
        if (order == null) {
            throw new BusinessException(404, "工单不存在");
        }
        return order;
    }

    private void checkViewPermission(RepairOrder order) {
        String roleCode = UserContext.getRoleCode();
        Long userId = UserContext.getUserId();
        if ("STUDENT".equals(roleCode) && !userId.equals(order.getStudentId())) {
            throw new BusinessException(403, "无权查看该工单");
        }
        if ("REPAIRER".equals(roleCode) && !userId.equals(order.getRepairerId())) {
            throw new BusinessException(403, "无权查看该工单");
        }
    }

    private void checkRepairerPermission(RepairOrder order) {
        if (!UserContext.getUserId().equals(order.getRepairerId())) {
            throw new BusinessException(403, "只能操作分配给自己的工单");
        }
    }

    private void addLog(Long orderId, Long operatorId, String action, String remark) {
        OrderLog log = new OrderLog();
        log.setOrderId(orderId);
        log.setOperatorId(operatorId);
        log.setAction(action);
        log.setRemark(remark);
        logMapper.insert(log);
    }

    private String generateOrderNo() {
        return "RO" + DateUtil.format(new Date(), "yyyyMMddHHmmss") + RandomUtil.randomNumbers(4);
    }

    private List<OrderVO> convert(List<RepairOrder> orders) {
        if (CollUtil.isEmpty(orders)) {
            return new ArrayList<>();
        }

        Set<Long> userIds = new HashSet<>();
        Set<Long> categoryIds = new HashSet<>();
        for (RepairOrder order : orders) {
            if (order.getStudentId() != null) {
                userIds.add(order.getStudentId());
            }
            if (order.getRepairerId() != null) {
                userIds.add(order.getRepairerId());
            }
            if (order.getCategoryId() != null) {
                categoryIds.add(order.getCategoryId());
            }
        }

        Map<Long, String> userNameMap = queryUserNameMap(userIds);
        Map<Long, String> categoryNameMap = categoryIds.isEmpty()
                ? Collections.emptyMap()
                : categoryMapper.selectBatchIds(categoryIds).stream()
                        .collect(Collectors.toMap(DeviceCategory::getId, DeviceCategory::getName));

        return orders.stream().map(order -> {
            OrderVO vo = new OrderVO();
            BeanUtil.copyProperties(order, vo);
            vo.setStudentName(userNameMap.get(order.getStudentId()));
            vo.setRepairerName(userNameMap.get(order.getRepairerId()));
            vo.setCategoryName(categoryNameMap.get(order.getCategoryId()));
            vo.setStatusName(OrderStatus.name(order.getStatus()));
            return vo;
        }).collect(Collectors.toList());
    }

    private Map<Long, String> queryUserNameMap(Set<Long> userIds) {
        if (CollUtil.isEmpty(userIds)) {
            return Collections.emptyMap();
        }
        return userMapper.selectBatchIds(userIds).stream()
                .collect(Collectors.toMap(SysUser::getId, user -> StrUtil.nullToEmpty(user.getRealName())));
    }
}
