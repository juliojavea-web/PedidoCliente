package com.pedidocliente

import android.app.DatePickerDialog
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.widget.ArrayAdapter
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

    private class Fila(val articulo: EditText, val cantidad: EditText, val unidad: EditText) {
        fun estaVacia(): Boolean =
            articulo.text.isNullOrBlank() && cantidad.text.isNullOrBlank() && unidad.text.isNullOrBlank()
    }

    private lateinit var etCliente: EditText
    private lateinit var etDireccion: EditText
    private lateinit var etCodigo: EditText
    private lateinit var etFecha: EditText
    private lateinit var contenedor: LinearLayout

    private val filas = mutableListOf<Fila>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        etCliente = findViewById(R.id.etCliente)
        etDireccion = findViewById(R.id.etDireccion)
        etCodigo = findViewById(R.id.etCodigo)
        etFecha = findViewById(R.id.etFecha)
        contenedor = findViewById(R.id.rowsContainer)

        // Empieza con una sola fila; al rellenarla aparece otra
        agregarFila()

        etFecha.setOnClickListener { elegirFecha() }
        findViewById<Button>(R.id.btnClientes).setOnClickListener {
            startActivity(Intent(this, ClientesActivity::class.java))
        }
        // Al salir del campo (o elegir una sugerencia), si coincide con clientes guardados se rellena el resto;
        // si hay varios (mismo nombre o código) se elige por la dirección
        etCliente.setOnFocusChangeListener { _, tieneFoco -> if (!tieneFoco) rellenarDesde(true) }
        etCodigo.setOnFocusChangeListener { _, tieneFoco -> if (!tieneFoco) rellenarDesde(false) }
        (etCliente as android.widget.AutoCompleteTextView).setOnItemClickListener { _, _, _, _ -> rellenarDesde(true) }
        findViewById<Button>(R.id.btnSend).setOnClickListener { crearYAbrirGmail() }
        findViewById<Button>(R.id.btnClear).setOnClickListener { limpiar() }
    }

    override fun onResume() {
        super.onResume()
        actualizarSugerencias()
    }

    private fun actualizarSugerencias() {
        val nombres = ClienteStore.todos(this).map { it.nombre }.distinctBy { it.lowercase() }
        (etCliente as android.widget.AutoCompleteTextView)
            .setAdapter(ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, nombres))
    }

    private fun aplicarCliente(c: ClienteStore.Cliente) {
        etCliente.setText(c.nombre)
        etDireccion.setText(c.direccion)
        etCodigo.setText(c.codigo)
    }

    private fun rellenarDesde(porNombre: Boolean) {
        val texto = (if (porNombre) etCliente else etCodigo).text.toString().trim()
        if (texto.isEmpty()) return
        val m = if (porNombre) ClienteStore.porNombre(this, texto) else ClienteStore.porCodigo(this, texto)
        when {
            m.isEmpty() -> return
            m.size == 1 -> aplicarCliente(m[0])
            else -> androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Elige el cliente")
                .setItems(m.map { "${it.codigo} - ${it.nombre} - ${it.direccion}" }.toTypedArray()) { _, i ->
                    aplicarCliente(m[i])
                }
                .setNegativeButton("Cancelar", null)
                .show()
        }
    }

    /** Añade una fila vacía al final y la vigila: cuando se rellena, añade la siguiente. */
    private fun agregarFila() {
        val vista = layoutInflater.inflate(R.layout.item_row, contenedor, false)
        contenedor.addView(vista)
        val fila = Fila(
            vista.findViewById(R.id.etArticulo),
            vista.findViewById(R.id.etCantidad),
            vista.findViewById(R.id.etUnidad)
        )
        filas.add(fila)

        val vigilante = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                if (fila === filas.lastOrNull() && !fila.estaVacia()) {
                    agregarFila()
                }
            }
        }
        fila.articulo.addTextChangedListener(vigilante)
        fila.cantidad.addTextChangedListener(vigilante)
        fila.unidad.addTextChangedListener(vigilante)
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

        val codigo = etCodigo.text.toString().trim()
        if (codigo.isEmpty()) {
            etCodigo.error = "Indica el código de cliente"
            etCodigo.requestFocus()
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

        val direccion = etDireccion.text.toString().trim()
        val fecha = etFecha.text.toString().trim()

        val carpeta = File(cacheDir, "pedidos").apply {
            mkdirs()
            listFiles()?.forEach { it.delete() }
        }
        val archivo = File(carpeta, "Pedido.docx")
        try {
            val enlaceGmail = "https://mail.google.com/mail/?view=cm&fs=1" +
                "&to=${Uri.encode(EMAIL_PARA)}&bcc=${Uri.encode(EMAIL_CCO)}" +
                "&su=${Uri.encode("Pedido - $cliente")}"
            DocxBuilder.write(archivo, DocxBuilder.Pedido(cliente, direccion, codigo, fecha, enlaceGmail, lineas))
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

    /** Nuevo pedido: borra todos los datos y deja una sola fila vacía. */
    private fun limpiar() {
        etCliente.setText("")
        etCliente.error = null
        etDireccion.setText("")
        etCodigo.setText("")
        etCodigo.error = null
        etFecha.setText("")
        contenedor.removeAllViews()
        filas.clear()
        agregarFila()
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
