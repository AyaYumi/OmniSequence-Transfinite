const fs = require('fs');
const path = require('path');

const out = path.resolve(__dirname, '../output/concepts/matter-ordering-well.svg');
const parts = [];
const cx = 475;
const cy = 335;
const step = 21;

function point(x, y, z = 0) {
  return [cx + (x - y) * step, cy + (x + y) * step / 2 - z * step];
}

function coords(points) {
  return points.map(([x, y]) => `${x.toFixed(1)},${y.toFixed(1)}`).join(' ');
}

function polygon(points, fill, stroke = '#263d4c', width = 1.2, extra = '') {
  parts.push(`<polygon points="${coords(points)}" fill="${fill}" stroke="${stroke}" stroke-width="${width}" stroke-linejoin="round" ${extra}/>`);
}

function line(a, b, stroke, width = 1, extra = '') {
  parts.push(`<line x1="${a[0].toFixed(1)}" y1="${a[1].toFixed(1)}" x2="${b[0].toFixed(1)}" y2="${b[1].toFixed(1)}" stroke="${stroke}" stroke-width="${width}" ${extra}/>`);
}

function prism(x, y, z, w, d, h, top, left, right, edge = '#304758') {
  const a = point(x, y, z + h);
  const b = point(x + w, y, z + h);
  const c = point(x + w, y + d, z + h);
  const d0 = point(x, y + d, z + h);
  const b0 = point(x + w, y, z);
  const c0 = point(x + w, y + d, z);
  const d1 = point(x, y + d, z);
  polygon([d0, c, c0, d1], left, edge);
  polygon([b, c, c0, b0], right, edge);
  polygon([a, b, c, d0], top, edge);
  return { a, b, c, d: d0 };
}

function isoRing(x, y, z, radius, color, width, dash = '') {
  const pts = [];
  for (let n = 0; n <= 64; n++) {
    const angle = n * Math.PI / 32;
    pts.push(point(x + Math.cos(angle) * radius, y + Math.sin(angle) * radius, z));
  }
  parts.push(`<polyline points="${coords(pts)}" fill="none" stroke="${color}" stroke-width="${width}" stroke-linejoin="round" ${dash}/>`);
}

function tower(x, y) {
  prism(x, y, 1.05, 3, 3, 1.25, '#283b4a', '#172632', '#1b2d3c');
  prism(x + .65, y + .65, 2.3, 1.7, 1.7, 7.3, '#364f60', '#1b3241', '#243b4a');
  for (const z of [3.1, 5.55, 8]) {
    prism(x + .38, y + .38, z, 2.24, 2.24, .55, '#3d5a68', '#234454', '#2a4b5a', '#547381');
    line(point(x + .38, y + 2.62, z + .55), point(x + 2.62, y + 2.62, z + .55), '#36bfb6', 2.2, 'opacity=".75"');
  }
  prism(x + .38, y + .38, 9.6, 2.24, 2.24, 1.2, '#4e7381', '#1e3e4d', '#2a5260', '#78b6b6');
  prism(x + .83, y + .83, 10.8, 1.34, 1.34, .48, '#84e7df', '#2b9294', '#246f7e', '#9effef');
  const p = point(x + 1.5, y + 1.5, 11.3);
  parts.push(`<circle cx="${p[0]}" cy="${p[1]}" r="12" fill="#48e6df" opacity=".5" filter="url(#glow)"/>`);
  parts.push(`<circle cx="${p[0]}" cy="${p[1]}" r="4" fill="#c7fff7"/>`);
}

parts.push(`<svg xmlns="http://www.w3.org/2000/svg" width="1400" height="900" viewBox="0 0 1400 900">`);
parts.push(`<defs>
  <linearGradient id="bg" x2="0" y2="1"><stop stop-color="#0b1420"/><stop offset="1" stop-color="#071019"/></linearGradient>
  <radialGradient id="aura"><stop stop-color="#2df4d0" stop-opacity=".26"/><stop offset="1" stop-color="#2df4d0" stop-opacity="0"/></radialGradient>
  <radialGradient id="core"><stop stop-color="#eeffff"/><stop offset=".3" stop-color="#8cf0eb"/><stop offset=".72" stop-color="#448bba"/><stop offset="1" stop-color="#2d3d85"/></radialGradient>
  <pattern id="grid" width="40" height="40" patternUnits="userSpaceOnUse"><path d="M 40 0 L 0 0 0 40" fill="none" stroke="#2b4350" stroke-width="1" opacity=".32"/></pattern>
  <filter id="glow" x="-100%" y="-100%" width="300%" height="300%"><feGaussianBlur stdDeviation="11"/></filter>
  <filter id="soft" x="-100%" y="-100%" width="300%" height="300%"><feGaussianBlur stdDeviation="3"/></filter>
  <style>
    text { font-family: "Microsoft YaHei", "Noto Sans CJK SC", sans-serif; }
    .title { fill: #eef9f8; font-size: 38px; font-weight: 700; }
    .sub { fill: #8ba8b2; font-size: 17px; }
    .small { fill: #64949e; font-size: 14px; letter-spacing: 2px; }
    .legendTitle { fill: #dbf8f2; font-size: 22px; font-weight: 700; }
    .legendBody { fill: #a4bbc1; font-size: 16px; }
    .marker { fill: #d5fff6; font-size: 18px; font-weight: 700; paint-order: stroke; stroke: #07151c; stroke-width: 4px; }
  </style>
</defs>`);
parts.push(`<rect width="1400" height="900" fill="url(#bg)"/>`);
parts.push(`<rect width="1400" height="900" fill="url(#grid)"/>`);
parts.push(`<ellipse cx="480" cy="550" rx="430" ry="260" fill="url(#aura)"/>`);
parts.push(`<text x="72" y="78" class="small">OMNISEQUENCE / RESOURCE ACQUISITION</text>`);
parts.push(`<text x="72" y="132" class="title">\u7269\u8d28\u6784\u5e8f\u4e95</text>`);
parts.push(`<text x="73" y="166" class="sub">\u8d44\u6e90\u91c7\u6837 \u00b7 \u7269\u8d28\u51dd\u805a \u00b7 \u7ed3\u6784\u6982\u5ff5\u56fe</text>`);

// A dark service slab with visible block-grid scale.
prism(0, 0, -.25, 17, 17, 1.25, '#172c39', '#0d1d27', '#10222f', '#3d6170');
for (let i = 1; i < 17; i++) {
  line(point(i, 0, 1), point(i, 17, 1), '#315161', .8, 'opacity=".53"');
  line(point(0, i, 1), point(17, i, 1), '#315161', .8, 'opacity=".53"');
}

// Inlaid perimeter conduits make the complete footprint readable.
for (const [x, y, w, d] of [[0, 0, 17, .55], [0, 16.45, 17, .55], [0, .55, .55, 15.9], [16.45, .55, .55, 15.9]]) {
  const q = prism(x, y, 1, w, d, .26, '#3a6572', '#244953', '#2e5660', '#6ca6aa');
  line(q.a, q.b, '#53d2c1', 1.2, 'opacity=".65"');
}

// Four intake lanes converge under the levitating core.
for (const [x, y, w, d] of [[4, 8, 4, 1], [9, 8, 4, 1], [8, 4, 1, 4], [8, 9, 1, 4]]) {
  prism(x, y, 1.04, w, d, .2, '#28505b', '#1c3b47', '#20434e', '#4c7980');
}
prism(6, 6, 1.06, 5, 5, .65, '#354f60', '#203848', '#284252', '#6b919b');
prism(7, 7, 1.71, 3, 3, .9, '#1b303e', '#172b39', '#1c3544', '#447887');
isoRing(8.5, 8.5, 2.65, 2.3, '#4ce3d3', 4, 'opacity=".82"');

// Draw the rear units first so the central structure remains legible.
tower(2, 2);
tower(12, 2);
tower(2, 12);

// Focus beams from the tower emitters to the condensation point.
const focus = point(8.5, 8.5, 9.2);
for (const [x, y] of [[3.5, 3.5], [13.5, 3.5], [3.5, 13.5], [13.5, 13.5]]) {
  const from = point(x, y, 11.3);
  line(from, focus, '#37ecdb', 12, 'opacity=".28" filter="url(#glow)"');
  line(from, focus, '#61eadf', 2.5, 'opacity=".8" stroke-dasharray="8 7"');
}

// The upper ring is a second-stage, physical block extension.
const ringBlocks = [];
for (let k = 0; k < 16; k++) {
  const a = k * Math.PI / 8;
  const x = 8.5 + Math.cos(a) * 3.55;
  const y = 8.5 + Math.sin(a) * 3.55;
  ringBlocks.push({ x, y, depth: x + y });
}
ringBlocks.sort((a, b) => a.depth - b.depth);
for (const { x, y } of ringBlocks) {
  prism(x - .42, y - .42, 10.05, .84, .84, .52, '#527b80', '#294853', '#345965', '#8bc3b8');
}
isoRing(8.5, 8.5, 10.45, 3.54, '#81f3d7', 2.5, 'opacity=".78"');

// A framed, suspended chamber holds a luminous voxel core.
prism(7.05, 7.05, 6, 2.9, 2.9, .55, '#264655', '#17333f', '#1b3b49', '#5d8790');
for (const [x, y] of [[7.05, 7.05], [9.6, 7.05], [7.05, 9.6], [9.6, 9.6]]) {
  prism(x, y, 6.55, .35, .35, 3.45, '#548188', '#2c5260', '#376775', '#78bbb7');
}
const nucleus = point(8.5, 8.5, 9.2);
parts.push(`<circle cx="${nucleus[0]}" cy="${nucleus[1]}" r="65" fill="#58e2da" opacity=".38" filter="url(#glow)"/>`);
parts.push(`<circle cx="${nucleus[0]}" cy="${nucleus[1]}" r="23" fill="url(#core)" stroke="#a9fff2" stroke-width="2"/>`);
const diamond = [point(8.5, 7.35, 9.2), point(9.65, 8.5, 9.2), point(8.5, 9.65, 9.2), point(7.35, 8.5, 9.2)];
polygon(diamond, 'none', '#d2fff8', 2.2, 'opacity=".8"');
isoRing(8.5, 8.5, 9.2, 1.82, '#b7fff0', 2.1, 'opacity=".78"');
for (const [x, y, w, d] of [[6.8, 6.8, 3.4, .36], [6.8, 9.84, 3.4, .36], [6.8, 7.16, .36, 2.68], [9.84, 7.16, .36, 2.68]]) {
  prism(x, y, 10, w, d, .45, '#486f76', '#274955', '#2c4d5c', '#91bfb7');
}

// The nearest tower completes the silhouette.
tower(12, 12);

// Service faces on the visible front edge.
for (const x of [4, 6, 8, 10, 12]) {
  const p = point(x, 17, .6);
  parts.push(`<rect x="${p[0] - 3}" y="${p[1] - 4}" width="6" height="8" fill="#52d7bb" opacity=".9" transform="skewY(-26)"/>`);
}

// A slim guide stays outside the voxel scene.
parts.push(`<line x1="990" y1="70" x2="990" y2="825" stroke="#355363" stroke-width="1"/>`);
parts.push(`<text x="1035" y="103" class="small">STRUCTURE / 01</text>`);
parts.push(`<text x="1035" y="143" class="legendTitle">\u5efa\u7b51\u8f6e\u5ed3</text>`);
parts.push(`<text x="1035" y="178" class="legendBody">\u7ea6 17 \u00d7 17 \u00d7 13 \u683c</text>`);
const legend = [
  ['01', '\u56db\u5ea7\u91c7\u6837\u5854', '\u6a21\u62df\u91c7\u96c6\u7684\u53d1\u751f\u7aef'],
  ['02', '\u60ac\u6d6e\u51dd\u805a\u6838', '\u5c06\u6837\u672c\u51dd\u805a\u4e3a\u8d44\u6e90'],
  ['03', '\u4e0a\u5c42\u51dd\u805a\u73af', '\u540e\u671f\u590d\u5236\u529f\u80fd\u6269\u5c55'],
  ['04', '\u57fa\u5ea7\u63a5\u53e3\u5c42', 'AE \u7f51\u7edc\u4e0e\u80fd\u6e90\u63a5\u5165']
];
legend.forEach(([n, title, body], i) => {
  const y = 254 + i * 131;
  parts.push(`<line x1="1035" y1="${y - 34}" x2="1330" y2="${y - 34}" stroke="#294652"/>`);
  parts.push(`<text x="1035" y="${y}" fill="#67e4d3" font-size="18" font-weight="700">${n}</text>`);
  parts.push(`<text x="1085" y="${y}" fill="#e7f7f4" font-size="20" font-weight="700">${title}</text>`);
  parts.push(`<text x="1085" y="${y + 32}" class="legendBody">${body}</text>`);
});
parts.push(`<text x="72" y="835" class="small">ISOMETRIC CONCEPT / BLOCK SCALE SHOWN</text>`);
parts.push(`<text x="1329" y="835" class="small" text-anchor="end">V1</text>`);
parts.push(`</svg>`);

fs.mkdirSync(path.dirname(out), { recursive: true });
fs.writeFileSync(out, parts.join('\n'), 'utf8');
console.log(out);
