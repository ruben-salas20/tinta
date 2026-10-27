package dev.rubensalas.tinta.data

import android.app.Application
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import dev.rubensalas.tinta.data.local.BaseLocal
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * La migración 3→4 sobre una base con la tabla de la versión 3. Como `exportSchema`
 * está apagado, no hay esquemas JSON para MigrationTestHelper: la base se crea a mano.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class MigracionTest {
    private var helper: SupportSQLiteOpenHelper? = null

    @After fun cerrar() { helper?.close() }

    private fun abrirV3(): SupportSQLiteDatabase {
        val config = SupportSQLiteOpenHelper.Configuration.builder(RuntimeEnvironment.getApplication())
            .name("migracion.db")
            .callback(object : SupportSQLiteOpenHelper.Callback(3) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    db.execSQL(
                        "CREATE TABLE IF NOT EXISTS `publicaciones` (`id` TEXT NOT NULL, `canal_id` TEXT NOT NULL, `formato` TEXT NOT NULL, " +
                            "`fecha` TEXT NOT NULL, `hora` TEXT, `estado` TEXT NOT NULL, `titulo` TEXT NOT NULL, `partes` TEXT NOT NULL, " +
                            "`articulo_id` TEXT, `enlace` TEXT, `origen` TEXT NOT NULL, `creado` TEXT NOT NULL, `cambiado` TEXT NOT NULL, " +
                            "`rev` INTEGER NOT NULL, `borrado` INTEGER NOT NULL, PRIMARY KEY(`id`))",
                    )
                    db.execSQL(
                        "INSERT INTO publicaciones VALUES ('p1', 'x', 'post', '2026-11-02', '18:00', 'programada', 'Hola', '[]', " +
                            "'a1', 'https://ejemplo', 'tu', '2026-10-01', '2026-10-02', 7, 0)",
                    )
                }

                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
            })
            .build()
        val h = FrameworkSQLiteOpenHelperFactory().create(config)
        helper = h
        return h.writableDatabase
    }

    @Test fun conservaLasFilasYAdmiteIdeasSinCanalNiFecha() {
        val db = abrirV3()
        BaseLocal.MIGRACION_3_4.migrate(db)

        db.query("SELECT canal_id, formato, fecha, hora, titulo, articulo_id, enlace, rev FROM publicaciones WHERE id = 'p1'").use { c ->
            assertEquals(true, c.moveToFirst())
            assertEquals("x", c.getString(0))
            assertEquals("post", c.getString(1))
            assertEquals("2026-11-02", c.getString(2))
            assertEquals("18:00", c.getString(3))
            assertEquals("Hola", c.getString(4))
            assertEquals("a1", c.getString(5))
            assertEquals("https://ejemplo", c.getString(6))
            assertEquals(7L, c.getLong(7))
        }

        db.execSQL("INSERT INTO publicaciones (id, estado, titulo, partes, origen, creado, cambiado, rev, borrado) VALUES ('idea', 'idea', '', '[]', 'tu', '', '', -1, 0)")
        db.query("SELECT canal_id, formato, fecha FROM publicaciones WHERE id = 'idea'").use { c ->
            assertEquals(true, c.moveToFirst())
            assertNull(c.getString(0))
            assertNull(c.getString(1))
            assertNull(c.getString(2))
        }
    }

    @Test fun laTablaNuevaTieneLasColumnasNulas() {
        val db = abrirV3()
        BaseLocal.MIGRACION_3_4.migrate(db)
        // PRAGMA table_info: nombre en la columna 1, NOT NULL (1 o 0) en la 3.
        val admiteNulo = mutableMapOf<String, Boolean>()
        db.query("PRAGMA table_info(`publicaciones`)").use { c ->
            while (c.moveToNext()) admiteNulo[c.getString(1)] = c.getInt(3) == 0
        }
        assertEquals(true, admiteNulo["canal_id"])
        assertEquals(true, admiteNulo["formato"])
        assertEquals(true, admiteNulo["fecha"])
        assertEquals(true, admiteNulo["hora"])
        assertEquals(false, admiteNulo["estado"])
        assertEquals(false, admiteNulo["id"])
    }
}
