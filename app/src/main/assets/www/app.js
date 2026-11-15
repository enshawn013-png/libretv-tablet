/* LibreTV 本地版：搜索 / 选集 / 播放，全部在 APK 内运行，不需要任何服务器。
 *
 * 数据来源：各采集站的苹果CMS V10 接口（用户自己维护源列表）。
 * 播放：m3u8 → hls.js；mp4 → 原生 <video>；/share/ 播放页 → iframe。
 */

const DEFAULT_SOURCES = [
  { name: "暴风", url: "https://bfzyapi.com/api.php/provide/vod", on: true },
  { name: "最大", url: "http://zuidazy.me/api.php/provide/vod", on: true },
  { name: "量子", url: "https://cj.lziapi.com/api.php/provide/vod", on: true },
  { name: "155", url: "https://155api.com/api.php/provide/vod", on: true },
  { name: "光速", url: "https://api.guangsuapi.com/api.php/provide/vod", on: true },
  { name: "极速", url: "https://jszyapi.com/api.php/provide/vod", on: true },
  { name: "红牛", url: "http://hongniuzy2.com/api.php/provide/vod", on: true },
  { name: "虎牙", url: "https://www.huyaapi.com/api.php/provide/vod", on: true },
  { name: "速播", url: "https://subocj.com/api.php/provide/vod", on: true },
  { name: "金鹰", url: "https://jinyingzy.com/api.php/provide/vod", on: true },
  { name: "非凡", url: "https://ffzy5.tv/api.php/provide/vod", on: true },
  { name: "电影天堂", url: "http://caiji.dyttzyapi.com/api.php/provide/vod", on: true },
  { name: "吉吉", url: "https://caiji.jjzyapi.com/api.php/provide/vod", on: true },
  { name: "360", url: "https://360zy.com/api.php/provide/vod", on: true },
  { name: "U酷", url: "https://api.ukuapi88.com/api.php/provide/vod", on: true },
  { name: "爱奇艺", url: "https://iqiyizyapi.com/api.php/provide/vod", on: true },
  { name: "林中小屋 · 木机", url: "https://mujiziyuan00.com/api.php/provide/vod", on: true },
  { name: "猫眼", url: "https://api.maoyanapi.top/api.php/provide/vod", on: true },
  { name: "豆豆", url: "https://api.ddapi.cc/api.php/provide/vod", on: true },
];

const HOT = ['庆余年', '繁花', '流浪地球', '狂飙', '三体'];

const $ = (id) => document.getElementById(id);
const store = {
  get(k, d) {
    try { const v = localStorage.getItem(k); return v ? JSON.parse(v) : d; } catch { return d; }
  },
  set(k, v) { try { localStorage.setItem(k, JSON.stringify(v)); } catch {} },
};

let sources = store.get('ltv_sources', DEFAULT_SOURCES);
let hls = null;
let lastQuery = '';
let results = [];

/* ---------- 源管理 ---------- */
function renderSources() {
  const box = $('srcList');
  box.innerHTML = '';
  sources.forEach((s, i) => {
    const row = document.createElement('div');
    row.className = 'srcRow';
    row.innerHTML = `
      <input type="checkbox" ${s.on ? 'checked' : ''} data-i="${i}">
      <div class="n">${esc(s.name)}<div class="u">${esc(s.url)}</div></div>
      <button class="del" data-del="${i}">删除</button>`;
    box.appendChild(row);
  });
  box.querySelectorAll('input[type=checkbox]').forEach((el) => {
    el.onchange = () => {
      sources[el.dataset.i].on = el.checked;
      store.set('ltv_sources', sources);
      renderSrcSummary();
    };
  });
  box.querySelectorAll('button[data-del]').forEach((el) => {
    el.onclick = () => {
      sources.splice(Number(el.dataset.del), 1);
      store.set('ltv_sources', sources);
      renderSources();
      renderSrcSummary();
    };
  });
  renderSrcSummary();
}

function renderSrcSummary() {
  const on = sources.filter((s) => s.on);
  $('srcSummary').textContent = on.length
    ? `已启用 ${on.length} 个源：${on.map((s) => s.name).join('、')}`
    : '没有启用任何源 —— 点右上角「源」去勾选';
  const q = $('quick');
  q.innerHTML = '';
  HOT.forEach((w) => {
    const b = document.createElement('button');
    b.textContent = w;
    b.onclick = () => search(w);
    q.appendChild(b);
  });
}

/* ---------- 搜索 ---------- */
async function search(kw) {
  kw = (kw || '').trim();
  if (!kw) return;
  const on = sources.filter((s) => s.on);
  if (!on.length) { alert('还没有启用任何源'); return; }

  lastQuery = kw;
  $('kw').value = kw;
  $('home').hidden = true;
  $('results').hidden = false;
  $('resultHead').textContent = `正在搜索「${kw}」…（${on.length} 个源）`;
  $('grid').innerHTML = '';

  const jobs = on.map((s) => fetchList(s, kw).catch(() => []));
  const lists = await Promise.all(jobs);

  results = [];
  lists.forEach((list, i) => {
    const s = on[i];
    (list || []).forEach((v) => results.push({ ...v, _src: s, _srcName: s.name }));
  });

  $('resultHead').textContent = results.length
    ? `「${kw}」共 ${results.length} 条结果（点海报播放）`
    : `「${kw}」没有结果。换个源试试，或者换个关键词。`;
  renderGrid();
}

async function fetchList(src, kw) {
  const url = src.url.replace(/\/$/, '') + '/?ac=videolist&pg=1&wd=' + encodeURIComponent(kw);
  const r = await fetch(url);
  const d = await r.json();
  return d.list || [];
}

function renderGrid() {
  const g = $('grid');
  g.innerHTML = '';
  results.forEach((v, idx) => {
    const card = document.createElement('div');
    card.className = 'card';
    const pic = v.vod_pic && /^https?:/.test(v.vod_pic) ? v.vod_pic : '';
    card.innerHTML = `
      <img loading="lazy" src="${pic || 'data:image/svg+xml;charset=utf8,%3Csvg xmlns=%22http://www.w3.org/2000/svg%22/%3E'}" alt="">
      <div class="meta">
        <div class="name">${esc(v.vod_name || '')}</div>
        <div class="sub">${esc(v._srcName)} · ${esc(v.vod_year || '')} ${esc(v.vod_remarks || '')}</div>
      </div>`;
    card.onclick = () => openDetail(v);
    g.appendChild(card);
  });
}

/* ---------- 详情 / 播放 ---------- */
async function openDetail(v) {
  $('player').hidden = false;
  $('title').textContent = v.vod_name || '';
  $('eps').innerHTML = '<div class="muted" style="padding:8px 0">正在取播放地址…</div>';
  setVideo('');

  try {
    const url = v._src.url.replace(/\/$/, '') + '/?ac=detail&ids=' + encodeURIComponent(v.vod_id);
    const r = await fetch(url);
    const d = await r.json();
    const item = (d.list && d.list[0]) || v;
    const groups = parsePlay(item);
    renderEps(groups);
  } catch (e) {
    $('eps').innerHTML = '<div class="muted" style="padding:8px 0">取播放地址失败：' + esc(e.message) + '</div>';
  }
}

/** 解析 vod_play_url：多线路用 $$$ 分隔，集用 # 分隔，名称$地址 */
function parsePlay(item) {
  const froms = (item.vod_play_from || '').split('$$$');
  const urls = (item.vod_play_url || '').split('$$$');
  const out = [];
  urls.forEach((chunk, i) => {
    const eps = chunk.split('#').map((p) => {
      const [name, u] = p.split('$');
      return { name: name || ('第 ' + (i + 1) + ' 集'), url: u || name };
    }).filter((e) => e.url);
    if (eps.length) out.push({ from: froms[i] || ('线路' + (i + 1)), eps });
  });
  return out;
}

function renderEps(groups) {
  const box = $('eps');
  box.innerHTML = '';
  if (!groups.length) {
    box.innerHTML = '<div class="muted" style="padding:8px 0">这个源没有可播放地址，换一个源试试。</div>';
    return;
  }
  const g = groups[0];
  if (groups.length > 1) {
    const row = document.createElement('div');
    row.style.cssText = 'width:100%;display:flex;gap:8px;flex-wrap:wrap;margin-bottom:6px';
    groups.forEach((grp, gi) => {
      const b = document.createElement('button');
      b.textContent = grp.from;
      if (gi === 0) b.className = 'on';
      b.onclick = () => {
        row.querySelectorAll('button').forEach((x) => (x.className = ''));
        b.className = 'on';
        fillEps(grp);
      };
      row.appendChild(b);
    });
    box.appendChild(row);
  }
  fillEps(g);
}

function fillEps(grp) {
  const box = $('eps');
  [...box.querySelectorAll('button[data-ep]')].forEach((b) => b.remove());
  grp.eps.forEach((ep, i) => {
    const b = document.createElement('button');
    b.dataset.ep = i;
    b.textContent = ep.name;
    b.onclick = () => {
      box.querySelectorAll('button[data-ep]').forEach((x) => x.classList.remove('on'));
      b.classList.add('on');
      setVideo(ep.url);
    };
    box.appendChild(b);
  });
  // 自动播第一集
  const first = box.querySelector('button[data-ep]');
  if (first) first.click();
}

/* ---------- 播放器 ---------- */
let iframeEl = null;

function setVideo(url) {
  const video = $('video');
  const box = $('player');

  if (iframeEl) { iframeEl.remove(); iframeEl = null; }
  video.style.display = '';
  if (hls) { hls.destroy(); hls = null; }
  video.removeAttribute('src');
  video.load();

  if (!url) return;

  const isM3u8 = /\.m3u8(\?|$)/i.test(url);
  const isFile = /\.(mp4|webm|ogg)(\?|$)/i.test(url);

  if (isFile) {
    video.src = url;
    video.play().catch(() => {});
    return;
  }

  if (isM3u8) {
    if (video.canPlayType('application/vnd.apple.mpegurl')) {
      video.src = url;
      video.play().catch(() => {});
    } else if (window.Hls && window.Hls.isSupported()) {
      hls = new Hls({ lowLatencyMode: false });
      hls.loadSource(url);
      hls.attachMedia(video);
      hls.on(Hls.Events.MANIFEST_PARSED, () => video.play().catch(() => {}));
    } else {
      alert('这个设备不支持 HLS 播放');
    }
    return;
  }

  // 播放页（/share/、/watch/ 之类）：用 iframe 当内嵌浏览器
  video.style.display = 'none';
  iframeEl = document.createElement('iframe');
  iframeEl.src = url;
  iframeEl.style.cssText = 'flex:1;width:100%;border:0;background:#000';
  iframeEl.allow = 'autoplay; fullscreen; encrypted-media';
  box.insertBefore(iframeEl, $('eps'));
}

/* ---------- 交互 ---------- */
function esc(s) {
  return String(s == null ? '' : s).replace(/[&<>"']/g, (c) =>
    ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
}

$('go').onclick = () => search($('kw').value);
$('kw').addEventListener('keydown', (e) => { if (e.key === 'Enter') search($('kw').value); });
$('back').onclick = () => {
  $('player').hidden = true;
  setVideo('');
  $('results').hidden = results.length === 0;
  $('home').hidden = results.length > 0;
};
$('openSrc').onclick = () => { renderSources(); $('srcPanel').hidden = false; };
$('closeSrc').onclick = () => { $('srcPanel').hidden = true; };
$('addSrc').onclick = () => {
  const n = $('newSrcName').value.trim();
  let u = $('newSrcUrl').value.trim();
  if (!n || !u) return;
  if (!/^https?:\/\//.test(u)) u = 'https://' + u;
  sources.push({ name: n, url: u, on: true });
  store.set('ltv_sources', sources);
  $('newSrcName').value = '';
  $('newSrcUrl').value = '';
  renderSources();
};

renderSrcSummary();
