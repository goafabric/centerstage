const RADAR_JS  = 'https://opensource.zalando.com/tech-radar/radar.js';
const RADAR_CSS = 'https://opensource.zalando.com/tech-radar/radar.css';
const D3_JS     = 'https://d3js.org/d3.v7.min.js';

function renderTechRadar(container) {
  container.innerHTML = `
    <div class="page-header">
      <div class="page-title">Tech Radar</div>
      <div class="page-subtitle">Technology choices and recommendations</div>
    </div>
    <div class="card" style="padding: 24px; overflow-x: auto; overflow-y: visible;">
      <div id="radar-container" style="min-width: 1450px;">
        <svg id="radar"></svg>
      </div>
      <div id="radar-loading" class="loading">Loading Tech Radar...</div>
    </div>
  `;

  fetch('/api/techradar')
    .then(r => {
      if (!r.ok) throw new Error('Tech radar not configured or unavailable');
      return r.json();
    })
    .then(data => {
      loadRadarDeps(() => initRadar(data));
    })
    .catch(err => {
      container.innerHTML = `
        <div class="page-header">
          <div class="page-title">Tech Radar</div>
        </div>
        <div class="empty-state">Failed to load Tech Radar: ${err.message}</div>
      `;
    });
}

function initRadar(data) {
  const loading = document.getElementById('radar-loading');
  if (loading) loading.style.display = 'none';

  // Map the backstage/goafabric JSON format to Zalando radar_visualization format
  // Quadrants: radar expects exactly 4, ordered as: Q1(top-right), Q2(top-left), Q3(bottom-left), Q4(bottom-right)
  const quadrantDefs = data.quadrants || [];
  const ringDefs     = data.rings     || [];

  // Build quadrant name list (up to 4, pad if needed)
  const quadrantNames = quadrantDefs.map(q => q.name || q.id);
  while (quadrantNames.length < 4) quadrantNames.push('Other');

  const rings = ringDefs.map(r => ({ name: (r.name || r.id).toUpperCase(), color: r.color || '#999' }));

  // Map entries: find ring index from ringId + quadrant index from quadrant id
  const quadrantIdToIndex = Object.fromEntries(quadrantDefs.map((q, i) => [q.id, i]));
  const ringIdToIndex     = Object.fromEntries(ringDefs.map((r, i) => [r.id, i]));

  const entries = (data.entries || []).map(e => {
    const latestTimeline = (e.timeline || []).slice().sort((a, b) =>
      new Date(b.date) - new Date(a.date))[0] || {};
    return {
      label:    e.title || e.key || e.id,
      quadrant: quadrantIdToIndex[e.quadrant] ?? 0,
      ring:     ringIdToIndex[latestTimeline.ringId] ?? 3,
      moved:    latestTimeline.moved ?? 0,
      link:     e.url || null,
      active:   true
    };
  });

  radar_visualization({
    svg_id:    'radar',
    width:     1450,
    height:    1000,
    colors: {
      background: '#ffffff',
      grid:       '#e5e7eb',
      inactive:   '#ddd'
    },
    title:      'Tech Radar',
    quadrants: quadrantNames.slice(0, 4).map(name => ({ name })),
    rings,
    entries,
    print_layout: true
  });
}

function loadRadarDeps(callback) {
  if (window.radar_visualization) {
    callback();
    return;
  }

  // Load CSS
  if (!document.getElementById('radar-css')) {
    const link = document.createElement('link');
    link.id   = 'radar-css';
    link.rel  = 'stylesheet';
    link.href = RADAR_CSS;
    document.head.appendChild(link);
  }

  // Load D3 then radar.js sequentially
  if (window.d3) {
    loadScript(RADAR_JS, callback);
  } else {
    loadScript(D3_JS, () => loadScript(RADAR_JS, callback));
  }
}

function loadScript(src, callback) {
  const script = document.createElement('script');
  script.src = src;
  script.onload = callback;
  document.head.appendChild(script);
}
