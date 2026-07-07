let catalogData = [];
let catalogSort = { col: null, dir: 1 };

function renderCatalog(container) {
  container.innerHTML = `
    <div class="page-header">
      <div class="page-title">Catalog</div>
      <div class="page-subtitle">All registered components</div>
    </div>
    <div class="table-container">
      <table id="catalog-table">
        <thead>
          <tr>
            <th class="col-nowrap col-name sortable" data-col="name">Name <span class="sort-icon"></span></th>
            <th class="col-nowrap col-owner sortable" data-col="owner">Owner <span class="sort-icon"></span></th>
            <th class="col-nowrap col-type sortable" data-col="type">Type <span class="sort-icon"></span></th>
            <th class="col-nowrap col-lifecycle sortable" data-col="lifecycle">Lifecycle <span class="sort-icon"></span></th>
            <th class="col-description sortable" data-col="description">Description <span class="sort-icon"></span></th>
            <th class="col-nowrap col-tags sortable" data-col="tags">Tags <span class="sort-icon"></span></th>
          </tr>
        </thead>
        <tbody id="catalog-tbody">
          <tr><td colspan="6" class="loading">Loading...</td></tr>
        </tbody>
      </table>
    </div>
  `;

  document.getElementById('catalog-table').addEventListener('click', e => {
    const th = e.target.closest('th.sortable');
    if (!th) return;
    const col = th.dataset.col;
    if (catalogSort.col === col) {
      catalogSort.dir *= -1;
    } else {
      catalogSort.col = col;
      catalogSort.dir = 1;
    }
    renderCatalogRows();
    updateSortIcons();
  });

  fetch('/api/catalog/components')
    .then(r => r.json())
    .then(components => {
      catalogData = components || [];
      if (catalogData.length === 0) {
        document.getElementById('catalog-tbody').innerHTML =
          '<tr><td colspan="6" class="empty-state">No components found.</td></tr>';
        return;
      }
      renderCatalogRows();
    })
    .catch(err => {
      document.getElementById('catalog-tbody').innerHTML =
        `<tr><td colspan="6" class="empty-state">Failed to load catalog: ${err.message}</td></tr>`;
    });
}

function renderCatalogRows() {
  const sorted = [...catalogData].sort((a, b) => {
    if (!catalogSort.col) return 0;
    const av = sortKey(a, catalogSort.col);
    const bv = sortKey(b, catalogSort.col);
    return av.localeCompare(bv) * catalogSort.dir;
  });

  document.getElementById('catalog-tbody').innerHTML = sorted.map(c => `
    <tr>
      <td class="col-nowrap col-name">
        <a class="component-link" onclick="navigate('component', '${c.name}')">
          ${c.name}
        </a>
      </td>
      <td class="col-nowrap col-owner">${c.owner || '—'}</td>
      <td class="col-nowrap col-type">${c.type || '—'}</td>
      <td class="col-nowrap col-lifecycle">${lifecycleBadge(c.lifecycle)}</td>
      <td class="col-description"><span class="desc-text">${shortDesc(c.description)}</span></td>
      <td class="col-nowrap col-tags">${renderTags(c.tags)}</td>
    </tr>
  `).join('');
}

function updateSortIcons() {
  document.querySelectorAll('#catalog-table th.sortable').forEach(th => {
    const icon = th.querySelector('.sort-icon');
    if (th.dataset.col === catalogSort.col) {
      icon.textContent = catalogSort.dir === 1 ? ' ▲' : ' ▼';
    } else {
      icon.textContent = '';
    }
  });
}

function sortKey(component, col) {
  switch (col) {
    case 'tags': return (component.tags || []).join(', ');
    default:     return (component[col] || '').toLowerCase();
  }
}

function lifecycleBadge(lifecycle) {
  if (!lifecycle) return '—';
  const cls = 'lifecycle--' + lifecycle.toLowerCase();
  return `<span class="lifecycle ${cls}">${lifecycle}</span>`;
}

function renderTags(tags) {
  if (!tags || tags.length === 0) return '—';
  return `<div class="tag-list">${tags.map(t => `<span class="tag">${t}</span>`).join('')}</div>`;
}

function shortDesc(desc) {
  if (!desc) return '—';
  const plain = desc
    .replace(/\[!\[.*?\]\(.*?\)\]\(.*?\)/g, '')
    .replace(/\n+/g, ' ')
    .trim();
  return plain || '—';
}
