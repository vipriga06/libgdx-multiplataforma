# Tutorial: Sprites y animaciones con libGDX (Animation)

Este tutorial muestra cómo usar la clase `Animation` de libGDX para reproducir un spritesheet y cómo trabajar con `Texture`, `TextureRegion`, `Sprite` y `TextureAtlas`.

## Conceptos básicos
- Texture: gestiona una imagen en GPU.
- TextureRegion: recorta una parte de una `Texture` (ideal para frames de spritesheet).
- Sprite: una `TextureRegion` con información de posición, origen, escala y rotación.
- Animation<T>: recibe un array de frames (`TextureRegion`, normalmente) y un tiempo por frame; su método `getKeyFrame(stateTime, looping)` devuelve el frame actual.

## 1) Preparar un spritesheet regular (matriz)

Si tus frames están en una rejilla regular (ej. 6 columnas x 5 filas), puedes usar `TextureRegion.split()` para obtener los frames.

Ejemplo mínimo (Java):

```java
public class Animator implements ApplicationListener {
    private static final int FRAME_COLS = 6, FRAME_ROWS = 5;

    Animation<TextureRegion> walkAnimation;
    Texture walkSheet;
    SpriteBatch spriteBatch;
    float stateTime;

    @Override
    public void create() {
        walkSheet = new Texture(Gdx.files.internal("animation_sheet.png"));
        TextureRegion[][] tmp = TextureRegion.split(walkSheet,
                walkSheet.getWidth() / FRAME_COLS,
                walkSheet.getHeight() / FRAME_ROWS);

        TextureRegion[] walkFrames = new TextureRegion[FRAME_COLS * FRAME_ROWS];
        int index = 0;
        for (int i = 0; i < FRAME_ROWS; i++) {
            for (int j = 0; j < FRAME_COLS; j++) {
                walkFrames[index++] = tmp[i][j];
            }
        }

        walkAnimation = new Animation<TextureRegion>(0.025f, walkFrames);
        spriteBatch = new SpriteBatch();
        stateTime = 0f;
    }

    @Override
    public void render() {
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
        stateTime += Gdx.graphics.getDeltaTime();
        TextureRegion currentFrame = walkAnimation.getKeyFrame(stateTime, true);
        spriteBatch.begin();
        spriteBatch.draw(currentFrame, 50, 50);
        spriteBatch.end();
    }

    @Override
    public void dispose() {
        spriteBatch.dispose();
        walkSheet.dispose();
    }
}
```

Explicación: `stateTime` acumula el tiempo; `getKeyFrame(stateTime, true)` devuelve el frame correspondiente y lo dibujamos con `SpriteBatch`.

## 2) Usar TextureAtlas / TexturePacker (recomendado)

Si trabajas con muchos sprites o animaciones, convierte tus imágenes en un `TextureAtlas` con `TexturePacker`. Nombra los archivos con un sufijo numérico (`run_0.png`, `run_1.png`, ...) y luego:

```java
runningAnimation = new Animation<TextureRegion>(0.033f, atlas.findRegions("running"), PlayMode.LOOP);
```

`findRegions` devuelve un `Array<TextureRegion>` con los frames en orden de índices.

## 3) Spritesheets irregulares (mapeo manual)

Si los frames no están alineados en una rejilla, crea manualmente `TextureRegion` con coordenadas y dimensiones:

```java
TextureRegion[] frames = new TextureRegion[4];
frames[0] = new TextureRegion(sheet,9,190,107,112);
frames[1] = new TextureRegion(sheet,124,190,113,107);
frames[2] = new TextureRegion(sheet,248,190,101,110);
frames[3] = new TextureRegion(sheet,364,190,122,109);
yeti = new Animation<TextureRegion>(0.25f, frames);

// en render:
stateTime += Gdx.graphics.getDeltaTime();
TextureRegion frame = yeti.getKeyFrame(stateTime, true);
batch.begin();
batch.draw(frame, 200, 100);
batch.end();
```

Si quieres voltear horizontalmente al dibujar (por ejemplo para invertir dirección) puedes usar `SpriteBatch.draw` con `scaleX = -1` para evitar artefactes por frames de distinto ancho:

```java
batch.draw(frame, x + frame.getRegionWidth(), y, // x,y (mueve origen)
           0, 0,
           frame.getRegionWidth(), frame.getRegionHeight(),
           -1f, 1f, 0f);
```

También existe `TextureRegion.flip(true, false)`, pero puede producir saltos si los frames tienen distintos tamaños.

## 4) Buenas prácticas
- Empaquetar frames en un `TextureAtlas` para reducir draw calls.
- Elegir un `frameDuration` adecuado: juegos retro pueden usar 10 FPS; animaciones suaves ~24-60 FPS.
- Reusar `SpriteBatch` y liberar `Texture`/`SpriteBatch` en `dispose()`.
- Para animaciones con físicas o posiciones, separar la lógica de estado (animación) de la lógica de posición.

## 5) Ejercicio propuesto
1. Busca un spritesheet libre en las webs sugeridas (por ejemplo https://www.gameart2d.com o https://www.kindpng.com).
2. Comprueba si está en una rejilla regular. Si lo está, usa `TextureRegion.split()` y crea una `Animation`.
3. Si no lo está, genera un atlas con `TexturePacker` o mapea manualmente las regiones.
4. Añade control de teclado para cambiar de animación (ej. caminar / correr / saltar).
5. Presenta tu spritesheet al profesor antes de la demo.

Enlaces útiles:
- TexturePacker: https://github.com/crashinvaders/gdx-texture-packer-gui
- TexturePacker (proprietary): https://www.codeandweb.com/texturepacker
- Recursos de imágenes: https://www.kindpng.com, https://www.gameart2d.com
- Sonidos: https://freesound.org

---
Si quieres, puedo:
- Añadir este archivo al repositorio en `docs/animation.md` también en catalán.
- Generar un proyecto de ejemplo en `2d_animation/core/src/main/java/...` que ejecute la animació con un spritesheet de ejemplo (necesitaré que subas el sprite o me indiques uno URL).

## Ejemplo práctico: Yeti (spritesheet)

He añadido una clase de ejemplo `YetiAnimator` en el módulo `core` que carga `yeti.png` desde la carpeta de assets y crea una `Animation` usando `TextureRegion.split()`.

Pasos para usarla:
1. Copia tu spritesheet (el que has subido) a `2d_animation/assets/yeti.png`.
2. Ajusta `FRAME_COLS` y `FRAME_ROWS` en `core/src/main/java/com/vprieto/animation/YetiAnimator.java` para que coincidan con la rejilla de tu spritesheet.
3. Ejecuta la configuración normal del proyecto libGDX (ej. desde Gradle: `./gradlew desktop:run` o el launcher que uses).

Notas:
- Si tu spritesheet no es una rejilla regular, puedes generar un `TextureAtlas` con TexturePacker y usar `atlas.findRegions("name")`, o crear `TextureRegion` manualmente con las coordenadas.
- El `frameDuration` por defecto en el ejemplo está en `0.12f`; ajústalo para acelerar o ralentizar la animación.
