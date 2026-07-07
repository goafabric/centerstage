// Swagger UI CDN
const SWAGGER_UI_CSS = 'https://unpkg.com/swagger-ui-dist@5/swagger-ui.css';
const SWAGGER_UI_JS  = 'https://unpkg.com/swagger-ui-dist@5/swagger-ui-bundle.js';

function renderApiView(container, name) {
  container.innerHTML = `
    <div class="breadcrumb">
      <a onclick="navigate('catalog')">Catalog</a>
      <span>/</span>
      <a onclick="navigate('component', '${name}')">${name}</a>
      <span>/</span>
      <span>API</span>
    </div>

        <div class="tab-bar">
          <div class="tab" onclick="navigate('component', '${name}')">Overview</div>
          <div class="tab tab--active" onclick="navigate('component', '${name}/api')">API</div>
          <div class="tab" onclick="navigate('component', '${name}/adr')">ADR</div>
          <div class="tab" onclick="navigate('component', '${name}/docs')">Docs</div>
          <div class="tab" onclick="navigate('component', '${name}/graph')">Graph</div>
        </div>

    <div id="api-content"><div class="loading">Loading APIs...</div></div>
  `;

  fetch(`/api/catalog/components/${encodeURIComponent(name)}/apis`)
    .then(r => r.json())
    .then(apis => {
      const content = document.getElementById('api-content');
      if (!apis || apis.length === 0) {
        content.innerHTML = '<div class="empty-state">No APIs registered for this component.</div>';
        return;
      }

      const openApiEntries = apis.filter(a => a.type === 'openapi' && a.definitionUrl);
      if (openApiEntries.length === 0) {
        content.innerHTML = `
          <div class="card">
            <div class="card-title" style="font-size:15px">APIs</div>
            ${apis.map(a => `
              <div style="margin-bottom:12px">
                <strong>${a.name}</strong> <span class="tag">${a.type || ''}</span>
                ${a.description ? `<p style="margin-top:6px;color:#6b7280">${a.description}</p>` : ''}
              </div>`).join('')}
          </div>`;
        return;
      }

      // Render tabs if multiple, default to first
      const api = openApiEntries[0];
      const rawUrl = api.definitionUrl;

      // Convert GitHub blob URL to raw URL
      const resolvedUrl = convertToRawUrl(rawUrl);

      content.innerHTML = `
        <div class="card" style="padding:16px">
          ${openApiEntries.length > 1 ? `
            <div style="margin-bottom:16px;display:flex;gap:8px">
              ${openApiEntries.map((a, i) => `
                <span class="tag" style="cursor:pointer" onclick="loadSwaggerUi('${convertToRawUrl(a.definitionUrl)}')">${a.name}</span>
              `).join('')}
            </div>` : ''}
          <div id="swagger-ui"></div>
        </div>`;

      loadSwaggerUiCdn(() => loadSwaggerUi(resolvedUrl));
    })
    .catch(err => {
      const content = document.getElementById('api-content');
      content.innerHTML = `<div class="empty-state">Failed to load APIs: ${err.message}</div>`;
    });
}

function convertToRawUrl(url) {
  if (!url) return url;
  // https://github.com/owner/repo/blob/branch/path -> https://raw.githubusercontent.com/owner/repo/branch/path
  return url.replace(
    /https:\/\/github\.com\/([^/]+)\/([^/]+)\/blob\/([^/]+)\/(.*)/,
    'https://raw.githubusercontent.com/$1/$2/$3/$4'
  );
}

function loadSwaggerUiCdn(callback) {
  if (window.SwaggerUIBundle) {
    callback();
    return;
  }
  // Load CSS
  if (!document.getElementById('swagger-css')) {
    const link = document.createElement('link');
    link.id = 'swagger-css';
    link.rel = 'stylesheet';
    link.href = SWAGGER_UI_CSS;
    document.head.appendChild(link);
  }
  // Load JS
  const script = document.createElement('script');
  script.src = SWAGGER_UI_JS;
  script.onload = callback;
  document.head.appendChild(script);
}

function loadSwaggerUi(url) {
  if (!window.SwaggerUIBundle) return;
  window.SwaggerUIBundle({
    url: url,
    dom_id: '#swagger-ui',
    presets: [SwaggerUIBundle.presets.apis, SwaggerUIBundle.SwaggerUIStandalonePreset],
    layout: 'BaseLayout',
    deepLinking: true,
    tryItOutEnabled: true
  });
}
