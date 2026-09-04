package com.netwatch.android

import android.graphics.BitmapFactory
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.Image
import androidx.compose.ui.text.font.FontWeight
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

private object GatewayArtworkCache {
    val images = ConcurrentHashMap<String, ImageBitmap>()
    private val clients = ConcurrentHashMap<String, PinnedGatewayClient>()

    fun client(profile: GatewayProfile): PinnedGatewayClient = clients.computeIfAbsent("${profile.host}:${profile.port}:${profile.spkiSha256}") {
        PinnedGatewayClient.forProfile(profile)
    }

    fun put(key: String, image: ImageBitmap) {
        if (images.size >= 48) images.keys.take(12).forEach(images::remove)
        images[key] = image
    }
}

@Composable
fun GatewayArtwork(
    path: String?,
    profile: GatewayProfile,
    modifier: Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    showFallbackMark: Boolean = true,
) {
    val key = "${profile.host}:${profile.port}:$path"
    val bitmap by produceState<ImageBitmap?>(GatewayArtworkCache.images[key], key) {
        if (value == null && path != null) value = withContext(Dispatchers.IO) {
            runCatching {
                val bytes = GatewayArtworkCache.client(profile).getBytes(path)
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()?.also { GatewayArtworkCache.put(key, it) }
            }.getOrNull()
        }
    }
    if (bitmap != null) {
        Image(bitmap!!, null, modifier, contentScale = contentScale)
    } else {
        Box(
            modifier.background(Brush.linearGradient(listOf(Color(0xFF15151C), Color(0xFF1D1729)))),
            contentAlignment = Alignment.Center,
        ) {
            if (showFallbackMark) Text("NW", color = Color.White.copy(.18f), fontWeight = FontWeight.Bold)
        }
    }
}
