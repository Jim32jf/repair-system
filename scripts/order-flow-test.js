/**
 * Day 5 全流程冒烟测试：提交 → 派单 → 处理 → 完成 → 评价
 * 运行：node scripts/order-flow-test.js
 */
const http = require('http');

function request({ path, method = 'GET', headers = {}, body }) {
  return new Promise((resolve, reject) => {
    const data = body === undefined ? null : Buffer.from(JSON.stringify(body), 'utf8');
    const req = http.request(
      {
        hostname: '127.0.0.1',
        port: 8080,
        path,
        method,
        headers: {
          ...headers,
          ...(data ? { 'Content-Type': 'application/json', 'Content-Length': data.length } : {}),
        },
      },
      (res) => {
        let raw = '';
        res.on('data', (chunk) => (raw += chunk));
        res.on('end', () => resolve({ status: res.statusCode, body: raw }));
      }
    );
    req.on('error', reject);
    if (data) req.write(data);
    req.end();
  });
}

async function login(username) {
  const res = await request({
    path: '/api/auth/login',
    method: 'POST',
    body: { username, password: '123456' },
  });
  return JSON.parse(res.body).data.token;
}

function auth(token) {
  return { Authorization: `Bearer ${token}` };
}

(async () => {
  const studentToken = await login('student');
  const adminToken = await login('admin');
  const repairerToken = await login('repairer');

  console.log('1. 学生提交报修');
  console.log(
    (await request({
      path: '/api/orders',
      method: 'POST',
      headers: auth(studentToken),
      body: {
        categoryId: 2,
        deviceName: '投影仪',
        location: '教学楼 A301',
        description: '设备无法开机，指示灯不亮',
      },
    })).body
  );

  console.log('\n2. 管理员查看待派单列表');
  const adminPage = await request({
    path: '/api/orders/page?pageNum=1&pageSize=5&status=0',
    headers: auth(adminToken),
  });
  console.log(adminPage.body);
  const orderId = JSON.parse(adminPage.body).data.records[0].id;

  console.log('\n3. 管理员派单给维修工(id=3)');
  console.log(
    (await request({
      path: '/api/orders/assign',
      method: 'PUT',
      headers: auth(adminToken),
      body: { orderId, repairerId: 3 },
    })).body
  );

  console.log('\n4. 维修工查看自己的任务');
  console.log(
    (await request({
      path: '/api/orders/page?pageNum=1&pageSize=5',
      headers: auth(repairerToken),
    })).body
  );

  console.log('\n5. 维修工开始处理');
  console.log(
    (await request({
      path: `/api/orders/${orderId}/start`,
      method: 'PUT',
      headers: auth(repairerToken),
    })).body
  );

  console.log('\n6. 维修工完成工单');
  console.log(
    (await request({
      path: `/api/orders/${orderId}/finish`,
      method: 'PUT',
      headers: auth(repairerToken),
    })).body
  );

  console.log('\n7. 学生评价 5 星');
  console.log(
    (await request({
      path: '/api/orders/evaluate',
      method: 'POST',
      headers: auth(studentToken),
      body: { orderId, score: 5, content: '师傅来得很快，修好了' },
    })).body
  );

  console.log('\n8. 学生查看工单详情（含流转记录和评价）');
  console.log(
    (await request({
      path: `/api/orders/${orderId}`,
      headers: auth(studentToken),
    })).body
  );

  console.log('\n9. 越权测试：维修工尝试评价（应 403）');
  const forbidden = await request({
    path: '/api/orders/evaluate',
    method: 'POST',
    headers: auth(repairerToken),
    body: { orderId, score: 5 },
  });
  console.log(`HTTP ${forbidden.status}`, forbidden.body);

  console.log('\n10. 越权测试：维修工尝试给非自己工单派单（应 403）');
  const forbidden2 = await request({
    path: '/api/orders/assign',
    method: 'PUT',
    headers: auth(repairerToken),
    body: { orderId, repairerId: 3 },
  });
  console.log(`HTTP ${forbidden2.status}`, forbidden2.body);
})();
