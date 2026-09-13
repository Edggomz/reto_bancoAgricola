# Cómo se armó la v3

La v3 parte de la v2 (`~/Desktop/presentacion/Ruta_Presentacion_Bancoagricola_v2.pptx`
y su PDF) y no se edita a mano: se regenera.

```bash
python3 construir_deck.py
```

Escribe `Ruta_Presentacion_Bancoagricola_v3.pptx` y las réplicas `nueva-11.html` y
`nueva-12.html` de las dos diapositivas nuevas. Para el PDF de respaldo, imprime las
réplicas a PDF de 13.333 × 7.5 pulgadas (Chrome) y compón:

```bash
swift componer_pdf.swift Ruta_Presentacion_Bancoagricola_v2.pdf nueva-11.pdf nueva-12.pdf Ruta_Presentacion_Bancoagricola_v3.pdf
```

`assets/` tiene las capturas reales: el teléfono de la llamada emulada y la auditoría
de esa llamada. Si alguien tiene PowerPoint, exportar el `.pptx` a PDF da una copia
exacta y reemplaza este paso.
