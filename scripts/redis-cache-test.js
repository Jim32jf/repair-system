/**
 * Redis 缓存冒烟测试
 * 用法：
 *   node scripts/redis-cache-test.js list   查询分类列表
 *   node scripts/redis-cache-test.js add    新增一个分类
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
  const action = process.argv[2] || 'list';

  const login = await request({
    path: '/api/auth/login',
    method: 'POST',
    body: { username: 'admin', password: '123456' },
  });
  const token = JSON.parse(login.body).data.token;
  const auth = { Authorization: `Bearer ${token}` };

  if (action === 'add') {
    const res = await request({
      path: '/api/categories',
      method: 'POST',
      headers: auth,
      body: { name: '调试插座', sort: 10 },
    });
    console.log('新增分类:', res.body);
  } else {
    const res = await request({ path: '/api/categories/list', headers: auth });
    console.log('分类列表:', res.body);
  }
})();
