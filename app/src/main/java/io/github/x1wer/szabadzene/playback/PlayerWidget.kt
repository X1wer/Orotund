package io.github.x1wer.szabadzene.playback

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.view.KeyEvent
import android.view.View
import android.widget.RemoteViews
import androidx.annotation.OptIn
import androidx.core.content.edit
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaButtonReceiver
import io.github.x1wer.szabadzene.MainActivity
import io.github.x1wer.szabadzene.R
import io.github.x1wer.szabadzene.data.settings.SkipSettings

/**
 * Kezdőképernyős widget: balra a borító, jobbra felül a gombok (a két szélen ±N másodperces ugrás,
 * köztük előző, lejátszás/szünet, következő), alattuk a szám címe, alatta kisebben a következő számé.
 *
 * Az állapotot mindig a lejátszószolgáltatás küldi (ugyanabból a lejátszóból, amit az app és az
 * értesítés is mutat), és el is menti, hogy a rendszer által kért újrarajzolás is ugyanazt mutassa.
 * A gombok ide (saját, nem exportált fogadó) érkeznek: futó szolgáltatásnál közvetlenül a lejátszót
 * vezérlik; leállított appnál a lejátszógomb a Media3 `MediaButtonReceiver`-en át a mentett
 * várólistával folytat. (A `MediaButtonReceiver` Android 8 felett csak a „play” gombot fogadja.)
 */
class PlayerWidget : AppWidgetProvider() {

    data class Snapshot(
        val trackId: Long?,
        val title: String?,
        val artist: String?,
        val playing: Boolean,
        /** A következő szám címe (ismétlés és keverés szerint), ha van. */
        val nextTitle: String? = null,
        val backSeconds: Int = SkipSettings.DEFAULT_SECONDS,
        val forwardSeconds: Int = SkipSettings.DEFAULT_SECONDS,
    )

    @OptIn(UnstableApi::class)
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_WIDGET_KEY) {
            super.onReceive(context, intent)
            return
        }
        val keyCode = intent.getIntExtra(EXTRA_KEY_CODE, 0)
        if (PlaybackService.handleWidgetKey(keyCode)) return
        if (keyCode == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE) {
            // Nem fut a lejátszó: folytatás a mentett várólistával (mint a fejhallgató lejátszógombja).
            context.sendBroadcast(
                Intent(Intent.ACTION_MEDIA_BUTTON)
                    .setClass(context, MediaButtonReceiver::class.java)
                    .putExtra(Intent.EXTRA_KEY_EVENT, KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PLAY)),
            )
        }
    }

    override fun onUpdate(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) {
        // Csak akkor mutat lejátszást, ha a szolgáltatás valóban fut (különben a mentett állapot régi).
        val saved = load(context).let { it.copy(playing = it.playing && PlaybackService.isRunning) }
        render(context, manager, appWidgetIds, saved, art = null)
    }

    companion object {
        const val ART_SIZE_PX = 256
        private const val ACTION_WIDGET_KEY = "io.github.x1wer.szabadzene.WIDGET_KEY"
        private const val EXTRA_KEY_CODE = "key_code"
        private const val PREFS = "player_widget"

        fun update(context: Context, snapshot: Snapshot, art: Bitmap?) {
            save(context, snapshot)
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, PlayerWidget::class.java))
            if (ids.isNotEmpty()) render(context, manager, ids, snapshot, art)
        }

        /** A szolgáltatás leállt: a widget szünetet mutat (a szám adatai megmaradnak). */
        fun markStopped(context: Context) {
            update(context, load(context).copy(playing = false), art = null)
        }

        private fun save(context: Context, snapshot: Snapshot) {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit {
                putLong("track_id", snapshot.trackId ?: -1L)
                putString("title", snapshot.title)
                putString("artist", snapshot.artist)
                putBoolean("playing", snapshot.playing)
                putString("next_title", snapshot.nextTitle)
                putInt("back_seconds", snapshot.backSeconds)
                putInt("forward_seconds", snapshot.forwardSeconds)
            }
        }

        private fun load(context: Context): Snapshot {
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            return Snapshot(
                trackId = prefs.getLong("track_id", -1L).takeIf { it >= 0 },
                title = prefs.getString("title", null),
                artist = prefs.getString("artist", null),
                playing = prefs.getBoolean("playing", false),
                nextTitle = prefs.getString("next_title", null),
                backSeconds = SkipSettings.sanitize(prefs.getInt("back_seconds", SkipSettings.DEFAULT_SECONDS)),
                forwardSeconds = SkipSettings.sanitize(prefs.getInt("forward_seconds", SkipSettings.DEFAULT_SECONDS)),
            )
        }

        private fun render(
            context: Context,
            manager: AppWidgetManager,
            ids: IntArray,
            snapshot: Snapshot,
            art: Bitmap?,
        ) {
            val views = RemoteViews(context.packageName, R.layout.widget_player)
            val hasTrack = snapshot.trackId != null && !snapshot.title.isNullOrBlank()
            views.setTextViewText(
                R.id.widget_title,
                if (hasTrack) snapshot.title else context.getString(R.string.player_nothing_loaded),
            )
            val next = snapshot.nextTitle?.takeIf { hasTrack && it.isNotBlank() }
            views.setTextViewText(R.id.widget_next_title, next?.let { context.getString(R.string.widget_next, it) }.orEmpty())
            views.setViewVisibility(R.id.widget_next_title, if (next != null) View.VISIBLE else View.GONE)
            views.setTextViewText(R.id.widget_skip_back_label, snapshot.backSeconds.toString())
            views.setTextViewText(R.id.widget_skip_forward_label, snapshot.forwardSeconds.toString())
            if (art != null) {
                views.setImageViewBitmap(R.id.widget_art, art)
            } else {
                views.setImageViewResource(R.id.widget_art, R.drawable.ic_widget_art_placeholder)
            }
            views.setImageViewResource(
                R.id.widget_play_pause,
                if (snapshot.playing) R.drawable.ic_widget_pause else R.drawable.ic_widget_play,
            )
            views.setContentDescription(
                R.id.widget_play_pause,
                context.getString(if (snapshot.playing) R.string.cd_pause else R.string.cd_play),
            )
            // Minden felirat az app nyelvén (a rétegfájl szövegeit a kezdőképernyő a rendszer nyelvén oldaná fel).
            views.setContentDescription(R.id.widget_previous, context.getString(R.string.cd_previous))
            views.setContentDescription(R.id.widget_next, context.getString(R.string.cd_next))
            views.setContentDescription(R.id.widget_skip_back, context.getString(R.string.cd_skip_back, snapshot.backSeconds))
            views.setContentDescription(R.id.widget_skip_forward, context.getString(R.string.cd_skip_forward, snapshot.forwardSeconds))
            views.setOnClickPendingIntent(R.id.widget_play_pause, mediaKey(context, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE))
            views.setOnClickPendingIntent(R.id.widget_previous, mediaKey(context, KeyEvent.KEYCODE_MEDIA_PREVIOUS))
            views.setOnClickPendingIntent(R.id.widget_next, mediaKey(context, KeyEvent.KEYCODE_MEDIA_NEXT))
            views.setOnClickPendingIntent(R.id.widget_skip_back, mediaKey(context, KeyEvent.KEYCODE_MEDIA_REWIND))
            views.setOnClickPendingIntent(R.id.widget_skip_forward, mediaKey(context, KeyEvent.KEYCODE_MEDIA_FAST_FORWARD))
            views.setOnClickPendingIntent(R.id.widget_art, openApp(context))
            views.setOnClickPendingIntent(R.id.widget_texts, openApp(context))
            manager.updateAppWidget(ids, views)
        }

        private fun mediaKey(context: Context, keyCode: Int): PendingIntent {
            val intent = Intent(context, PlayerWidget::class.java)
                .setAction(ACTION_WIDGET_KEY)
                .putExtra(EXTRA_KEY_CODE, keyCode)
            return PendingIntent.getBroadcast(
                context,
                keyCode,
                intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
        }

        private fun openApp(context: Context): PendingIntent {
            val intent = Intent(context, MainActivity::class.java)
                .putExtra(MainActivity.EXTRA_OPEN_ORIGIN, true)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            return PendingIntent.getActivity(
                context,
                1,
                intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
        }
    }
}
