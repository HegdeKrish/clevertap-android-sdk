package com.clevertap.android.pushtemplates.styles

import android.app.PendingIntent
import android.content.Context
import android.os.Bundle
import android.widget.RemoteViews
import com.clevertap.android.pushtemplates.TemplateRenderer
import com.clevertap.android.pushtemplates.content.BASIC_CONTENT_PENDING_INTENT
import com.clevertap.android.pushtemplates.content.BigImageContentView
import com.clevertap.android.pushtemplates.content.PendingIntentFactory
import com.clevertap.android.pushtemplates.content.SmallContentView

class BasicStyle(private var renderer: TemplateRenderer) : Style(renderer) {

    override fun makeSmallContentView(context: Context, renderer: TemplateRenderer): RemoteViews {
        return SmallContentView(context, renderer).remoteView
    }

    override fun makeBigContentView(context: Context, renderer: TemplateRenderer): RemoteViews {
        return BigImageContentView(context, renderer).remoteView
    }

    override fun makePendingIntent(context: Context, extras: Bundle, notificationId: Int): PendingIntent? {
        return PendingIntentFactory.getPendingIntent(
            context, notificationId, extras, true,
            BASIC_CONTENT_PENDING_INTENT, renderer
        )
    }

    override fun makeDismissIntent(context: Context, extras: Bundle, notificationId: Int): PendingIntent? {
        return null
    }
}