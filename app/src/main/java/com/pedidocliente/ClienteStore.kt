package com.pedidocliente

import android.content.Context
import java.io.File

/**
 * Almacén de clientes: un fichero clientes.csv (Codigo;Cliente;Direccion) en la memoria privada de la app.
 * Pueden repetirse código y nombre; dos clientes son el mismo si coinciden nombre y dirección.
 * El mismo formato se usa para exportar/importar entre móviles y Windows.
 */
object ClienteStore {

    data class Cliente(val codigo: String, val nombre: String, val direccion: String) {
        fun mismoQue(o: Cliente) =
            nombre.equals(o.nombre, ignoreCase = true) && direccion.equals(o.direccion, ignoreCase = true)
    }

    private fun fichero(ctx: Context) = File(ctx.filesDir, "clientes.csv")

    /** Quita los caracteres que romperían el formato del fichero. */
    fun limpiar(s: String): String = s.replace(Regex("[;\\r\\n]+"), " ").trim()

    fun parsear(texto: String): List<Cliente> =
        texto.removePrefix("﻿").lines().mapNotNull { linea ->
            val p = linea.trim('﻿').split(";")
            if (p.size < 3) return@mapNotNull null
            val codigo = p[0].trim()
            val nombre = p[1].trim()
            if (nombre.isEmpty() || (codigo == "Codigo" && nombre == "Cliente")) null
            else Cliente(codigo, nombre, p[2].trim())
        }

    fun serializar(lista: List<Cliente>): String =
        (listOf("Codigo;Cliente;Direccion") +
            lista.sortedWith(compareBy({ it.nombre.lowercase() }, { it.direccion.lowercase() }, { it.codigo }))
                .map { "${it.codigo};${it.nombre};${it.direccion}" }).joinToString("\r\n")

    fun todos(ctx: Context): List<Cliente> {
        val f = fichero(ctx)
        if (!f.exists()) return emptyList()
        return parsear(f.readText(Charsets.UTF_8))
            .sortedWith(compareBy({ it.nombre.lowercase() }, { it.direccion.lowercase() }, { it.codigo }))
    }

    fun guardar(ctx: Context, lista: List<Cliente>) {
        fichero(ctx).writeText(serializar(lista), Charsets.UTF_8)
    }

    fun porCodigo(ctx: Context, codigo: String): List<Cliente> =
        todos(ctx).filter { it.codigo.equals(codigo, ignoreCase = true) }

    fun porNombre(ctx: Context, nombre: String): List<Cliente> =
        todos(ctx).filter { it.nombre.equals(nombre, ignoreCase = true) }

    /** Añade a [actuales] los de [nuevos] que no estén ya (mismo nombre y dirección). Devuelve (lista, añadidos). */
    fun combinar(actuales: List<Cliente>, nuevos: List<Cliente>): Pair<List<Cliente>, Int> {
        val res = actuales.toMutableList()
        var n = 0
        for (c in nuevos) if (res.none { it.mismoQue(c) }) { res.add(c); n++ }
        return Pair(res, n)
    }
}
