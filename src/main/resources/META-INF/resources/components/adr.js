// marked.js CDN for markdown rendering
const MARKED_JS = 'https://cdn.jsdelivr.net/npm/marked@12/marked.min.js';

function renderAdrView(container, name) {
  container.innerHTML = `
    <div class="breadcrumb">
      <a onclick="navigate('catalog')">Catalog</a>
      <span>/</span>
      <a onclick="navigate('component', '${name}')">${name}</a>
      <span>/</span>
      <span>ADR</span>
    </div>

        <div class="tab-bar">
          <div class="tab" onclick="navigate('component', '${name}')">Overview</div>
          <div class="tab" onclick="navigate('component', '${name}/api')">API</div>
          <div class="tab tab--active" onclick="navigate('component', '${name}/adr')">ADR</div>
          <div class="tab" onclick="navigate('component', '${name}/docs')">Docs</div>
          <div class="tab" onclick="navigate('component', '${name}/graph')">Graph</div>
        </div>

    <div id="adr-content"><div class="loading">Loading ADRs...</div></div>
  `;

  fetch(`/api/catalog/components/${encodeURIComponent(name)}/adrs`)
    .then(r => r.json())
    .then(adrs => {
      const content = document.getElementById('adr-content');
      if (!adrs || adrs.length === 0) {
        content.innerHTML = '<div class="empty-state">No ADRs found for this component.</div>';
        return;
      }

      content.innerHTML = `
        <div class="adr-layout">
          <div class="adr-nav" id="adr-nav">
            ${adrs.map((adr, i) => `
              <div class="adr-nav-item${i === 0 ? ' adr-nav-item--active' : ''}"
                   id="adr-nav-${i}"
                   onclick="selectAdr(${i})">
                ${adr.name}
              </div>`).join('')}
          </div>
          <div class="adr-content markdown" id="adr-body">
            <div class="loading">Loading...</div>
          </div>
        </div>
      `;

      // Store ADRs for selection
      window._currentAdrs = adrs;

      loadMarked(() => selectAdr(0));
    })
    .catch(err => {
      const content = document.getElementById('adr-content');
      content.innerHTML = `<div class="empty-state">Failed to load ADRs: ${err.message}</div>`;
    });
}

function selectAdr(index) {
  const adrs = window._currentAdrs;
  if (!adrs || !adrs[index]) return;

  // Update active nav item
  document.querySelectorAll('.adr-nav-item').forEach((el, i) => {
    el.classList.toggle('adr-nav-item--active', i === index);
  });

  // Render markdown
  const body = document.getElementById('adr-body');
  if (body) {
    body.innerHTML = window.marked
      ? window.marked.parse(adrs[index].content)
      : `<pre>${adrs[index].content}</pre>`;
  }
}

function loadMarked(callback) {
  if (window.marked) {
    callback();
    return;
  }
  const script = document.createElement('script');
  script.src = MARKED_JS;
  script.onload = callback;
  document.head.appendChild(script);
}
