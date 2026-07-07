function renderDocsView(container, name, initialIndex) {
  container.innerHTML = `
    <div class="breadcrumb">
      <a onclick="navigate('catalog')">Catalog</a>
      <span>/</span>
      <a onclick="navigate('component', '${name}')">${name}</a>
      <span>/</span>
      <span>Docs</span>
    </div>

    <div class="tab-bar">
      <div class="tab" onclick="navigate('component', '${name}')">Overview</div>
      <div class="tab" onclick="navigate('component', '${name}/api')">API</div>
      <div class="tab" onclick="navigate('component', '${name}/adr')">ADR</div>
      <div class="tab tab--active" onclick="navigate('component', '${name}/docs')">Docs</div>
      <div class="tab" onclick="navigate('component', '${name}/graph')">Graph</div>
    </div>

    <div id="docs-content"><div class="loading">Loading Docs...</div></div>
  `;

  fetch(`/api/catalog/components/${encodeURIComponent(name)}/docs`)
    .then(r => r.json())
    .then(docs => {
      const content = document.getElementById('docs-content');
      if (!docs || docs.length === 0) {
        content.innerHTML = '<div class="empty-state">No documentation found for this component.</div>';
        return;
      }

      content.innerHTML = `
        <div class="adr-layout">
          <div class="adr-nav" id="docs-nav">
            ${docs.map((doc, i) => `
              <div class="adr-nav-item${i === 0 ? ' adr-nav-item--active' : ''}"
                   id="docs-nav-${i}"
                   onclick="selectDoc(${i})">
                ${doc.name}
              </div>`).join('')}
          </div>
          <div class="adr-content markdown" id="docs-body">
            <div class="loading">Loading...</div>
          </div>
        </div>
      `;

      window._currentDocs = docs;
      window._currentDocsComponent = name;

      loadMarked(() => selectDoc(initialIndex || 0));
    })
    .catch(err => {
      const content = document.getElementById('docs-content');
      content.innerHTML = `<div class="empty-state">Failed to load docs: ${err.message}</div>`;
    });
}

function selectDoc(index) {
  const docs = window._currentDocs;
  if (!docs || !docs[index]) return;

  document.querySelectorAll('.adr-nav-item').forEach((el, i) => {
    el.classList.toggle('adr-nav-item--active', i === index);
  });

  const body = document.getElementById('docs-body');
  if (!body) return;

  if (window.marked) {
    // Rewrite relative image src to the asset endpoint
    const componentName = window._currentDocsComponent;
    const renderer = new window.marked.Renderer();
    renderer.image = (href, title, text) => {
      // href may be an object in newer marked versions
      const src = (typeof href === 'object' && href !== null) ? (href.href || href) : href;
      const resolvedSrc = src.startsWith('http') ? src
        : `/api/catalog/components/${encodeURIComponent(componentName)}/docs/assets/${src}`;
      const titleAttr = title ? ` title="${title}"` : '';
      return `<img src="${resolvedSrc}" alt="${text}"${titleAttr} style="max-width:100%;height:auto;">`;
    };
    body.innerHTML = window.marked.parse(docs[index].content, { renderer });
  } else {
    body.innerHTML = `<pre>${docs[index].content}</pre>`;
  }
}

// Sidebar Docs index: lists all components that have techdocs, with their pages as sub-items
function renderDocsIndex(container) {
  container.innerHTML = `
    <div class="page-header">
      <div class="page-title">Docs</div>
      <div class="page-subtitle">Technical documentation across all components</div>
    </div>
    <div id="docs-index-content"><div class="loading">Loading...</div></div>
  `;

  fetch('/api/catalog/components')
    .then(r => r.json())
    .then(components => {
      const withDocs = components.filter(c =>
        c.annotations && c.annotations['backstage.io/techdocs-ref']
      );
      const content = document.getElementById('docs-index-content');
      if (withDocs.length === 0) {
        content.innerHTML = '<div class="empty-state">No components with documentation found.</div>';
        return;
      }

      content.innerHTML = `
        <div class="table-container">
          <table>
            <thead><tr><th>Component</th><th>Description</th></tr></thead>
            <tbody>
              ${withDocs.map(c => `
                <tr>
                  <td><a class="component-link" onclick="navigate('component', '${c.name}/docs')">${c.name}</a></td>
                  <td>${c.description ? c.description.replace(/\n.*/s, '').trim() : '—'}</td>
                </tr>
              `).join('')}
            </tbody>
          </table>
        </div>
      `;
    })
    .catch(err => {
      document.getElementById('docs-index-content').innerHTML =
        `<div class="empty-state">Failed to load docs: ${err.message}</div>`;
    });
}
