package dev.rubensalas.tinta.data.local

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction
import androidx.room.TypeConverters
import androidx.room.Upsert
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

@Dao
interface DaoTinta {
    // --- Lo que leen las pantallas: todo lo vivo. Son pocos datos (un solo autor).
    @Query("SELECT * FROM series WHERE borrado = 0") fun series(): Flow<List<SerieE>>
    @Query("SELECT * FROM etiquetas WHERE borrado = 0") fun etiquetas(): Flow<List<EtiquetaE>>
    @Query("SELECT * FROM articulos WHERE borrado = 0") fun articulos(): Flow<List<ArticuloE>>
    @Query("SELECT * FROM idiomas WHERE borrado = 0") fun idiomas(): Flow<List<IdiomaE>>
    @Query("SELECT * FROM ramas WHERE borrado = 0") fun ramas(): Flow<List<RamaE>>
    @Query("SELECT * FROM secciones WHERE borrado = 0") fun secciones(): Flow<List<SeccionE>>
    @Query("SELECT * FROM variantes_seccion WHERE borrado = 0") fun variantesSeccion(): Flow<List<VarianteSeccionE>>
    @Query("SELECT * FROM bloques WHERE borrado = 0") fun bloques(): Flow<List<BloqueE>>
    @Query("SELECT * FROM variantes_bloque WHERE borrado = 0") fun variantesBloque(): Flow<List<VarianteBloqueE>>
    @Query("SELECT * FROM medios WHERE borrado = 0") fun medios(): Flow<List<MedioE>>
    @Query("SELECT * FROM canales WHERE borrado = 0 ORDER BY posicion") fun canales(): Flow<List<CanalE>>
    @Query("SELECT * FROM publicaciones WHERE borrado = 0") fun publicaciones(): Flow<List<PublicacionE>>
    @Query("SELECT COUNT(*) FROM cola") fun pendientes(): Flow<Int>
    @Query("SELECT * FROM avisos ORDER BY id DESC") fun avisos(): Flow<List<AvisoE>>

    // --- Copia desde el servicio.
    @Upsert suspend fun series(v: List<SerieE>)
    @Upsert suspend fun etiquetas(v: List<EtiquetaE>)
    @Upsert suspend fun articulos(v: List<ArticuloE>)
    @Upsert suspend fun idiomas(v: List<IdiomaE>)
    @Upsert suspend fun ramas(v: List<RamaE>)
    @Upsert suspend fun secciones(v: List<SeccionE>)
    @Upsert suspend fun variantesSeccion(v: List<VarianteSeccionE>)
    @Upsert suspend fun bloques(v: List<BloqueE>)
    @Upsert suspend fun variantesBloque(v: List<VarianteBloqueE>)
    @Upsert suspend fun medios(v: List<MedioE>)
    @Upsert suspend fun canales(v: List<CanalE>)
    @Upsert suspend fun publicaciones(v: List<PublicacionE>)

    // --- Lecturas sueltas para aplicar cambios locales.
    @Query("SELECT * FROM articulos WHERE id = :id") suspend fun articulo(id: String): ArticuloE?
    @Query("SELECT * FROM idiomas WHERE id = :id") suspend fun idioma(id: String): IdiomaE?
    @Query("SELECT * FROM idiomas WHERE articulo_id = :articulo AND borrado = 0") suspend fun idiomasDe(articulo: String): List<IdiomaE>
    @Query("SELECT * FROM ramas WHERE id = :id") suspend fun rama(id: String): RamaE?
    @Query("SELECT * FROM series WHERE id = :id") suspend fun serie(id: String): SerieE?
    @Query("SELECT * FROM etiquetas WHERE nombre = :nombre") suspend fun etiquetaLlamada(nombre: String): EtiquetaE?
    @Query("SELECT * FROM articulos WHERE serie_id = :serie AND borrado = 0") suspend fun articulosDeSerie(serie: String): List<ArticuloE>
    @Query("SELECT * FROM variantes_seccion WHERE seccion_id = :seccion AND borrado = 0 ORDER BY creado") suspend fun variantesSeccionDe(seccion: String): List<VarianteSeccionE>
    @Query("SELECT * FROM secciones WHERE id = :id") suspend fun seccion(id: String): SeccionE?
    @Query("SELECT * FROM secciones WHERE rama_id = :rama AND borrado = 0 ORDER BY posicion") suspend fun seccionesDe(rama: String): List<SeccionE>
    @Query("SELECT * FROM variantes_seccion WHERE id = :id") suspend fun varianteSeccion(id: String): VarianteSeccionE?
    @Query("SELECT * FROM bloques WHERE id = :id") suspend fun bloque(id: String): BloqueE?
    @Query("SELECT * FROM bloques WHERE variante_seccion_id = :vs AND borrado = 0 ORDER BY posicion") suspend fun bloquesDe(vs: String): List<BloqueE>
    @Query("SELECT * FROM variantes_bloque WHERE id = :id") suspend fun varianteBloque(id: String): VarianteBloqueE?
    @Query("SELECT * FROM variantes_bloque WHERE bloque_id = :bloque AND borrado = 0 ORDER BY creado") suspend fun variantesDe(bloque: String): List<VarianteBloqueE>
    @Query("SELECT * FROM publicaciones WHERE id = :id") suspend fun publicacion(id: String): PublicacionE?
    @Query("SELECT * FROM publicaciones WHERE articulo_id = :articulo") suspend fun publicacionesDe(articulo: String): List<PublicacionE>
    @Query("SELECT * FROM publicaciones WHERE borrado = 0") suspend fun todasPublicaciones(): List<PublicacionE>

    // --- Cola.
    @Insert suspend fun encolar(op: OperacionE): Long
    @Query("SELECT * FROM cola ORDER BY seq") suspend fun cola(): List<OperacionE>
    @Query("UPDATE cola SET json = :json WHERE seq = :seq") suspend fun cambiarOperacion(seq: Long, json: String)
    @Query("DELETE FROM cola WHERE seq = :seq") suspend fun quitarOperacion(seq: Long)
    @Query("SELECT * FROM medios_pendientes") suspend fun mediosPendientes(): List<MedioPendienteE>
    @Upsert suspend fun medioPendiente(m: MedioPendienteE)
    @Query("DELETE FROM medios_pendientes WHERE id = :id") suspend fun quitarMedioPendiente(id: String)
    @Insert suspend fun avisar(a: AvisoE)
    @Query("DELETE FROM avisos") suspend fun borrarAvisos()
}

@Database(
    entities = [
        SerieE::class, EtiquetaE::class, ArticuloE::class, IdiomaE::class, RamaE::class, SeccionE::class,
        VarianteSeccionE::class, BloqueE::class, VarianteBloqueE::class, MedioE::class, CanalE::class,
        PublicacionE::class, OperacionE::class, MedioPendienteE::class, AvisoE::class,
    ],
    version = 4,
    exportSchema = false,
)
@TypeConverters(Conversores::class)
abstract class BaseLocal : RoomDatabase() {
    abstract fun dao(): DaoTinta

    companion object {
        /** La versión 2 guarda de qué dispositivo viene una variante de conflicto. */
        val MIGRACION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE variantes_bloque ADD COLUMN dispositivo TEXT")
            }
        }

        /** La versión 3 guarda qué partes de una variante vienen de una propuesta del agente aceptada. */
        val MIGRACION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE variantes_bloque ADD COLUMN autoria TEXT NOT NULL DEFAULT '[]'")
            }
        }

        /**
         * La versión 4 permite ideas sin fecha, sin canal y sin formato. SQLite no
         * quita un NOT NULL con ALTER, así que se rehace la tabla. La tabla no tiene
         * índices, así que no hay ninguno que volver a crear.
         */
        val MIGRACION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `publicaciones_nueva` (`id` TEXT NOT NULL, `canal_id` TEXT, `formato` TEXT, `fecha` TEXT, " +
                        "`hora` TEXT, `estado` TEXT NOT NULL, `titulo` TEXT NOT NULL, `partes` TEXT NOT NULL, `articulo_id` TEXT, " +
                        "`enlace` TEXT, `origen` TEXT NOT NULL, `creado` TEXT NOT NULL, `cambiado` TEXT NOT NULL, `rev` INTEGER NOT NULL, " +
                        "`borrado` INTEGER NOT NULL, PRIMARY KEY(`id`))",
                )
                db.execSQL(
                    "INSERT INTO `publicaciones_nueva` (`id`, `canal_id`, `formato`, `fecha`, `hora`, `estado`, `titulo`, `partes`, " +
                        "`articulo_id`, `enlace`, `origen`, `creado`, `cambiado`, `rev`, `borrado`) " +
                        "SELECT `id`, `canal_id`, `formato`, `fecha`, `hora`, `estado`, `titulo`, `partes`, " +
                        "`articulo_id`, `enlace`, `origen`, `creado`, `cambiado`, `rev`, `borrado` FROM `publicaciones`",
                )
                db.execSQL("DROP TABLE `publicaciones`")
                db.execSQL("ALTER TABLE `publicaciones_nueva` RENAME TO `publicaciones`")
            }
        }

        fun crear(context: Context): BaseLocal =
            Room.databaseBuilder(context, BaseLocal::class.java, "tinta.db")
                .addMigrations(MIGRACION_1_2, MIGRACION_2_3, MIGRACION_3_4).build()

        fun enMemoria(context: Context): BaseLocal =
            Room.inMemoryDatabaseBuilder(context, BaseLocal::class.java).allowMainThreadQueries().build()
    }
}
