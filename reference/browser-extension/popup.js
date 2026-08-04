
const enabled = document.getElementById("enabled");
const statusBox = document.getElementById("status");
const privacyStatus = document.getElementById("privacyStatus");
const optionsBtn = document.getElementById("options");

async function load() {
  const data = await chrome.storage.local.get(["enabled"]);
  enabled.checked = Boolean(data.enabled);
  render();
}

function render() {
  statusBox.textContent = enabled.checked ? "Proxy: 195.209.210.144:28443" : "Proxy: off";
  privacyStatus.textContent = enabled.checked ? "WebRTC protection: on" : "WebRTC protection: off";
}

enabled.addEventListener("change", async () => {
  await chrome.storage.local.set({ enabled: enabled.checked });
  render();
});

optionsBtn.addEventListener("click", () => chrome.runtime.openOptionsPage());
load();
