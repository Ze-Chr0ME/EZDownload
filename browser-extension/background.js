const active = new Map();
let lastResult = null;
let nextId = 0;

function progressIcon(percent, color) {
  const result = {};
  for (const size of [16, 32]) {
    const canvas = new OffscreenCanvas(size, size);
    const ctx = canvas.getContext("2d");
    ctx.scale(size / 32, size / 32);
    ctx.lineCap = "round";
    ctx.lineWidth = 3;
    ctx.strokeStyle = "#4b5860";
    ctx.beginPath(); ctx.arc(16, 16, 13, 0, Math.PI * 2); ctx.stroke();
    ctx.strokeStyle = color;
    ctx.beginPath(); ctx.arc(16, 16, 13, -Math.PI / 2, -Math.PI / 2 + Math.PI * 2 * Math.max(0.07, percent / 100)); ctx.stroke();
    ctx.lineWidth = 2.5;
    ctx.beginPath(); ctx.moveTo(16, 9); ctx.lineTo(16, 22);
    ctx.moveTo(11, 17); ctx.lineTo(16, 22); ctx.lineTo(21, 17); ctx.stroke();
    result[size] = ctx.getImageData(0, 0, size, size);
  }
  return result;
}

function render() {
  if (active.size) {
    const jobs = [...active.values()];
    const percent = Math.floor(jobs.reduce((total, job) => total + job.percent, 0) / jobs.length);
    chrome.action.setIcon({ imageData: progressIcon(percent, "#4cdbab") });
    chrome.action.setBadgeBackgroundColor({ color: "#146c55" });
    chrome.action.setBadgeText({ text: active.size > 1 ? `${active.size}↓` : `${percent}%` });
    chrome.action.setTitle({ title: `EzSocial · ${active.size} download${active.size > 1 ? "s" : ""} · ${percent}%\n${jobs.map(job => job.message).join("\n")}` });
  } else if (lastResult) {
    chrome.action.setIcon({ imageData: progressIcon(100, lastResult.ok ? "#4cdbab" : "#f87171") });
    chrome.action.setBadgeBackgroundColor({ color: lastResult.ok ? "#146c55" : "#b42318" });
    chrome.action.setBadgeText({ text: lastResult.ok ? "✓" : "!" });
    chrome.action.setTitle({ title: lastResult.ok ? `Saved · ${lastResult.path}` : `Download failed · ${lastResult.error}` });
  } else {
    chrome.action.setIcon({ path: "icon.png" });
    chrome.action.setBadgeText({ text: "" });
    chrome.action.setTitle({ title: "Download this video with EzSocial" });
  }
}

function reset() { lastResult = null; render(); }
chrome.runtime.onInstalled.addListener(() => {
  chrome.contextMenus.removeAll(() => {
    chrome.contextMenus.create({ id: "ezsocial", title: "Download with EzSocial", contexts: ["page", "link", "video"] });
  });
  reset();
});
chrome.runtime.onStartup.addListener(reset);
chrome.alarms.onAlarm.addListener(alarm => {
  if (alarm.name === "clear-result" && !active.size) reset();
});

function send(url) {
  if (!/^https?:\/\//i.test(url || "")) return;
  chrome.alarms.clear("clear-result");
  lastResult = null;
  const id = ++nextId;
  let finished = false;
  let receivedProgress = false;
  active.set(id, { percent: 0, message: "Connecting to downloader…" });
  render();
  const finish = result => {
    if (finished) return;
    finished = true;
    active.delete(id);
    // Keep a concurrent failure visible when another download finishes successfully.
    if (!lastResult || lastResult.ok || !result.ok) lastResult = result;
    render();
    if (!active.size && lastResult.ok) chrome.alarms.create("clear-result", { delayInMinutes: 0.15 });
    if (!result.ok) chrome.notifications.create({
      type: "basic", iconUrl: "icon.png", title: "EzSocial download failed",
      message: String(result.error || "Unknown download error").slice(0, 500)
    });
  };
  try {
    const port = chrome.runtime.connectNative("com.ezsocial.video");
    port.onMessage.addListener(message => {
      if (finished) return;
      if (message.type === "progress") {
        receivedProgress = true;
        const percent = Number(message.percent);
        active.set(id, { percent: Number.isFinite(percent) ? Math.min(99, Math.max(0, percent)) : 0, message: String(message.message || "Downloading…") });
        render();
      } else if (message.type === "done" || message.ok === false) {
        finish(message);
      } else if (message.ok === true) {
        finish({ ok: false, error: "Update the Windows downloader to enable live progress. The older app may still finish this download." });
      }
    });
    port.onDisconnect.addListener(() => {
      const error = chrome.runtime.lastError?.message;
      if (finished) return;
      finish({ ok: false, error: receivedProgress
        ? `Downloader disconnected before confirming completion. ${error || ""}`
        : `Could not connect to the downloader. Run Register-Browser.ps1 with this extension's ID. ${error || ""}` });
    });
    port.postMessage({ url, stream: true });
  } catch (error) {
    finish({ ok: false, error: error.message });
  }
}
chrome.action.onClicked.addListener(tab => send(tab.url));
chrome.contextMenus.onClicked.addListener((info, tab) => {
  if (info.menuItemId === "ezsocial") send(info.linkUrl || info.pageUrl || tab?.url);
});
