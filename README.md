# Pedido Cliente (app Android)

Rellena un `Pedido.docx` con el formato:

- PEDIDO
- Cliente / Código de cliente / Fecha de entrega
- Tabla de 20 filas: Artículo, Cantidad, Unidad

Al pulsar **Crear pedido y abrir Gmail**, la app genera el documento y abre Gmail con el
archivo **ya adjunto**, el asunto y el texto escritos, y los destinatarios predefinidos
(Para: pedidos@avicolasanbernardo.es, Cco: juliojavea@gmail.com). Solo tienes que pulsar
Enviar. Para cambiarlos, edita `EMAIL_PARA` y `EMAIL_CCO` en `MainActivity.kt`.
**Nuevo pedido** borra todos los datos para empezar otro.

## Conseguir el APK (el "instalador" de Android)

### Opción A: en línea con GitHub, sin instalar nada

1. Crea una cuenta gratuita en https://github.com y un repositorio nuevo (puede ser privado).
2. Sube **el contenido** de esta carpeta al repositorio (en la web: *Add file > Upload files*,
   arrastrando todo, incluida la carpeta oculta `.github`; si no la ves, usa Git).
3. Abre la pestaña **Actions > Compilar APK > Run workflow**.
4. Al terminar (unos 3-5 minutos), entra en la ejecución y descarga **PedidoCliente-apk**
   (un zip con `app-debug.apk`).

### Opción B: con Android Studio

1. Instala [Android Studio](https://developer.android.com/studio).
2. `File > Open` y elige esta carpeta. Acepta la sincronización de Gradle.
3. `Build > Build Bundle(s) / APK(s) > Build APK(s)`. El APK queda en
   `app/build/outputs/apk/debug/app-debug.apk`.

## Instalar el APK en el móvil

1. Copia `app-debug.apk` al móvil (cable, Drive, email…).
2. Ábrelo desde el gestor de archivos y permite *instalar apps de orígenes desconocidos*
   cuando Android lo pida.
3. Abre **Pedido Cliente** desde el cajón de aplicaciones.

Requisitos: Android 7.0 (API 24) o superior y la app Gmail instalada (si no, ofrece otras apps de correo).
