"""Construye Ruta_Presentacion_Bancoagricola_v3.pptx a partir de la v2.

- Agrega dos diapositivas: la llamada (después de la 6) y la auditoría (después de la 8).
- Corrige textos que dejaron de ser ciertos y renumera.
- Escribe una réplica HTML de las dos nuevas, para el PDF de respaldo.
"""
import html
import pathlib
import re
import shutil
import sys
import zipfile

S = pathlib.Path(__file__).parent
V2 = pathlib.Path.home() / 'Desktop/presentacion/Ruta_Presentacion_Bancoagricola_v2.pptx'
SALIDA = pathlib.Path(sys.argv[1]) if len(sys.argv) > 1 else S / 'Ruta_Presentacion_Bancoagricola_v3.pptx'
TRABAJO = S / 'deck3'
EMU = 914400

# ----------------------------------------------------------------------------
# Especificación de las diapositivas nuevas (pulgadas). La misma sirve para el
# XML de PowerPoint y para la réplica HTML.
# ----------------------------------------------------------------------------
BLANCO, TINTA, GRIS, GRIS_CLARO, AMARILLO = 'FFFFFF', '1A1B1A', '5B5B5B', 'E8E7E4', 'FDDA24'
PIE_OSCURO, PIE_CLARO = '8A8986', '8A8B8E'


def R(texto, sz, color, fuente='Segoe UI', b=False, i=False, spc=None):
    return dict(t=texto, sz=sz, color=color, fuente=fuente, b=b, i=i, spc=spc)


def texto(x, y, w, h, parrafos, anchor='t', algn=None):
    if isinstance(parrafos[0], dict):
        parrafos = [parrafos]
    return dict(tipo='texto', x=x, y=y, w=w, h=h, parrafos=parrafos, anchor=anchor, algn=algn)


def forma(prst, x, y, w, h, fill, ln=None, ln_w=12700, adj=None):
    return dict(tipo='forma', prst=prst, x=x, y=y, w=w, h=h, fill=fill, ln=ln or fill, ln_w=ln_w, adj=adj)


def imagen(archivo, x, y, w, h):
    return dict(tipo='imagen', archivo=archivo, x=x, y=y, w=w, h=h)


def encabezado(ceja, titulo, oscuro):
    return [
        forma('ellipse', 0.7, 0.63, 0.13, 0.13, AMARILLO),
        texto(0.95, 0.58, 8.0, 0.24, [R(ceja, 11, AMARILLO if oscuro else GRIS, b=True, spc=300)]),
        texto(0.7, 0.9, 11.9, 0.75, [R(titulo, 34, BLANCO if oscuro else TINTA, 'Segoe UI Semibold')]),
    ]


def pie(numero, oscuro):
    color = PIE_OSCURO if oscuro else PIE_CLARO
    return [
        texto(0.7, 7.05, 6.0, 0.2, [R('Ruta · Prevención de mora en Banca Móvil', 9, color)]),
        texto(11.93, 7.05, 0.7, 0.2, [R(str(numero), 9, color)], algn='r'),
    ]


def check(x, y, oscuro, lado=0.3):
    return [
        forma('ellipse', x, y, lado, lado, AMARILLO if oscuro else TINTA),
        texto(x, y, lado, lado, [R('✓', 12, TINTA if oscuro else AMARILLO, 'Segoe UI Semibold')], anchor='ctr', algn='ctr'),
    ]


def diapositiva_llamada(numero):
    el = encabezado('PARA QUIEN NO USA LA APP', 'Una llamada que mueve la fecha, no que cobra.', True)
    tel_h = 4.95
    el.append(imagen('telefono-llamada.png', 0.85, 1.85, round(tel_h * 1140 / 2343, 3), tel_h))
    x0, ancho, hueco, y, h = 3.75, 2.813, 0.22, 1.95, 1.95
    tarjetas = [
        ('PREGUNTA', '2', 'preguntas: cómo y qué día le llega el dinero. Propone un día en que ya le llegó.', False),
        ('RESPONDE', '<300 ms', 'por respuesta en la llamada emulada, pasando por n8n y las reglas del backend.', False),
        ('NUNCA PIDE', '0', 'claves, PIN ni números de tarjeta. Con otra persona al teléfono no habla del préstamo.', True),
    ]
    for k, (ceja, grande, detalle, clave) in enumerate(tarjetas):
        x = x0 + k * (ancho + hueco)
        fondo, borde = (AMARILLO, AMARILLO) if clave else ('252423', '3E3D3A')
        tinta_ceja = TINTA if clave else AMARILLO
        tinta = TINTA if clave else BLANCO
        tinta_det = TINTA if clave else GRIS_CLARO
        el += [
            forma('roundRect', x, y, ancho, h, fondo, borde, 9525, adj=6000),
            texto(x + 0.28, y + 0.24, ancho - 0.56, 0.22, [R(ceja, 10, tinta_ceja, b=True, spc=200)]),
            texto(x + 0.26, y + 0.46, ancho - 0.52, 0.72, [R(grande, 40, tinta, 'Segoe UI Semibold')]),
            texto(x + 0.28, y + 1.2, ancho - 0.56, 0.66, [R(detalle, 11, tinta_det)]),
        ]
    puntos = [
        ('Solo cambia con un sí. ', 'Antes dice el interés de los días que se corre la cuota, que se cobra una sola vez.'),
        ('Responde y vuelve al tema. ', '«¿Quién habla?», «¿es una estafa?», «¿cuánto es mi cuota?»: contesta en una frase y retoma su pregunta.'),
        ('Dentro de la ley, con salida. ', 'Solo de lunes a viernes, de 8:00 a 18:00 (LPC), y siempre ofrece Telebanca.'),
    ]
    for k, (lead, resto) in enumerate(puntos):
        yy = 4.2 + k * 0.6
        el += check(3.75, yy, True)
        el.append(texto(4.2, yy + 0.02, 8.43, 0.52, [R(lead, 12.5, BLANCO, 'Segoe UI Semibold'), R(resto, 12.5, GRIS_CLARO)]))
    el.append(texto(3.75, 6.12, 8.88, 0.62, [
        R('Por qué sí llamar: ', 14, AMARILLO, b=True),
        R('no es un recordatorio, ataca la causa. Un día de desfase entre el ingreso y el vencimiento sube 10 puntos el pago tardío (Dahan, 2022).', 14, BLANCO),
    ]))
    el += pie(numero, True)
    return dict(fondo_imagen='Slide-3-image-1.png', fondo='1A1B1A', elementos=el)


def diapositiva_auditoria(numero):
    el = encabezado('AUDITORÍA', 'Todo queda registrado, y se puede comprobar.', False)
    img_w = 7.05
    img_h = round(img_w * 1062 / 2264, 3)
    el += [
        forma('roundRect', 0.7, 1.95, 7.35, img_h + 0.27, BLANCO, 'D5D6D8', 12700, adj=3000),
        imagen('registro-llamada.png', 0.85, 2.085, img_w, img_h),
        texto(0.7, 2.25 + img_h + 0.12, 7.35, 0.45, [R('La llamada a Samuel, paso a paso, con el tiempo de n8n en cada respuesta. Datos de prueba.', 10.5, GRIS)]),
    ]
    puntos = [
        ('Cada paso deja rastro', 'La app, el asesor, la llamada, n8n y el motor diario, en un solo registro.'),
        ('Solo lo que quedó guardado', 'Si algo falla, queda el error con su ruta; nunca un «listo» que no pasó.'),
        ('Sin datos sensibles', 'Nunca claves ni tokens, y el teléfono va enmascarado.'),
        ('¿Funciona?, en vivo', 'Una lista marca qué se probó en las últimas 24 horas y cómo probar lo que falta.'),
    ]
    for k, (lead, resto) in enumerate(puntos):
        yy = 2.0 + k * 0.98
        el += check(8.45, yy, False, 0.34)
        el.append(texto(8.95, yy + 0.02, 3.68, 0.3, [R(lead, 13.5, TINTA, 'Segoe UI Semibold')]))
        el.append(texto(8.95, yy + 0.36, 3.68, 0.58, [R(resto, 11.5, GRIS)]))
    el += [
        forma('roundRect', 0.7, 6.22, 11.93, 0.52, BLANCO, 'D5D6D8', 12700, adj=15385),
        texto(0.95, 6.22, 11.5, 0.52, [
            R('Para el piloto:  ', 11.5, TINTA, b=True),
            R('el mismo registro mide desde el primer día cuántas fechas se mueven, cuántas llamadas se contestan y qué falla.', 11.5, GRIS),
        ], anchor='ctr'),
    ]
    el += pie(numero, False)
    return dict(fondo_imagen=None, fondo='F5F7F9', elementos=el)


NOTA_LLAMADA = ('4:00–4:50. Para quien no usa la app, el banco llama. Díganlo así: «no es un recordatorio de pago: '
                'es la misma acción de la app, por teléfono». La asistente hace dos preguntas (cómo y qué día le llega el '
                'dinero), propone un día en que ya le llegó y dice el interés antes de cambiar nada; solo cambia con un sí. '
                'Si preguntan «¿es una estafa?», responde que nunca pide claves. Con otra persona al teléfono no habla del '
                'préstamo. Si alguien pregunta por qué llamar si la evidencia dice que los mensajes no funcionan: porque esto '
                'no es un mensaje, cambia la fecha, y la fecha es causa (Dahan, 2022). Aclaren que en la demo la llamada es '
                'emulada: la voz real con número es el siguiente paso.')
NOTA_AUDITORIA = ('6:05–6:35. Todo lo que pasó en la demo quedó registrado: el ingreso de Edgar, su fecha, su apartado y la '
                  'llamada a Samuel, con el tiempo de cada respuesta. Tres ideas: cada paso deja rastro; solo se registra lo '
                  'que de verdad se guardó; y nunca claves ni tokens. La lista «¿funciona?» sirve para verificar en vivo. '
                  'Para el piloto es la base de la medición. Si el tiempo es corto, esta diapositiva se puede saltar.')

# Notas existentes: tiempos nuevos y textos que cambiaron
TIEMPOS = {4: ('2:00–2:40.', '2:00–2:35.'), 5: ('2:40–3:30.', '2:35–3:25.'), 6: ('3:30–4:10.', '3:25–4:00.'),
           7: ('4:10–4:55.', '4:50–5:30.'), 8: ('4:55–5:35.', '5:30–6:05.'), 9: ('5:35–6:05.', '6:35–7:00.'),
           10: ('6:05–6:30.', '7:00–7:25.')}
NOTAS_TEXTO = {
    4: [('mover la fecha al día en que ya le pagaron,',
         'mover la fecha al día en que ya le pagaron (si la primera cuota se corre unos días, el interés de esos días se dice antes y se cobra una sola vez),')],
    9: [('automático y motor diario que aparta, cobra y avisa.', 'automático, motor diario que aparta, cobra y avisa, la llamada de voz por n8n y la auditoría.'),
        ('Digan en voz alta lo pendiente', 'Digan en voz alta lo pendiente (la llamada real necesita número y Vapi)')],
}

# Textos de las diapositivas existentes que dejaron de ser ciertos
CORRECCIONES = {
    4: [('Al día en que ya le pagaron. Mismo monto, mismo plazo.', 'Al día en que ya le pagaron. Mismo plazo, sin sorpresas.')],
    9: [('<a:t>36 tablas</a:t>', '<a:t>40 tablas</a:t>'),
        ('push reales (credenciales FCM/EAS)  ·  canales de voz y WhatsApp  ·  pruebas de carga  ·  revisión Legal y SSF',
         'push reales (FCM/EAS)  ·  llamada real con número  ·  WhatsApp  ·  pruebas de carga  ·  revisión Legal y SSF')],
}
# archivo v2 → número que se ve en la v3
NUMEROS = {7: 8, 8: 9, 9: 11}

# ----------------------------------------------------------------------------
# XML
# ----------------------------------------------------------------------------
NS = ('xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main" '
      'xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships" '
      'xmlns:p="http://schemas.openxmlformats.org/presentationml/2006/main"')


def emu(v):
    return int(round(v * EMU))


def xml_run(r):
    atrs = f'lang="es-ES" sz="{int(round(r["sz"] * 100))}"'
    if r['b']:
        atrs += ' b="1"'
    if r['i']:
        atrs += ' i="1"'
    if r['spc'] is not None:
        atrs += f' spc="{r["spc"]}"'
    f = html.escape(r['fuente'], quote=True)
    return (f'<a:r><a:rPr {atrs} dirty="0"><a:solidFill><a:srgbClr val="{r["color"]}"/></a:solidFill>'
            f'<a:latin typeface="{f}" pitchFamily="34" charset="0"/><a:ea typeface="{f}" pitchFamily="34" charset="-122"/>'
            f'<a:cs typeface="{f}" pitchFamily="34" charset="-120"/></a:rPr><a:t>{html.escape(r["t"], quote=False)}</a:t></a:r>')


def xml_elemento(e, id_, rels):
    xfrm = f'<a:xfrm><a:off x="{emu(e["x"])}" y="{emu(e["y"])}"/><a:ext cx="{emu(e["w"])}" cy="{emu(e["h"])}"/></a:xfrm>'
    if e['tipo'] == 'forma':
        av = f'<a:gd name="adj" fmla="val {e["adj"]}"/>' if e['adj'] is not None else ''
        return (f'<p:sp><p:nvSpPr><p:cNvPr id="{id_}" name="Shape {id_}"/><p:cNvSpPr/><p:nvPr/></p:nvSpPr><p:spPr>{xfrm}'
                f'<a:prstGeom prst="{e["prst"]}"><a:avLst>{av}</a:avLst></a:prstGeom><a:solidFill><a:srgbClr val="{e["fill"]}"/></a:solidFill>'
                f'<a:ln w="{e["ln_w"]}"><a:solidFill><a:srgbClr val="{e["ln"]}"/></a:solidFill><a:prstDash val="solid"/></a:ln></p:spPr></p:sp>')
    if e['tipo'] == 'imagen':
        rid = rels[e['archivo']]
        return (f'<p:pic><p:nvPicPr><p:cNvPr id="{id_}" name="Image {id_}" descr="{e["archivo"]}"/><p:cNvPicPr><a:picLocks noChangeAspect="1"/></p:cNvPicPr><p:nvPr/></p:nvPicPr>'
                f'<p:blipFill><a:blip r:embed="{rid}"/><a:stretch><a:fillRect/></a:stretch></p:blipFill><p:spPr>{xfrm}'
                f'<a:prstGeom prst="rect"><a:avLst/></a:prstGeom></p:spPr></p:pic>')
    algn = f' algn="{e["algn"]}"' if e['algn'] else ''
    parrafos = ''.join(
        f'<a:p><a:pPr{algn} indent="0" marL="0"><a:buNone/></a:pPr>{"".join(xml_run(r) for r in p)}'
        f'<a:endParaRPr lang="es-ES" sz="{int(round(p[-1]["sz"] * 100))}" dirty="0"/></a:p>' for p in e['parrafos'])
    return (f'<p:sp><p:nvSpPr><p:cNvPr id="{id_}" name="Text {id_}"/><p:cNvSpPr txBox="1"/><p:nvPr/></p:nvSpPr><p:spPr>{xfrm}'
            f'<a:prstGeom prst="rect"><a:avLst/></a:prstGeom><a:noFill/><a:ln/></p:spPr>'
            f'<p:txBody><a:bodyPr wrap="square" lIns="0" tIns="0" rIns="0" bIns="0" rtlCol="0" anchor="{e["anchor"]}"/><a:lstStyle/>{parrafos}</p:txBody></p:sp>')


def xml_diapositiva(spec, nombre, rels):
    if spec['fondo_imagen']:
        bg = (f'<p:bg><p:bgPr><a:blipFill dpi="0" rotWithShape="1"><a:blip r:embed="{rels[spec["fondo_imagen"]]}"><a:lum/></a:blip>'
              '<a:srcRect/><a:stretch><a:fillRect/></a:stretch></a:blipFill><a:effectLst/></p:bgPr></p:bg>')
    else:
        bg = f'<p:bg><p:bgPr><a:solidFill><a:srgbClr val="{spec["fondo"]}"/></a:solidFill><a:effectLst/></p:bgPr></p:bg>'
    cuerpo = ''.join(xml_elemento(e, 2 + k, rels) for k, e in enumerate(spec['elementos']))
    return (f'<?xml version="1.0" encoding="UTF-8" standalone="yes"?>\n<p:sld {NS}><p:cSld name="{nombre}">{bg}<p:spTree>'
            '<p:nvGrpSpPr><p:cNvPr id="1" name=""/><p:cNvGrpSpPr/><p:nvPr/></p:nvGrpSpPr><p:grpSpPr><a:xfrm><a:off x="0" y="0"/>'
            '<a:ext cx="0" cy="0"/><a:chOff x="0" y="0"/><a:chExt cx="0" cy="0"/></a:xfrm></p:grpSpPr>'
            f'{cuerpo}</p:spTree></p:cSld><p:clrMapOvr><a:masterClrMapping/></p:clrMapOvr></p:sld>')


def xml_notas(texto_nota, numero):
    return (f'<?xml version="1.0" encoding="UTF-8" standalone="yes"?>\n<p:notes {NS}><p:cSld><p:spTree><p:nvGrpSpPr><p:cNvPr id="1" name=""/>'
            '<p:cNvGrpSpPr/><p:nvPr/></p:nvGrpSpPr><p:grpSpPr><a:xfrm><a:off x="0" y="0"/><a:ext cx="0" cy="0"/><a:chOff x="0" y="0"/>'
            '<a:chExt cx="0" cy="0"/></a:xfrm></p:grpSpPr><p:sp><p:nvSpPr><p:cNvPr id="2" name="Slide Image Placeholder 1"/><p:cNvSpPr>'
            '<a:spLocks noGrp="1" noRot="1" noChangeAspect="1"/></p:cNvSpPr><p:nvPr><p:ph type="sldImg"/></p:nvPr></p:nvSpPr><p:spPr/></p:sp>'
            '<p:sp><p:nvSpPr><p:cNvPr id="3" name="Notes Placeholder 2"/><p:cNvSpPr><a:spLocks noGrp="1"/></p:cNvSpPr><p:nvPr>'
            '<p:ph type="body" idx="1"/></p:nvPr></p:nvSpPr><p:spPr/><p:txBody><a:bodyPr/><a:lstStyle/><a:p><a:r><a:rPr lang="es-ES" dirty="0"/>'
            f'<a:t>{html.escape(texto_nota, quote=False)}</a:t></a:r><a:endParaRPr lang="es-ES" dirty="0"/></a:p></p:txBody></p:sp>'
            '<p:sp><p:nvSpPr><p:cNvPr id="4" name="Slide Number Placeholder 3"/><p:cNvSpPr><a:spLocks noGrp="1"/></p:cNvSpPr><p:nvPr>'
            '<p:ph type="sldNum" sz="quarter" idx="10"/></p:nvPr></p:nvSpPr><p:spPr/><p:txBody><a:bodyPr/><a:lstStyle/><a:p>'
            f'<a:fld id="{{F7021451-1387-4CA6-816F-3879F97B5CBC}}" type="slidenum"><a:rPr lang="es-ES"/><a:t>{numero}</a:t></a:fld>'
            '<a:endParaRPr lang="es-ES"/></a:p></p:txBody></p:sp></p:spTree></p:cSld><p:clrMapOvr><a:masterClrMapping/></p:clrMapOvr></p:notes>')


# ----------------------------------------------------------------------------
# HTML (réplica para el PDF)
# ----------------------------------------------------------------------------
def html_diapositiva(spec, carpeta_img):
    fondo = f"background:#{spec['fondo']};"
    if spec['fondo_imagen']:
        fondo += f"background-image:url('{carpeta_img}/{spec['fondo_imagen']}');background-size:100% 100%;"
    partes = [f'<section class="slide" style="{fondo}">']
    for e in spec['elementos']:
        caja = f"left:{e['x']}in;top:{e['y']}in;width:{e['w']}in;height:{e['h']}in;"
        if e['tipo'] == 'forma':
            radio = '50%' if e['prst'] == 'ellipse' else (f"{min(e['w'], e['h']) * (e['adj'] or 16667) / 100000}in" if e['prst'] == 'roundRect' else '0')
            partes.append(f'<div style="position:absolute;{caja}background:#{e["fill"]};border:{e["ln_w"] / 12700}pt solid #{e["ln"]};border-radius:{radio};box-sizing:border-box"></div>')
        elif e['tipo'] == 'imagen':
            partes.append(f'<img style="position:absolute;{caja}" src="{carpeta_img}/{e["archivo"]}">')
        else:
            just = {'t': 'flex-start', 'ctr': 'center', 'b': 'flex-end'}[e['anchor']]
            alin = {'r': 'right', 'ctr': 'center', None: 'left'}[e['algn']]
            ps = []
            for p in e['parrafos']:
                runs = ''.join(
                    f'<span style="font-size:{r["sz"]}pt;color:#{r["color"]};font-weight:{700 if r["b"] else (600 if "Semibold" in r["fuente"] else 400)};'
                    f'font-style:{"italic" if r["i"] else "normal"};letter-spacing:{(r["spc"] or 0) / 100}pt">{html.escape(r["t"])}</span>'
                    for r in p)
                ps.append(f'<p style="margin:0;text-align:{alin}">{runs}</p>')
            partes.append(f'<div style="position:absolute;{caja}display:flex;flex-direction:column;justify-content:{just}">{"".join(ps)}</div>')
    partes.append('</section>')
    return ''.join(partes)


def main():
    if TRABAJO.exists():
        shutil.rmtree(TRABAJO)
    with zipfile.ZipFile(V2) as z:
        z.extractall(TRABAJO)
    ppt = TRABAJO / 'ppt'

    nuevas = {11: ('Slide 11', diapositiva_llamada(7), NOTA_LLAMADA, 7, ['telefono-llamada.png'], 'Slide-3-image-1.png'),
              12: ('Slide 12', diapositiva_auditoria(10), NOTA_AUDITORIA, 10, ['registro-llamada.png'], None)}
    for n, (nombre, spec, nota, numero, imagenes, fondo) in nuevas.items():
        rels, lineas = {}, []
        k = 1
        for archivo in ([fondo] if fondo else []) + imagenes:
            destino = archivo if archivo.startswith('Slide-') else f'image-{n}-{k}.png'
            if not archivo.startswith('Slide-'):
                shutil.copy(S / 'assets' / archivo, ppt / 'media' / destino)
            rels[archivo] = f'rId{k}'
            lineas.append(f'<Relationship Id="rId{k}" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/image" Target="../media/{destino}"/>')
            k += 1
        lineas.append(f'<Relationship Id="rId{k}" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/slideLayout" Target="../slideLayouts/slideLayout1.xml"/>')
        lineas.append(f'<Relationship Id="rId{k + 1}" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/notesSlide" Target="../notesSlides/notesSlide{n}.xml"/>')
        (ppt / 'slides' / f'slide{n}.xml').write_text(xml_diapositiva(spec, nombre, rels), encoding='utf-8')
        (ppt / 'slides' / '_rels' / f'slide{n}.xml.rels').write_text(
            '<?xml version="1.0" encoding="UTF-8" standalone="yes"?>\n<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">'
            + ''.join(lineas) + '</Relationships>', encoding='utf-8')
        (ppt / 'notesSlides' / f'notesSlide{n}.xml').write_text(xml_notas(nota, numero), encoding='utf-8')
        (ppt / 'notesSlides' / '_rels' / f'notesSlide{n}.xml.rels').write_text(
            '<?xml version="1.0" encoding="UTF-8" standalone="yes"?>\n<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">'
            '<Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/notesMaster" Target="../notesMasters/notesMaster1.xml"/>'
            f'<Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/slide" Target="../slides/slide{n}.xml"/>'
            '</Relationships>', encoding='utf-8')

    # Correcciones y numeración en las existentes
    for n, cambios in CORRECCIONES.items():
        p = ppt / 'slides' / f'slide{n}.xml'
        s = p.read_text(encoding='utf-8')
        for viejo, nuevo in cambios:
            assert s.count(viejo) == 1, (n, viejo)
            s = s.replace(viejo, nuevo)
        p.write_text(s, encoding='utf-8')
    for n, nuevo in NUMEROS.items():
        p = ppt / 'slides' / f'slide{n}.xml'
        s = p.read_text(encoding='utf-8')
        patron = re.compile(r'(<a:off x="10908792" y="6446520"/>.*?<a:t>)' + str(n) + r'(</a:t>)', re.S)
        s, cuantos = patron.subn(lambda m: m.group(1) + str(nuevo) + m.group(2), s)
        assert cuantos == 1, (n, cuantos)
        p.write_text(s, encoding='utf-8')
    for n, (viejo, nuevo) in TIEMPOS.items():
        p = ppt / 'notesSlides' / f'notesSlide{n}.xml'
        s = p.read_text(encoding='utf-8')
        assert s.count(viejo) == 1, (n, viejo)
        s = s.replace(viejo, nuevo)
        for a, b in NOTAS_TEXTO.get(n, []):
            assert s.count(a) == 1, (n, a)
            s = s.replace(a, b)
        p.write_text(s, encoding='utf-8')

    # Paquete: tipos, relaciones, orden y propiedades
    ct = TRABAJO / '[Content_Types].xml'
    s = ct.read_text(encoding='utf-8')
    extra = ''.join(
        f'<Override PartName="/ppt/slides/slide{n}.xml" ContentType="application/vnd.openxmlformats-officedocument.presentationml.slide+xml"/>'
        f'<Override PartName="/ppt/notesSlides/notesSlide{n}.xml" ContentType="application/vnd.openxmlformats-officedocument.presentationml.notesSlide+xml"/>'
        for n in nuevas)
    ct.write_text(s.replace('</Types>', extra + '</Types>'), encoding='utf-8')
    pr = ppt / '_rels' / 'presentation.xml.rels'
    s = pr.read_text(encoding='utf-8')
    s = s.replace('</Relationships>',
                  '<Relationship Id="rId17" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/slide" Target="slides/slide11.xml"/>'
                  '<Relationship Id="rId18" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/slide" Target="slides/slide12.xml"/></Relationships>')
    pr.write_text(s, encoding='utf-8')
    px = ppt / 'presentation.xml'
    s = px.read_text(encoding='utf-8')
    viejo = re.search(r'<p:sldIdLst>.*?</p:sldIdLst>', s).group(0)
    orden = ['<p:sldId id="256" r:id="rId2"/>', '<p:sldId id="257" r:id="rId3"/>', '<p:sldId id="258" r:id="rId4"/>',
             '<p:sldId id="259" r:id="rId5"/>', '<p:sldId id="260" r:id="rId6"/>', '<p:sldId id="261" r:id="rId7"/>',
             '<p:sldId id="266" r:id="rId17"/>', '<p:sldId id="262" r:id="rId8"/>', '<p:sldId id="263" r:id="rId9"/>',
             '<p:sldId id="267" r:id="rId18"/>', '<p:sldId id="264" r:id="rId10"/>', '<p:sldId id="265" r:id="rId11"/>']
    px.write_text(s.replace(viejo, '<p:sldIdLst>' + ''.join(orden) + '</p:sldIdLst>'), encoding='utf-8')
    app = TRABAJO / 'docProps' / 'app.xml'
    s = app.read_text(encoding='utf-8')
    s = s.replace('<Slides>10</Slides>', '<Slides>12</Slides>').replace('<Notes>10</Notes>', '<Notes>12</Notes>')
    s = s.replace('<vt:i4>10</vt:i4>', '<vt:i4>12</vt:i4>').replace('<vt:vector size="13" baseType="lpstr">', '<vt:vector size="15" baseType="lpstr">')
    s = s.replace('<vt:lpstr>Slide 10</vt:lpstr>', '<vt:lpstr>Slide 10</vt:lpstr><vt:lpstr>Slide 11</vt:lpstr><vt:lpstr>Slide 12</vt:lpstr>')
    app.write_text(s, encoding='utf-8')

    # Zip: [Content_Types].xml primero
    if SALIDA.exists():
        SALIDA.unlink()
    with zipfile.ZipFile(SALIDA, 'w', zipfile.ZIP_DEFLATED) as z:
        z.write(ct, '[Content_Types].xml')
        for f in sorted(TRABAJO.rglob('*')):
            if f.is_file() and f != ct:
                arc = f.relative_to(TRABAJO).as_posix()
                z.write(f, arc, compress_type=zipfile.ZIP_STORED if f.suffix == '.mp4' else zipfile.ZIP_DEFLATED)

    # Réplica HTML de las nuevas
    shutil.copy(ppt / 'media' / 'Slide-3-image-1.png', S / 'assets' / 'Slide-3-image-1.png')
    estilos = ('<style>@page{size:13.333in 7.5in;margin:0}html,body{margin:0;padding:0}'
               '.slide{position:relative;width:13.333in;height:7.5in;overflow:hidden;page-break-after:always;'
               'font-family:"Segoe UI","Helvetica Neue",Arial,sans-serif;line-height:1.28}</style>')
    for n, (_, spec, *_resto) in nuevas.items():
        (S / f'nueva-{n}.html').write_text('<!doctype html><meta charset="utf-8">' + estilos + html_diapositiva(spec, 'assets'), encoding='utf-8')
    print('listo:', SALIDA)


if __name__ == '__main__':
    main()
