/**
 * 线上全功能回归测试（针对 http://101.35.25.235 的 Docker 部署环境）
 * 运行：node scripts/cloud-regression.js [BASE_URL]
 * 覆盖：认证/权限隔离/用户管理/分类缓存/工单状态机/评价/分页搜索 等全部接口，含正反用例
 */
const BASE = process.argv[2] || 'http://101.35.25.235';
let pass = 0, fail = 0;

function check(name, ok, detail) {
  if (ok) { pass++; console.log(`  ✅ ${name}`); }
  else { fail++; console.log(`  ❌ ${name}  ->  ${detail}`); }
}

async function req(path, { method = 'GET', token, body } = {}) {
  const headers = {};
  if (token) headers.Authorization = `Bearer ${token}`;
  if (body) headers['Content-Type'] = 'application/json';
  const res = await fetch(BASE + path, { method, headers, body: body ? JSON.stringify(body) : undefined });
  let json = null;
  try { json = await res.json(); } catch (e) { /* 非 JSON 响应 */ }
  return { status: res.status, json };
}

async function login(username, password) {
  const { json } = await req('/api/auth/login', { method: 'POST', body: { username, password } });
  return json && json.code === 200 ? json.data.token : null;
}

(async () => {
  console.log(`\n========== 目标：${BASE} ==========\n`);
  console.log('—— A. 认证与鉴权 ——');
  const admin = await login('admin', '123456');
  check('A1 admin 登录签发 JWT', !!admin, '未返回 token');
  check('A2 密码错误被拒', (await req('/api/auth/login', { method: 'POST', body: { username: 'admin', password: 'wrong' } })).json.code === 400, '应返回 400');
  const me = await req('/api/auth/me', { token: admin });
  check('A3 /auth/me 返回当前身份', me.json && me.json.data.username === 'admin', JSON.stringify(me.json));
  check('A4 无 token 访问被拦', (r => r.status === 401 || (r.json && r.json.code === 401))(await req('/api/users/page')), '未返回 401');
  check('A5 伪造 token 被拦', (r => r.status === 401 || (r.json && r.json.code === 401))(await req('/api/users/page', { token: 'abc.def.ghi' })), '未返回 401');

  const student = await login('student', '123456');    // 张三 id=2
  const student2 = await login('student2', '123456');  // 李四 id=4
  const repairer = await login('repairer', '123456');  // 李师傅 id=3
  const repairer2 = await login('repairer2', '123456'); // 王师傅 id=6
  check('A6 三业务角色均可登录', !!(student && student2 && repairer && repairer2), '有角色登录失败');

  console.log('—— B. 用户管理（仅 admin）——');
  const uname = 'reg' + Date.now().toString().slice(-6);
  const create = await req('/api/users', { method: 'POST', token: admin, body: { username: uname, password: '123456', realName: '回归测试', phone: '13900000000', roleId: 1 } });
  check('B1 新增用户', create.json.code === 200, JSON.stringify(create.json));
  check('B2 重名用户被拒', (await req('/api/users', { method: 'POST', token: admin, body: { username: uname, password: '123456', realName: 'x', phone: '13900000001', roleId: 1 } })).json.code !== 200, '不该允许重名');
  const pageU = await req('/api/users/page?pageNum=1&pageSize=10', { token: admin });
  check('B3 用户分页(total>=7)', pageU.json.data.total >= 7, JSON.stringify(pageU.json.data && pageU.json.data.total));
  const newId = (pageU.json.data.records.find(u => u.username === uname) || {}).id;
  check('B4 新用户可登录', !!(await login(uname, '123456')), '登录失败');
  check('B5 更新用户资料', (await req('/api/users', { method: 'PUT', token: admin, body: { id: newId, realName: '回归测试改名', phone: '13911111111', roleId: 1 } })).json.code === 200, '更新失败');
  check('B6 禁用后登录被拒(403)', (await req('/api/users/' + newId + '/status?status=0', { method: 'PUT', token: admin })).json.code === 200 && (await req('/api/auth/login', { method: 'POST', body: { username: uname, password: '123456' } })).json.code === 403, '禁用逻辑失效');
  check('B7 解禁后可登录', (await req('/api/users/' + newId + '/status?status=1', { method: 'PUT', token: admin })).json.code === 200 && !!(await login(uname, '123456')), '解禁失败');
  check('B8 admin 重置密码为 123456', (await req('/api/users/' + newId + '/reset-password', { method: 'PUT', token: admin })).json.code === 200 && !!(await login(uname, '123456')), '重置后无法登录');
  check('B9 不能禁用当前登录账号', (await req('/api/users/1/status?status=0', { method: 'PUT', token: admin })).json.code !== 200, '竟然能自禁');
  check('B10 学生无权限查用户列表', (await req('/api/users/page', { token: student })).json.code === 403, '越权未拦截');
  const reps = await req('/api/users/repairers', { token: admin });
  check('B11 维修工下拉列表(>=2人)', reps.json.code === 200 && reps.json.data.length >= 2, JSON.stringify(reps.json.data));
  check('B12 删除用户后无法登录', (await req('/api/users/' + newId, { method: 'DELETE', token: admin })).json.code === 200 && !(await login(uname, '123456')), '删除或拦截失败');
  check('B13 角色列表 3 条', (await req('/api/roles/list', { token: admin })).json.data.length === 3, '角色数不对');

  console.log('—— C. 分类管理 + Redis 缓存 ——');
  const catList = await req('/api/categories/list', { token: admin });
  check('C1 分类列表(初始5条)', (catList.json.data || []).length === 5, JSON.stringify(catList.json));
  const catAdd = await req('/api/categories', { method: 'POST', token: admin, body: { name: '回归测试分类', sort: 99 } });
  check('C2 新增分类', catAdd.json.code === 200, JSON.stringify(catAdd.json));
  const afterAdd = await req('/api/categories/list', { token: admin });
  const newCat = afterAdd.json.data.find(c => c.name === '回归测试分类');
  check('C3 缓存即时失效·新分类可见', !!newCat, '列表未更新');
  check('C4 改名后列表同步', (await req('/api/categories', { method: 'PUT', token: admin, body: { id: newCat.id, name: '回归测试分类改', sort: 99 } })).json.code === 200 && !!(await req('/api/categories/list', { token: admin })).json.data.find(c => c.name === '回归测试分类改'), '改名未生效');
  check('C5 被工单引用的分类禁止删除', (await req('/api/categories/1', { method: 'DELETE', token: admin })).json.code !== 200, '竟然删掉了有工单的分类');
  check('C6 无引用分类可删除', (await req('/api/categories/' + newCat.id, { method: 'DELETE', token: admin })).json.code === 200, '删除失败');
  check('C7 学生无权限加分类', (await req('/api/categories', { method: 'POST', token: student, body: { name: 'x', sort: 1 } })).json.code === 403, '越权未拦截');

  console.log('—— D. 工单全生命周期 + 状态机 + 数据隔离 ——');
  const ocreate = await req('/api/orders', { method: 'POST', token: student, body: { categoryId: 1, deviceName: '回归测试机', location: '实验楼999', description: '屏幕闪烁不定' } });
  check('D1 学生提交报修', ocreate.json.code === 200, JSON.stringify(ocreate.json));
  const oid = ocreate.json.data && ocreate.json.data.id ? ocreate.json.data.id : (await req('/api/orders/page?pageNum=1&pageSize=1&keyword=回归测试机', { token: admin })).json.data.records[0].id;
  const detail0 = await req('/api/orders/' + oid, { token: admin });
  check('D2 详情含"提交报修"日志', detail0.json.data.logs.some(l => l.action === '提交报修'), JSON.stringify(detail0.json.data.logs));
  check('D3 维修工无权限提交报修', (await req('/api/orders', { method: 'POST', token: repairer, body: { categoryId: 1, deviceName: 'x', location: 'y', description: 'z' } })).json.code === 403, '越权未拦截');
  check('D4 学生无权限派单', (await req('/api/orders/assign', { method: 'PUT', token: student, body: { orderId: oid, repairerId: 3 } })).json.code === 403, '越权未拦截');
  check('D5 派单给不存在成员被拒', (await req('/api/orders/assign', { method: 'PUT', token: admin, body: { orderId: oid, repairerId: 9999 } })).json.code !== 200, '未拦截');
  check('D6 admin 派单成功→已派单', (await req('/api/orders/assign', { method: 'PUT', token: admin, body: { orderId: oid, repairerId: 3 } })).json.code === 200 && (await req('/api/orders/' + oid, { token: admin })).json.data.status === 1, '状态未到 1');
  check('D7 非指派维修工无法开工', (await req('/api/orders/' + oid + '/start', { method: 'PUT', token: repairer2 })).json.code === 403, '数据隔离失效');
  check('D8 跳过"开始处理"直接完成被拒(状态机)', (await req('/api/orders/' + oid + '/finish', { method: 'PUT', token: repairer })).json.code !== 200, '非法流转未拦截');
  check('D9 维修工开始处理→处理中', (await req('/api/orders/' + oid + '/start', { method: 'PUT', token: repairer })).json.code === 200 && (await req('/api/orders/' + oid, { token: admin })).json.data.status === 2, '状态未到 2');
  check('D10 未完成不许评价', (await req('/api/orders/evaluate', { method: 'POST', token: student, body: { orderId: oid, score: 5 } })).json.code !== 200, '未拦截提前评价');
  check('D11 维修工完成→已完成', (await req('/api/orders/' + oid + '/finish', { method: 'PUT', token: repairer })).json.code === 200 && (await req('/api/orders/' + oid, { token: admin })).json.data.status === 3, '状态未到 3');
  check('D12 他人学生无法评价该单(隔离)', (await req('/api/orders/evaluate', { method: 'POST', token: student2, body: { orderId: oid, score: 5 } })).json.code === 403, '隔离失效');
  check('D13 报修人评价→已评价', (await req('/api/orders/evaluate', { method: 'POST', token: student, body: { orderId: oid, score: 4, content: '回归测试评价' } })).json.code === 200 && (await req('/api/orders/' + oid, { token: admin })).json.data.status === 4, '评价链路失败');
  check('D14 重复评价被拒', (await req('/api/orders/evaluate', { method: 'POST', token: student, body: { orderId: oid, score: 5 } })).json.code !== 200, '未拦截');
  check('D15 已评价工单不能再派单', (await req('/api/orders/assign', { method: 'PUT', token: admin, body: { orderId: oid, repairerId: 3 } })).json.code !== 200, '未拦截');
  const detailF = await req('/api/orders/' + oid, { token: student });
  check('D16 完整流转链 5 步日志', detailF.json.data.logs.map(l => l.action).join('→') === '提交报修→派单→开始处理→完成维修→评价', JSON.stringify(detailF.json.data.logs.map(l => l.action)));
  check('D17 学生只能看到自己的工单', ((await req('/api/orders/page?pageNum=1&pageSize=100', { token: student })).json.data.records || []).every(o => o.studentName === '张三'), '列表混入他人工单');
  check('D18 admin 能看到全部工单', (await req('/api/orders/page?pageNum=1&pageSize=100', { token: admin })).json.data.total >= 13, 'admin 视野不完整');
  check('D19 状态筛选(status=0 全为待派单)', ((await req('/api/orders/page?status=0&pageNum=1&pageSize=50', { token: admin })).json.data.records || []).every(o => o.status === 0), '筛选失真');
  check('D20 关键词搜索', (await req('/api/orders/page?keyword=投影仪&pageNum=1&pageSize=50', { token: admin })).json.data.records.every(o => (o.orderNo + o.deviceName + o.location).includes('投影仪') || o.categoryName === '投影仪'), '搜索结果不符');
  check('D21 查不存在工单返回错误', (await req('/api/orders/99999', { token: admin })).json.code !== 200, '未拦截');
  check('D22 越权查他人工单详情 403', (await req('/api/orders/1', { token: student2 })).json.code === 403, '隔离失效');

  console.log('—— E. 页面与接口文档 ——');
  const home = await fetch(BASE + '/');
  check('E1 首页 200(text/html)', home.status === 200 && (home.headers.get('content-type') || '').includes('text/html'), home.status);
  check('E2 Knife4j 文档页 200', (await fetch(BASE + '/doc.html')).status === 200, '非 200');
  check('E3 OpenAPI JSON 200', (await fetch(BASE + '/v3/api-docs')).status === 200, '非 200');
  check('E4 静态资源 lib 可加载', (await fetch(BASE + '/lib/vue.js')).status === 200, '非 200');

  console.log('—— F. 登录限流（放最后，会临时锁定本机出口 IP）——');
  let blocked = false, lastCode = 0;
  for (let i = 0; i < 12; i++) {
    const r = await req('/api/auth/login', { method: 'POST', body: { username: 'admin', password: 'wrong' + i } });
    lastCode = r.json ? r.json.code : 0;
    if (lastCode === 429) { blocked = true; break; }
  }
  check('F1 连续失败达阈值后返回 429 锁定', blocked, `12 次尝试最后 code=${lastCode}，未触发锁定`);
  console.log('  ℹ️  本机出口 IP 已被临时锁定，解锁：服务器上删 Redis 键 repair:login:fail:<IP> 或等 5 分钟');

  console.log(`\n========== 结果：通过 ${pass} / 失败 ${fail} ==========\n`);
  process.exitCode = fail > 0 ? 1 : 0;
})().catch(e => { console.error('脚本异常终止：', e.message); process.exit(2); });
