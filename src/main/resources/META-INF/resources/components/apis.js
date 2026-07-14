let apisData = [];
let apisFilter = '';

function renderApis(container) {
  apisFilter = '';
  container.innerHTML = `
    <div class="page-header" style="display:flex;align-items:center;justify-content:space-between;margin-bottom:20px">
      <div>
        <div class="page-title">APIs</div>
        <div class="page-subtitle">All registered API definitions</div>
      </div>
      <input class="catalog-filter-input" type="text" placeholder="Filter APIs…"
             oninput="apisFilter=this.value; renderApisRows()"
             autocomplete="off"/>
    </div>
    <div class="table-container">
      <table id="apis-table">
        <thead>
          <tr>
            <th class="col-nowrap col-name">Name</th>
            <th class="col-nowrap col-owner">Owner</th>
            <th class="col-nowrap col-type">Type</th>
            <th class="col-nowrap col-lifecycle">Lifecycle</th>
            <th class="col-description">Description</th>
            <th class="col-nowrap col-tags">Tags</th>
          </tr>
        </thead>
        <tbody id="apis-tbody">
          <tr><td colspan="6" class="loading">Loading...</td></tr>
        </tbody>
      </table>
    </div>
  `;

  fetch('/api/catalog/apis')
    .then(r => r.json())
    .then(apis => {
      apisData = (apis || []).slice().sort((a, b) => (a.name || '').localeCompare(b.name || ''));
      renderApisRows();
    })
    .catch(err => {
      document.getElementById('apis-tbody').innerHTML =
        `<tr><td colspan="6" class="empty-state">Failed to load APIs: ${err.message}</td></tr>`;
    });
}

function renderApisRows() {
  const tbody = document.getElementById('apis-tbody');
  if (!tbody) return;

  const q = apisFilter.toLowerCase();
  const filtered = q
    ? apisData.filter(a =>
        (a.name        || '').toLowerCase().includes(q) ||
        (a.owner       || '').toLowerCase().includes(q) ||
        (a.type        || '').toLowerCase().includes(q) ||
        (a.description || '').toLowerCase().includes(q)
      )
    : apisData;

  if (filtered.length === 0) {
    tbody.innerHTML = '<tr><td colspan="6" class="empty-state">No APIs match the filter.</td></tr>';
    return;
  }

  tbody.innerHTML = filtered.map(a => `
    <tr>
      <td class="col-nowrap col-name">
        ${a.definitionUrl
          ? `<a class="component-link" onclick="navigate('api', encodeURIComponent('${a.name}'))">${a.name}</a>`
          : `<span>${a.name}</span>`
        }
      </td>
      <td class="col-nowrap col-owner">${a.owner || '—'}</td>
      <td class="col-nowrap col-type">${a.type ? `<span class="tag">${a.type}</span>` : '—'}</td>
      <td class="col-nowrap col-lifecycle">${lifecycleBadge(a.lifecycle)}</td>
      <td class="col-description"><span class="desc-text">${shortDesc(a.description)}</span></td>
      <td class="col-nowrap col-tags">${renderTags(a.tags)}</td>
    </tr>
  `).join('');
}

// Render OpenAPI view directly from a standalone API entry (not via component)
function renderApiViewByName(container, apiName) {
  fetch('/api/catalog/apis')
    .then(r => r.json())
    .then(apis => {
      const api = apis.find(a => a.name === decodeURIComponent(apiName));
      if (!api) {
        container.innerHTML = `<div class="empty-state">API not found: ${apiName}</div>`;
        return;
      }

      container.innerHTML = `
        <div class="breadcrumb">
          <a onclick="navigate('apis')">APIs</a>
          <span>/</span>
          <span>${api.name}</span>
        </div>

        <div class="card" style="margin-bottom:16px">
          <div class="card-title">${api.name}</div>
          <div class="card-grid">
            <div class="card-field"><label>Owner</label><span>${api.owner || '—'}</span></div>
            <div class="card-field"><label>Type</label><span>${api.type || '—'}</span></div>
            <div class="card-field"><label>Lifecycle</label><span>${lifecycleBadge(api.lifecycle)}</span></div>
          </div>
          ${api.description ? `<div class="card-description"><p>${api.description.replace(/\n/g,'<br>')}</p></div>` : ''}
        </div>

        <div class="card" style="padding:16px">
          <div id="swagger-ui"><div class="loading">Loading OpenAPI spec...</div></div>
        </div>
      `;

      const specUrl = `/api/catalog/apis/${encodeURIComponent(api.name)}/spec`;
      loadSwaggerUiCdn(() => loadSwaggerUi(specUrl));
    })
    .catch(err => {
      container.innerHTML = `<div class="empty-state">Error: ${err.message}</div>`;
    });
}
