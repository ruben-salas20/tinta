---
version: alpha
name: Tinta
description: Sistema visual de Tinta, el calendario de contenido y la mesa de escritura de Rubén. Grises, negro y blanco, con color solo para los estados. Web en el portátil y app nativa de Android.
---

# Tinta

## Overview

Tinta es una herramienta personal para escribir artículos con orden y planear publicaciones en redes. Su aspecto es el de un cuaderno de trabajo: tranquilo, sobrio y sin adornos, para que el texto sea lo protagonista.

- **Personalidad:** callada y precisa. Nada compite con lo que se escribe.
- **Paleta:** grises, negro y blanco. El único color está en los estados (idea, borrador, revisión, listo, publicado), y es una escala viva pero contenida.
- **Dos voces tipográficas:** Inter para la interfaz y Source Serif 4 para el texto de los artículos. JetBrains Mono para código y marcas técnicas.
- **Densidad:** espaciosa en el editor, compacta en las listas y el calendario.
- **Temas:** claro y oscuro, siempre según el sistema. Los dos tienen el mismo peso; ninguno es una versión secundaria del otro.
- **Plataformas:** la web en el portátil y una app nativa de Android, en Kotlin con Jetpack Compose. Las dos comparten estos tokens.

Las maquetas aprobadas viven en `diseno/` y son la referencia visual de este documento.

## Colors

Todo es gris salvo los estados. La tinta es casi negra en claro y casi blanca en oscuro, y hace de color principal: texto, botón principal y selección.

**Tema claro**

- **Tinta (#121212):** texto principal, botón principal, selección activa y bordes de lo seleccionado.
- **Papel (#F4F4F4):** el fondo de la app.
- **Hoja (#FFFFFF):** tarjetas, barra lateral, inspector, ventanas y el bloque que se edita.
- **Gris suave (#EFEFEF) y gris medio (#E6E6E6):** fondos de chips, código, pestañas y el indicador de la navegación de Android.
- **Línea (#E0E0E0) y línea marcada (#C9C9C9):** separadores, bordes de tarjetas y de campos.
- **Texto secundario (#666666):** descripciones, metadatos y etiquetas de campo.
- **Texto tenue (#9B9B9B):** fechas, contadores, ayudas y texto tachado. Nunca para texto que haya que leer.
- **Añadido (#E4E4E4):** fondo del texto propuesto pendiente de aceptar.

**Tema oscuro**

- **Tinta (#ECECEC)** sobre **papel (#0C0C0C)** y **hoja (#161616)**.
- **Gris suave (#1D1D1D)**, **gris medio (#262626)**, **línea (#2A2A2A)** y **línea marcada (#3A3A3A)**.
- **Texto secundario (#9A9A9A)**, **texto tenue (#6A6A6A)** y **añadido (#2E2E2E)**.

**Estados: la única nota de color**

| Estado del artículo | Estado de la publicación | Claro | Oscuro |
|---|---|---|---|
| Idea | Idea | Gris #878787 | #8F8F8F |
| Borrador | Preparando | Ámbar #C27C0E | #EEAB3A |
| Revisión | — | Violeta #7452D6 | #AE95FF |
| Listo | Programada | Azul #1F6FD1 | #5FA6FF |
| Publicado | Publicada | Verde #1C9152 | #4CCF86 |

El color de estado nunca va en el texto. Se usa de tres formas:

- **Punto de 6 px** delante del nombre del estado, dentro de un chip.
- **Fondo tintado:** el color de estado al 10 % sobre la hoja en los chips, al 9 % en las publicaciones del calendario y al 16 % en las programadas. El borde del chip mezcla el color al 35 % con la línea.
- **Franja de 3 px** a la izquierda de cada publicación del calendario.

El texto encima sigue siendo tinta, así que el contraste no depende del color de estado.

```yaml
colors:
  primary: "#121212"
  on-primary: "#FFFFFF"
  background: "#F4F4F4"
  surface: "#FFFFFF"
  surface-container: "#EFEFEF"
  surface-container-high: "#E6E6E6"
  outline: "#E0E0E0"
  outline-strong: "#C9C9C9"
  on-surface: "#121212"
  on-surface-variant: "#666666"
  on-surface-faint: "#9B9B9B"
  mark-added: "#E4E4E4"
  state-idea: "#878787"
  state-amber: "#C27C0E"
  state-violet: "#7452D6"
  state-blue: "#1F6FD1"
  state-green: "#1C9152"
  primary-dark: "#ECECEC"
  on-primary-dark: "#111111"
  background-dark: "#0C0C0C"
  surface-dark: "#161616"
  surface-container-dark: "#1D1D1D"
  surface-container-high-dark: "#262626"
  outline-dark: "#2A2A2A"
  outline-strong-dark: "#3A3A3A"
  on-surface-dark: "#ECECEC"
  on-surface-variant-dark: "#9A9A9A"
  on-surface-faint-dark: "#6A6A6A"
  mark-added-dark: "#2E2E2E"
  state-idea-dark: "#8F8F8F"
  state-amber-dark: "#EEAB3A"
  state-violet-dark: "#AE95FF"
  state-blue-dark: "#5FA6FF"
  state-green-dark: "#4CCF86"
```

## Typography

Tres familias, cada una con un papel fijo:

- **Source Serif 4** para todo lo que escribe Rubén: títulos, subtítulos, párrafos y vistas previas de variantes. Da al editor el aire de una página impresa. La entradilla del artículo va en cursiva.
- **Inter** para la interfaz: botones, menús, listas, calendario y formularios.
- **JetBrains Mono** para bloques de código, atajos de teclado, contadores de caracteres y la marca «IA».

Las etiquetas de grupo (por ejemplo «VARIANTES DEL PÁRRAFO») van en mayúsculas, con letra de 11 px y espaciado de 0,07 em.

En Android, el texto del artículo mantiene 16,5 px y el título baja a 26 px.

```yaml
typography:
  brand-title:
    fontFamily: Inter
    fontSize: 40px
    fontWeight: 600
    lineHeight: 1.1
    letterSpacing: -0.03em
  article-title:
    fontFamily: Source Serif 4
    fontSize: 32px
    fontWeight: 600
    lineHeight: 1.2
    letterSpacing: -0.015em
  article-lead:
    fontFamily: Source Serif 4
    fontSize: 16.5px
    fontWeight: 400
    lineHeight: 1.55
  article-heading:
    fontFamily: Source Serif 4
    fontSize: 21px
    fontWeight: 600
    lineHeight: 1.35
  article-body:
    fontFamily: Source Serif 4
    fontSize: 16.5px
    fontWeight: 400
    lineHeight: 1.75
  article-preview:
    fontFamily: Source Serif 4
    fontSize: 13.5px
    fontWeight: 400
    lineHeight: 1.55
  article-title-mobile:
    fontFamily: Source Serif 4
    fontSize: 26px
    fontWeight: 600
    lineHeight: 1.18
    letterSpacing: -0.01em
  article-body-mobile:
    fontFamily: Source Serif 4
    fontSize: 16.5px
    fontWeight: 400
    lineHeight: 1.65
  screen-title-mobile:
    fontFamily: Inter
    fontSize: 22px
    fontWeight: 700
    lineHeight: 1.2
    letterSpacing: -0.02em
  title-md:
    fontFamily: Inter
    fontSize: 15px
    fontWeight: 600
    lineHeight: 1.4
  body-md:
    fontFamily: Inter
    fontSize: 14px
    fontWeight: 400
    lineHeight: 1.5
  body-sm:
    fontFamily: Inter
    fontSize: 13px
    fontWeight: 400
    lineHeight: 1.45
  label-md:
    fontFamily: Inter
    fontSize: 12px
    fontWeight: 400
    lineHeight: 1.3
  label-caps:
    fontFamily: Inter
    fontSize: 11px
    fontWeight: 600
    lineHeight: 1.2
    letterSpacing: 0.07em
  code-md:
    fontFamily: JetBrains Mono
    fontSize: 12.5px
    fontWeight: 400
    lineHeight: 1.65
  code-badge:
    fontFamily: JetBrains Mono
    fontSize: 10px
    fontWeight: 600
    lineHeight: 1
    letterSpacing: 0.04em
```

## Layout

**Web.** Tres columnas fijas en el editor:

- **Barra lateral** de 224 px: logo, secciones, series, etiquetas o canales, y el estado de sincronización abajo («Sincronizado (5 min)»).
- **Contenido** flexible. El papel del artículo mide como mucho 660 px y queda centrado, con 48 px de margen arriba y 56 px a los lados.
- **Inspector** de 340 px en el editor y de 380 px en el calendario.

La cabecera mide 56 px, es fija y deja ver el contenido detrás con desenfoque. Por debajo de 900 px de ancho se esconde la barra lateral.

**Ritmo vertical del editor.** 52 px entre secciones, 18 px entre párrafos y 44 px bajo la entradilla. El editor tiene que respirar: si algo se siente cargado, se gana espacio antes que reducir aún más la letra.

**Android.** Margen lateral de 16 px y de 20 px en el editor. Navegación inferior con cuatro destinos: Hoy, Artículos, Calendario y Ajustes. El botón flotante queda a 18 px del borde. En el editor se trabaja un párrafo a la vez, con las herramientas sobre el teclado.

La escala de espacios es de 4 px, con pasos de 8, 12, 16 y 20.

```yaml
spacing:
  xs: 4px
  sm: 8px
  md: 12px
  lg: 16px
  xl: 20px
  sidebar-width: 224px
  inspector-width: 340px
  article-max-width: 660px
  header-height: 56px
  control-height: 32px
  mobile-nav-height: 72px
  fab-size: 56px
```

## Elevation & Depth

Tinta es casi plana. La jerarquía sale de capas de tono, no de sombras:

1. **Papel** al fondo.
2. **Hoja** para lo que se usa: tarjetas, paneles y el bloque en edición.
3. **Grises suaves** para lo que agrupa: chips, código y pestañas.

Los bordes de 1 px (línea) separan casi todo. La selección se marca con un borde de tinta, no con color.

Solo flota lo que de verdad está encima: menús, la barra del bloque, ventanas, hojas inferiores de Android y el botón flotante. Usan una sombra doble y suave:

- **Claro:** `0 1px 2px rgb(0 0 0 / 0.04), 0 8px 24px rgb(0 0 0 / 0.06)`.
- **Oscuro:** `0 1px 2px rgb(0 0 0 / 0.4), 0 8px 24px rgb(0 0 0 / 0.4)`.

Detrás de una ventana o una hoja inferior va un velo negro al 35–40 %.

## Shapes

Esquinas redondeadas y moderadas, que crecen con el tamaño del elemento:

- **4 px:** atajos de teclado y la marca «IA».
- **6 px:** publicaciones del calendario y elementos de menú.
- **8 px:** botones, campos y la búsqueda.
- **10 px:** tarjetas de variante, código, menús y avisos.
- **12 px:** listas, medios y la rejilla del calendario.
- **16 px:** ventanas y tarjetas de Android.
- **18 px:** el botón flotante de Android.
- **26 px:** la parte superior de las hojas inferiores de Android.
- **Píldora:** chips, el indicador de la navegación de Android y los botones grandes del móvil.

```yaml
rounded:
  xs: 4px
  sm: 6px
  md: 8px
  lg: 10px
  xl: 12px
  2xl: 16px
  fab: 18px
  sheet: 26px
  full: 9999px
```

## Iconography

- **Iconos de interfaz:** de trazo, 24 × 24, grosor 1,7, extremos y uniones redondeados, sin relleno. Se dibujan a 16 px (14 px en chips y 20 px en Android). Están en `diseno/iconos.svg`.
- **Iconos de redes:** los oficiales, en un solo color y en el color del texto. X, Instagram, YouTube y TikTok salen de Simple Icons (CC0). LinkedIn sale de Font Awesome Free (CC BY 4.0), porque Simple Icons ya no lo incluye. El portfolio se representa con un globo. Están en `diseno/redes.svg`.
- **Logo:** un tintero con la tinta hasta la mitad y una pluma que entra por el cuello. Un solo color, en `diseno/logo.svg`. Sirve como favicon y como icono de Android sobre fondo negro o blanco.

## Components

**Botones.** Altura de 32 px, borde de línea y fondo de hoja. El principal es tinta con texto blanco (en oscuro, al revés). El fantasma no tiene borde ni fondo. En Android los botones miden 40 px y son píldoras.

**Chips.** Píldoras de 22 px con fondo gris suave y texto secundario. El chip de estado lleva un punto del color de estado, fondo tintado y texto en tinta.

**Tarjetas de variante.** Borde de línea, radio de 10 px y vista previa en Source Serif de dos líneas como mucho. La variante activa lleva borde de tinta. La propuesta de un agente lleva la marca «IA».

**Marcas de revisión.** Son la forma visible del CriticMarkup:

- **Tachado:** texto tenue con una raya de 1,5 px. Se quita al exportar.
- **Añadido:** fondo gris de añadido y subrayado de puntos.
- **Nota:** el texto anclado lleva un subrayado de 2 px en línea marcada; la nota va en una tarjeta aparte.
- **Propuesta del agente:** la marca «IA» y dos botones de 22 px para aceptar o descartar.

**Publicaciones del calendario.** Una fila con el icono de la red y el título en dos líneas como mucho. Llevan la franja de estado a la izquierda. La idea tiene borde de rayas y fondo transparente. La publicada va en texto tenue con un ✓ verde al final.

**Inspector.** El panel derecho del editor. Agrupa variantes, ramas, propuestas del agente e historial, con 32 px entre grupos.

**Android.** La navegación inferior usa un indicador gris en píldora tras el destino activo. Las hojas inferiores tienen un asa de 36 × 4 px. El editor pone las herramientas (tachar, nota, fork, historial, medio, bloque) en una tira sobre el teclado.

```yaml
components:
  app:
    backgroundColor: "{colors.background}"
    textColor: "{colors.on-surface}"
    typography: "{typography.body-md}"
  app-dark:
    backgroundColor: "{colors.background-dark}"
    textColor: "{colors.on-surface-dark}"
  brand-title:
    textColor: "{colors.primary}"
    typography: "{typography.brand-title}"
  sidebar:
    backgroundColor: "{colors.surface}"
    textColor: "{colors.on-surface-variant}"
    width: "{spacing.sidebar-width}"
    padding: "{spacing.lg}"
  sidebar-item-active:
    backgroundColor: "{colors.surface-container}"
    textColor: "{colors.on-surface}"
    rounded: "{rounded.sm}"
  header:
    backgroundColor: "{colors.background}"
    textColor: "{colors.on-surface}"
    typography: "{typography.title-md}"
    height: "{spacing.header-height}"
    padding: "{spacing.xl}"
  button:
    backgroundColor: "{colors.surface}"
    textColor: "{colors.on-surface}"
    typography: "{typography.body-sm}"
    rounded: "{rounded.md}"
    height: "{spacing.control-height}"
    padding: "{spacing.md}"
  button-primary:
    backgroundColor: "{colors.primary}"
    textColor: "{colors.on-primary}"
    rounded: "{rounded.md}"
    height: "{spacing.control-height}"
  button-primary-dark:
    backgroundColor: "{colors.primary-dark}"
    textColor: "{colors.on-primary-dark}"
  chip:
    backgroundColor: "{colors.surface-container}"
    textColor: "{colors.on-surface-variant}"
    typography: "{typography.label-md}"
    rounded: "{rounded.full}"
    height: 22px
    padding: "{spacing.sm}"
  chip-dark:
    backgroundColor: "{colors.surface-container-dark}"
    textColor: "{colors.on-surface-variant-dark}"
  state-dot-idea:
    backgroundColor: "{colors.state-idea}"
    size: 6px
  state-dot-amber:
    backgroundColor: "{colors.state-amber}"
    size: 6px
  state-dot-violet:
    backgroundColor: "{colors.state-violet}"
    size: 6px
  state-dot-blue:
    backgroundColor: "{colors.state-blue}"
    size: 6px
  state-dot-green:
    backgroundColor: "{colors.state-green}"
    size: 6px
  state-dot-idea-dark:
    backgroundColor: "{colors.state-idea-dark}"
    size: 6px
  state-dot-amber-dark:
    backgroundColor: "{colors.state-amber-dark}"
    size: 6px
  state-dot-violet-dark:
    backgroundColor: "{colors.state-violet-dark}"
    size: 6px
  state-dot-blue-dark:
    backgroundColor: "{colors.state-blue-dark}"
    size: 6px
  state-dot-green-dark:
    backgroundColor: "{colors.state-green-dark}"
    size: 6px
  divider:
    backgroundColor: "{colors.outline}"
    height: 1px
  divider-strong:
    backgroundColor: "{colors.outline-strong}"
    height: 1px
  divider-dark:
    backgroundColor: "{colors.outline-dark}"
    height: 1px
  divider-strong-dark:
    backgroundColor: "{colors.outline-strong-dark}"
    height: 1px
  article-page:
    width: "{spacing.article-max-width}"
    textColor: "{colors.on-surface}"
    typography: "{typography.article-body}"
  article-title:
    typography: "{typography.article-title}"
  article-lead:
    textColor: "{colors.on-surface-variant}"
    typography: "{typography.article-lead}"
  article-heading:
    typography: "{typography.article-heading}"
  block-focused:
    backgroundColor: "{colors.surface}"
    textColor: "{colors.on-surface}"
    rounded: "{rounded.sm}"
    padding: "{spacing.xs}"
  mark-deleted:
    textColor: "{colors.on-surface-faint}"
  mark-deleted-dark:
    textColor: "{colors.on-surface-faint-dark}"
  mark-added:
    backgroundColor: "{colors.mark-added}"
    textColor: "{colors.on-surface}"
    rounded: "{rounded.xs}"
  mark-added-dark:
    backgroundColor: "{colors.mark-added-dark}"
    textColor: "{colors.on-surface-dark}"
  ai-badge:
    textColor: "{colors.on-surface-variant}"
    typography: "{typography.code-badge}"
    rounded: "{rounded.xs}"
  code-block:
    backgroundColor: "{colors.surface-container}"
    textColor: "{colors.on-surface}"
    typography: "{typography.code-md}"
    rounded: "{rounded.lg}"
    padding: "{spacing.lg}"
  code-block-dark:
    backgroundColor: "{colors.surface-container-dark}"
    textColor: "{colors.on-surface-dark}"
  card-variant:
    backgroundColor: "{colors.surface}"
    textColor: "{colors.on-surface-variant}"
    typography: "{typography.article-preview}"
    rounded: "{rounded.lg}"
    padding: "{spacing.md}"
  card-variant-dark:
    backgroundColor: "{colors.surface-dark}"
    textColor: "{colors.on-surface-variant-dark}"
  inspector:
    backgroundColor: "{colors.surface}"
    width: "{spacing.inspector-width}"
    padding: "{spacing.xl}"
  inspector-group-title:
    textColor: "{colors.on-surface-variant}"
    typography: "{typography.label-caps}"
  calendar-grid:
    backgroundColor: "{colors.surface}"
    rounded: "{rounded.xl}"
  calendar-item:
    textColor: "{colors.on-surface}"
    typography: "{typography.label-md}"
    rounded: "{rounded.sm}"
    padding: "{spacing.xs}"
  modal:
    backgroundColor: "{colors.surface}"
    rounded: "{rounded.2xl}"
    padding: "{spacing.xl}"
  mobile-screen-title:
    textColor: "{colors.on-surface}"
    typography: "{typography.screen-title-mobile}"
  mobile-article-title:
    typography: "{typography.article-title-mobile}"
  mobile-article-body:
    typography: "{typography.article-body-mobile}"
  mobile-card:
    backgroundColor: "{colors.surface}"
    rounded: "{rounded.2xl}"
    padding: "{spacing.lg}"
  mobile-nav:
    backgroundColor: "{colors.surface}"
    textColor: "{colors.on-surface-variant}"
    height: "{spacing.mobile-nav-height}"
  mobile-nav-indicator:
    backgroundColor: "{colors.surface-container-high}"
    rounded: "{rounded.full}"
    width: 56px
    height: 30px
  mobile-nav-indicator-dark:
    backgroundColor: "{colors.surface-container-high-dark}"
    rounded: "{rounded.full}"
  mobile-fab:
    backgroundColor: "{colors.primary}"
    textColor: "{colors.on-primary}"
    rounded: "{rounded.fab}"
    size: "{spacing.fab-size}"
  mobile-sheet:
    backgroundColor: "{colors.surface}"
    rounded: "{rounded.sheet}"
    padding: "{spacing.lg}"
```

### Android con Jetpack Compose y Material 3

La app usa el `ColorScheme` de Material 3 con estos tokens. No usa el color dinámico de Android (el que toma los tonos del fondo de pantalla), porque rompería la paleta neutra.

| Rol de Material 3 | Claro | Oscuro |
|---|---|---|
| `primary` | `primary` | `primary-dark` |
| `onPrimary` | `on-primary` | `on-primary-dark` |
| `background` | `background` | `background-dark` |
| `surface` | `surface` | `surface-dark` |
| `surfaceContainer` | `surface-container` | `surface-container-dark` |
| `surfaceContainerHigh` | `surface-container-high` | `surface-container-high-dark` |
| `onSurface` | `on-surface` | `on-surface-dark` |
| `onSurfaceVariant` | `on-surface-variant` | `on-surface-variant-dark` |
| `outline` | `outline-strong` | `outline-strong-dark` |
| `outlineVariant` | `outline` | `outline-dark` |

- Los colores de estado van aparte del `ColorScheme`, en un objeto propio (por ejemplo `TintaEstados`) con sus versiones clara y oscura.
- El tema sigue a `isSystemInDarkTheme()`.
- Las tipografías se empaquetan en la app: Inter, Source Serif 4 y JetBrains Mono.

## Do's and Don'ts

- Do usar el color solo para los estados. Todo lo demás es gris, negro o blanco.
- Do poner el color de estado en el punto, la franja o el fondo tintado, nunca en el texto.
- Do escribir el contenido de los artículos en Source Serif 4 y la interfaz en Inter.
- Do marcar la selección con un borde de tinta, no con color.
- Do mantener el contraste WCAG AA (4,5:1) del texto en los dos temas.
- Do probar cada pantalla en claro y en oscuro antes de darla por buena.
- Don't usar el texto tenue para nada que haya que leer: solo fechas, contadores y ayudas.
- Don't añadir sombras a tarjetas o paneles: solo flota lo que está encima.
- Don't usar los colores de marca de las redes: sus iconos van en el color del texto.
- Don't reducir la letra del editor para ganar sitio: se gana con espacio.
- Don't usar el color dinámico de Android.
