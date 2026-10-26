/**
 * Day 4 冒烟测试：用 Node 发送 UTF-8 JSON，避免 Git Bash 终端编码问题
 * 运行：node scripts/smoke-test.js
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

(async () => {
  const login = await request({
    path: '/api/auth/login',
    method: 'POST',
    body: { username: 'admin', password: '123456' },
  });
  const token = JSON.parse(login.body).data.token;
  const auth = { Authorization: `Bearer ${token}` };

  const create = await request({
    path: '/api/users',
    method: 'POST',
    headers: auth,
    body: {
      username: 'testuser',
      password: '123456',
      realName: '测试用户',
      phone: '13900000000',
      roleId: 1,
    },
  });
  console.log('新增用户:', create.body);

  const page = await request({
    path: '/api/users/page?pageNum=1&pageSize=10',
    method: 'GET',
    headers: auth,
  });
  console.log('用户分页:', page.body);

  const login2 = await request({
    path: '/api/auth/login',
    method: 'POST',
    body: { username: 'testuser', password: '123456' },
  });
  console.log('新用户登录:', login2.body);

  const category = await request({
    path: '/api/categories',
    method: 'POST',
    headers: auth,
    body: { name: '测试分类', sort: 99 },
  });
  console.log('新增分类:', category.body);
})();
