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

    private final Map<String, Animation<TextureRegion>> animations = new HashMap<>();
    private Animation<TextureRegion> currentAnim;

    private final List<Texture> ownedTextures = new ArrayList<>();

    private float x = 200f, y = 120f;
    private float speed = 160f;
    private boolean facingRight = true;

    @Override
    public void create() {
        batch = new SpriteBatch();
        stateTime = 0f;

        loadAnimationsFromFolder(ASSET_FOLDER);

        if (animations.containsKey("Idle")) currentAnim = animations.get("Idle");
        else if (animations.containsKey("idle")) currentAnim = animations.get("idle");
        else if (!animations.isEmpty()) currentAnim = animations.values().iterator().next();
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
            animations.put(e.getKey(), anim);
        }

        System.out.println("Loaded animations: " + animations.keySet());
    }

    @Override
    public void render() {
        float delta = Gdx.graphics.getDeltaTime();
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
        stateTime += delta;

        boolean left = Gdx.input.isKeyPressed(Keys.LEFT) || Gdx.input.isKeyPressed(Keys.A);
        boolean right = Gdx.input.isKeyPressed(Keys.RIGHT) || Gdx.input.isKeyPressed(Keys.D);

        if (left && !right) {
            x -= speed * delta;
            facingRight = false;
            pickAnimation("Run");
            pickAnimation("run");
        } else if (right && !left) {
            x += speed * delta;
            facingRight = true;
            pickAnimation("Run");
            pickAnimation("run");
        } else {
            pickAnimation("Idle");
            pickAnimation("idle");
        }

        if (currentAnim == null) return;

        TextureRegion frame = currentAnim.getKeyFrame(stateTime, true);

        batch.begin();
        if (facingRight) {
            batch.draw(frame, x, y);
        } else {
            batch.draw(frame, x + frame.getRegionWidth(), y,
                    0, 0, frame.getRegionWidth(), frame.getRegionHeight(), -1f, 1f, 0f);
        }
        batch.end();
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
        for (Texture t : ownedTextures) t.dispose();
    }
}
