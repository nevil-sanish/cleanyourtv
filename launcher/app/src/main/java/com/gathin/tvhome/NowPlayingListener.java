package com.gathin.tvhome;

import android.service.notification.NotificationListenerService;

/**
 * Lets the now_playing panel read other apps' media sessions; Android only shares them with an approved
 * notification listener. No notification is read. Enable with
 * {@code cmd notification allow_listener com.gathin.tvhome/.NowPlayingListener}.
 */
public class NowPlayingListener extends NotificationListenerService {
}
