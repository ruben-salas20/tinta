package dev.rubensalas.tinta.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverter
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/*
 * Copia de las tablas que baja `GET /api/sync`, con los mismos nombres de
 * campo que el JSON del servicio. `rev = -1` marca lo que el móvil creó y el
 * servicio aún no ha confirmado. `borrado = 1` es una lápida.
 */

@Serializable
@Entity(tableName = "series")
data class SerieE(
    @PrimaryKey val id: String,
    val nombre: String,
    val rev: Long,
    val borrado: Int = 0,
)

@Serializable
@Entity(tableName = "etiquetas")
data class EtiquetaE(
    @PrimaryKey val id: String,
    val nombre: String,
    val rev: Long,
    val borrado: Int = 0,
)

@Serializable
@Entity(tableName = "articulos")
data class ArticuloE(
    @PrimaryKey val id: String,
    val slug: String,
    val estado: String,
    @SerialName("serie_id") @ColumnInfo(name = "serie_id") val serieId: String? = null,
    @SerialName("serie_parte") @ColumnInfo(name = "serie_parte") val serieParte: Long? = null,
    val fecha: String? = null,
    val creado: String,
    val cambiado: String,
    val rev: Long,
    val borrado: Int = 0,
    /** Ids de sus etiquetas. */
    val etiquetas: List<String> = emptyList(),
)

@Serializable
@Entity(tableName = "idiomas")
data class IdiomaE(
    @PrimaryKey val id: String,
    @SerialName("articulo_id") @ColumnInfo(name = "articulo_id") val articuloId: String,
    val idioma: String,
    val titulo: String = "",
    val descripcion: String = "",
    val borrador: Int = 1,
    @SerialName("traducido_ia") @ColumnInfo(name = "traducido_ia") val traducidoIa: Int = 0,
    @SerialName("rama_principal_id") @ColumnInfo(name = "rama_principal_id") val ramaPrincipalId: String? = null,
    val rev: Long,
    val borrado: Int = 0,
)

@Serializable
@Entity(tableName = "ramas")
data class RamaE(
    @PrimaryKey val id: String,
    @SerialName("idioma_id") @ColumnInfo(name = "idioma_id") val idiomaId: String,
    val nombre: String,
    @SerialName("origen_id") @ColumnInfo(name = "origen_id") val origenId: String? = null,
    val creado: String,
    val rev: Long,
    val borrado: Int = 0,
)

@Serializable
@Entity(tableName = "secciones")
data class SeccionE(
    @PrimaryKey val id: String,
    @SerialName("rama_id") @ColumnInfo(name = "rama_id") val ramaId: String,
    val posicion: String,
    @SerialName("variante_activa_id") @ColumnInfo(name = "variante_activa_id") val varianteActivaId: String? = null,
    val rev: Long,
    val borrado: Int = 0,
)

@Serializable
@Entity(tableName = "variantes_seccion")
data class VarianteSeccionE(
    @PrimaryKey val id: String,
    @SerialName("seccion_id") @ColumnInfo(name = "seccion_id") val seccionId: String,
    val titulo: String = "",
    val origen: String = "tu",
    val creado: String,
    val rev: Long,
    val borrado: Int = 0,
)

@Serializable
@Entity(tableName = "bloques")
data class BloqueE(
    @PrimaryKey val id: String,
    @SerialName("variante_seccion_id") @ColumnInfo(name = "variante_seccion_id") val varianteSeccionId: String,
    val posicion: String,
    val tipo: String = "parrafo",
    @SerialName("variante_activa_id") @ColumnInfo(name = "variante_activa_id") val varianteActivaId: String? = null,
    val rev: Long,
    val borrado: Int = 0,
)

@Serializable
@Entity(tableName = "variantes_bloque")
data class VarianteBloqueE(
    @PrimaryKey val id: String,
    @SerialName("bloque_id") @ColumnInfo(name = "bloque_id") val bloqueId: String,
    val contenido: String = "",
    val origen: String = "tu",
    val creado: String,
    val cambiado: String,
    val rev: Long,
    val borrado: Int = 0,
    val dispositivo: String? = null,
    /** Partes del texto que vienen de propuestas del agente ya aceptadas. */
    @ColumnInfo(defaultValue = "'[]'") val autoria: List<RangoAutoria> = emptyList(),
)

@Serializable
@Entity(tableName = "medios")
data class MedioE(
    @PrimaryKey val id: String,
    val sha256: String,
    val nombre: String,
    val tipo: String,
    val tamano: Long,
    val creado: String,
    val rev: Long,
    val borrado: Int = 0,
)

@Serializable
data class Formato(val id: String, val nombre: String, val limite: Int? = null, val partes: Boolean = false)

@Serializable
@Entity(tableName = "canales")
data class CanalE(
    @PrimaryKey val id: String,
    val nombre: String,
    val icono: String,
    val formatos: List<Formato> = emptyList(),
    val activo: Int = 1,
    val posicion: String,
    val rev: Long,
    val borrado: Int = 0,
)

@Serializable
data class Parte(val texto: String = "", val medios: List<String> = emptyList())

/** Una parte del texto que viene de una propuesta del agente. [desde] incluido, [hasta] excluido, en puntos de código del contenido crudo. */
@Serializable
data class RangoAutoria(val desde: Int, val hasta: Int, val agente: String)

@Serializable
@Entity(tableName = "publicaciones")
data class PublicacionE(
    @PrimaryKey val id: String,
    @SerialName("canal_id") @ColumnInfo(name = "canal_id") val canalId: String,
    val formato: String,
    val fecha: String,
    val hora: String? = null,
    val estado: String = "idea",
    val titulo: String = "",
    val partes: List<Parte> = listOf(Parte()),
    @SerialName("articulo_id") @ColumnInfo(name = "articulo_id") val articuloId: String? = null,
    val enlace: String? = null,
    val origen: String = "tu",
    val creado: String,
    val cambiado: String,
    val rev: Long,
    val borrado: Int = 0,
)

/** Una operación que espera a ser enviada a `POST /api/sync`. Se envían por [seq]. */
@Entity(tableName = "cola")
data class OperacionE(
    @PrimaryKey(autoGenerate = true) val seq: Long = 0,
    /** La operación en JSON, tal como la espera el servicio. */
    val json: String,
    val creado: Long,
)

/** Un archivo elegido en el móvil que aún no se ha subido. */
@Entity(tableName = "medios_pendientes")
data class MedioPendienteE(
    @PrimaryKey val id: String,
    val ruta: String,
    val nombre: String,
    val tipo: String,
)

/** Una operación que el servicio rechazó por datos no válidos, para avisar. */
@Entity(tableName = "avisos")
data class AvisoE(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val texto: String,
    val creado: Long,
)

private val json = Json { ignoreUnknownKeys = true }

private val textos = ListSerializer(String.serializer())
private val formatos = ListSerializer(Formato.serializer())
private val partes = ListSerializer(Parte.serializer())
private val rangos = ListSerializer(RangoAutoria.serializer())

class Conversores {
    @TypeConverter fun deTextos(v: List<String>): String = json.encodeToString(textos, v)
    @TypeConverter fun aTextos(v: String): List<String> = json.decodeFromString(textos, v)
    @TypeConverter fun deFormatos(v: List<Formato>): String = json.encodeToString(formatos, v)
    @TypeConverter fun aFormatos(v: String): List<Formato> = json.decodeFromString(formatos, v)
    @TypeConverter fun dePartes(v: List<Parte>): String = json.encodeToString(partes, v)
    @TypeConverter fun aPartes(v: String): List<Parte> = json.decodeFromString(partes, v)
    @TypeConverter fun deRangos(v: List<RangoAutoria>): String = json.encodeToString(rangos, v)
    @TypeConverter fun aRangos(v: String): List<RangoAutoria> = json.decodeFromString(rangos, v)
}
