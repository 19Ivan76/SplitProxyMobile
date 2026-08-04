
const PROXY_HOST = "195.209.210.144";
const PROXY_PORT = 28443;
const WEBRTC_POLICY_VALUE = "disable_non_proxied_udp";

const DEFAULT_DOMAINS = [
  "ifconfig.me",
  "openai.com", "chatgpt.com", "auth.openai.com", "cdn.openai.com", "oaistatic.com", "oaiusercontent.com",
  "youtube.com", "youtu.be", "youtube-nocookie.com", "googlevideo.com", "ytimg.com",
  "youtubei.googleapis.com", "ggpht.com", "googleapis.com", "googleusercontent.com", "gstatic.com"
];

function getSettings() {
  return chrome.storage.local.get(["enabled", "domains"]);
}

function normalizeDomain(domain) {
  return String(domain || "")
    .trim()
    .toLowerCase()
    .replace(/^https?:\/\//, "")
    .replace(/\/.*$/, "")
    .replace(/^\*\./, "");
}

function generatePacScript(domains) {
  const safeDomains = (domains || []).map(normalizeDomain).filter(Boolean);

  if (safeDomains.length === 0) {
    return 'function FindProxyForURL(url, host) { return "DIRECT"; }';
  }

  const conditions = safeDomains.map(domain => {
    return '(dnsDomainIs(host, "' + domain + '") || shExpMatch(host, "*.' + domain + '"))';
  }).join(" || ");

  return 'function FindProxyForURL(url, host) { if (' + conditions + ') { return "PROXY ' + PROXY_HOST + ':' + PROXY_PORT + '"; } return "DIRECT"; }';
}

async function setWebRtcProtection(enabled) {
  if (!chrome.privacy || !chrome.privacy.network || !chrome.privacy.network.webRTCIPHandlingPolicy) {
    return;
  }

  try {
    if (enabled) {
      await chrome.privacy.network.webRTCIPHandlingPolicy.set({
        value: WEBRTC_POLICY_VALUE,
        scope: "regular"
      });
    } else {
      await chrome.privacy.network.webRTCIPHandlingPolicy.clear({
        scope: "regular"
      });
    }
  } catch (error) {
    console.warn("WebRTC policy error:", error);
  }
}

async function applyProxy() {
  const settings = await getSettings();

  if (!settings.enabled) {
    await setWebRtcProtection(false);
    chrome.proxy.settings.clear({ scope: "regular" });
    return;
  }

  const pacScript = generatePacScript(settings.domains || []);

  chrome.proxy.settings.set({
    value: {
      mode: "pac_script",
      pacScript: { data: pacScript }
    },
    scope: "regular"
  });

  await setWebRtcProtection(true);
}

chrome.runtime.onInstalled.addListener(async function () {
  const current = await chrome.storage.local.get();
  if (!current.domains) {
    await chrome.storage.local.set({ enabled: false, domains: DEFAULT_DOMAINS });
  }
  applyProxy();
});

chrome.runtime.onStartup.addListener(function () {
  applyProxy();
});

chrome.storage.onChanged.addListener(function () {
  applyProxy();
});

applyProxy();
