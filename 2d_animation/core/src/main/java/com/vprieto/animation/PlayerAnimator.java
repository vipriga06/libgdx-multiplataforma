package com.vprieto.animation;

import com.badlogic.gdx.ApplicationListener;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input.Keys;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.Animation;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;

public class PlayerAnimator implements ApplicationListener {

    private static final String SPRITESHEET_PATH = "sprites/C3ZwL.png";
    private static final int SHEET_ROWS = 4;
    private static final int SHEET_COLS = 9;

    private static final int DIR_DOWN = 0;
    private static final int DIR_LEFT = 1;
    private static final int DIR_RIGHT = 2;
    private static final int DIR_UP = 3;

    // Row mapping for C3ZwL spritesheet (rows are: UP, LEFT, DOWN, RIGHT)
    private static final int[] DIR_TO_ROW = {2, 1, 3, 0};

    private static final int TILE_SIZE = 32;
    private static final float FRAME_DURATION = 0.09f;
    private static final float SCALE = 0.8f;

    private SpriteBatch batch;
    private OrthographicCamera camera;
    private Texture spriteSheet;
    private Texture tileTexture;

    private Animation<TextureRegion>[] idleAnimations;
    private Animation<TextureRegion>[] walkAnimations;

    private int currentDir = DIR_DOWN;
    private float stateTime;

    private float x = 160f;
    private float y = 160f;
    private float targetX;
    private float targetY;
    private boolean movingTile;
    private float speed = 280f;

    private int gridX;
    private int gridY;

    @SuppressWarnings("unchecked")
    @Override
    public void create() {
        batch = new SpriteBatch();
        camera = new OrthographicCamera();
        camera.setToOrtho(false, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());

        loadAnimationsFromSpriteSheet();

        gridX = Math.round(x / TILE_SIZE);
        gridY = Math.round(y / TILE_SIZE);
        x = gridX * TILE_SIZE;
        y = gridY * TILE_SIZE;
        targetX = x;
        targetY = y;

        createBackgroundTile();
    }

    @SuppressWarnings("unchecked")
    private void loadAnimationsFromSpriteSheet() {
        spriteSheet = new Texture(Gdx.files.internal(SPRITESHEET_PATH));
        TextureRegion[][] split = TextureRegion.split(
            spriteSheet,
            spriteSheet.getWidth() / SHEET_COLS,
            spriteSheet.getHeight() / SHEET_ROWS
        );

        idleAnimations = new Animation[SHEET_ROWS];
        walkAnimations = new Animation[SHEET_ROWS];

        for (int dir = 0; dir < SHEET_ROWS; dir++) {
            int row = DIR_TO_ROW[dir];
            TextureRegion idle = split[row][0];
            TextureRegion[] walkFrames = new TextureRegion[SHEET_COLS - 1];

            for (int col = 1; col < SHEET_COLS; col++) {
                walkFrames[col - 1] = split[row][col];
            }

            idleAnimations[dir] = new Animation<TextureRegion>(FRAME_DURATION, idle);
            idleAnimations[dir].setPlayMode(Animation.PlayMode.LOOP);

            walkAnimations[dir] = new Animation<TextureRegion>(FRAME_DURATION, walkFrames);
            walkAnimations[dir].setPlayMode(Animation.PlayMode.LOOP);
        }
    }

    private void createBackgroundTile() {
        Pixmap pm = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
        pm.setColor(0.60f, 0.78f, 0.60f, 1f);
        pm.fill();
        tileTexture = new Texture(pm);
        pm.dispose();
    }

    @Override
    public void render() {
        float delta = Gdx.graphics.getDeltaTime();
        stateTime += delta;
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

        int[] input = readDirectionalInput();
        int desiredDX = input[0];
        int desiredDY = input[1];

        if (desiredDX != 0 || desiredDY != 0) {
            currentDir = directionFromVector(desiredDX, desiredDY, currentDir);
        }

        if (!movingTile && (desiredDX != 0 || desiredDY != 0)) {
            startStep(desiredDX, desiredDY);
        }

        if (movingTile) {
            updateStepMovement(delta, desiredDX, desiredDY);
        }

        Animation<TextureRegion> currentAnim = movingTile ? walkAnimations[currentDir] : idleAnimations[currentDir];
        TextureRegion frame = currentAnim.getKeyFrame(stateTime, true);
        float w = frame.getRegionWidth() * SCALE;
        float h = frame.getRegionHeight() * SCALE;

        updateCamera(x + w / 2f, y + h / 2f);

        batch.setProjectionMatrix(camera.combined);
        batch.begin();
        drawInfinitePattern();
        batch.draw(frame, x, y, w, h);
        batch.end();
    }

    private int[] readDirectionalInput() {
        boolean left = Gdx.input.isKeyPressed(Keys.LEFT) || Gdx.input.isKeyPressed(Keys.A);
        boolean right = Gdx.input.isKeyPressed(Keys.RIGHT) || Gdx.input.isKeyPressed(Keys.D);
        boolean up = Gdx.input.isKeyPressed(Keys.UP) || Gdx.input.isKeyPressed(Keys.W);
        boolean down = Gdx.input.isKeyPressed(Keys.DOWN) || Gdx.input.isKeyPressed(Keys.S);

        int desiredDX = 0;
        int desiredDY = 0;
        if (left) desiredDX -= 1;
        if (right) desiredDX += 1;
        if (up) desiredDY += 1;
        if (down) desiredDY -= 1;
        return new int[] {desiredDX, desiredDY};
    }

    private void startStep(int dx, int dy) {
        gridX += dx;
        gridY += dy;
        targetX = gridX * TILE_SIZE;
        targetY = gridY * TILE_SIZE;
        movingTile = true;
    }

    private void updateStepMovement(float delta, int desiredDX, int desiredDY) {
        float dx = targetX - x;
        float dy = targetY - y;
        float dist = (float) Math.sqrt(dx * dx + dy * dy);

        if (dist <= speed * delta) {
            x = targetX;
            y = targetY;
            movingTile = false;
            stateTime = 0f;

            // Keep movement continuous while key is pressed
            if (desiredDX != 0 || desiredDY != 0) {
                currentDir = directionFromVector(desiredDX, desiredDY, currentDir);
                startStep(desiredDX, desiredDY);
            }
            return;
        }

        x += (dx / dist) * speed * delta;
        y += (dy / dist) * speed * delta;
    }

    private void drawInfinitePattern() {
        float left = camera.position.x - camera.viewportWidth * 0.5f - TILE_SIZE;
        float right = camera.position.x + camera.viewportWidth * 0.5f + TILE_SIZE;
        float bottom = camera.position.y - camera.viewportHeight * 0.5f - TILE_SIZE;
        float top = camera.position.y + camera.viewportHeight * 0.5f + TILE_SIZE;

        int startX = (int) Math.floor(left / TILE_SIZE) * TILE_SIZE;
        int startY = (int) Math.floor(bottom / TILE_SIZE) * TILE_SIZE;

        for (int py = startY; py <= top; py += TILE_SIZE) {
            for (int px = startX; px <= right; px += TILE_SIZE) {
                int ix = (int) Math.floor((float) px / TILE_SIZE);
                int iy = (int) Math.floor((float) py / TILE_SIZE);
                float tint = ((ix + iy) & 1) == 0 ? 1f : 0.9f;
                batch.setColor(tint, 1f, tint, 1f);
                batch.draw(tileTexture, px, py, TILE_SIZE, TILE_SIZE);
            }
        }
        batch.setColor(1f, 1f, 1f, 1f);
    }

    private void updateCamera(float centerX, float centerY) {
        camera.position.set(centerX, centerY, 0);
        camera.update();
    }

    private int directionFromVector(int dx, int dy, int fallbackDir) {
        if (dx == 0 && dy == 0) return fallbackDir;
        if (Math.abs(dx) >= Math.abs(dy)) {
            return dx < 0 ? DIR_LEFT : DIR_RIGHT;
        }
        return dy < 0 ? DIR_DOWN : DIR_UP;
    }

    @Override
    public void resize(int width, int height) {
        camera.setToOrtho(false, width, height);
        camera.update();
    }

    @Override
    public void pause() {
    }

    @Override
    public void resume() {
    }

    @Override
    public void dispose() {
        if (batch != null) batch.dispose();
        if (spriteSheet != null) spriteSheet.dispose();
        if (tileTexture != null) tileTexture.dispose();
    }
}
