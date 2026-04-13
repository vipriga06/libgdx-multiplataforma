package com.vprieto.scene2d;

import java.net.URI;
import java.net.URISyntaxException;

import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;

import com.badlogic.gdx.Application;
import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.Animation;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.ScreenUtils;
import com.badlogic.gdx.utils.TimeUtils;
import com.badlogic.gdx.utils.viewport.FitViewport;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

/** {@link com.badlogic.gdx.ApplicationListener} implementation shared by all platforms. */
public class Main extends ApplicationAdapter {
    private static final float WORLD_WIDTH = 640f;
    private static final float WORLD_HEIGHT = 480f;
    private static final float PLAYER_SIZE = 48f;
    private static final float PLAYER_SPEED = 220f;
    private static final float NET_SEND_INTERVAL = 1f / 30f;
    private static final String WS_URL = "ws://localhost:8888";

    private static final int IDLE = 0;
    private static final int UP = 1;
    private static final int DOWN = 2;
    private static final int LEFT = 3;
    private static final int RIGHT = 4;

    private FitViewport viewport;
    private SpriteBatch batch;
    private ShapeRenderer shapeRenderer;
    private BitmapFont font;

    private Texture playerSheet;
    private Animation<TextureRegion>[] walkAnimations;  // 4 animaciones: [UP, DOWN, LEFT, RIGHT]
    private TextureRegion[] idleFrames;  // Idle frame para cada dirección
    private final Vector2 playerPosition = new Vector2();
    private final Vector2 movementAxis = new Vector2();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private float stateTime;
    private float netSendAccumulator;
    private long netSequence;
    private boolean moving;
    private boolean touchInputActive;
    private boolean keyboardInputActive;
    private int currentDirection = IDLE;
    private boolean movingDiagonal;
    private WebSocketClient wsClient;

    private Rectangle btnUpLeft;
    private Rectangle btnUp;
    private Rectangle btnUpRight;
    private Rectangle btnLeft;
    private Rectangle btnRight;
    private Rectangle btnDownLeft;
    private Rectangle btnDown;
    private Rectangle btnDownRight;
    private Rectangle padBounds;

    private boolean touchUpLeft;
    private boolean touchUp;
    private boolean touchUpRight;
    private boolean touchLeft;
    private boolean touchRight;
    private boolean touchDownLeft;
    private boolean touchDown;
    private boolean touchDownRight;
    private final Vector3 touchPos = new Vector3();

    @Override
    public void create() {
        Gdx.app.setLogLevel(Application.LOG_DEBUG);

        viewport = new FitViewport(WORLD_WIDTH, WORLD_HEIGHT);
        batch = new SpriteBatch();
        shapeRenderer = new ShapeRenderer();
        font = new BitmapFont(Gdx.files.internal("com/badlogic/gdx/utils/lsans-15.fnt"));

        createPlayerAnimation();
        updateControlRegions();

        playerPosition.set(
            WORLD_WIDTH / 2f - PLAYER_SIZE / 2f,
            WORLD_HEIGHT / 2f - PLAYER_SIZE / 2f
        );

        connectWebSocket();
    }

    @Override
    public void render() {
        float delta = Gdx.graphics.getDeltaTime();
        updateMovement(delta);
        updateNetwork(delta);

        ScreenUtils.clear(0.08f, 0.09f, 0.13f, 1f);
        viewport.apply();

        drawControlRegions();

        batch.setProjectionMatrix(viewport.getCamera().combined);
        batch.begin();
        
        // Seleccionar animación según dirección (IDLE no tiene animación)
        int animDir = currentDirection;
        if (currentDirection == IDLE) {
            animDir = DOWN;  // Usar frames DOWN como fallback
        }
        
        TextureRegion currentFrame = moving && currentDirection != IDLE
            ? walkAnimations[animDir].getKeyFrame(stateTime, true)
            : idleFrames[animDir];
        batch.draw(currentFrame, playerPosition.x, playerPosition.y, PLAYER_SIZE, PLAYER_SIZE);

        font.setColor(Color.WHITE);
        font.draw(batch, "Touch: D-pad amb diagonals visibles", 12f, WORLD_HEIGHT - 10f);
        font.draw(batch, "Desktop: WASD o fletxes", 12f, WORLD_HEIGHT - 30f);
        font.draw(batch, "Direction: " + directionName(currentDirection), 12f, WORLD_HEIGHT - 50f);
        batch.end();
    }

    @Override
    public void resize(int width, int height) {
        if(width <= 0 || height <= 0) return;

        viewport.update(width, height, true);
        updateControlRegions();
    }

    @Override
    public void dispose() {
        if (wsClient != null) {
            wsClient.close();
        }
        batch.dispose();
        shapeRenderer.dispose();
        font.dispose();
        playerSheet.dispose();
    }

    private void createPlayerAnimation() {
        // Cargar spritesheet desde archivo (9 columnas x 4 filas)
        playerSheet = new Texture(Gdx.files.internal("sprites/C3ZwL.png"));
        
        // Dividir spritesheet en regiones (frameWidth, frameHeight)
        int FRAME_COLS = 9;
        int FRAME_ROWS = 4;
        int frameWidth = playerSheet.getWidth() / FRAME_COLS;
        int frameHeight = playerSheet.getHeight() / FRAME_ROWS;
        
        TextureRegion[][] split = TextureRegion.split(playerSheet, frameWidth, frameHeight);
        
        // Mapeo de constantes de dirección a filas del spritesheet
        // UP=1->row0, DOWN=2->row2, LEFT=3->row1, RIGHT=4->row3
        int[] dirToRow = {-1, 0, 2, 1, 3};  // Índice: dirección constante, valor: fila del sprite
        
        // Crear animaciones para cada dirección (solo para UP, DOWN, LEFT, RIGHT)
        walkAnimations = new Animation[5];
        idleFrames = new TextureRegion[5];
        
        for (int dir = 1; dir <= 4; dir++) {  // UP, DOWN, LEFT, RIGHT
            int row = dirToRow[dir];
            
            // Guardar idle frame (columna 0)
            idleFrames[dir] = split[row][0];
            
            // Crear frames de caminata (columnas 1-8)
            TextureRegion[] walkFrames = new TextureRegion[8];
            for (int col = 1; col < FRAME_COLS; col++) {
                walkFrames[col - 1] = split[row][col];
            }
            
            // Crear animación para esta dirección
            walkAnimations[dir] = new Animation<>(0.09f, walkFrames);
        }
    }

    private void updateControlRegions() {
        float worldWidth = viewport.getWorldWidth();
        float padSize = Math.min(worldWidth * 0.36f, 180f);
        float cell = padSize / 3f;
        float x = 18f;
        float y = 18f;

        padBounds = new Rectangle(x, y, padSize, padSize);

        btnDownLeft = new Rectangle(x, y, cell, cell);
        btnDown = new Rectangle(x + cell, y, cell, cell);
        btnDownRight = new Rectangle(x + 2f * cell, y, cell, cell);

        btnLeft = new Rectangle(x, y + cell, cell, cell);
        btnRight = new Rectangle(x + 2f * cell, y + cell, cell, cell);

        btnUpLeft = new Rectangle(x, y + 2f * cell, cell, cell);
        btnUp = new Rectangle(x + cell, y + 2f * cell, cell, cell);
        btnUpRight = new Rectangle(x + 2f * cell, y + 2f * cell, cell, cell);
    }

    private void updateMovement(float delta) {
        movementAxis.set(0f, 0f);

        applyTouchAxis(movementAxis);
        touchInputActive = hasActiveTouchPadInput();
        applyKeyboardAxis(movementAxis);

        moving = !movementAxis.isZero();
        movingDiagonal = moving && movementAxis.x != 0f && movementAxis.y != 0f;

        if (moving) {
            movementAxis.nor();
            currentDirection = dominantDirection(movementAxis);
        } else {
            currentDirection = IDLE;
        }

        if (moving) {
            stateTime += delta;
        } else {
            stateTime = 0f;
        }

        playerPosition.x += movementAxis.x * PLAYER_SPEED * delta;
        playerPosition.y += movementAxis.y * PLAYER_SPEED * delta;

        playerPosition.x = Math.max(0f, Math.min(playerPosition.x, WORLD_WIDTH - PLAYER_SIZE));
        playerPosition.y = Math.max(0f, Math.min(playerPosition.y, WORLD_HEIGHT - PLAYER_SIZE));
    }

    private void updateNetwork(float delta) {
        if (wsClient == null || !wsClient.isOpen()) {
            return;
        }

        netSendAccumulator += delta;
        while (netSendAccumulator >= NET_SEND_INTERVAL) {
            netSendAccumulator -= NET_SEND_INTERVAL;

            PlayerStateMessage message = new PlayerStateMessage();
            message.type = "player_state";
            message.sequence = ++netSequence;
            message.clientTimeMs = TimeUtils.millis();
            message.x = playerPosition.x;
            message.y = playerPosition.y;
            message.axisX = movementAxis.x;
            message.axisY = movementAxis.y;
            message.direction = directionName(currentDirection);
            message.moving = moving;
            message.touch = touchInputActive;
            message.keyboard = keyboardInputActive;

            try {
                wsClient.send(objectMapper.writeValueAsString(message));
            } catch (JsonProcessingException e) {
                Gdx.app.error("WS", "Error serializando estado del jugador", e);
            }
        }
    }

    private void applyKeyboardAxis(Vector2 axis) {
        boolean keyUp = Gdx.input.isKeyPressed(Input.Keys.W) || Gdx.input.isKeyPressed(Input.Keys.UP);
        boolean keyDown = Gdx.input.isKeyPressed(Input.Keys.S) || Gdx.input.isKeyPressed(Input.Keys.DOWN);
        boolean keyLeft = Gdx.input.isKeyPressed(Input.Keys.A) || Gdx.input.isKeyPressed(Input.Keys.LEFT);
        boolean keyRight = Gdx.input.isKeyPressed(Input.Keys.D) || Gdx.input.isKeyPressed(Input.Keys.RIGHT);
        keyboardInputActive = keyUp || keyDown || keyLeft || keyRight;

        if (keyUp) axis.y += 1f;
        if (keyDown) axis.y -= 1f;
        if (keyLeft) axis.x -= 1f;
        if (keyRight) axis.x += 1f;

        int keyboardY = (keyUp ? 1 : 0) + (keyDown ? -1 : 0);
        int keyboardX = (keyRight ? 1 : 0) + (keyLeft ? -1 : 0);

        if (keyboardX < 0 && keyboardY > 0) {
            touchUpLeft = true;
        } else if (keyboardX == 0 && keyboardY > 0) {
            touchUp = true;
        } else if (keyboardX > 0 && keyboardY > 0) {
            touchUpRight = true;
        } else if (keyboardX < 0 && keyboardY == 0) {
            touchLeft = true;
        } else if (keyboardX > 0 && keyboardY == 0) {
            touchRight = true;
        } else if (keyboardX < 0 && keyboardY < 0) {
            touchDownLeft = true;
        } else if (keyboardX == 0 && keyboardY < 0) {
            touchDown = true;
        } else if (keyboardX > 0 && keyboardY < 0) {
            touchDownRight = true;
        }
    }

    private void applyTouchAxis(Vector2 axis) {
        touchUpLeft = false;
        touchUp = false;
        touchUpRight = false;
        touchLeft = false;
        touchRight = false;
        touchDownLeft = false;
        touchDown = false;
        touchDownRight = false;

        for (int i = 0; i < 10; i++) {
            if (Gdx.input.isTouched(i)) {
                touchPos.set(Gdx.input.getX(i), Gdx.input.getY(i), 0f);
                viewport.unproject(touchPos);

                if (!padBounds.contains(touchPos.x, touchPos.y)) {
                    continue;
                }

                if (btnUpLeft.contains(touchPos.x, touchPos.y)) {
                    touchUpLeft = true;
                } else if (btnUp.contains(touchPos.x, touchPos.y)) {
                    touchUp = true;
                } else if (btnUpRight.contains(touchPos.x, touchPos.y)) {
                    touchUpRight = true;
                } else if (btnLeft.contains(touchPos.x, touchPos.y)) {
                    touchLeft = true;
                } else if (btnRight.contains(touchPos.x, touchPos.y)) {
                    touchRight = true;
                } else if (btnDownLeft.contains(touchPos.x, touchPos.y)) {
                    touchDownLeft = true;
                } else if (btnDown.contains(touchPos.x, touchPos.y)) {
                    touchDown = true;
                } else if (btnDownRight.contains(touchPos.x, touchPos.y)) {
                    touchDownRight = true;
                }
            }
        }

        if (touchUpLeft) {
            axis.x -= 1f;
            axis.y += 1f;
        }
        if (touchUp) {
            axis.y += 1f;
        }
        if (touchUpRight) {
            axis.x += 1f;
            axis.y += 1f;
        }
        if (touchLeft) {
            axis.x -= 1f;
        }
        if (touchRight) {
            axis.x += 1f;
        }
        if (touchDownLeft) {
            axis.x -= 1f;
            axis.y -= 1f;
        }
        if (touchDown) {
            axis.y -= 1f;
        }
        if (touchDownRight) {
            axis.x += 1f;
            axis.y -= 1f;
        }
    }

    private boolean hasActiveTouchPadInput() {
        return touchUpLeft || touchUp || touchUpRight || touchLeft || touchRight || touchDownLeft || touchDown || touchDownRight;
    }

    private void connectWebSocket() {
        try {
            wsClient = new WebSocketClient(new URI(WS_URL)) {
                @Override
                public void onOpen(ServerHandshake handshake) {
                    Gdx.app.log("WS", "Conectado a " + WS_URL);
                }

                @Override
                public void onMessage(String message) {
                    // Mensajes de estado recibidos se procesan en servidor; cliente sin log para evitar ruido.
                }

                @Override
                public void onClose(int code, String reason, boolean remote) {
                    Gdx.app.log("WS", "Cerrado code=" + code + " reason=" + reason);
                }

                @Override
                public void onError(Exception ex) {
                    Gdx.app.error("WS", "Error en websocket", ex);
                }
            };
            wsClient.connect();
        } catch (URISyntaxException e) {
            Gdx.app.error("WS", "URL de websocket invalida: " + WS_URL, e);
        }
    }

    private int dominantDirection(Vector2 axis) {
        if (Math.abs(axis.x) > Math.abs(axis.y)) {
            return axis.x > 0f ? RIGHT : LEFT;
        }
        return axis.y > 0f ? UP : DOWN;
    }

    private void drawControlRegions() {
        shapeRenderer.setProjectionMatrix(viewport.getCamera().combined);
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);

        drawRegion(btnUpLeft, touchUpLeft);
        drawRegion(btnUp, touchUp);
        drawRegion(btnUpRight, touchUpRight);
        drawRegion(btnLeft, touchLeft);
        drawRegion(btnRight, touchRight);
        drawRegion(btnDownLeft, touchDownLeft);
        drawRegion(btnDown, touchDown);
        drawRegion(btnDownRight, touchDownRight);

        shapeRenderer.end();

        shapeRenderer.begin(ShapeRenderer.ShapeType.Line);
        shapeRenderer.setColor(0.35f, 0.46f, 0.58f, 0.9f);
        shapeRenderer.rect(padBounds.x, padBounds.y, padBounds.width, padBounds.height);
        shapeRenderer.end();

        batch.setProjectionMatrix(viewport.getCamera().combined);
        batch.begin();
        font.setColor(0.92f, 0.95f, 1f, 0.9f);
        drawButtonLabel("UL", btnUpLeft);
        drawButtonLabel("U", btnUp);
        drawButtonLabel("UR", btnUpRight);
        drawButtonLabel("L", btnLeft);
        drawButtonLabel("R", btnRight);
        drawButtonLabel("DL", btnDownLeft);
        drawButtonLabel("D", btnDown);
        drawButtonLabel("DR", btnDownRight);
        batch.end();
    }

    private void drawRegion(Rectangle region, boolean active) {
        if (active) {
            shapeRenderer.setColor(0.15f, 0.65f, 1f, 0.30f);
        } else {
            shapeRenderer.setColor(0.08f, 0.12f, 0.18f, 0.15f);
        }
        shapeRenderer.rect(region.x, region.y, region.width, region.height);
    }

    private void drawButtonLabel(String text, Rectangle region) {
        float textX = region.x + region.width * 0.5f - text.length() * 3.2f;
        float textY = region.y + region.height * 0.5f + 5f;
        font.draw(batch, text, textX, textY);
    }

    private String directionName(int direction) {
        if (movingDiagonal) {
            String vertical = movementAxis.y > 0f ? "UP" : "DOWN";
            String horizontal = movementAxis.x > 0f ? "RIGHT" : "LEFT";
            return vertical + "+" + horizontal;
        }

        switch (direction) {
            case UP:
                return "UP";
            case DOWN:
                return "DOWN";
            case LEFT:
                return "LEFT";
            case RIGHT:
                return "RIGHT";
            default:
                return "IDLE";
        }
    }

    private static class PlayerStateMessage {
        public String type;
        public long sequence;
        public long clientTimeMs;
        public float x;
        public float y;
        public float axisX;
        public float axisY;
        public String direction;
        public boolean moving;
        public boolean touch;
        public boolean keyboard;
    }
}