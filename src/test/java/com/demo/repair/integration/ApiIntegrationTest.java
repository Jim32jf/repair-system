package com.demo.repair.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 全链路集成测试：真实 Spring 上下文 + H2 内存库 + 拦截器鉴权 + MockMvc。
 * 覆盖单测测不到的部分：JwtInterceptor、@RequireRole、GlobalExceptionHandler、
 * 工单状态机落库、水平越权、参数校验、登出吊销。
 * Redis 用 HashMap 替身，让吊销与缓存降级路径在测试里真实生效。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ApiIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private StringRedisTemplate redisTemplate;

    /** 替身 Redis 的存储 */
    private Map<String, String> redisStore;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void stubRedis() {
        redisStore = new HashMap<>();
        ValueOperations<String, String> valueOps = mock(ValueOperations.class);
        when(valueOps.get(anyString())).thenAnswer(inv -> redisStore.get(inv.getArgument(0)));
        doAnswer(inv -> {
            redisStore.put(inv.getArgument(0), inv.getArgument(1));
            return null;
        }).when(valueOps).set(anyString(), anyString(), anyLong(), any(TimeUnit.class));
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(redisTemplate.delete(anyString())).thenAnswer(inv -> redisStore.remove(inv.getArgument(0)) != null);
    }

    // ---------- 工具 ----------

    /** 登录并取回 token，三个种子账号密码都是 123456 */
    private String login(String username) throws Exception {
        MvcResult res = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"123456\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andReturn();
        return objectMapper.readTree(res.getResponse().getContentAsString())
                .path("data").path("token").asText();
    }

    /** 学生提交一条报修，返回新工单 ID */
    private Long createOrder(String studentToken) throws Exception {
        mockMvc.perform(post("/api/orders")
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"categoryId\":1,\"deviceName\":\"投影仪\",\"location\":\"一教101\",\"description\":\"灯泡不亮\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
        Long id = jdbc.queryForObject("SELECT MAX(id) FROM repair_order", Long.class);
        assertEquals(0, orderStatus(id), "新建工单应为待派单");
        return id;
    }

    private int orderStatus(Long orderId) {
        return jdbc.queryForObject("SELECT status FROM repair_order WHERE id = ?", Integer.class, orderId);
    }

    // ---------- 认证链路 ----------

    @Test
    @DisplayName("无 token 访问受保护接口 → HTTP 401")
    void noTokenRejected() throws Exception {
        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401));
    }

    @Test
    @DisplayName("伪造 token → HTTP 401")
    void forgedTokenRejected() throws Exception {
        mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer abc.def.ghi"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("登录 → 持 token 访问 /me 全链路成功（真实 JWT 签发与校验）")
    void loginThenMe() throws Exception {
        String token = login("student");
        mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.username").value("student"))
                .andExpect(jsonPath("$.data.roleCode").value("STUDENT"));
    }

    @Test
    @DisplayName("密码错误 → 400 统一提示，不泄露账号存在性")
    void wrongPassword() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"student\",\"password\":\"wrong\"}"))
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("用户名或密码错误"));
    }

    // ---------- 权限控制 ----------

    @Test
    @DisplayName("@RequireRole 生效：学生调管理员接口 → HTTP 403")
    void roleForbidden() throws Exception {
        String studentToken = login("student");
        mockMvc.perform(get("/api/users/page").header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));
    }

    @Test
    @DisplayName("水平越权：学生 B 查看学生 A 的工单详情 → code 403")
    void crossStudentDetailForbidden() throws Exception {
        String tokenA = login("student");
        Long orderId = createOrder(tokenA);

        String tokenB = login("student2");
        mockMvc.perform(get("/api/orders/" + orderId).header("Authorization", "Bearer " + tokenB))
                .andExpect(jsonPath("$.code").value(403));
    }

    // ---------- 业务全流程（真实数据库） ----------

    @Test
    @DisplayName("工单闭环：报修→派单→开始→完成→评价，状态与流转日志落库正确")
    void fullOrderFlow() throws Exception {
        String studentToken = login("student");
        String adminToken = login("admin");
        String repairerToken = login("repairer");

        Long orderId = createOrder(studentToken);

        mockMvc.perform(put("/api/orders/assign")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"orderId\":" + orderId + ",\"repairerId\":3}"))
                .andExpect(jsonPath("$.code").value(200));
        assertEquals(1, orderStatus(orderId), "派单后应为已派单");

        mockMvc.perform(put("/api/orders/" + orderId + "/start")
                        .header("Authorization", "Bearer " + repairerToken))
                .andExpect(jsonPath("$.code").value(200));
        assertEquals(2, orderStatus(orderId), "开始后应为处理中");

        mockMvc.perform(put("/api/orders/" + orderId + "/finish")
                        .header("Authorization", "Bearer " + repairerToken))
                .andExpect(jsonPath("$.code").value(200));
        assertEquals(3, orderStatus(orderId), "完成后应为已完成");

        mockMvc.perform(post("/api/orders/evaluate")
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"orderId\":" + orderId + ",\"score\":5,\"content\":\"修得很快\"}"))
                .andExpect(jsonPath("$.code").value(200));
        assertEquals(4, orderStatus(orderId), "评价后应为已评价");

        // 状态变更与流转日志同事务：5 步流转应恰好 5 条日志
        Integer logCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM order_log WHERE order_id = ?", Integer.class, orderId);
        assertEquals(5, logCount, "每个状态变更都应留痕");

        // 详情页含流转记录
        mockMvc.perform(get("/api/orders/" + orderId).header("Authorization", "Bearer " + studentToken))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.status").value(4));
    }

    @Test
    @DisplayName("非法状态流转：刚派单的工单直接评价被拦截，状态不变")
    void illegalTransitionRejected() throws Exception {
        String studentToken = login("student");
        String adminToken = login("admin");
        Long orderId = createOrder(studentToken);

        mockMvc.perform(put("/api/orders/assign")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"orderId\":" + orderId + ",\"repairerId\":3}"))
                .andExpect(jsonPath("$.code").value(200));

        mockMvc.perform(post("/api/orders/evaluate")
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"orderId\":" + orderId + ",\"score\":5}"))
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("当前状态不允许评价"));

        assertEquals(1, orderStatus(orderId), "非法流转后状态保持已派单");
    }

    // ---------- 参数校验与异常链路 ----------

    @Test
    @DisplayName("pageSize=1000 超限 → 校验返回 400 友好文案而非 500")
    void oversizedPageRejected() throws Exception {
        String adminToken = login("admin");
        mockMvc.perform(get("/api/users/page?pageSize=1000")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    @DisplayName("评分超范围（score=6） → 400 且提示来自 DTO 注解")
    void scoreOutOfRangeRejected() throws Exception {
        String studentToken = login("student");
        mockMvc.perform(post("/api/orders/evaluate")
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"orderId\":1,\"score\":6}"))
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("评分最高 5 分"));
    }

    // ---------- 用户管理生命周期（补 UserServiceImpl 空白） ----------

    @Test
    @DisplayName("用户全生命周期：新增→重名拦截→普通修改不踢人→重置密码踢人→禁用踢人→删除")
    void userLifecycle() throws Exception {
        String adminToken = login("admin");

        // 新增（login() 辅助方法固定用密码 123456）
        mockMvc.perform(post("/api/users").header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"tempuser\",\"password\":\"123456\",\"realName\":\"临时工\",\"phone\":\"13900000009\",\"roleId\":1,\"status\":1}"))
                .andExpect(jsonPath("$.code").value(200));
        Long tempId = jdbc.queryForObject("SELECT MAX(id) FROM sys_user", Long.class);

        // 重名拦截
        mockMvc.perform(post("/api/users").header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"tempuser\",\"password\":\"123456\",\"roleId\":1}"))
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("用户名已存在"));

        // 只改姓名/手机号：不涉及角色与状态，旧 token 应继续可用
        String tempToken = login("tempuser");
        mockMvc.perform(put("/api/users").header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"id\":" + tempId + ",\"realName\":\"改名了\",\"phone\":\"13900000009\"}"))
                .andExpect(jsonPath("$.code").value(200));
        mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + tempToken))
                .andExpect(status().isOk());

        // 重置密码 → 旧 token 立即作废，新密码 123456 可登录
        mockMvc.perform(put("/api/users/" + tempId + "/reset-password").header("Authorization", "Bearer " + adminToken))
                .andExpect(jsonPath("$.code").value(200));
        mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + tempToken))
                .andExpect(status().isUnauthorized());
        String newToken = login("tempuser");

        // 禁用 → 即刻踢下线
        mockMvc.perform(put("/api/users/" + tempId + "/status?status=0").header("Authorization", "Bearer " + adminToken))
                .andExpect(jsonPath("$.code").value(200));
        mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + newToken))
                .andExpect(status().isUnauthorized());

        // 禁用后不允许登录
        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"tempuser\",\"password\":\"123456\"}"))
                .andExpect(jsonPath("$.code").value(403));

        // 删除
        mockMvc.perform(delete("/api/users/" + tempId).header("Authorization", "Bearer " + adminToken))
                .andExpect(jsonPath("$.code").value(200));
        Integer alive = jdbc.queryForObject("SELECT COUNT(*) FROM sys_user WHERE id = ? AND deleted = 0",
                Integer.class, tempId);
        assertEquals(0, alive, "应为逻辑删除");
    }

    // ---------- 分类管理 + 缓存读写失效（补 CategoryServiceImpl 空白） ----------

    @Test
    @DisplayName("分类增改删与缓存维护；被工单引用的分类禁止删除")
    void categoryCrudAndReferenceGuard() throws Exception {
        String adminToken = login("admin");
        String studentToken = login("student");

        // 列表（未命中 → 查库 → 回写缓存）
        mockMvc.perform(get("/api/categories/list").header("Authorization", "Bearer " + adminToken))
                .andExpect(jsonPath("$.data.length()").value(5));

        // 新增（写后清缓存）
        mockMvc.perform(post("/api/categories").header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"测试分类\",\"sort\":9}"))
                .andExpect(jsonPath("$.code").value(200));
        Long newId = jdbc.queryForObject("SELECT MAX(id) FROM device_category", Long.class);
        mockMvc.perform(get("/api/categories/list").header("Authorization", "Bearer " + adminToken))
                .andExpect(jsonPath("$.data.length()").value(6));

        // 修改
        mockMvc.perform(put("/api/categories").header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"id\":" + newId + ",\"name\":\"改名分类\",\"sort\":9}"))
                .andExpect(jsonPath("$.code").value(200));

        // 学生工单引用分类 1（电脑）→ 删除被拦截
        createOrder(studentToken);
        mockMvc.perform(delete("/api/categories/1").header("Authorization", "Bearer " + adminToken))
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("该分类下存在工单，无法删除"));

        // 删除未被引用的分类
        mockMvc.perform(delete("/api/categories/" + newId).header("Authorization", "Bearer " + adminToken))
                .andExpect(jsonPath("$.code").value(200));
    }

    // ---------- 登出吊销（端到端） ----------

    @Test
    @DisplayName("登出后旧 token 立即失效，重新登录的新 token 可用")
    void logoutRevokesTokenEndToEnd() throws Exception {
        String token = login("student");
        mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        // 吊销水位线按毫秒比较，留出时间差避免同一毫秒签发
        Thread.sleep(5);
        mockMvc.perform(post("/api/auth/logout").header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.code").value(200));

        mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("登录已失效，请重新登录"));

        // 水位线只作废签发时刻之前的 token：重新登录后应恢复正常
        String fresh = login("student");
        mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + fresh))
                .andExpect(status().isOk());
    }

    // ---------- 缓存降级 ----------

    @Test
    @DisplayName("Redis 未命中时分类列表降级查库，结果正确")
    void categoryListFallbackToDb() throws Exception {
        String token = login("student");
        mockMvc.perform(get("/api/categories/list").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.length()").value(5));
    }
}
