package com.gathin.tvhome;

import android.content.ComponentName;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Outline;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.List;

/**
 * Theme key "now_playing": a frosted panel under the clock with the track another app is playing (Spotify,
 * YouTube Music, ...) and previous, play/pause and next. Event-driven: it listens only between onResume and
 * onPause, and draws nothing when no session is playing or paused, or when NowPlayingListener is not approved.
 */
final class NowPlaying implements MediaSessionManager.OnActiveSessionsChangedListener {
    private final Context context;
    private final MediaSessionManager sessions;
    private final ComponentName listener;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final View anchor;
    private final Runnable focusAway;
    private final float density;
    private final LinearLayout panel;
    private final ImageView art;
    private final TextView title;
    private final TextView artist;
    private final ImageView play;
    private MediaController controller;
    private boolean listening;

    private final MediaController.Callback callback = new MediaController.Callback() {
        @Override public void onMetadataChanged(MediaMetadata metadata) { render(); }
        @Override public void onPlaybackStateChanged(PlaybackState state) { render(); }
        @Override public void onSessionDestroyed() { bind(null); }
    };

    /** anchor: the clock block the panel sits under; focusAway moves focus off the panel when it hides. */
    NowPlaying(Context context, FrameLayout root, View anchor, Typeface font, Runnable focusAway) {
        this.context = context;
        this.anchor = anchor;
        this.focusAway = focusAway;
        density = context.getResources().getDisplayMetrics().density;
        sessions = context.getSystemService(MediaSessionManager.class);
        listener = new ComponentName(context, NowPlayingListener.class);

        panel = new LinearLayout(context);
        panel.setOrientation(LinearLayout.HORIZONTAL);
        panel.setGravity(Gravity.CENTER_VERTICAL);
        GradientDrawable glass = new GradientDrawable();
        glass.setCornerRadius(dp(18));
        glass.setColor(0x26FFFFFF);
        glass.setStroke(Math.max(1, Math.round(density)), 0x40FFFFFF);
        panel.setBackground(glass);
        panel.setPadding(dp(6), dp(6), dp(10), dp(6));
        panel.setVisibility(View.GONE);

        art = new ImageView(context);
        art.setScaleType(ImageView.ScaleType.CENTER_CROP);
        art.setOutlineProvider(new ViewOutlineProvider() {
            @Override public void getOutline(View view, Outline outline) {
                outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), dp(10));
            }
        });
        art.setClipToOutline(true);
        art.setBackgroundColor(0x33FFFFFF);
        panel.addView(art, new LinearLayout.LayoutParams(dp(40), dp(40)));

        LinearLayout texts = new LinearLayout(context);
        texts.setOrientation(LinearLayout.VERTICAL);
        title = line(font, 14f, 0xFFFFFFFF);
        artist = line(font, 12f, 0xB3FFFFFF);
        texts.addView(title);
        texts.addView(artist);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(dp(200), ViewGroup.LayoutParams.WRAP_CONTENT);
        tlp.setMarginStart(dp(12));
        tlp.setMarginEnd(dp(10));
        panel.addView(texts, tlp);

        panel.addView(button(R.drawable.ic_np_prev, v -> {
            if (controller != null) controller.getTransportControls().skipToPrevious();
        }));
        play = button(R.drawable.ic_np_play, v -> {
            if (controller == null) return;
            if (playing(controller.getPlaybackState())) controller.getTransportControls().pause();
            else controller.getTransportControls().play();
        });
        panel.addView(play);
        panel.addView(button(R.drawable.ic_np_next, v -> {
            if (controller != null) controller.getTransportControls().skipToNext();
        }));

        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        root.addView(panel, lp);
        // Under the clock block, wherever the theme put it.
        anchor.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> place(root));
    }

    void start() {
        if (sessions == null || listening) return;
        try {
            sessions.addOnActiveSessionsChangedListener(this, listener, main);
            listening = true;
            onActiveSessionsChanged(sessions.getActiveSessions(listener));
        } catch (SecurityException notApproved) {
            listening = false;
            bind(null);
        }
    }

    void stop() {
        if (listening) sessions.removeOnActiveSessionsChangedListener(this);
        listening = false;
        bind(null);
    }

    @Override
    public void onActiveSessionsChanged(List<MediaController> controllers) {
        MediaController best = null;
        if (controllers != null) {
            for (MediaController c : controllers) {
                if (playing(c.getPlaybackState())) { best = c; break; }
                if (best == null && c.getMetadata() != null && paused(c.getPlaybackState())) best = c;
            }
        }
        bind(best);
    }

    private void bind(MediaController next) {
        if (controller != null && (next == null || !controller.getSessionToken().equals(next.getSessionToken()))) {
            controller.unregisterCallback(callback);
        } else if (controller != null) {
            render();
            return;
        }
        controller = next;
        if (controller != null) controller.registerCallback(callback, main);
        render();
    }

    private void render() {
        MediaMetadata meta = controller == null ? null : controller.getMetadata();
        PlaybackState state = controller == null ? null : controller.getPlaybackState();
        String name = meta == null ? null : text(meta, MediaMetadata.METADATA_KEY_TITLE,
                MediaMetadata.METADATA_KEY_DISPLAY_TITLE);
        if (TextUtils.isEmpty(name) || !(playing(state) || paused(state))) {
            if (panel.hasFocus()) focusAway.run();
            panel.setVisibility(View.GONE);
            art.setImageDrawable(null);
            return;
        }
        title.setText(name);
        String by = text(meta, MediaMetadata.METADATA_KEY_ARTIST, MediaMetadata.METADATA_KEY_ALBUM_ARTIST,
                MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE);
        artist.setText(by);
        artist.setVisibility(TextUtils.isEmpty(by) ? View.GONE : View.VISIBLE);
        Bitmap cover = meta.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART);
        if (cover == null) cover = meta.getBitmap(MediaMetadata.METADATA_KEY_ART);
        if (cover == null) cover = meta.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON);
        art.setImageBitmap(cover);
        play.setImageResource(playing(state) ? R.drawable.ic_np_pause : R.drawable.ic_np_play);
        panel.setVisibility(View.VISIBLE);
    }

    private void place(FrameLayout root) {
        int[] a = new int[2], r = new int[2];
        anchor.getLocationInWindow(a);
        root.getLocationInWindow(r);
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) panel.getLayoutParams();
        int top = a[1] - r[1] + anchor.getHeight() + dp(12);
        if (lp.topMargin != top) {
            lp.topMargin = top;
            panel.setLayoutParams(lp);
        }
    }

    private TextView line(Typeface font, float sp, int color) {
        TextView t = new TextView(context);
        t.setTextSize(sp);
        t.setTextColor(color);
        if (font != null) t.setTypeface(font);
        t.setSingleLine(true);
        t.setEllipsize(TextUtils.TruncateAt.END);
        return t;
    }

    private ImageView button(int icon, View.OnClickListener click) {
        ImageView b = new ImageView(context);
        b.setImageResource(icon);
        b.setScaleType(ImageView.ScaleType.CENTER);
        b.setFocusable(true);
        b.setClickable(true);
        b.setOnClickListener(click);
        GradientDrawable ring = new GradientDrawable();
        ring.setShape(GradientDrawable.OVAL);
        ring.setColor(0x00FFFFFF);
        b.setBackground(ring);
        b.setOnFocusChangeListener((v, focused) -> {
            ring.setColor(focused ? 0x4DFFFFFF : 0x00FFFFFF);
            v.animate().scaleX(focused ? 1.15f : 1f).scaleY(focused ? 1.15f : 1f).setDuration(150).start();
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(38), dp(38));
        lp.setMarginStart(dp(2));
        b.setLayoutParams(lp);
        return b;
    }

    private static String text(MediaMetadata meta, String... keys) {
        for (String key : keys) {
            CharSequence value = meta.getText(key);
            if (!TextUtils.isEmpty(value)) return value.toString();
        }
        return null;
    }

    private static boolean playing(PlaybackState s) {
        return s != null && (s.getState() == PlaybackState.STATE_PLAYING
                || s.getState() == PlaybackState.STATE_BUFFERING);
    }

    private static boolean paused(PlaybackState s) {
        return s != null && s.getState() == PlaybackState.STATE_PAUSED;
    }

    private int dp(float v) {
        return Math.round(v * density);
    }
}
