package com.vprieto.animation;

import com.badlogic.gdx.ApplicationListener;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input.Keys;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.Animation;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.Pixmap;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PlayerAnimator implements ApplicationListener {

    private static final String ASSET_FOLDER = "sprites/ninja";

    private SpriteBatch batch;
    private float stateTime;
    private OrthographicCamera camera;

    private final Map<String, Animation<TextureRegion>> animations = new HashMap<>();
    private Animation<TextureRegion> currentAnim;

    private final List<Texture> ownedTextures = new ArrayList<>();

    private float x = 200f, y = 120f;
    private float speed = 240f;
    private boolean facingRight = true;
    private static final float SCALE = 0.5f;

    private boolean actionPlaying = false;
    private String actionKey = null;
    private float actionTime = 0f;
    private static final int TILE_SIZE = 32;
    private boolean movingTile = false;
    private float targetX, targetY;
    private int gridX, gridY;
    private static final int MAP_WIDTH = 40; // tiles
    private static final int MAP_HEIGHT = 30; // tiles
    private Texture tileTexture;
    private int desiredDX = 0, desiredDY = 0; // requested movement direction while holding

    @Override
    public void create() {
        batch = new SpriteBatch();
        stateTime = 0f;

        camera = new OrthographicCamera();
        camera.setToOrtho(false, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());

        loadAnimationsFromFolder(ASSET_FOLDER);

        if (animations.containsKey("Idle")) currentAnim = animations.get("Idle");
        else if (animations.containsKey("idle")) currentAnim = animations.get("idle");
        else if (!animations.isEmpty()) currentAnim = animations.values().iterator().next();

        gridX = Math.round(x / TILE_SIZE);
        gridY = Math.round(y / TILE_SIZE);
        x = gridX * TILE_SIZE;
        y = gridY * TILE_SIZE;
        targetX = x;
        targetY = y;

        // simple tile texture (1x1 white) used to draw background tiles
        Pixmap pm = new Pixmap(1,1, Pixmap.Format.RGBA8888);
        pm.setColor(0.6f,0.8f,0.6f,1f);
        pm.fill();
        tileTexture = new Texture(pm);
        pm.dispose();
    }

    private void loadAnimationsFromFolder(String folder) {
        FileHandle dir = Gdx.files.internal(folder);
        if (!dir.exists()) {
            System.err.println("Asset folder not found: " + folder);
            return;
        }

        FileHandle[] files = dir.list();
        Arrays.sort(files, Comparator.comparing(FileHandle::name));

        Map<String, List<FileHandle>> groups = new HashMap<>();
        for (FileHandle fh : files) {
            if (!fh.name().toLowerCase().endsWith(".png")) continue;
            String name = fh.nameWithoutExtension();
            String key = name;
            if (name.contains("__")) key = name.split("__")[0];
            else if (name.contains("_")) key = name.split("_")[0];
            else {
                key = name.replaceAll("[^A-Za-z].*$", "");
                if (key.isEmpty()) key = "anim";
            }
            groups.computeIfAbsent(key, k -> new ArrayList<>()).add(fh);
        }

        for (Map.Entry<String, List<FileHandle>> e : groups.entrySet()) {
            List<FileHandle> list = e.getValue();
            list.sort(Comparator.comparing(FileHandle::name));
            TextureRegion[] regs = new TextureRegion[list.size()];
            for (int i = 0; i < list.size(); i++) {
                FileHandle fh = list.get(i);
                Texture t = new Texture(fh);
                ownedTextures.add(t);
                regs[i] = new TextureRegion(t);
            }
            Animation<TextureRegion> anim = new Animation<TextureRegion>(0.08f, regs);
            String key = e.getKey();
            if (key.equalsIgnoreCase("Idle") || key.equalsIgnoreCase("Run") || key.equalsIgnoreCase("Glide") || key.equalsIgnoreCase("Climb")) {
                anim.setPlayMode(Animation.PlayMode.LOOP);
            } else {
                anim.setPlayMode(Animation.PlayMode.NORMAL);
            }
            animations.put(key, anim);
        }

        System.out.println("Loaded animations: " + animations.keySet());
    }

    @Override
    public void render() {
        float delta = Gdx.graphics.getDeltaTime();
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
        stateTime += delta;

        // Directional inputs (continuous while key is held)
        boolean left = Gdx.input.isKeyPressed(Keys.LEFT) || Gdx.input.isKeyPressed(Keys.A);
        boolean right = Gdx.input.isKeyPressed(Keys.RIGHT) || Gdx.input.isKeyPressed(Keys.D);
        boolean up = Gdx.input.isKeyPressed(Keys.UP) || Gdx.input.isKeyPressed(Keys.W);
        boolean downKey = Gdx.input.isKeyPressed(Keys.DOWN) || Gdx.input.isKeyPressed(Keys.S);

        // Actions
        boolean jump = Gdx.input.isKeyJustPressed(Keys.SPACE);
        boolean attack = Gdx.input.isKeyJustPressed(Keys.J);
        boolean thr = Gdx.input.isKeyJustPressed(Keys.K);
        boolean kun = Gdx.input.isKeyJustPressed(Keys.L);
        boolean slide = Gdx.input.isKeyJustPressed(Keys.SHIFT_LEFT);
        boolean dead = Gdx.input.isKeyJustPressed(Keys.X);
        boolean down = Gdx.input.isKeyPressed(Keys.DOWN) || Gdx.input.isKeyPressed(Keys.C);

        // Simple touch regions: left third, right third, top center = jump, bottom center = crouch
        boolean touchLeft = false, touchRight = false, touchJump = false, touchDown = false;
        if (Gdx.input.justTouched()) {
            int sx = Gdx.graphics.getWidth();
            int sy = Gdx.graphics.getHeight();
            int tx = Gdx.input.getX();
            int ty = Gdx.input.getY();
            if (tx < sx / 3) touchLeft = true;
            else if (tx > sx * 2 / 3) touchRight = true;
            else if (ty < sy / 3) touchJump = true;
            else if (ty > sy * 2 / 3) touchDown = true;
        }

        // Handle triggered actions first
        if ((jump || touchJump) && animations.containsKey("Jump")) {
            triggerAction("Jump");
            if (!movingTile) {
                if (facingRight) gridX += 1; else gridX -= 1;
                targetX = gridX * TILE_SIZE;
                movingTile = true;
            }
        } else if (attack && animations.containsKey("Attack")) triggerAction("Attack");
        else if (thr && animations.containsKey("Throw")) triggerAction("Throw");
        else if (kun && animations.containsKey("Kunai")) triggerAction("Kunai");
        else if (slide && (left || right || touchLeft || touchRight) && animations.containsKey("Slide")) triggerAction("Slide");
        else if (dead && animations.containsKey("Dead")) triggerAction("Dead");

        if (!actionPlaying) {
            if (!movingTile) {
                // determine desired movement from keys/touch (allow diagonals)
                desiredDX = 0;
                desiredDY = 0;

                if (left || touchLeft) desiredDX -= 1;
                if (right || touchRight) desiredDX += 1;
                if (up) desiredDY += 1;
                if (downKey) desiredDY -= 1;

                // keep in [-1, 1]
                if (desiredDX > 1) desiredDX = 1;
                if (desiredDX < -1) desiredDX = -1;
                if (desiredDY > 1) desiredDY = 1;
                if (desiredDY < -1) desiredDY = -1;

                if (desiredDX != 0 || desiredDY != 0) {
                    int newGX = gridX + desiredDX;
                    int newGY = gridY + desiredDY;
                    // clamp to map
                    newGX = Math.max(0, Math.min(MAP_WIDTH - 1, newGX));
                    newGY = Math.max(0, Math.min(MAP_HEIGHT - 1, newGY));
                    if (newGX != gridX || newGY != gridY) {
                        gridX = newGX; gridY = newGY;
                        targetX = gridX * TILE_SIZE;
                        targetY = gridY * TILE_SIZE;
                        movingTile = true;
                        if (desiredDX < 0) facingRight = false;
                        else if (desiredDX > 0) facingRight = true;
                        pickAnimation("Run");
                    }
                } else if ((down && !left && !right) || touchDown) {
                    if (animations.containsKey("Crouch")) {
                        Animation<TextureRegion> a = animations.get("Crouch");
                        a.setPlayMode(Animation.PlayMode.LOOP);
                        currentAnim = a;
                    } else {
                        pickAnimation("Idle");
                    }
                } else {
                    pickAnimation("Idle");
                }
            }

            if (movingTile) {
                float dx = targetX - x;
                float dy = targetY - y;
                float dist = (float)Math.sqrt(dx*dx + dy*dy);
                if (dist <= speed * delta) {
                    x = targetX; y = targetY; movingTile = false; stateTime = 0f; pickAnimation("Idle");
                    // recompute desired direction from currently held keys/touch (allow diagonals)
                    desiredDX = 0;
                    desiredDY = 0;
                    if (touchLeft || Gdx.input.isKeyPressed(Keys.LEFT) || Gdx.input.isKeyPressed(Keys.A)) desiredDX -= 1;
                    if (touchRight || Gdx.input.isKeyPressed(Keys.RIGHT) || Gdx.input.isKeyPressed(Keys.D)) desiredDX += 1;
                    if (Gdx.input.isKeyPressed(Keys.UP) || Gdx.input.isKeyPressed(Keys.W)) desiredDY += 1;
                    if (Gdx.input.isKeyPressed(Keys.DOWN) || Gdx.input.isKeyPressed(Keys.S)) desiredDY -= 1;

                    if (desiredDX > 1) desiredDX = 1;
                    if (desiredDX < -1) desiredDX = -1;
                    if (desiredDY > 1) desiredDY = 1;
                    if (desiredDY < -1) desiredDY = -1;
                    if (desiredDX != 0 || desiredDY != 0) {
                        int newGX = gridX + desiredDX;
                        int newGY = gridY + desiredDY;
                        newGX = Math.max(0, Math.min(MAP_WIDTH - 1, newGX));
                        newGY = Math.max(0, Math.min(MAP_HEIGHT - 1, newGY));
                        if (newGX != gridX || newGY != gridY) {
                            gridX = newGX; gridY = newGY;
                            targetX = gridX * TILE_SIZE;
                            targetY = gridY * TILE_SIZE;
                            movingTile = true;
                            if (desiredDX < 0) facingRight = false; else if (desiredDX > 0) facingRight = true;
                            pickAnimation("Run");
                        }
                    }
                } else {
                    x += (dx/dist) * speed * delta;
                    y += (dy/dist) * speed * delta;
                }
            }

            if (currentAnim == null) return;
            TextureRegion frame = currentAnim.getKeyFrame(stateTime, true);
            float w = frame.getRegionWidth() * SCALE;
            float h = frame.getRegionHeight() * SCALE;
            // clamp camera to map bounds
            float mapPixelW = MAP_WIDTH * TILE_SIZE;
            float mapPixelH = MAP_HEIGHT * TILE_SIZE;
            float halfW = camera.viewportWidth / 2f;
            float halfH = camera.viewportHeight / 2f;
            float camX = x + w/2f;
            float camY = y + h/2f;
            if (mapPixelW > camera.viewportWidth) camX = Math.max(halfW, Math.min(mapPixelW - halfW, camX));
            else camX = mapPixelW / 2f;
            if (mapPixelH > camera.viewportHeight) camY = Math.max(halfH, Math.min(mapPixelH - halfH, camY));
            else camY = mapPixelH / 2f;
            camera.position.set(camX, camY, 0);
            camera.update();
            batch.setProjectionMatrix(camera.combined);

            // draw background
            batch.begin();
            for (int ty = 0; ty < MAP_HEIGHT; ty++) {
                for (int tx = 0; tx < MAP_WIDTH; tx++) {
                    float px = tx * TILE_SIZE;
                    float py = ty * TILE_SIZE;
                    batch.setColor(((tx + ty) % 2 == 0) ? 1f : 0.9f, 1f, 1f, 1f);
                    batch.draw(tileTexture, px, py, TILE_SIZE, TILE_SIZE);
                }
            }
            batch.setColor(1f, 1f, 1f, 1f);
            batch.end();

            batch.begin();
            if (facingRight) batch.draw(frame, x, y, w, h);
            else batch.draw(frame, x + w, y, -w, h);
            batch.end();
        } else {
            actionTime += delta;
            Animation<TextureRegion> a = animations.get(actionKey);
            if (a == null) { actionPlaying = false; pickAnimation("Idle"); return; }
            TextureRegion frame = a.getKeyFrame(actionTime, false);
            float w = frame.getRegionWidth() * SCALE;
            float h = frame.getRegionHeight() * SCALE;
            float mapPixelW = MAP_WIDTH * TILE_SIZE;
            float mapPixelH = MAP_HEIGHT * TILE_SIZE;
            float halfW = camera.viewportWidth / 2f;
            float halfH = camera.viewportHeight / 2f;
            float camX = x + w/2f;
            float camY = y + h/2f;
            if (mapPixelW > camera.viewportWidth) camX = Math.max(halfW, Math.min(mapPixelW - halfW, camX));
            else camX = mapPixelW / 2f;
            if (mapPixelH > camera.viewportHeight) camY = Math.max(halfH, Math.min(mapPixelH - halfH, camY));
            else camY = mapPixelH / 2f;
            camera.position.set(camX, camY, 0);
            camera.update();
            batch.setProjectionMatrix(camera.combined);

            // draw background during action animations too
            batch.begin();
            for (int ty = 0; ty < MAP_HEIGHT; ty++) {
                for (int tx = 0; tx < MAP_WIDTH; tx++) {
                    float px = tx * TILE_SIZE;
                    float py = ty * TILE_SIZE;
                    batch.setColor(((tx + ty) % 2 == 0) ? 1f : 0.9f, 1f, 1f, 1f);
                    batch.draw(tileTexture, px, py, TILE_SIZE, TILE_SIZE);
                }
            }
            batch.setColor(1f, 1f, 1f, 1f);
            batch.end();

            batch.begin();
            if (facingRight) batch.draw(frame, x, y, w, h);
            else batch.draw(frame, x + w, y, -w, h);
            batch.end();
            if (a.isAnimationFinished(actionTime)) {
                actionPlaying = false; actionKey = null; actionTime = 0f; stateTime = 0f; pickAnimation("Idle");
            }
        }
        }

    private void triggerAction(String key) {
        if (!animations.containsKey(key)) return;
        actionPlaying = true;
        actionKey = key;
        actionTime = 0f;
    }

    private void pickAnimation(String name) {
        if (currentAnim == null || !animations.containsValue(currentAnim)) {
            if (animations.containsKey(name)) currentAnim = animations.get(name);
            return;
        }
        Animation<TextureRegion> a = animations.get(name);
        if (a != null && a != currentAnim) currentAnim = a;
    }

    @Override
    public void resize(int width, int height) {
        if (camera != null) {
            camera.setToOrtho(false, width, height);
            camera.update();
        }
    }

    @Override
    public void pause() {
    }

    @Override
    public void resume() {
    }

    @Override
    public void dispose() {
        batch.dispose();
        if (tileTexture != null) tileTexture.dispose();
        for (Texture t : ownedTextures) t.dispose();
    }
}
