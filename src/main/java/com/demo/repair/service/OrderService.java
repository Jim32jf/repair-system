package com.demo.repair.service;

import com.demo.repair.common.PageResult;
import com.demo.repair.dto.EvaluationDTO;
import com.demo.repair.dto.OrderAssignDTO;
import com.demo.repair.dto.OrderCreateDTO;
import com.demo.repair.dto.OrderQueryDTO;
import com.demo.repair.vo.OrderDetailVO;
import com.demo.repair.vo.OrderVO;

public interface OrderService {

    void create(OrderCreateDTO dto);

    PageResult<OrderVO> page(OrderQueryDTO query);

    OrderDetailVO detail(Long id);

    void assign(OrderAssignDTO dto);

    void start(Long id);

    void finish(Long id);

    void evaluate(EvaluationDTO dto);
}
