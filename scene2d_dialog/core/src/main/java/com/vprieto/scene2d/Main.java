package com.vprieto.scene2d;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.actions.Actions;
import com.badlogic.gdx.scenes.scene2d.ui.Dialog;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.ProgressBar;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.ScreenUtils;
import com.badlogic.gdx.utils.Timer;
import com.badlogic.gdx.utils.viewport.FitViewport;

/** {@link com.badlogic.gdx.ApplicationListener} implementation shared by all platforms. */
public class Main extends ApplicationAdapter {
    private Stage stage;
    private Skin skin;
    private LaunchDialog launchDialog;

    @Override
    public void create() {
        stage = new Stage(new FitViewport(640, 480));
        skin = new Skin(Gdx.files.internal("ui/uiskin.json"));
        launchDialog = new LaunchDialog(skin);

        final Dialog info = new Dialog("Mission Console", skin, "border");
        info.getContentTable().defaults().pad(6f);
        info.text("Open the custom dialog and let the timer run.");
        final TextButton button = new TextButton("Open Launch Dialog", skin);
        button.pad(8f);
        button.addListener(new ChangeListener() {
            @Override
            public void changed(final ChangeEvent event, final Actor actor) {
                launchDialog.resetAndShow(stage);
            }
        });
        info.getContentTable().row();
        info.getContentTable().add(button).center();
        info.pack();
        // We round the window position to avoid awkward half-pixel artifacts.
        // Casting using (int) would also work.
        info.setPosition(MathUtils.roundPositive(stage.getWidth() / 2f - info.getWidth() / 2f),
            MathUtils.roundPositive(stage.getHeight() / 2f - info.getHeight() / 2f));
        info.addAction(Actions.sequence(Actions.alpha(0f), Actions.fadeIn(0.7f)));
        stage.addActor(info);

        Gdx.input.setInputProcessor(stage);
    }

    @Override
    public void render() {
        ScreenUtils.clear(0f, 0f, 0f, 1f);
        stage.act(Gdx.graphics.getDeltaTime());
        stage.draw();
    }

    @Override
    public void resize(int width, int height) {
        // If the window is minimized on a desktop (LWJGL3) platform, width and height are 0, which causes problems.
        // In that case, we don't resize anything, and wait for the window to be a normal size before updating.
        if(width <= 0 || height <= 0) return;

        stage.getViewport().update(width, height);
    }

    @Override
    public void dispose() {
        launchDialog.dispose();
        stage.dispose();
        skin.dispose();
    }

    private final class LaunchDialog extends Dialog {
        private static final int START_SECONDS = 5;

        private final Label countdownLabel;
        private final ProgressBar progressBar;
        private Timer.Task countdownTask;
        private int secondsLeft;

        LaunchDialog(Skin skin) {
            super("Launch Sequence", skin, "border");

            getContentTable().defaults().pad(6f);
            text("Reactor will auto-launch unless you abort.");

            countdownLabel = new Label("T-" + START_SECONDS, skin);
            countdownLabel.setAlignment(Align.center);

            progressBar = new ProgressBar(0f, START_SECONDS, 1f, false, skin);
            progressBar.setAnimateDuration(0.15f);
            progressBar.setValue(0f);

            getContentTable().row();
            getContentTable().add(countdownLabel).growX();
            getContentTable().row();
            getContentTable().add(progressBar).width(280f).padBottom(8f);

            button("Abort", false);
            button("Launch now", true);

            key(com.badlogic.gdx.Input.Keys.ESCAPE, false);
            key(com.badlogic.gdx.Input.Keys.ENTER, true);
        }

        void resetAndShow(Stage stage) {
            cancelCountdown();
            secondsLeft = START_SECONDS;
            progressBar.setValue(0f);
            countdownLabel.setText("T-" + secondsLeft);

            show(stage);
            pack();
            setPosition(
                MathUtils.roundPositive(stage.getWidth() / 2f - getWidth() / 2f),
                MathUtils.roundPositive(stage.getHeight() / 2f - getHeight() / 2f)
            );

            addAction(Actions.sequence(
                Actions.alpha(0f),
                Actions.moveBy(0f, 60f),
                Actions.parallel(Actions.fadeIn(0.35f), Actions.moveBy(0f, -60f, 0.35f))
            ));

            countdownTask = Timer.schedule(new Timer.Task() {
                @Override
                public void run() {
                    secondsLeft--;
                    progressBar.setValue(START_SECONDS - secondsLeft);
                    countdownLabel.setText("T-" + Math.max(secondsLeft, 0));

                    if (secondsLeft <= 0) {
                        cancelCountdown();
                        result(Boolean.TRUE);
                        hide();
                    }
                }
            }, 1f, 1f);
        }

        @Override
        protected void result(Object object) {
            cancelCountdown();
            boolean launched = Boolean.TRUE.equals(object);

            if (launched) {
                stage.addAction(Actions.sequence(
                    Actions.run(() -> Gdx.app.log("Dialog", "Launch confirmed")),
                    Actions.repeat(6, Actions.sequence(
                        Actions.moveBy(MathUtils.random(-6f, 6f), MathUtils.random(-3f, 3f), 0.03f),
                        Actions.moveBy(MathUtils.random(-6f, 6f), MathUtils.random(-3f, 3f), 0.03f)
                    )),
                    Actions.run(() -> {
                        Dialog done = new Dialog("Status", skin, "border");
                        done.text("Launch successful. System stable.");
                        done.button("Nice", true);
                        done.show(stage);
                    })
                ));
            } else {
                Gdx.app.log("Dialog", "Launch aborted");
            }
        }

        private void cancelCountdown() {
            if (countdownTask != null) {
                countdownTask.cancel();
                countdownTask = null;
            }
        }

        void dispose() {
            cancelCountdown();
        }
    }
}