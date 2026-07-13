// marked.js CDN for markdown rendering
const MARKED_JS = 'https://cdn.jsdelivr.net/npm/marked@12/marked.min.js';

// ---- Global ADR index -------------------------------------------------------

let _adrsComponents = [];

function renderAdrsIndex(container, selectedComponent) {
  container.innerHTML = `
    <div class="page-header" style="margin-bottom:16px">
      <div class="page-title">ADRs</div>
      <div class="page-subtitle">Architecture Decision Records across all components</div>
    </div>
    <div class="adrs-layout">
      <div class="adrs-component-panel">
        <input class="catalog-filter-input" style="width:100%;margin-bottom:10px" type="text"
               placeholder="Filter components…"
               oninput="filterAdrsComponents(this.value)"
               autocomplete="off"/>
        <div id="adrs-component-list"><div class="loading">Loading…</div></div>
      </div>
      <div class="adrs-right" id="adrs-right">
        <div class="empty-state" style="margin-top:80px">Select a component to view its ADRs</div>
      </div>
    </div>
  `;

  fetch('/api/catalog/adrs/components')
    .then(r => r.json())
    .then(names => {
      _adrsComponents = (names || []).map(name => ({ name }));
      renderAdrsComponentList(_adrsComponents);
      if (selectedComponent) selectAdrsComponent(selectedComponent);
    })
    .catch(() => {
      document.getElementById('adrs-component-list').innerHTML =
        '<div class="empty-state">Failed to load components.</div>';
    });
}

function filterAdrsComponents(query) {
  const q = query.toLowerCase();
  const filtered = q
    ? _adrsComponents.filter(c => c.name.toLowerCase().includes(q))
    : _adrsComponents;
  renderAdrsComponentList(filtered);
}

function renderAdrsComponentList(components) {
  const list = document.getElementById('adrs-component-list');
  if (!list) return;
  if (components.length === 0) {
    list.innerHTML = '<div class="empty-state" style="margin-top:20px">No components found.</div>';
    return;
  }
  const active = window._adrsSelectedComponent;
  list.innerHTML = components.map(c => `
    <div class="adr-nav-item${c.name === active ? ' adr-nav-item--active' : ''}"
         id="adrs-comp-${c.name}"
         onclick="selectAdrsComponent('${c.name}')">
      ${c.name}
    </div>`).join('');
}

function selectAdrsComponent(componentName) {
  window._adrsSelectedComponent = componentName;
  // Update active highlight
  document.querySelectorAll('[id^="adrs-comp-"]').forEach(el => {
    el.classList.toggle('adr-nav-item--active', el.id === 'adrs-comp-' + componentName);
  });

  const right = document.getElementById('adrs-right');
  if (!right) return;
  right.innerHTML = '<div class="loading">Loading ADRs…</div>';

  fetch(`/api/catalog/components/${encodeURIComponent(componentName)}/adrs`)
    .then(r => r.json())
    .then(adrs => {
      if (!adrs || adrs.length === 0) {
        right.innerHTML = '<div class="empty-state" style="margin-top:80px">No ADRs found for this component.</div>';
        return;
      }
      right.innerHTML = `
        <div class="adr-layout" style="height:100%">
          <div class="adr-nav" id="adrs-adr-nav">
            ${adrs.map((adr, i) => `
              <div class="adr-nav-item${i === 0 ? ' adr-nav-item--active' : ''}"
                   id="adrs-adr-${i}"
                   onclick="selectAdrsAdr(${i})">
                ${adr.name}
              </div>`).join('')}
          </div>
          <div class="adr-content markdown" id="adrs-adr-body">
            <div class="loading">Loading…</div>
          </div>
        </div>`;
      window._adrsCurrentAdrs = adrs;
      loadMarked(() => selectAdrsAdr(0));
    })
    .catch(() => {
      right.innerHTML = '<div class="empty-state" style="margin-top:80px">Failed to load ADRs.</div>';
    });
}

function selectAdrsAdr(index) {
  const adrs = window._adrsCurrentAdrs;
  if (!adrs || !adrs[index]) return;
  document.querySelectorAll('[id^="adrs-adr-"]').forEach((el, i) => {
    el.classList.toggle('adr-nav-item--active', i === index);
  });
  const body = document.getElementById('adrs-adr-body');
  if (body) {
    body.innerHTML = window.marked
      ? window.marked.parse(adrs[index].content || '')
      : `<pre>${adrs[index].content || ''}</pre>`;
  }
}

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
