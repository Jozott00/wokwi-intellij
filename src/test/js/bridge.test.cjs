const assert = require('node:assert/strict');
const { readFileSync } = require('node:fs');
const { resolve } = require('node:path');
const { test } = require('node:test');
const { runInNewContext } = require('node:vm');

const bridge = readFileSync(resolve(__dirname, '../../main/resources/wokwi/wrapper/bridge.js'), 'utf8');

// Execute the production wrapper with only its DOM and IDE bridge boundaries stubbed.
function createWrapper(url = 'https://wokwi.com/vscode/wcode?v=test') {
  const listeners = {};
  const forwarded = [];
  const loadingClasses = new Set();
  const offlineClasses = new Set();
  const iframe = {
    contentWindow: {},
    getAttribute: () => url,
    addEventListener() {},
  };
  const window = {
    __WokwiIntellij: { __postMessageToPipe: (message) => forwarded.push(JSON.parse(message)) },
    addEventListener: (type, listener) => { listeners[type] = listener; },
  };
  const document = {
    querySelector: (selector) => ({
      iframe,
      '#loading-message': { classList: loadingClasses },
      '#offline-message': { classList: offlineClasses },
    })[selector],
  };
  runInNewContext(bridge, { window, document, URL, console });
  return {
    iframe, forwarded, loadingClasses, offlineClasses,
    receive: (data) => window.__WokwiIntellij.__receiveMessageFromPipe('wokwi', data),
    handshake: (port, overrides = {}) => listeners.message({
      origin: new URL(url).origin,
      source: iframe.contentWindow,
      data: { command: 'start', port },
      ...overrides,
    }),
  };
}

function createPort() {
  const sent = [];
  return { sent, postMessage: (message) => sent.push(message) };
}

for (const url of ['https://wokwi.com/vscode/wcode?v=test', 'http://localhost:3002/vscode/wcode?v=test']) {
  test(`accepts the configured iframe and origin: ${url}`, () => {
    const wrapper = createWrapper(url);
    const port = createPort();
    const payload = { command: 'start', license: 'test-license', diagram: '{}', firmware: ':hex' };
    wrapper.receive(payload);
    wrapper.handshake(port);
    assert.deepEqual(port.sent, [payload]);
    assert.deepEqual(wrapper.forwarded, [{ type: 'wokwi', data: { command: 'start' } }]);
    assert.equal(wrapper.loadingClasses.has('hidden'), true);
    assert.equal(wrapper.offlineClasses.has('hidden'), true);
    port.onmessage({ data: { command: 'sim:run' } });
    assert.deepEqual(wrapper.forwarded[1], { type: 'wokwi', data: { command: 'sim:run' } });
    wrapper.receive({ command: 'sim:resume' });
    assert.deepEqual(port.sent[1], { command: 'sim:resume' });
  });
}

for (const [name, overrides] of [
  ['redirected iframe', { origin: 'https://attacker.example' }],
  ['origin with trusted hostname prefix', { origin: 'https://wokwi.com.attacker.example' }],
  ['different port on trusted host', { origin: 'https://wokwi.com:444' }],
  ['opaque origin', { origin: 'null' }],
  ['another window on the trusted origin', { source: {} }],
  ['missing sender', { source: null }],
]) {
  test(`rejects ${name} without releasing queued startup data or replacing a trusted port`, () => {
    const wrapper = createWrapper();
    const attacker = createPort();
    const trusted = createPort();
    const payload = { command: 'start', license: 'test-license', diagram: '{}', firmware: ':hex' };
    wrapper.receive(payload);
    wrapper.handshake(attacker, overrides);
    assert.deepEqual(attacker.sent, []);
    assert.equal(attacker.onmessage, undefined);
    assert.deepEqual(wrapper.forwarded, []);
    assert.equal(wrapper.loadingClasses.has('hidden'), false);
    assert.equal(wrapper.offlineClasses.has('hidden'), false);

    wrapper.handshake(trusted);
    assert.deepEqual(trusted.sent, [payload]);
    wrapper.handshake(attacker, overrides);
    wrapper.receive({ command: 'sim:resume' });
    assert.deepEqual(attacker.sent, []);
    assert.deepEqual(trusted.sent[1], { command: 'sim:resume' });
    assert.equal(wrapper.forwarded.length, 1);
  });
}

test('ignores messages without a start handshake and port', () => {
  const wrapper = createWrapper();
  for (const data of [null, {}, { command: 'start' }, { command: 'sim:run', port: createPort() }]) {
    wrapper.handshake(undefined, { data });
  }
  assert.deepEqual(wrapper.forwarded, []);
});
