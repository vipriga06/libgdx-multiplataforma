package com.mygdx.game;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.utils.ScreenUtils;
import com.badlogic.gdx.utils.viewport.FitViewport;

public class Joc extends ApplicationAdapter {
    private SpriteBatch batch;
    public FitViewport viewport;
    private Texture ballTexture;
    private float posx, posy, velx, vely;
    private float ballSize;

    @Override
    public void create() {
        batch = new SpriteBatch();
        viewport = new FitViewport(8, 5); // 8x5 virtual units
        updateBallSize();
        createBallTexture();
        // Initial position and velocity
        posx = viewport.getWorldWidth() / 2f - ballSize / 2f;
        posy = viewport.getWorldHeight() / 2f - ballSize / 2f;
        velx = 1f;
        vely = 1f;
        System.out.println("La pilota ha estat creada!");
    }

    private void updateBallSize() {
        // La pelota será el 10% del menor lado de la ventana
        ballSize = Math.min(viewport.getWorldWidth(), viewport.getWorldHeight()) * 0.1f;
    }

    private void createBallTexture() {
        int pixelSize = 100;
        Pixmap ballPixmap = new Pixmap(pixelSize, pixelSize, Pixmap.Format.RGBA8888);
        ballPixmap.setColor(Color.RED);
        ballPixmap.fillCircle(pixelSize / 2, pixelSize / 2, pixelSize / 2);
        if (ballTexture != null) ballTexture.dispose();
        ballTexture = new Texture(ballPixmap);
        ballPixmap.dispose();
    }

    @Override
    public void render() {
        // Clear background
        ScreenUtils.clear(0.15f, 0.15f, 0.2f, 1f);

        // Apply viewport
        viewport.apply();
        batch.setProjectionMatrix(viewport.getCamera().combined);

        // Move ball
        float delta = Gdx.graphics.getDeltaTime();
        posx += velx * delta;
        posy += vely * delta;

        // Obtener límites reales de rebote
        float windowWidth = viewport.getCamera().viewportWidth;
        float windowHeight = viewport.getCamera().viewportHeight;
        float bordeIzq = 0f;
        float bordeDer = windowWidth - ballSize;
        float bordeAbajo = 0f;
        float bordeArriba = windowHeight - ballSize;

        // Bounce logic usando el tamaño real de la ventana
        if (posx <= bordeIzq) {
            posx = bordeIzq;
            velx *= -1;
            System.out.printf("[REBOTE X-] Pelota: %.3f | Limite: %.3f\n", posx, bordeIzq);
        } else if (posx >= bordeDer) {
            posx = bordeDer;
            velx *= -1;
            System.out.printf("[REBOTE X+] Pelota: %.3f | Limite: %.3f\n", posx, bordeDer);
        }
        if (posy <= bordeAbajo) {
            posy = bordeAbajo;
            vely *= -1;
            System.out.printf("[REBOTE Y-] Pelota: %.3f | Limite: %.3f\n", posy, bordeAbajo);
        } else if (posy >= bordeArriba) {
            posy = bordeArriba;
            vely *= -1;
            System.out.printf("[REBOTE Y+] Pelota: %.3f | Limite: %.3f\n", posy, bordeArriba);
        }

        // Draw ball
        batch.begin();
        batch.draw(ballTexture, posx, posy, ballSize, ballSize);
        batch.end();
    }

    @Override
    public void resize(int width, int height) {
        viewport.update(width, height, true);
        updateBallSize();
        createBallTexture();
        // Recentrar la pelota si está fuera de los nuevos límites
        float windowWidth = viewport.getCamera().viewportWidth;
        float windowHeight = viewport.getCamera().viewportHeight;
        if (posx + ballSize > windowWidth) posx = windowWidth - ballSize;
        if (posy + ballSize > windowHeight) posy = windowHeight - ballSize;
        System.out.printf("[RESIZE] Nueva ventana: %dx%d | Limites: X[0, %.3f] Y[0, %.3f] Tamaño pelota: %.3f%n",
            width, height, windowWidth, windowHeight, ballSize);
    }

    @Override
    public void dispose() {
        batch.dispose();
        ballTexture.dispose();
        System.out.println("Recursos alliberats!");
    }
}
