let _searchTimer = null;

function onSearchInput(query) {
  clearTimeout(_searchTimer);
  if (query.trim().length < 2) { closeSearch(); return; }
  _searchTimer = setTimeout(() => fetchAndRender(query.trim()), 250);
}

function closeSearch() {
  const dd = document.getElementById('search-dropdown');
  if (dd) { dd.style.display = 'none'; dd.innerHTML = ''; }
}

function clearSearch() {
  closeSearch();
  const input = document.getElementById('search-input');
  if (input) input.value = '';
}

function fetchAndRender(query) {
  fetch(`/api/catalog/search?q=${encodeURIComponent(query)}`)
    .then(r => r.json())
    .then(results => renderDropdown(results))
    .catch(() => closeSearch());
}

function positionDropdown() {
  const input = document.getElementById('search-input');
  const dd    = document.getElementById('search-dropdown');
  if (!input || !dd) return;
  const rect = input.getBoundingClientRect();
  dd.style.top  = (rect.bottom + 6) + 'px';
  dd.style.left = rect.left + 'px';
}

function renderDropdown(results) {
  const dd = document.getElementById('search-dropdown');
  if (!dd) return;

  positionDropdown();

  if (!results || results.length === 0) {
    dd.innerHTML = '<div class="search-no-results">No results found</div>';
    dd.style.display = 'block';
    return;
  }

  dd.innerHTML = results.map(r => `
    <div class="search-result" onclick="navigateToResult('${esc(r.type)}','${esc(r.componentName)}','${esc(r.name)}')">
      <span class="search-result-badge badge-${esc(r.type)}">${esc(r.type)}</span>
      <div class="search-result-body">
        <div class="search-result-name">${esc(r.type === 'adr' || r.type === 'doc' ? r.componentName + ' / ' + r.name : r.name)}</div>
        <div class="search-result-excerpt">${esc(r.excerpt)}</div>
      </div>
    </div>`).join('');
  dd.style.display = 'block';
}

function navigateToResult(type, componentName, name) {
  clearSearch();
  switch (type) {
    case 'component':
      navigate('component', componentName);
      break;
    case 'api':
      navigate('api', encodeURIComponent(name));
      break;
    case 'adr':
      navigate('component', componentName + '/adr');
      break;
    case 'doc':
      navigate('component', componentName + '/docs');
      break;
    default:
      navigate('component', componentName);
  }
}

// Close dropdown when clicking outside the search widget
document.addEventListener('click', function(e) {
  const wrap = document.querySelector('.sidebar-search-wrap');
  if (wrap && !wrap.contains(e.target)) closeSearch();
});

function esc(str) {
  return String(str ?? '')
    .replace(/&/g,'&amp;')
    .replace(/</g,'&lt;')
    .replace(/>/g,'&gt;')
    .replace(/"/g,'&quot;')
    .replace(/'/g,'&#39;');
}
