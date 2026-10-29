package com.pedidocliente

import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.ListView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity

/** Altas, modificaciones y bajas de clientes (Cliente, Dirección, Código de cliente). */
class ClientesActivity : AppCompatActivity() {

    private lateinit var lista: ListView
    private lateinit var etCodigo: EditText
    private lateinit var etNombre: EditText
    private lateinit var etDireccion: EditText

    private var clientes: List<ClienteStore.Cliente> = emptyList()

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
            etCodigo.setText(c.codigo)
            etNombre.setText(c.nombre)
            etDireccion.setText(c.direccion)
        }

        findViewById<Button>(R.id.btnAlta).setOnClickListener { alta() }
        findViewById<Button>(R.id.btnModificar).setOnClickListener { modificar() }
        findViewById<Button>(R.id.btnBaja).setOnClickListener { baja() }
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
    }

    /** Devuelve el cliente escrito en el formulario, o null si hay algún error. */
    private fun leerFormulario(): ClienteStore.Cliente? {
        val codigo = etCodigo.text.toString().trim()
        val nombre = ClienteStore.limpiar(etNombre.text.toString())
        val direccion = ClienteStore.limpiar(etDireccion.text.toString())
        if (!ClienteStore.codigoValido(codigo)) {
            etCodigo.error = "El código de cliente debe tener 4 dígitos"
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
        if (ClienteStore.buscar(this, nuevo.codigo) != null) {
            Toast.makeText(this, "Ya existe el código ${nuevo.codigo}. Usa Modificar.", Toast.LENGTH_LONG).show()
            return
        }
        ClienteStore.guardar(this, ClienteStore.todos(this) + nuevo)
        refrescar()
        vaciarCampos()
        Toast.makeText(this, "Cliente ${nuevo.codigo} dado de alta", Toast.LENGTH_SHORT).show()
    }

    private fun modificar() {
        val c = leerFormulario() ?: return
        if (ClienteStore.buscar(this, c.codigo) == null) {
            Toast.makeText(this, "No existe el código ${c.codigo}. Usa Alta.", Toast.LENGTH_LONG).show()
            return
        }
        ClienteStore.guardar(this, ClienteStore.todos(this).filter { it.codigo != c.codigo } + c)
        refrescar()
        Toast.makeText(this, "Cliente ${c.codigo} modificado", Toast.LENGTH_SHORT).show()
    }

    private fun baja() {
        val codigo = etCodigo.text.toString().trim()
        val c = if (ClienteStore.codigoValido(codigo)) ClienteStore.buscar(this, codigo) else null
        if (c == null) {
            Toast.makeText(this, "Elige en la lista el cliente que quieres dar de baja", Toast.LENGTH_LONG).show()
            return
        }
        AlertDialog.Builder(this)
            .setTitle("Baja de cliente")
            .setMessage("¿Dar de baja al cliente ${c.codigo} - ${c.nombre}?")
            .setPositiveButton("Sí") { _, _ ->
                ClienteStore.guardar(this, ClienteStore.todos(this).filter { it.codigo != c.codigo })
                refrescar()
                vaciarCampos()
                Toast.makeText(this, "Cliente ${c.codigo} dado de baja", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("No", null)
            .show()
    }
}
