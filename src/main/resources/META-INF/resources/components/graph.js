const CYTOSCAPE_JS = 'https://cdnjs.cloudflare.com/ajax/libs/cytoscape/3.30.2/cytoscape.min.js';

// Node colours by type
const NODE_COLORS = {
  component: { bg: '#7c83fd', border: '#5a62e8', text: '#ffffff' },
  resource:  { bg: '#f59e0b', border: '#d97706', text: '#ffffff' },
  api:       { bg: '#10b981', border: '#059669', text: '#ffffff' },
  focus:     { bg: '#1a1a2e', border: '#7c83fd', text: '#ffffff' },
  other:     { bg: '#9ca3af', border: '#6b7280', text: '#ffffff' }
};

// Edge colours by relation
const EDGE_COLORS = {
  dependsOn:    '#ef4444',
  dependencyOf: '#8b5cf6',
  providesApis: '#10b981'
};

function renderGraphView(container, name) {
  container.innerHTML = `
    <div class="breadcrumb">
      <a onclick="navigate('catalog')">Catalog</a>
      <span>/</span>
      <a onclick="navigate('component', '${name}')">${name}</a>
      <span>/</span>
      <span>Graph</span>
    </div>

    <div class="tab-bar">
      <div class="tab" onclick="navigate('component', '${name}')">Overview</div>
      <div class="tab" onclick="navigate('component', '${name}/api')">API</div>
      <div class="tab" onclick="navigate('component', '${name}/adr')">ADR</div>
      <div class="tab" onclick="navigate('component', '${name}/docs')">Docs</div>
      <div class="tab tab--active">Graph</div>
    </div>

    <div class="card" style="padding:0; overflow:hidden; position:relative;">
      <!-- Legend -->
      <div class="graph-legend">
        <span class="graph-legend-item"><span class="graph-legend-dot" style="background:#1a1a2e;border:2px solid #7c83fd"></span>Focus</span>
        <span class="graph-legend-item"><span class="graph-legend-dot" style="background:#7c83fd"></span>Component</span>
        <span class="graph-legend-item"><span class="graph-legend-dot" style="background:#f59e0b"></span>Resource</span>
        <span class="graph-legend-item"><span class="graph-legend-dot" style="background:#10b981"></span>API</span>
        <span class="graph-legend-sep"></span>
        <span class="graph-legend-item"><span class="graph-legend-line" style="background:#ef4444"></span>dependsOn</span>
        <span class="graph-legend-item"><span class="graph-legend-line" style="background:#8b5cf6"></span>dependencyOf</span>
        <span class="graph-legend-item"><span class="graph-legend-line" style="background:#10b981"></span>providesApis</span>
      </div>

      <!-- Tooltip -->
      <div id="cy-tooltip" class="cy-tooltip" style="display:none"></div>

      <!-- Graph canvas -->
      <div id="cy" style="width:100%; height:600px; background:#f9fafb;"></div>
    </div>
  `;

  fetch(`/api/catalog/components/${encodeURIComponent(name)}/graph`)
    .then(r => r.json())
    .then(graph => {
      loadScript(CYTOSCAPE_JS, () => drawGraph(graph, name));
    })
    .catch(err => {
      document.getElementById('cy').innerHTML =
        `<div class="empty-state">Failed to load graph: ${err.message}</div>`;
    });
}

function drawGraph(graph, focusName) {
  const elements = [];

  // Nodes
  for (const n of graph.nodes) {
    const colorKey = n.isFocus ? 'focus' : (NODE_COLORS[n.type] ? n.type : 'other');
    const colors   = NODE_COLORS[colorKey];
    elements.push({
      group: 'nodes',
      data: {
        id:        n.id,
        label:     n.label,
        type:      n.type,
        kind:      n.kind,
        owner:     n.owner  || '',
        lifecycle: n.lifecycle || '',
        isFocus:   n.isFocus,
        bgColor:   colors.bg,
        borderColor: colors.border,
        textColor: colors.text
      }
    });
  }

  // Edges
  for (const e of graph.edges) {
    elements.push({
      group: 'edges',
      data: {
        id:       e.id,
        source:   e.source,
        target:   e.target,
        relation: e.relation,
        edgeColor: EDGE_COLORS[e.relation] || '#9ca3af'
      }
    });
  }

  const cy = cytoscape({
    container: document.getElementById('cy'),
    elements,
    style: [
      {
        selector: 'node',
        style: {
          'background-color':   'data(bgColor)',
          'border-color':       'data(borderColor)',
          'border-width':       2,
          'label':              'data(label)',
          'color':              'data(textColor)',
          'text-valign':        'center',
          'text-halign':        'center',
          'font-size':          11,
          'font-weight':        600,
          'width':              'label',
          'height':             'label',
          'padding':            '10px',
          'shape':              'roundrectangle',
          'text-wrap':          'wrap',
          'text-max-width':     120,
          'min-zoomed-font-size': 8
        }
      },
      {
        selector: 'node[isFocus]',
        style: {
          'border-width': 3,
          'font-size':    13,
          'font-weight':  700
        }
      },
      {
        selector: 'node[type="resource"]',
        style: { 'shape': 'cylinder' }
      },
      {
        selector: 'node[type="api"]',
        style: { 'shape': 'diamond', 'padding': '14px' }
      },
      {
        selector: 'edge',
        style: {
          'width':              2,
          'line-color':         'data(edgeColor)',
          'target-arrow-color': 'data(edgeColor)',
          'target-arrow-shape': 'triangle',
          'curve-style':        'bezier',
          'label':              'data(relation)',
          'font-size':          9,
          'color':              '#6b7280',
          'text-background-color': '#ffffff',
          'text-background-opacity': 0.8,
          'text-background-padding': '2px',
          'text-rotation':      'autorotate'
        }
      },
      {
        selector: ':selected',
        style: {
          'border-width':  4,
          'border-color':  '#1a1a2e',
          'overlay-color': '#7c83fd',
          'overlay-opacity': 0.1
        }
      },
      {
        selector: 'node.faded, edge.faded',
        style: { 'opacity': 0.15 }
      }
    ],
    layout: {
      name:             'cose',
      animate:          true,
      animationDuration: 600,
      nodeRepulsion:    () => 8000,
      idealEdgeLength:  () => 120,
      edgeElasticity:   () => 100,
      gravity:          0.25,
      numIter:          1000,
      fit:              true,
      padding:          40,
      randomize:        false
    }
  });

  // Tooltip on hover
  const tooltip = document.getElementById('cy-tooltip');
  cy.on('mouseover', 'node', evt => {
    const n = evt.target.data();
    tooltip.style.display = 'block';
    tooltip.innerHTML = `
      <strong>${n.label}</strong><br>
      ${n.kind ? `<span>Kind: ${n.kind}</span><br>` : ''}
      ${n.owner ? `<span>Owner: ${n.owner}</span><br>` : ''}
      ${n.lifecycle ? `<span>Lifecycle: ${n.lifecycle}</span>` : ''}
    `;
  });
  cy.on('mousemove', evt => {
    const pos = evt.renderedPosition || evt.position;
    const rect = document.getElementById('cy').getBoundingClientRect();
    tooltip.style.left = (pos.x + 12) + 'px';
    tooltip.style.top  = (pos.y - 10) + 'px';
  });
  cy.on('mouseout', 'node', () => { tooltip.style.display = 'none'; });

  // Click node → navigate to component (if it's a component)
  cy.on('tap', 'node', evt => {
    const n = evt.target.data();
    if (n.type === 'component') navigate('component', n.id);
  });

  // Highlight neighbourhood on select
  cy.on('tap', 'node', evt => {
    cy.elements().removeClass('faded');
    const node = evt.target;
    const connected = node.closedNeighborhood();
    cy.elements().not(connected).addClass('faded');
  });
  cy.on('tap', evt => {
    if (evt.target === cy) cy.elements().removeClass('faded');
  });
}
