package com.pedidocliente

import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Genera el fichero Pedido.docx sin dependencias externas:
 * un .docx es un ZIP con varios ficheros XML.
 */
object DocxBuilder {

    data class Linea(val articulo: String, val cantidad: String, val unidad: String)

    data class Pedido(
        val cliente: String,
        val direccion: String,
        val codigoCliente: String,
        val fechaEntrega: String,
        val enlaceGmail: String,
        val lineas: List<Linea>
    )

    private val ANCHOS = intArrayOf(5400, 1800, 1800) // suma 9000 (A4 con márgenes de 2,54 cm)

    fun write(file: File, pedido: Pedido) {
        ZipOutputStream(FileOutputStream(file).buffered()).use { zip ->
            entrada(zip, "[Content_Types].xml", CONTENT_TYPES)
            entrada(zip, "_rels/.rels", RELS)
            entrada(zip, "word/document.xml", documento(pedido))
            entrada(zip, "word/styles.xml", ESTILOS)
            entrada(zip, "word/_rels/document.xml.rels", docRels(pedido.enlaceGmail))
        }
    }

    private fun entrada(zip: ZipOutputStream, nombre: String, contenido: String) {
        zip.putNextEntry(ZipEntry(nombre))
        zip.write(contenido.toByteArray(Charsets.UTF_8))
        zip.closeEntry()
    }

    private fun esc(s: String): String = s
        .replace("\r", " ")
        .replace("\n", " ")
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")

    private fun run(texto: String, negrita: Boolean = false, tamano: Int = 22): String {
        val b = if (negrita) "<w:b/>" else ""
        return "<w:r><w:rPr>$b<w:sz w:val=\"$tamano\"/></w:rPr>" +
            "<w:t xml:space=\"preserve\">${esc(texto)}</w:t></w:r>"
    }

    private fun parrafo(runs: String, despues: Int = 0, centrado: Boolean = false): String {
        val jc = if (centrado) "<w:jc w:val=\"center\"/>" else ""
        return "<w:p><w:pPr><w:spacing w:after=\"$despues\"/>$jc</w:pPr>$runs</w:p>"
    }

    private fun campo(etiqueta: String, valor: String): String =
        parrafo(run("$etiqueta ", negrita = true, tamano = 24) + run(valor, tamano = 24), despues = 120)

    private fun celda(ancho: Int, texto: String, cabecera: Boolean, centrado: Boolean): String {
        val runs = if (texto.isEmpty()) "" else run(texto, negrita = cabecera)
        return "<w:tc><w:tcPr><w:tcW w:w=\"$ancho\" w:type=\"dxa\"/></w:tcPr>" +
            parrafo(runs, centrado = centrado) + "</w:tc>"
    }

    private fun fila(textos: List<String>, cabecera: Boolean = false): String {
        val props = if (cabecera) "<w:trPr><w:tblHeader/></w:trPr>" else ""
        val celdas = textos.indices.joinToString("") { i ->
            celda(ANCHOS[i], textos[i], cabecera, centrado = i > 0)
        }
        return "<w:tr>$props$celdas</w:tr>"
    }

    private fun tabla(lineas: List<Linea>): String {
        val sb = StringBuilder()
        // Sin bordes: las líneas del pedido no llevan recuadro
        sb.append("<w:tbl><w:tblPr><w:tblW w:w=\"${ANCHOS.sum()}\" w:type=\"dxa\"/><w:tblLayout w:type=\"fixed\"/><w:tblCellMar>")
        sb.append("<w:top w:w=\"80\" w:type=\"dxa\"/><w:left w:w=\"120\" w:type=\"dxa\"/>")
        sb.append("<w:bottom w:w=\"80\" w:type=\"dxa\"/><w:right w:w=\"120\" w:type=\"dxa\"/>")
        sb.append("</w:tblCellMar></w:tblPr><w:tblGrid>")
        ANCHOS.forEach { sb.append("<w:gridCol w:w=\"$it\"/>") }
        sb.append("</w:tblGrid>")
        sb.append(fila(listOf("Artículo", "Cantidad", "Unidad"), cabecera = true))
        // Solo las filas rellenadas
        for (l in lineas) {
            sb.append(fila(listOf(l.articulo, l.cantidad, l.unidad)))
        }
        sb.append("</w:tbl>")
        return sb.toString()
    }

    /** Enlace directo a Gmail, justo después de la última línea rellenada. */
    private fun parrafoEnlace(): String =
        "<w:p><w:pPr><w:spacing w:before=\"120\" w:after=\"0\"/></w:pPr>" +
            run("Enviar por Email: ", negrita = true, tamano = 24) +
            "<w:hyperlink r:id=\"rId2\" w:history=\"1\"><w:r><w:rPr><w:color w:val=\"0563C1\"/>" +
            "<w:sz w:val=\"24\"/><w:u w:val=\"single\"/></w:rPr><w:t>Abrir en Gmail</w:t></w:r></w:hyperlink></w:p>"

    private fun documento(p: Pedido): String {
        val cuerpo = parrafo(run("PEDIDO", negrita = true, tamano = 40), despues = 360, centrado = true) +
            campo("Cliente:", p.cliente) +
            campo("Dirección:", p.direccion) +
            campo("Código de cliente:", p.codigoCliente) +
            campo("Fecha de entrega:", p.fechaEntrega) +
            parrafo("", despues = 200) +
            tabla(p.lineas) +
            parrafoEnlace() // Word exige un párrafo después de la tabla: es el del enlace
        return XML + "<w:document xmlns:w=\"$NS\" xmlns:r=\"$NS_REL\"><w:body>$cuerpo" +
            "<w:sectPr><w:pgSz w:w=\"11906\" w:h=\"16838\"/>" +
            "<w:pgMar w:top=\"1440\" w:right=\"1440\" w:bottom=\"1440\" w:left=\"1440\" " +
            "w:header=\"708\" w:footer=\"708\" w:gutter=\"0\"/></w:sectPr></w:body></w:document>"
    }

    private const val NS = "http://schemas.openxmlformats.org/wordprocessingml/2006/main"
    private const val NS_REL = "http://schemas.openxmlformats.org/officeDocument/2006/relationships"
    private const val XML = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"

    private const val CONTENT_TYPES = XML +
        "<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">" +
        "<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>" +
        "<Default Extension=\"xml\" ContentType=\"application/xml\"/>" +
        "<Override PartName=\"/word/document.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml\"/>" +
        "<Override PartName=\"/word/styles.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.wordprocessingml.styles+xml\"/>" +
        "</Types>"

    private const val RELS = XML +
        "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">" +
        "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"word/document.xml\"/>" +
        "</Relationships>"

    private fun docRels(enlace: String): String = XML +
        "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">" +
        "<Relationship Id=\"rId1\" Type=\"$NS_REL/styles\" Target=\"styles.xml\"/>" +
        "<Relationship Id=\"rId2\" Type=\"$NS_REL/hyperlink\" Target=\"${esc(enlace)}\" TargetMode=\"External\"/>" +
        "</Relationships>"

    private const val ESTILOS = XML +
        "<w:styles xmlns:w=\"$NS\"><w:docDefaults>" +
        "<w:rPrDefault><w:rPr><w:rFonts w:ascii=\"Calibri\" w:hAnsi=\"Calibri\" w:cs=\"Calibri\"/>" +
        "<w:sz w:val=\"22\"/><w:lang w:val=\"es-ES\"/></w:rPr></w:rPrDefault>" +
        "<w:pPrDefault><w:pPr><w:spacing w:after=\"0\" w:line=\"240\" w:lineRule=\"auto\"/></w:pPr></w:pPrDefault>" +
        "</w:docDefaults>" +
        "<w:style w:type=\"paragraph\" w:default=\"1\" w:styleId=\"Normal\"><w:name w:val=\"Normal\"/><w:qFormat/></w:style>" +
        "</w:styles>"
}
