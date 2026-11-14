package com.cooper.binge.companion.jellyfin.demo

import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer

private const val JSON = "application/json; charset=UTF-8"
private const val HTTP_ERROR_MIN = 400

/**
 * Answers every call to the demo server from [model], before the network: no socket is opened and nothing leaves
 * the device. A call to any other host passes through untouched, so this sits in the one client and is inert unless
 * the saved server is the demo.
 *
 * It is the whole demo: the screens and the exported Service call the same API functions as against a real server,
 * and read what this answers.
 */
internal class DemoTransport(
    val model: DemoModel = DemoModel(),
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (!request.url.isDemo()) return chain.proceed(request)
        val call =
            DemoCall(
                method = request.method,
                path = request.url.encodedPath,
                query = { name -> request.url.queryParameter(name) ?: queryIgnoringCase(request.url, name) },
                readBody = { request.body?.let { body -> Buffer().also(body::writeTo).readUtf8() }.orEmpty() },
            )
        val answer = model.answer(call)
        return Response
            .Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(answer.code)
            .message(if (answer.code < HTTP_ERROR_MIN) "OK" else "Not available in the demo")
            .body(answer.body.toResponseBody(JSON.toMediaType()))
            .build()
    }

    private fun queryIgnoringCase(
        url: okhttp3.HttpUrl,
        name: String,
    ): String? =
        url.queryParameterNames
            .firstOrNull { it.equals(name, ignoreCase = true) }
            ?.let(url::queryParameter)

    /** The demo's event socket: what a real server would push, from the demo's state. */
    fun openSocket(
        request: okhttp3.Request,
        listener: okhttp3.WebSocketListener,
    ): okhttp3.WebSocket = DemoSocket(model, request, listener)
}
