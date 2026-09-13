package com.lucasdss.ftpmusic.app.playback

import com.lucasdss.ftpmusic.app.data.cache.CacheService
import io.netty.bootstrap.ServerBootstrap
import io.netty.buffer.Unpooled
import io.netty.channel.*
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.nio.NioServerSocketChannel
import io.netty.handler.codec.http.*
import java.io.File
import java.io.RandomAccessFile
import java.net.InetAddress
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Local HTTP proxy for Cast tier 3: serves cached audio files to Cast receiver over LAN.
 * ExoPlayer uses its own CacheDataSource for local playback — this proxy is only for Cast.
 *
 * Binds to 127.0.0.1:9000. HTTP/1.1 with zero-copy file serving via FileRegion.
 * Cache miss → 404 (Cast receiver should use Tier 1 direct URL as fallback).
 */
@Singleton
class PlaybackProxy @Inject constructor(private val cacheService: CacheService) {
    private var channel: Channel? = null
    private var bossGroup: NioEventLoopGroup? = null
    private var workerGroup: NioEventLoopGroup? = null

    /** Single shared scope for request handling — never one scope per request.
     *  Cancelled when the proxy stops. */
    private var requestScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * Start the embedded stream proxy.
     *
     * @param listenOnAllInterfaces when true (Cast tier-3 LAN proxy, user opt-in
     *   via castFromPhone), binds 0.0.0.0 so the Cast receiver can reach the
     *   phone over the LAN — unauthenticated on the local network. The local
     *   ExoPlayer path always binds loopback only.
     */
    fun start(port: Int = 9000, listenOnAllInterfaces: Boolean = false) {
        if (channel?.isActive == true) return
        stop()

        bossGroup = NioEventLoopGroup(1)
        workerGroup = NioEventLoopGroup()

        try {
            val bootstrap = ServerBootstrap()
                .group(bossGroup, workerGroup)
                .channel(NioServerSocketChannel::class.java)
                .childHandler(object : ChannelInitializer<Channel>() {
                    override fun initChannel(ch: Channel) {
                        ch.pipeline().addLast(
                            HttpServerCodec(),
                            HttpObjectAggregator(65536),
                            ProxyRequestHandler(),
                        )
                    }
                })
                .option(ChannelOption.SO_BACKLOG, 128)
                .childOption(ChannelOption.SO_KEEPALIVE, true)

            // Default: bind loopback only — the proxy serves ExoPlayer on the
            // same device. Never expose cached music over the LAN without auth.
            // LAN binding (Cast tier-3) is an explicit, user-opted-in choice.
            val bindAddress = if (listenOnAllInterfaces) {
                null
            } else {
                InetAddress.getByName("127.0.0.1")
            }
            channel = if (bindAddress != null) {
                bootstrap.bind(bindAddress, port).sync().channel()
            } else {
                bootstrap.bind(port).sync().channel()
            }
            channel?.closeFuture()?.addListener {
                android.util.Log.w(
                    "ftpmusic-proxy",
                    "[DEBUG-pxy] proxy channel closed — will restart on next start() call",
                )
                lastPort = port
            }
            android.util.Log.d("ftpmusic-proxy", "[DEBUG-pxy] Cast proxy started on port $port")
        } catch (e: Exception) {
            android.util.Log.e("ftpmusic-proxy", "[DEBUG-pxy] proxy failed: ${e.message}", e)
            stop()
        }
    }

    private var lastPort: Int = 9000

    /** Restart the proxy if it died, or force-restart if requested.
     *  Safe to call from any thread. */
    fun restartIfNeeded() {
        if (channel?.isActive != true) {
            android.util.Log.w("ftpmusic-proxy", "[DEBUG-pxy] proxy not active — restarting on port $lastPort")
            start(lastPort)
        }
    }

    /** Force-restart the proxy. Used after sleep/resume to guarantee a fresh socket. */
    fun forceRestart() {
        android.util.Log.w("ftpmusic-proxy", "[DEBUG-pxy] force-restarting proxy on port $lastPort")
        stop()
        start(lastPort)
    }

    val isReady: Boolean get() = channel?.isActive == true

    /** Actual bound address (useful when started on an ephemeral port 0). */
    internal fun channelAddress(): java.net.InetSocketAddress? = channel?.localAddress() as? java.net.InetSocketAddress

    fun stop() {
        try {
            channel?.close()?.sync()
        } catch (_: Exception) {}
        channel = null
        try {
            bossGroup?.shutdownGracefully()
        } catch (_: Exception) {}
        try {
            workerGroup?.shutdownGracefully()
        } catch (_: Exception) {}
        bossGroup = null
        workerGroup = null
        requestScope.cancel()
        requestScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }

    // ── HTTP request handler — cache-only (Cast tier 3) ──────────────────

    /** Matches `bytes=<start>-<end>`, `bytes=<start>-`, `bytes=-<suffix>`. */
    private val rangeRegex = Regex("""^bytes=(\d*)-(\d*)$""")

    private inner class ProxyRequestHandler : SimpleChannelInboundHandler<FullHttpRequest>() {

        override fun channelRead0(ctx: ChannelHandlerContext, req: FullHttpRequest) {
            val path = req.uri()
            val params = parseParams(path)
            val trackId = params["id"]

            if (trackId == null) {
                sendError(ctx, HttpResponseStatus.BAD_REQUEST)
                return
            }

            // Parse Range header. Three shapes:
            //   bytes=100-200   bounded       → serve exactly 100..200
            //   bytes=100-      open-ended    → serve 100..EOF (Cast default)
            //   bytes=-500      suffix        → serve the LAST 500 bytes
            // Anything else (multi-range, garbage) → 416 Range Not Satisfiable.
            var rangeStart: Long = 0
            var rangeEnd: Long = Long.MAX_VALUE
            var suffixLength: Long? = null
            val rangeValue = req.headers().get(HttpHeaderNames.RANGE)
            if (rangeValue != null) {
                val m = rangeRegex.matchEntire(rangeValue.trim())
                if (m == null) {
                    sendError(ctx, HttpResponseStatus.REQUESTED_RANGE_NOT_SATISFIABLE)
                    return
                }
                val startStr = m.groupValues[1]
                val endStr = m.groupValues[2]
                when {
                    startStr.isEmpty() && endStr.isEmpty() ->
                        sendError(ctx, HttpResponseStatus.REQUESTED_RANGE_NOT_SATISFIABLE)

                    startStr.isEmpty() -> suffixLength = endStr.toLong()

                    else -> {
                        rangeStart = startStr.toLong()
                        rangeEnd = if (endStr.isEmpty()) Long.MAX_VALUE else endStr.toLong()
                    }
                }
            }

            // Check cache — serve file if found, 404 otherwise
            requestScope.launch {
                val cachedPath = cacheService.getCachedPath(trackId)

                if (cachedPath != null) {
                    val cachedFile = File(cachedPath)
                    if (cachedFile.exists() && cachedFile.length() > 0) {
                        val fileLen = cachedFile.length()
                        // Resolve the effective byte window against the actual file.
                        var start = rangeStart
                        var end: Long
                        if (suffixLength != null) {
                            val suffix = suffixLength!!.coerceAtMost(fileLen)
                            start = fileLen - suffix
                            end = fileLen - 1
                        } else {
                            end = if (rangeEnd == Long.MAX_VALUE) fileLen - 1 else rangeEnd.coerceAtMost(fileLen - 1)
                        }
                        if (start >= fileLen || start > end) {
                            // Range beyond EOF — 416 with Content-Range: bytes */<size>
                            ctx.channel().eventLoop().execute {
                                val resp = DefaultFullHttpResponse(
                                    HttpVersion.HTTP_1_1,
                                    HttpResponseStatus.REQUESTED_RANGE_NOT_SATISFIABLE,
                                )
                                resp.headers().set(HttpHeaderNames.CONTENT_LENGTH, 0)
                                resp.headers().set(HttpHeaderNames.CONTENT_RANGE, "bytes */$fileLen")
                                ctx.writeAndFlush(resp)
                            }
                            return@launch
                        }
                        val type = resolveContentType(trackId, cachedFile)
                        android.util.Log.d(
                            "ftpmusic-proxy",
                            "[DEBUG-pxy] cache hit: $trackId (${fileLen}B, $start-$end) type=$type",
                        )
                        ctx.channel().eventLoop().execute {
                            sendFileResponse(ctx, cachedFile, fileLen, start, end, type)
                        }
                        return@launch
                    }
                }

                // Cache miss — return 404 (Cast receiver should fall back to Tier 1 direct URL)
                android.util.Log.d("ftpmusic-proxy", "[DEBUG-pxy] cache miss: $trackId — returning 404")
                ctx.channel().eventLoop().execute { sendError(ctx, HttpResponseStatus.NOT_FOUND) }
            }
        }

        override fun exceptionCaught(ctx: ChannelHandlerContext, cause: Throwable) {
            android.util.Log.e("ftpmusic-proxy", "[DEBUG-pxy] error: ${cause.message}")
            ctx.close()
        }
    }

    // ── Response helpers ──────────────────────────────────────────────

    /** Serve the inclusive byte window rangeStart..rangeEnd of [file] with
     *  zero-copy transfer. The RandomAccessFile is closed once the write future
     *  completes (the DefaultFileRegion also releases its channel). */
    private fun sendFileResponse(
        ctx: ChannelHandlerContext,
        file: File,
        totalSize: Long,
        rangeStart: Long,
        rangeEnd: Long,
        contentType: String,
    ) {
        var raf: RandomAccessFile? = null
        try {
            raf = RandomAccessFile(file, "r")
            val fileChannel = raf.channel
            val contentLength = rangeEnd - rangeStart + 1
            val isPartial = rangeStart > 0 || rangeEnd < totalSize - 1

            val resp = DefaultHttpResponse(
                HttpVersion.HTTP_1_1,
                if (isPartial) HttpResponseStatus.PARTIAL_CONTENT else HttpResponseStatus.OK,
            )
            resp.headers().set(HttpHeaderNames.CONTENT_TYPE, contentType)
            resp.headers().set(HttpHeaderNames.CONTENT_LENGTH, contentLength)
            resp.headers().set(HttpHeaderNames.ACCEPT_RANGES, "bytes")
            if (isPartial) {
                resp.headers().set(HttpHeaderNames.CONTENT_RANGE, "bytes $rangeStart-$rangeEnd/$totalSize")
            }

            ctx.write(resp)
            // Zero-copy file transfer from disk to socket
            val region = io.netty.channel.DefaultFileRegion(fileChannel, rangeStart, contentLength)
            val rafToClose = raf
            ctx.write(region).addListener(ChannelFutureListener { rafToClose.close() })
            ctx.writeAndFlush(io.netty.handler.codec.http.LastHttpContent.EMPTY_LAST_CONTENT)
            raf = null // ownership transferred to the listener
        } catch (_: Exception) {
            try {
                raf?.close()
            } catch (_: Exception) {}
            sendError(ctx, HttpResponseStatus.INTERNAL_SERVER_ERROR)
        }
    }

    private fun sendError(ctx: ChannelHandlerContext, status: HttpResponseStatus) {
        val resp = DefaultFullHttpResponse(HttpVersion.HTTP_1_1, status)
        resp.headers().set(HttpHeaderNames.CONTENT_LENGTH, 0)
        ctx.writeAndFlush(resp)
    }

    private fun parseParams(path: String): Map<String, String> {
        val q = path.indexOf('?')
        if (q < 0) return emptyMap()
        return path.substring(q + 1).split("&").associate {
            val kv = it.split("=", limit = 2)
            if (kv.size == 2) kv[0] to java.net.URLDecoder.decode(kv[1], "UTF-8") else kv[0] to ""
        }
    }

    /** Resolve Content-Type for a cached track. SimpleCache span filenames carry no
     *  format hints, so prefer the track's format from Room metadata, then fall back
     *  to magic-byte sniffing of the file header. */
    private suspend fun resolveContentType(trackId: String, file: File): String {
        val entity = try {
            cacheService.getTrackEntity(trackId)
        } catch (_: Exception) {
            null
        }
        entity?.contentType?.takeIf { it.isNotBlank() }?.let { return it }
        entity?.suffix?.takeIf { it.isNotBlank() }?.let { suffix ->
            contentTypeFromName(".${suffix.lowercase()}")?.let { return it }
        }
        return detectContentTypeFromBytes(readFileHeader(file)) ?: "audio/mpeg"
    }

    private fun readFileHeader(file: File): ByteArray = try {
        java.io.FileInputStream(file).use { input ->
            val buf = ByteArray(12)
            val read = input.read(buf)
            if (read > 0) buf.copyOf(read) else ByteArray(0)
        }
    } catch (_: Exception) {
        ByteArray(0)
    }

    /** Magic-byte content sniffing for files with no format hint in name or metadata. */
    private fun detectContentTypeFromBytes(data: ByteArray): String? {
        if (data.size < 4) return null
        // FLAC: "fLaC"
        if (data[0] == 0x66.toByte() && data[1] == 0x4C.toByte() &&
            data[2] == 0x61.toByte() && data[3] == 0x43.toByte()
        ) {
            return "audio/flac"
        }
        // OGG: "OggS"
        if (data[0] == 0x4F.toByte() && data[1] == 0x67.toByte() &&
            data[2] == 0x67.toByte() && data[3] == 0x53.toByte()
        ) {
            return "audio/ogg"
        }
        // WAV: "RIFF"
        if (data[0] == 0x52.toByte() && data[1] == 0x49.toByte() &&
            data[2] == 0x46.toByte() && data[3] == 0x46.toByte()
        ) {
            return "audio/wav"
        }
        // MP4/M4A: ftyp box at offset 4
        if (data.size >= 12 && data[4] == 0x66.toByte() && data[5] == 0x74.toByte() &&
            data[6] == 0x79.toByte() && data[7] == 0x70.toByte()
        ) {
            return "audio/mp4"
        }
        // ID3 tag for MP3
        if (data[0] == 0x49.toByte() && data[1] == 0x44.toByte() && data[2] == 0x33.toByte()) {
            return "audio/mpeg"
        }
        return null
    }

    private fun contentTypeFromName(fileName: String): String? = when {
        fileName.contains(".mp3", true) || fileName.contains("format=mp3") -> "audio/mpeg"
        fileName.contains(".ogg", true) || fileName.contains("format=ogg") -> "audio/ogg"
        fileName.contains(".opus", true) || fileName.contains("format=opus") -> "audio/ogg;codecs=opus"
        fileName.contains(".flac", true) || fileName.contains("format=flac") -> "audio/flac"
        fileName.contains(".aac", true) || fileName.contains("format=aac") -> "audio/aac"
        fileName.contains(".m4a", true) || fileName.contains("format=m4a") -> "audio/mp4"
        fileName.contains(".wav", true) || fileName.contains("format=wav") -> "audio/wav"
        else -> null
    }
}
