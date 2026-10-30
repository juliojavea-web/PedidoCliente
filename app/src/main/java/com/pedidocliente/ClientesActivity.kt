package com.pedidocliente

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.ListView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import java.io.File

/** Altas, modificaciones y bajas de clientes (Cliente, Dirección, Código de cliente). */
class ClientesActivity : AppCompatActivity() {

    private lateinit var lista: ListView
    private lateinit var etCodigo: EditText
    private lateinit var etNombre: EditText
    private lateinit var etDireccion: EditText

    private var clientes: List<ClienteStore.Cliente> = emptyList()
    private var seleccionado: ClienteStore.Cliente? = null

    private val elegirFichero = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) importar(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_clientes)

        lista = findViewById(R.id.lvClientes)
        etCodigo = findViewById(R.id.etCliCodigo)
        etNombre = findViewById(R.id.etCliNombre)
        etDireccion = findViewById(R.id.etCliDireccion)
        lista.emptyView = findViewById<View>(R.id.tvVacio)

        lista.setOnItemClickListener { _, _, pos, _ ->
            val c = clientes[pos]
            seleccionado = c
            etCodigo.setText(c.codigo)
            etNombre.setText(c.nombre)
            etDireccion.setText(c.direccion)
        }

        findViewById<Button>(R.id.btnAlta).setOnClickListener { alta() }
        findViewById<Button>(R.id.btnModificar).setOnClickListener { modificar() }
        findViewById<Button>(R.id.btnBaja).setOnClickListener { baja() }
        findViewById<Button>(R.id.btnExportar).setOnClickListener { exportar() }
        findViewById<Button>(R.id.btnImportar).setOnClickListener {
            elegirFichero.launch(arrayOf("text/*", "application/octet-stream", "application/vnd.ms-excel"))
        }
        findViewById<Button>(R.id.btnCerrar).setOnClickListener { finish() }

        refrescar()
    }

    private fun refrescar() {
        clientes = ClienteStore.todos(this)
        lista.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_list_item_1,
            clientes.map { "${it.codigo} - ${it.nombre}" + if (it.direccion.isNotEmpty()) "\n${it.direccion}" else "" }
        )
    }

    private fun vaciarCampos() {
        etCodigo.setText("")
        etNombre.setText("")
        etDireccion.setText("")
        seleccionado = null
    }

    /** Devuelve el cliente escrito en el formulario, o null si hay algún error. */
    private fun leerFormulario(): ClienteStore.Cliente? {
        val codigo = ClienteStore.limpiar(etCodigo.text.toString())
        val nombre = ClienteStore.limpiar(etNombre.text.toString())
        val direccion = ClienteStore.limpiar(etDireccion.text.toString())
        if (codigo.isEmpty()) {
            etCodigo.error = "Indica el código de cliente"
            etCodigo.requestFocus()
            return null
        }
        if (nombre.isEmpty()) {
            etNombre.error = "Indica el nombre del cliente"
            etNombre.requestFocus()
            return null
        }
        return ClienteStore.Cliente(codigo, nombre, direccion)
    }

    private fun alta() {
        val nuevo = leerFormulario() ?: return
        val actuales = ClienteStore.todos(this)
        if (actuales.any { it.mismoQue(nuevo) }) {
            Toast.makeText(this, "Ya existe un cliente con ese nombre y dirección. Usa Modificar.", Toast.LENGTH_LONG).show()
            return
        }
        ClienteStore.guardar(this, actuales + nuevo)
        refrescar()
        vaciarCampos()
        Toast.makeText(this, "Cliente dado de alta", Toast.LENGTH_SHORT).show()
    }

    private fun modificar() {
        val orig = seleccionado
        if (orig == null) {
            Toast.makeText(this, "Elige en la lista el cliente que quieres modificar", Toast.LENGTH_LONG).show()
            return
        }
        val nuevo = leerFormulario() ?: return
        val resto = ClienteStore.todos(this).filter { it != orig }
        if (resto.any { it.mismoQue(nuevo) }) {
            Toast.makeText(this, "Ya existe otro cliente con ese nombre y dirección", Toast.LENGTH_LONG).show()
            return
        }
        ClienteStore.guardar(this, resto + nuevo)
        refrescar()
        vaciarCampos()
        Toast.makeText(this, "Cliente modificado", Toast.LENGTH_SHORT).show()
    }

    private fun baja() {
        val c = seleccionado
        if (c == null) {
            Toast.makeText(this, "Elige en la lista el cliente que quieres dar de baja", Toast.LENGTH_LONG).show()
            return
        }
        AlertDialog.Builder(this)
            .setTitle("Baja de cliente")
            .setMessage("¿Dar de baja al cliente ${c.codigo} - ${c.nombre} - ${c.direccion}?")
            .setPositiveButton("Sí") { _, _ ->
                ClienteStore.guardar(this, ClienteStore.todos(this).filter { it != c })
                refrescar()
                vaciarCampos()
                Toast.makeText(this, "Cliente dado de baja", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("No", null)
            .show()
    }

    /** Exporta clientes.csv y abre el menú de compartir (WhatsApp, Gmail, Drive...). */
    private fun exportar() {
        val todos = ClienteStore.todos(this)
        if (todos.isEmpty()) {
            Toast.makeText(this, "No hay clientes que exportar", Toast.LENGTH_LONG).show()
            return
        }
        val carpeta = File(cacheDir, "exportar").apply { mkdirs(); listFiles()?.forEach { it.delete() } }
        val archivo = File(carpeta, "clientes.csv")
        archivo.writeText("\uFEFF" + ClienteStore.serializar(todos), Charsets.UTF_8)
        val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", archivo)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/csv"
            putExtra(Intent.EXTRA_SUBJECT, "Clientes")
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(intent, "Enviar clientes.csv con…"))
    }

    private fun importar(uri: Uri) {
        val texto = try {
            contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) }
        } catch (e: Exception) { null }
        val nuevos = if (texto == null) emptyList() else ClienteStore.parsear(texto)
        if (nuevos.isEmpty()) {
            Toast.makeText(this, "El fichero no contiene clientes válidos", Toast.LENGTH_LONG).show()
            return
        }
        AlertDialog.Builder(this)
            .setTitle("Importar clientes")
            .setMessage("El fichero tiene ${nuevos.size} clientes. ¿Combinar con los que ya tienes o reemplazarlos?")
            .setPositiveButton("Combinar") { _, _ ->
                val (lista, n) = ClienteStore.combinar(ClienteStore.todos(this), nuevos)
                ClienteStore.guardar(this, lista)
                refrescar(); vaciarCampos()
                Toast.makeText(this, "$n clientes añadidos. Total: ${lista.size}", Toast.LENGTH_LONG).show()
            }
            .setNegativeButton("Reemplazar") { _, _ ->
                val (lista, n) = ClienteStore.combinar(emptyList(), nuevos)
                ClienteStore.guardar(this, lista)
                refrescar(); vaciarCampos()
                Toast.makeText(this, "$n clientes importados", Toast.LENGTH_LONG).show()
            }
            .setNeutralButton("Cancelar", null)
            .show()
    }
}
