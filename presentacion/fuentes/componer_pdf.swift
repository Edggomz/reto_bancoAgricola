import AppKit
import PDFKit

// Compone el PDF v3: páginas de la v2 (vectoriales), las dos nuevas, y parches sobre los textos que cambiaron.
let args = CommandLine.arguments
let v2 = PDFDocument(url: URL(fileURLWithPath: args[1]))!
let nueva7 = PDFDocument(url: URL(fileURLWithPath: args[2]))!
let nueva10 = PDFDocument(url: URL(fileURLWithPath: args[3]))!
let salida = URL(fileURLWithPath: args[4])

let ancho: CGFloat = 960, alto: CGFloat = 540
let pulgada: CGFloat = 72

struct Tramo { let texto: String; let negrita: Bool; let color: String }
struct Parche {
  let x: CGFloat, y: CGFloat, w: CGFloat, h: CGFloat // pulgadas, desde arriba a la izquierda
  let lineas: [[Tramo]]
  let tam: CGFloat
  let alineacion: NSTextAlignment
  let centradoVertical: Bool
  var tapa: (CGFloat, CGFloat, CGFloat, CGFloat)? = nil // área a cubrir si difiere de la caja
  var copiarDesde: CGFloat? = nil // si hay fondo con degradado: pulgadas a la izquierda de donde se copia el fondo
}

func color(_ hex: String) -> NSColor {
  let v = Int(hex, radix: 16)!
  return NSColor(srgbRed: CGFloat((v >> 16) & 255) / 255, green: CGFloat((v >> 8) & 255) / 255, blue: CGFloat(v & 255) / 255, alpha: 1)
}

// Color del fondo justo al lado del texto, leído de la página renderizada
func muestra(_ pagina: PDFPage, _ xIn: CGFloat, _ yIn: CGFloat) -> NSColor {
  let escala: CGFloat = 2
  let img = pagina.thumbnail(of: NSSize(width: ancho * escala, height: alto * escala), for: .mediaBox)
  let rep = NSBitmapImageRep(data: img.tiffRepresentation!)!
  let px = Int(xIn * pulgada * escala * CGFloat(rep.pixelsWide) / (ancho * escala))
  let py = Int(yIn * pulgada * escala * CGFloat(rep.pixelsHigh) / (alto * escala))
  return rep.colorAt(x: min(max(px, 0), rep.pixelsWide - 1), y: min(max(py, 0), rep.pixelsHigh - 1)) ?? .white
}

let numero = { (n: String, c: String) in Parche(x: 11.93, y: 7.05, w: 0.7, h: 0.2, lineas: [[Tramo(texto: n, negrita: false, color: c)]], tam: 9, alineacion: .right, centradoVertical: false, tapa: (12.2, 7.02, 0.45, 0.24), copiarDesde: 0.6) }

let parches: [Int: [Parche]] = [
  3: [Parche(x: 0.84, y: 6.12, w: 2.7, h: 0.62,
             lineas: [[Tramo(texto: "Al día en que ya le pagaron.", negrita: false, color: "5B5B5B")],
                      [Tramo(texto: "Mismo plazo, sin sorpresas.", negrita: false, color: "5B5B5B")]],
             tam: 11.5, alineacion: .center, centradoVertical: false)],
  6: [numero("8", "8A8986")],
  7: [numero("9", "8A8B8E")],
  8: [Parche(x: 6.37, y: 2.61, w: 1.55, h: 0.3, lineas: [[Tramo(texto: "40 tablas", negrita: false, color: "5B5B5B")]], tam: 11, alineacion: .left, centradoVertical: false),
      Parche(x: 0.95, y: 6.22, w: 11.5, h: 0.52,
             lineas: [[Tramo(texto: "Pendiente antes de producción:  ", negrita: true, color: "1A1B1A"),
                       Tramo(texto: "push reales (FCM/EAS)  ·  llamada real con número  ·  WhatsApp  ·  pruebas de carga  ·  revisión Legal y SSF", negrita: false, color: "5B5B5B")]],
             tam: 11.5, alineacion: .left, centradoVertical: true, tapa: (0.8, 6.26, 11.75, 0.44)),
      numero("11", "8A8B8E")],
]

var caja = CGRect(x: 0, y: 0, width: ancho, height: alto)
let ctx = CGContext(salida as CFURL, mediaBox: &caja, nil)!

func dibujar(_ pagina: PDFPage, _ parchesPagina: [Parche]) {
  ctx.beginPDFPage(nil)
  ctx.saveGState()
  pagina.draw(with: .mediaBox, to: ctx)
  ctx.restoreGState()
  NSGraphicsContext.saveGraphicsState()
  NSGraphicsContext.current = NSGraphicsContext(cgContext: ctx, flipped: false)
  for p in parchesPagina {
    let t = p.tapa ?? (p.x, p.y, p.w, p.h)
    let area = NSRect(x: t.0 * pulgada, y: alto - (t.1 + t.3) * pulgada, width: t.2 * pulgada, height: t.3 * pulgada)
    if let dx = p.copiarDesde {
      // el mismo fondo vectorial, corrido: sin saltos de color en los degradados
      ctx.saveGState()
      ctx.clip(to: area)
      ctx.translateBy(x: dx * pulgada, y: 0)
      pagina.draw(with: .mediaBox, to: ctx)
      ctx.restoreGState()
    } else {
      muestra(pagina, t.0 - 0.03, t.1 + t.3 / 2).setFill()
      area.fill()
    }
    let parrafo = NSMutableParagraphStyle()
    parrafo.alignment = p.alineacion
    parrafo.lineHeightMultiple = 1.08
    let texto = NSMutableAttributedString()
    for (i, linea) in p.lineas.enumerated() {
      for tramo in linea {
        let fuente = NSFont(name: tramo.negrita ? "HelveticaNeue-Bold" : "HelveticaNeue", size: p.tam)!
        texto.append(NSAttributedString(string: tramo.texto, attributes: [.font: fuente, .foregroundColor: color(tramo.color), .paragraphStyle: parrafo]))
      }
      if i < p.lineas.count - 1 { texto.append(NSAttributedString(string: "\n", attributes: [.paragraphStyle: parrafo])) }
    }
    var rect = NSRect(x: p.x * pulgada, y: alto - (p.y + p.h) * pulgada, width: p.w * pulgada, height: p.h * pulgada)
    if p.centradoVertical {
      let alturaTexto = texto.boundingRect(with: rect.size, options: [.usesLineFragmentOrigin]).height
      rect.origin.y += (rect.height - alturaTexto) / 2
      rect.size.height = alturaTexto
    }
    texto.draw(with: rect, options: [.usesLineFragmentOrigin])
  }
  NSGraphicsContext.restoreGraphicsState()
  ctx.endPDFPage()
}

let orden: [(PDFDocument, Int)] = [(v2, 0), (v2, 1), (v2, 2), (v2, 3), (v2, 4), (v2, 5), (nueva7, 0), (v2, 6), (v2, 7), (nueva10, 0), (v2, 8), (v2, 9)]
for (doc, i) in orden {
  dibujar(doc.page(at: i)!, doc === v2 ? (parches[i] ?? []) : [])
}
ctx.closePDF()
print("PDF listo con \(orden.count) páginas")
