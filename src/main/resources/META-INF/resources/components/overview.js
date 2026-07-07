function renderOverview(container, name) {
  container.innerHTML = `
    <div class="breadcrumb">
      <a onclick="navigate('catalog')">Catalog</a>
      <span>/</span>
      <span>${name}</span>
    </div>
    <div class="loading">Loading...</div>
  `;

  fetch(`/api/catalog/components/${encodeURIComponent(name)}`)
    .then(r => {
      if (!r.ok) throw new Error('Component not found');
      return r.json();
    })
    .then(c => {
      container.innerHTML = `
        <div class="breadcrumb">
          <a onclick="navigate('catalog')">Catalog</a>
          <span>/</span>
          <span>${c.name}</span>
        </div>

        <div class="tab-bar">
          <div class="tab tab--active" onclick="navigate('component', '${name}')">Overview</div>
          <div class="tab" onclick="navigate('component', '${name}/api')">API</div>
          <div class="tab" onclick="navigate('component', '${name}/adr')">ADR</div>
          <div class="tab" onclick="navigate('component', '${name}/docs')">Docs</div>
          <div class="tab" onclick="navigate('component', '${name}/graph')">Graph</div>
        </div>

        <div class="card">
          <div class="card-title">
            ${c.name}
            ${lifecycleBadge(c.lifecycle)}
          </div>

          <div class="card-grid">
            <div class="card-field">
              <label>Owner</label>
              <span>${c.owner || '—'}</span>
            </div>
            <div class="card-field">
              <label>Type</label>
              <span>${c.type || '—'}</span>
            </div>
            <div class="card-field">
              <label>Lifecycle</label>
              <span>${c.lifecycle || '—'}</span>
            </div>
          </div>

          ${c.tags && c.tags.length ? `
            <div style="margin-top:12px">
              <div class="tag-list">${c.tags.map(t => `<span class="tag">${t}</span>`).join('')}</div>
            </div>` : ''}

          ${c.description ? `
            <div class="card-description">
              ${renderDescription(c.description)}
            </div>` : ''}
        </div>

        ${c.links && c.links.length ? `
          <div class="card">
            <div class="card-title" style="font-size:15px">Links</div>
            <ul class="links-list">
              ${c.links.map(l => `<li><a href="${l.url}" target="_blank">${l.title || l.url}</a></li>`).join('')}
            </ul>
          </div>` : ''}
      `;
    })
    .catch(err => {
      container.innerHTML = `<div class="empty-state">Error: ${err.message}</div>`;
    });
}

function renderDescription(desc) {
  if (!desc) return '';

  // Extract badge markdown: [![alt](imgUrl)](linkUrl)
  const badgeRegex = /\[!\[([^\]]*)\]\(([^)]+)\)\]\(([^)]+)\)/g;
  const badges = [];
  let match;
  while ((match = badgeRegex.exec(desc)) !== null) {
    badges.push({ alt: match[1], imgUrl: match[2], linkUrl: match[3] });
  }

  // Strip badge lines from text
  const plainText = desc
    .replace(/\[!\[.*?\]\(.*?\)\]\(.*?\)/g, '')
    .replace(/\n{3,}/g, '\n\n')
    .trim();

  let html = '';
  if (plainText) {
    html += `<p>${plainText.replace(/\n/g, '<br>')}</p>`;
  }
  if (badges.length > 0) {
    html += `<div class="badges-row">
      ${badges.map(b => `<a href="${b.linkUrl}" target="_blank"><img src="${b.imgUrl}" alt="${b.alt}" title="${b.alt}"/></a>`).join('')}
    </div>`;
  }
  return html;
}
