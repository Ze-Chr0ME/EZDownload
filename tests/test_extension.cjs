const assert = require("node:assert/strict");
const fs = require("node:fs");
const vm = require("node:vm");
function event() {
  const listeners = [];
  return { addListener(fn) { listeners.push(fn); }, fire(...args) { listeners.forEach(fn => fn(...args)); } };
}
function setup() {
  const ui = {}, ports = [], notices = [], alarms = [];
  const chrome = {
    runtime: { onInstalled: event(), onStartup: event(), connectNative() {
      const port = { onMessage: event(), onDisconnect: event(), postMessage(message) { this.sent = message; } };
      ports.push(port); return port;
    } },
    action: { onClicked: event(), setIcon(x) { ui.icon = x; }, setBadgeText(x) { ui.badge = x.text; },
      setBadgeBackgroundColor(x) { ui.color = x.color; }, setTitle(x) { ui.title = x.title; } },
    contextMenus: { onClicked: event(), create() {}, removeAll(callback) { callback(); } },
    notifications: { create(x) { notices.push(x); } },
    alarms: { onAlarm: event(), clear() {}, create(...args) { alarms.push(args); } }
  };
  class OffscreenCanvas {
    getContext() { return { scale() {}, beginPath() {}, arc() {}, stroke() {}, moveTo() {}, lineTo() {}, getImageData() { return {}; } }; }
  }
  vm.runInNewContext(fs.readFileSync("browser-extension/background.js", "utf8"), { chrome, OffscreenCanvas });
  return { chrome, ui, ports, notices, alarms };
}
{
  const { chrome, ui, ports, notices, alarms } = setup();
  chrome.action.onClicked.fire({ url: "https://youtu.be/test" });
  assert.equal(ui.badge, "0%");
  assert.equal(ports[0].sent.stream, true);
  ports[0].onMessage.fire({ type: "progress", percent: 42, message: "Downloading" });
  assert.equal(ui.badge, "42%");
  assert.match(ui.title, /Downloading/);
  ports[0].onMessage.fire({ type: "progress", percent: 100, message: "Preparing file" });
  assert.equal(ui.badge, "99%");
  ports[0].onMessage.fire({ type: "done", ok: true, path: "C:/video.mp4" });
  ports[0].onDisconnect.fire();
  assert.equal(ui.badge, "✓");
  assert.match(ui.title, /video.mp4/);
  assert.equal(notices.length, 0);
  assert.equal(alarms.length, 1);
  chrome.alarms.onAlarm.fire({ name: "clear-result" });
  assert.equal(ui.badge, "");
}
{
  const { chrome, ui, ports, notices } = setup();
  chrome.action.onClicked.fire({ url: "https://youtu.be/a" });
  chrome.action.onClicked.fire({ url: "https://youtu.be/b" });
  assert.equal(ui.badge, "2↓");
  ports[0].onMessage.fire({ type: "done", ok: false, error: "Video unavailable" });
  assert.equal(ui.badge, "0%");
  ports[1].onMessage.fire({ type: "done", ok: true, path: "C:/b.mp4" });
  assert.equal(ui.badge, "!");
  assert.match(ui.title, /Video unavailable/);
  assert.equal(notices.length, 1);
}
{
  const { chrome, ui, ports, notices } = setup();
  chrome.action.onClicked.fire({ url: "https://youtu.be/a" });
  chrome.runtime.lastError = { message: "Specified native messaging host not found." };
  ports[0].onDisconnect.fire();
  assert.equal(ui.badge, "!");
  assert.match(notices[0].message, /Register-Browser/);
}
{
  const { chrome, ui, ports, notices } = setup();
  chrome.action.onClicked.fire({ url: "https://youtu.be/a" });
  ports[0].onMessage.fire({ type: "progress", percent: 20 });
  ports[0].onDisconnect.fire();
  assert.equal(ui.badge, "!");
  assert.match(notices[0].message, /before confirming completion/);
}
{
  const { chrome, ui, ports, notices } = setup();
  chrome.action.onClicked.fire({ url: "https://youtu.be/a" });
  ports[0].onMessage.fire({ ok: true });
  assert.equal(ui.badge, "!");
  assert.match(notices[0].message, /Update the Windows downloader/);
}
console.log("Extension state tests passed: progress, completion, reset, concurrency, disconnects, old-host detection.");
