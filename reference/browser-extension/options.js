
const domainList = document.getElementById("domainList");
const newDomain = document.getElementById("newDomain");
const addBtn = document.getElementById("addBtn");
const addYoutubeBtn = document.getElementById("addYoutubeBtn");
const addOpenAiBtn = document.getElementById("addOpenAiBtn");
const exportBtn = document.getElementById("exportBtn");
const importBtn = document.getElementById("importBtn");
const resetBtn = document.getElementById("resetBtn");
const jsonBox = document.getElementById("jsonBox");

const OPENAI_DOMAINS = ["openai.com", "chatgpt.com", "auth.openai.com", "cdn.openai.com", "oaistatic.com", "oaiusercontent.com"];
const YOUTUBE_DOMAINS = ["youtube.com", "youtu.be", "youtube-nocookie.com", "googlevideo.com", "ytimg.com", "youtubei.googleapis.com", "ggpht.com", "googleapis.com", "googleusercontent.com", "gstatic.com"];
const DEFAULT_DOMAINS = ["ifconfig.me", ...OPENAI_DOMAINS, ...YOUTUBE_DOMAINS];

function normalizeDomain(domain) {
  return String(domain || "")
    .trim()
    .toLowerCase()
    .replace(/^https?:\/\//, "")
    .replace(/\/.*$/, "")
    .replace(/^\*\./, "");
}

async function getDomains() {
  const data = await chrome.storage.local.get("domains");
  return data.domains || [];
}

async function setDomains(domains) {
  const clean = Array.from(new Set((domains || []).map(normalizeDomain).filter(Boolean)));
  await chrome.storage.local.set({ domains: clean });
}

async function addDomains(domainsToAdd) {
  const domains = await getDomains();
  await setDomains([...domains, ...domainsToAdd]);
  loadDomains();
}

async function loadDomains() {
  const domains = await getDomains();
  domainList.innerHTML = "";

  domains.forEach(domain => {
    const li = document.createElement("li");
    li.className = "domain-item";

    const span = document.createElement("span");
    span.textContent = domain;

    const remove = document.createElement("button");
    remove.textContent = "Delete";
    remove.addEventListener("click", async () => {
      const current = await getDomains();
      await setDomains(current.filter(d => d !== domain));
      loadDomains();
    });

    li.appendChild(span);
    li.appendChild(remove);
    domainList.appendChild(li);
  });
}

addBtn.addEventListener("click", async () => {
  const domain = normalizeDomain(newDomain.value);
  if (!domain) return;
  await addDomains([domain]);
  newDomain.value = "";
});

newDomain.addEventListener("keydown", event => {
  if (event.key === "Enter") addBtn.click();
});

addYoutubeBtn.addEventListener("click", async () => addDomains(YOUTUBE_DOMAINS));
addOpenAiBtn.addEventListener("click", async () => addDomains(OPENAI_DOMAINS));

exportBtn.addEventListener("click", async () => {
  const domains = await getDomains();
  jsonBox.value = JSON.stringify({ domains }, null, 2);
});

importBtn.addEventListener("click", async () => {
  try {
    const parsed = JSON.parse(jsonBox.value);
    await setDomains(parsed.domains || []);
    loadDomains();
    alert("Imported");
  } catch (error) {
    alert("Invalid JSON");
  }
});

resetBtn.addEventListener("click", async () => {
  await setDomains(DEFAULT_DOMAINS);
  loadDomains();
});

loadDomains();
