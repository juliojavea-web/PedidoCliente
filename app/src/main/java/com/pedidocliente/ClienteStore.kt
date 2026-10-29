package com.pedidocliente

import android.content.Context
import java.io.File

/** Almacén de clientes (altas y bajas): un fichero clientes.csv en la memoria privada de la app. */
object ClienteStore {

    data class Cliente(val codigo: String, val nombre: String, val direccion: String)

    private val CODIGO = Regex("^[0-9]{4}$")

    private fun fichero(ctx: Context) = File(ctx.filesDir, "clientes.csv")

    fun codigoValido(codigo: String): Boolean = CODIGO.matches(codigo)

    /** Quita los caracteres que romperían el formato del fichero. */
    fun limpiar(s: String): String = s.replace(Regex("[;\\r\\n]+"), " ").trim()

    fun todos(ctx: Context): List<Cliente> {
        val f = fichero(ctx)
        if (!f.exists()) return emptyList()
        return f.readLines(Charsets.UTF_8).mapNotNull { linea ->
            val p = linea.split(";")
            if (p.size < 3 || !codigoValido(p[0])) null else Cliente(p[0], p[1], p[2])
        }.sortedBy { it.codigo }
    }

    fun buscar(ctx: Context, codigo: String): Cliente? = todos(ctx).firstOrNull { it.codigo == codigo }

    fun guardar(ctx: Context, lista: List<Cliente>) {
        val texto = lista.sortedBy { it.codigo }
            .joinToString("\n") { "${it.codigo};${it.nombre};${it.direccion}" }
        fichero(ctx).writeText(texto, Charsets.UTF_8)
    }
}
