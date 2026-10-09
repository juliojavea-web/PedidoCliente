package com.pedidocliente

import android.app.DatePickerDialog
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import java.io.File
import java.util.Calendar
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private class Fila(val articulo: EditText, val cantidad: EditText, val unidad: EditText)

    private lateinit var etCliente: EditText
    private lateinit var etCodigo: EditText
    private lateinit var etFecha: EditText

    private val filas = mutableListOf<Fila>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        etCliente = findViewById(R.id.etCliente)
        etCodigo = findViewById(R.id.etCodigo)
        etFecha = findViewById(R.id.etFecha)

        val contenedor = findViewById<LinearLayout>(R.id.rowsContainer)
        repeat(DocxBuilder.ROWS) {
            val v = layoutInflater.inflate(R.layout.item_row, contenedor, false)
            contenedor.addView(v)
            filas += Fila(
                v.findViewById(R.id.etArticulo),
                v.findViewById(R.id.etCantidad),
                v.findViewById(R.id.etUnidad)
            )
        }

        etFecha.setOnClickListener { elegirFecha() }
        findViewById<Button>(R.id.btnSend).setOnClickListener { crearYAbrirGmail() }
        findViewById<Button>(R.id.btnClear).setOnClickListener { limpiar() }
    }

    private fun elegirFecha() {
        val cal = Calendar.getInstance()
        DatePickerDialog(
            this,
            { _, anio, mes, dia ->
                etFecha.setText(String.format(Locale.US, "%02d/%02d/%04d", dia, mes + 1, anio))
            },
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH),
            cal.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    /** Crea el Pedido.docx y abre Gmail con el archivo adjunto y los destinatarios predefinidos. */
    private fun crearYAbrirGmail() {
        val cliente = etCliente.text.toString().trim()
        if (cliente.isEmpty()) {
            etCliente.error = "Indica el cliente"
            etCliente.requestFocus()
            return
        }

        val lineas = filas.map {
            DocxBuilder.Linea(
                it.articulo.text.toString().trim(),
                it.cantidad.text.toString().trim(),
                it.unidad.text.toString().trim()
            )
        }.filter { it.articulo.isNotEmpty() || it.cantidad.isNotEmpty() || it.unidad.isNotEmpty() }

        if (lineas.isEmpty()) {
            Toast.makeText(this, "Añade al menos un artículo", Toast.LENGTH_LONG).show()
            return
        }

        val codigo = etCodigo.text.toString().trim()
        val fecha = etFecha.text.toString().trim()

        val carpeta = File(cacheDir, "pedidos").apply {
            mkdirs()
            listFiles()?.forEach { it.delete() }
        }
        val archivo = File(carpeta, "Pedido.docx")
        try {
            DocxBuilder.write(archivo, DocxBuilder.Pedido(cliente, codigo, fecha, lineas))
        } catch (e: Exception) {
            Toast.makeText(this, "No se pudo crear el documento: ${e.message}", Toast.LENGTH_LONG).show()
            return
        }

        val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", archivo)
        val cuerpo = buildString {
            append("Adjunto el pedido del cliente $cliente")
            if (codigo.isNotEmpty()) append(" (código $codigo)")
            append(".")
            if (fecha.isNotEmpty()) append("\nFecha de entrega: $fecha")
        }

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = DOCX_MIME
            putExtra(Intent.EXTRA_EMAIL, arrayOf(EMAIL_PARA))
            putExtra(Intent.EXTRA_BCC, arrayOf(EMAIL_CCO))
            putExtra(Intent.EXTRA_SUBJECT, "Pedido - $cliente")
            putExtra(Intent.EXTRA_TEXT, cuerpo)
            putExtra(Intent.EXTRA_STREAM, uri)
            clipData = ClipData.newRawUri("Pedido.docx", uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        // Gmail directamente; si no está instalado, selector de apps de correo
        try {
            startActivity(Intent(intent).setPackage(GMAIL_PACKAGE))
        } catch (e: ActivityNotFoundException) {
            startActivity(Intent.createChooser(intent, "Enviar pedido con…"))
        }
    }

    private fun limpiar() {
        etCliente.text = null
        etCodigo.text = null
        etFecha.text = null
        filas.forEach {
            it.articulo.text = null
            it.cantidad.text = null
            it.unidad.text = null
        }
        etCliente.requestFocus()
    }

    private companion object {
        // Destinatarios predefinidos
        const val EMAIL_PARA = "pedidos@avicolasanbernardo.es"
        const val EMAIL_CCO = "juliojavea@gmail.com"
        const val GMAIL_PACKAGE = "com.google.android.gm"
        const val DOCX_MIME =
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
    }
}
