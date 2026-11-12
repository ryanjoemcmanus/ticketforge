const { test } = require('node:test');
const assert = require('node:assert/strict');
const { readFileSync } = require('node:fs');
const { resolve } = require('node:path');
const vm = require('node:vm');
const { randomUUID } = require('node:crypto');

const source = readFileSync(resolve(__dirname, '../../main/resources/static/assets/app.js'), 'utf8');
const response = (status, data = {}) => ({ status, ok: status < 400, json: async () => data });

async function client() {
  const elements = new Map();
  const storage = new Map([['unrelated', 'preserve me']]);
  function element(selector) {
    if (!elements.has(selector)) elements.set(selector, {
      value: '', textContent: '', innerHTML: '', disabled: false, open: false,
      classList: { toggle() {}, add() {}, remove() {} },
      close() { this.open = false; }, showModal() { this.open = true; },
      scrollIntoView() {},
    });
    return elements.get(selector);
  }
  const context = vm.createContext({
    document: { querySelector: element, querySelectorAll: () => [], createElement: () => element('temporary') },
    sessionStorage: { getItem: key => storage.get(key) ?? null, setItem: (key, value) => storage.set(key, value), removeItem: key => storage.delete(key) },
    fetch: async () => response(200, { content: [] }),
    crypto: { randomUUID }, URLSearchParams, Intl, Date,
    location: { search: '' }, setTimeout() {}, clearTimeout() {},
    FormData: class { constructor(form) { return new Map(Object.entries(form.values)); } },
  });
  vm.runInContext(source, context);
  await new Promise(resolve => setImmediate(resolve));
  const run = code => vm.runInContext(code, context);
  run("setSession({accessToken:'old',role:'CUSTOMER',userId:'buyer'})");
  return { run, context, element, storage };
}

test('public browsing and authentication omit stale bearer tokens', async () => {
  const c = await client();
  const requests = [];
  c.context.fetch = async (url, options) => { requests.push({ url, options }); return response(200); };
  await c.run("api('/events?size=12')");
  await c.run("api('/events/event-id')");
  await c.run("api('/auth/login',{method:'POST',body:'{}'})");
  await c.run("api('/auth/password-reset/confirm',{method:'POST',body:'{}'})");
  assert.ok(requests.every(r => !r.options.headers.Authorization));
  await c.run("api('/auth/organizers/user-id/approve',{method:'POST'})");
  assert.equal(requests.at(-1).options.headers.Authorization, 'Bearer old');
});

test('protected 401 clears only application session keys and requests sign-in', async () => {
  const c = await client();
  c.context.fetch = async () => response(401);
  await assert.rejects(c.run("api('/orders')"), /sign in again/);
  assert.equal(c.run('state.token'), null);
  assert.equal(c.storage.has('tf_access'), false);
  assert.equal(c.storage.get('unrelated'), 'preserve me');
  assert.equal(c.element('#authDialog').open, true);
});

test('failed login does not clear a current session', async () => {
  const c = await client();
  c.context.fetch = async () => response(401, { detail: 'Invalid credentials' });
  await assert.rejects(c.run("api('/auth/login',{method:'POST'})"), /Invalid credentials/);
  assert.equal(c.run('state.token'), 'old');
});

test('late 401 does not clear a newer login', async () => {
  const c = await client();
  let finish;
  c.context.fetch = () => new Promise(resolve => { finish = resolve; });
  const pending = c.run("api('/orders')");
  c.run("setSession({accessToken:'new',role:'CUSTOMER',userId:'buyer'})");
  finish(response(401));
  await assert.rejects(pending, /sign in again/);
  assert.equal(c.run('state.token'), 'new');
});

test('password reset clears stale session and invites login', async () => {
  const c = await client();
  c.context.fetch = async () => response(204);
  await c.run("confirmReset({values:{newPassword:'test-password'},reset(){}})");
  assert.equal(c.run('state.token'), null);
  assert.equal(c.element('#authDialog').open, true);
});

test('checkout retry retains idempotency key after ambiguous network failure', async () => {
  const c = await client();
  const keys = [];
  c.run("state.hold={id:'hold-1'}");
  c.context.fetch = async (url, options) => {
    if (url.includes('/events')) return response(200, { content: [] });
    keys.push(options.headers['Idempotency-Key']);
    if (keys.length === 1) throw new Error('Connection lost');
    return response(201, { totalAmount: 10 });
  };
  await c.run('checkout()');
  assert.equal(c.element('#checkoutButton').disabled, false);
  await c.run('checkout()');
  assert.equal(keys.length, 2);
  assert.equal(keys[0], keys[1]);
  assert.equal(c.run('state.hold'), null);
});

test('double clicking checkout sends only one request', async () => {
  const c = await client();
  c.run("state.hold={id:'hold-1'}");
  let finish, calls = 0;
  c.context.fetch = async (url) => {
    if (url.includes('/events')) return response(200, { content: [] });
    calls++;
    return new Promise(resolve => { finish = resolve; });
  };
  const first = c.run('checkout()');
  await c.run('checkout()');
  assert.equal(calls, 1);
  assert.equal(c.element('#checkoutButton').disabled, true);
  finish(response(201, { totalAmount: 10 }));
  await first;
  assert.equal(c.element('#checkoutButton').disabled, false);
});

test('new hold gets a new checkout key', async () => {
  const c = await client();
  const keys = [];
  c.context.fetch = async (url, options) => {
    keys.push(options.headers['Idempotency-Key']);
    throw new Error('Connection lost');
  };
  c.run("state.hold={id:'hold-1'}");
  await c.run('checkout()');
  c.run("state.hold={id:'hold-2'}");
  await c.run('checkout()');
  assert.notEqual(keys[0], keys[1]);
});
