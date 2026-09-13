// Exportador de especificaciones desde Figma → design/.
// Se pega en figma_execute (MCP figma-console) con scripts/figma-receiver.mjs corriendo.
// Solo LEE el archivo de Figma; no modifica nada.
//
// Al final del código pegado se llama, por ejemplo:
//   return await RUTA_EXPORT.frames(['02', '03'], { png: true });
//   return await RUTA_EXPORT.tokens();
//   return await RUTA_EXPORT.images();

const RUTA_EXPORT = (() => {
  const RX = 'http://localhost:9230/save?path=';
  const save = (path, body) => fetch(RX + encodeURIComponent(path), { method: 'POST', body });
  const r = (v) => Math.round(v * 100) / 100;
  const hex = (c) => '#' + [c.r, c.g, c.b].map((x) => Math.round(x * 255).toString(16).padStart(2, '0')).join('').toUpperCase();

  let vName = null;
  const varNames = async () => {
    if (vName) return vName;
    vName = {};
    for (const v of await figma.variables.getLocalVariablesAsync()) vName[v.id] = v.name;
    return vName;
  };
  const styleCache = {};
  const styleName = async (id) => {
    if (!id || typeof id !== 'string') return undefined;
    if (!(id in styleCache)) {
      const s = await figma.getStyleByIdAsync(id);
      styleCache[id] = s ? s.name : id;
    }
    return styleCache[id];
  };

  const paints = (arr, V) => {
    if (arr === figma.mixed) return 'mixed';
    if (!Array.isArray(arr) || !arr.length) return undefined;
    const out = arr.filter((p) => p.visible !== false).map((p) => {
      const o = { type: p.type };
      if (p.type === 'SOLID') o.hex = hex(p.color);
      if (p.opacity !== undefined && p.opacity < 1) o.opacity = r(p.opacity);
      if (p.boundVariables && p.boundVariables.color) o.token = V[p.boundVariables.color.id];
      if (p.type === 'IMAGE') Object.assign(o, { imageHash: p.imageHash, scaleMode: p.scaleMode });
      if (p.type.startsWith('GRADIENT')) o.stops = p.gradientStops.map((s) => ({ hex: hex(s.color), a: r(s.color.a), pos: r(s.position) }));
      return o;
    });
    return out.length ? out : undefined;
  };

  const boundTokens = (n, V) => {
    if (!n.boundVariables) return undefined;
    const o = {};
    for (const [k, b] of Object.entries(n.boundVariables)) {
      if (k === 'fills' || k === 'strokes') continue;
      if (b && b.id) o[k] = V[b.id];
    }
    return Object.keys(o).length ? o : undefined;
  };

  async function ser(n, root, V) {
    const o = { id: n.id, name: n.name, type: n.type };
    if (n.visible === false) o.hidden = true;
    const bb = n.absoluteBoundingBox;
    const rb = root.absoluteBoundingBox;
    if (bb) Object.assign(o, { x: r(bb.x - rb.x), y: r(bb.y - rb.y), w: r(bb.width), h: r(bb.height) });
    if ('opacity' in n && n.opacity < 1) o.opacity = r(n.opacity);
    if ('rotation' in n && n.rotation) o.rotation = r(n.rotation);
    if ('layoutMode' in n && n.layoutMode !== 'NONE') {
      o.layout = {
        mode: n.layoutMode,
        main: n.primaryAxisAlignItems,
        cross: n.counterAxisAlignItems,
        pad: [n.paddingTop, n.paddingRight, n.paddingBottom, n.paddingLeft],
        gap: n.itemSpacing,
      };
      if (n.layoutWrap === 'WRAP') Object.assign(o.layout, { wrap: true, crossGap: n.counterAxisSpacing });
      if (n.strokesIncludedInLayout) o.layout.strokesInLayout = true;
    }
    if ('layoutSizingHorizontal' in n) o.sizing = [n.layoutSizingHorizontal, n.layoutSizingVertical];
    if (n.layoutPositioning === 'ABSOLUTE') o.absolute = true;
    if ('minWidth' in n && (n.minWidth || n.maxWidth || n.minHeight || n.maxHeight)) {
      o.minmax = { minW: n.minWidth, maxW: n.maxWidth, minH: n.minHeight, maxH: n.maxHeight };
    }
    if ('clipsContent' in n && n.clipsContent) o.clip = true;
    if ('cornerRadius' in n) {
      if (n.cornerRadius === figma.mixed) o.radius = [n.topLeftRadius, n.topRightRadius, n.bottomRightRadius, n.bottomLeftRadius];
      else if (n.cornerRadius) o.radius = n.cornerRadius;
    }
    const fills = paints(n.fills, V);
    if (fills) o.fills = fills;
    const strokes = paints(n.strokes, V);
    if (strokes) {
      o.strokes = strokes;
      o.strokeWeight = n.strokeWeight === figma.mixed
        ? [n.strokeTopWeight, n.strokeRightWeight, n.strokeBottomWeight, n.strokeLeftWeight]
        : n.strokeWeight;
      o.strokeAlign = n.strokeAlign;
      if (n.dashPattern && n.dashPattern.length) o.dash = n.dashPattern;
    }
    if (n.effectStyleId) o.effect = await styleName(n.effectStyleId);
    else if (n.effects && n.effects.length) {
      o.effects = n.effects.filter((e) => e.visible !== false).map((e) => ({
        type: e.type, radius: e.radius, spread: e.spread,
        offset: e.offset, color: e.color ? hex(e.color) : undefined, a: e.color ? r(e.color.a) : undefined,
      }));
    }
    const bt = boundTokens(n, V);
    if (bt) o.tok = bt;

    if (n.type === 'TEXT') {
      o.text = n.characters;
      o.textStyle = n.textStyleId === figma.mixed ? 'mixed' : await styleName(n.textStyleId);
      if (n.fontName !== figma.mixed) o.font = `${n.fontName.family} ${n.fontName.style}`;
      if (n.fontSize !== figma.mixed) o.fontSize = n.fontSize;
      if (n.lineHeight !== figma.mixed) o.lineHeight = n.lineHeight.unit === 'AUTO' ? 'AUTO' : `${r(n.lineHeight.value)}${n.lineHeight.unit === 'PIXELS' ? 'px' : '%'}`;
      if (n.letterSpacing !== figma.mixed && n.letterSpacing.value) o.letterSpacing = `${r(n.letterSpacing.value)}${n.letterSpacing.unit === 'PIXELS' ? 'px' : '%'}`;
      if (n.textCase !== figma.mixed && n.textCase !== 'ORIGINAL') o.textCase = n.textCase;
      if (n.textDecoration !== figma.mixed && n.textDecoration !== 'NONE') o.decoration = n.textDecoration;
      o.align = [n.textAlignHorizontal, n.textAlignVertical];
      o.autoResize = n.textAutoResize;
      if (n.textStyleId === figma.mixed || n.fills === figma.mixed || n.fontName === figma.mixed) {
        const segs = n.getStyledTextSegments(['fontName', 'fontSize', 'fills', 'textStyleId', 'textDecoration']);
        o.segments = [];
        for (const s of segs) {
          o.segments.push({
            text: s.characters, font: `${s.fontName.family} ${s.fontName.style}`, fontSize: s.fontSize,
            style: await styleName(s.textStyleId), fills: paints(s.fills, V), decoration: s.textDecoration !== 'NONE' ? s.textDecoration : undefined,
          });
        }
      }
    }
    if (n.type === 'INSTANCE') {
      const mc = await n.getMainComponentAsync();
      if (mc) o.component = mc.parent && mc.parent.type === 'COMPONENT_SET' ? `${mc.parent.name} / ${mc.name}` : mc.name;
    }
    if (n.reactions && n.reactions.length) {
      o.reactions = n.reactions.flatMap((re) => (re.actions || [re.action]).filter(Boolean).map((a) => ({
        trigger: re.trigger && re.trigger.type, nav: a.navigation || a.type, dest: a.destinationId || undefined,
        transition: a.transition ? { type: a.transition.type, dir: a.transition.direction, ms: Math.round(a.transition.duration * 1000), easing: a.transition.easing && a.transition.easing.type } : undefined,
      })));
    }
    const isVector = ['VECTOR', 'BOOLEAN_OPERATION', 'STAR', 'LINE', 'POLYGON'].includes(n.type);
    if (isVector) o.vector = true;
    if ('children' in n && !isVector) {
      if (/^Fondo/.test(n.name)) o.note = 'clon bloqueado de la vista de fondo; hijos omitidos';
      else if (n.name === 'Status bar') o.note = 'barra de estado del sistema; hijos omitidos';
      else o.children = await Promise.all(n.children.map((c) => ser(c, root, V)));
    }
    return o;
  }

  async function screens() {
    await figma.loadAllPagesAsync();
    const p3 = figma.root.children.find((p) => p.name === '03 · Pantallas');
    return p3.findAll((n) => n.type === 'FRAME' && n.parent.type === 'SECTION')
      .map((f) => ({ key: f.name.split(' · ')[0], frame: f, section: f.parent.name }));
  }

  return {
    async list() {
      return (await screens()).map((s) => ({ key: s.key, id: s.frame.id, name: s.frame.name, section: s.section }));
    },

    async frames(keys, opts = {}) {
      const V = await varNames();
      const all = await screens();
      const done = [];
      for (const s of all.filter((x) => !keys || keys.includes(x.key))) {
        const spec = await ser(s.frame, s.frame, V);
        spec.section = s.section;
        await save(`design/specs/${s.key}.json`, JSON.stringify(spec, null, 1));
        if (opts.png) {
          const f = s.frame;
          const bytes = await f.exportAsync({ format: 'PNG', constraint: { type: 'SCALE', value: 2 }, useAbsoluteBounds: true });
          await save(`design/reference/${s.key}.png`, bytes);
        }
        done.push(s.key);
      }
      return done;
    },

    async tokens() {
      const cols = {};
      for (const c of await figma.variables.getLocalVariableCollectionsAsync()) cols[c.id] = c;
      const vars = await figma.variables.getLocalVariablesAsync();
      const byId = {};
      for (const v of vars) byId[v.id] = v;
      const resolve = (v) => {
        const val = v.valuesByMode[cols[v.variableCollectionId].defaultModeId];
        return val && val.type === 'VARIABLE_ALIAS' ? resolve(byId[val.id]) : val;
      };
      const variables = vars.map((v) => {
        const val = resolve(v);
        const raw = v.valuesByMode[cols[v.variableCollectionId].defaultModeId];
        return {
          name: v.name, collection: cols[v.variableCollectionId].name, type: v.resolvedType,
          value: v.resolvedType === 'COLOR' ? hex(val) + (val.a < 1 ? ` @${r(val.a)}` : '') : val,
          alias: raw && raw.type === 'VARIABLE_ALIAS' ? byId[raw.id].name : undefined,
        };
      });
      const text = (await figma.getLocalTextStylesAsync()).map((s) => ({
        name: s.name, font: `${s.fontName.family} ${s.fontName.style}`, fontSize: s.fontSize,
        lineHeight: s.lineHeight.unit === 'AUTO' ? 'AUTO' : `${r(s.lineHeight.value)}${s.lineHeight.unit === 'PIXELS' ? 'px' : '%'}`,
        letterSpacing: `${r(s.letterSpacing.value)}${s.letterSpacing.unit === 'PIXELS' ? 'px' : '%'}`,
        textCase: s.textCase, decoration: s.textDecoration, description: s.description,
      }));
      const effects = (await figma.getLocalEffectStylesAsync()).map((s) => ({
        name: s.name,
        effects: s.effects.map((e) => ({ type: e.type, radius: e.radius, spread: e.spread, offset: e.offset, color: e.color ? hex(e.color) : undefined, a: e.color ? r(e.color.a) : undefined })),
      }));
      await save('design/tokens/figma-variables.json', JSON.stringify(variables, null, 1));
      await save('design/tokens/text-styles.json', JSON.stringify(text, null, 1));
      await save('design/tokens/effect-styles.json', JSON.stringify(effects, null, 1));
      return { variables: variables.length, text: text.length, effects: effects.length };
    },

    // Ilustraciones y logo: bytes originales por imageHash + el tamaño máximo en que se usan.
    async images() {
      const all = await screens();
      const seen = {};
      for (const s of all) {
        for (const n of s.frame.findAll((x) => 'fills' in x && Array.isArray(x.fills) && x.fills.some((p) => p.type === 'IMAGE'))) {
          if (/^Fondo/.test(n.parent && n.parent.name)) continue;
          for (const p of n.fills.filter((q) => q.type === 'IMAGE')) {
            const e = seen[p.imageHash] || (seen[p.imageHash] = { hash: p.imageHash, names: [], maxW: 0, maxH: 0, scaleMode: p.scaleMode });
            const nm = n.name.normalize('NFC');
            if (!e.names.includes(nm)) e.names.push(nm);
            e.maxW = Math.max(e.maxW, n.width);
            e.maxH = Math.max(e.maxH, n.height);
          }
        }
      }
      for (const e of Object.values(seen)) {
        const img = figma.getImageByHash(e.hash);
        const bytes = await img.getBytesAsync();
        const size = await img.getSizeAsync();
        e.srcW = size.width;
        e.srcH = size.height;
        await save(`design/assets-src/${e.hash}.png`, bytes);
      }
      await save('design/assets-src/manifest.json', JSON.stringify(Object.values(seen), null, 1));
      return Object.values(seen).map((e) => `${e.names.join(' | ')} ${e.maxW}x${e.maxH}`);
    },
  };
})();
