function renderApis(container) {
  container.innerHTML = `
    <div class="page-header">
      <div class="page-title">APIs</div>
      <div class="page-subtitle">All registered API definitions</div>
    </div>
    <div class="table-container">
      <table>
        <thead>
          <tr>
            <th>Name</th>
            <th>Owner</th>
            <th>Type</th>
            <th>Lifecycle</th>
            <th>Description</th>
            <th>Tags</th>
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
      const tbody = document.getElementById('apis-tbody');
      if (!apis || apis.length === 0) {
        tbody.innerHTML = '<tr><td colspan="6" class="empty-state">No APIs found.</td></tr>';
        return;
      }
      tbody.innerHTML = apis.map(a => `
        <tr>
          <td>
            ${a.definitionUrl
              ? `<a class="component-link" onclick="navigate('api', encodeURIComponent('${a.name}'))">${a.name}</a>`
              : `<span>${a.name}</span>`
            }
          </td>
          <td>${a.owner || '—'}</td>
          <td>${a.type ? `<span class="tag">${a.type}</span>` : '—'}</td>
          <td>${lifecycleBadge(a.lifecycle)}</td>
          <td>${shortDesc(a.description)}</td>
          <td>${renderTags(a.tags)}</td>
        </tr>
      `).join('');
    })
    .catch(err => {
      const tbody = document.getElementById('apis-tbody');
      tbody.innerHTML = `<tr><td colspan="6" class="empty-state">Failed to load APIs: ${err.message}</td></tr>`;
    });
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

      const resolvedUrl = convertToRawUrl(api.definitionUrl);
      loadSwaggerUiCdn(() => loadSwaggerUi(resolvedUrl));
    })
    .catch(err => {
      container.innerHTML = `<div class="empty-state">Error: ${err.message}</div>`;
    });
}
