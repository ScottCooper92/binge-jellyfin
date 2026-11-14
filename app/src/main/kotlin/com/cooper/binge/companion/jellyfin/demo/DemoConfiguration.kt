package com.cooper.binge.companion.jellyfin.demo

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.time.Duration
import java.time.Instant
import java.util.UUID

private const val SERVER =
    """
    {"ServerName":"$DEMO_SERVER_NAME","PreferredMetadataLanguage":"en","MetadataCountryCode":"GB","UICulture":"en-GB",
     "QuickConnectAvailable":true,"EnableFolderView":false,"DisplaySpecialsWithinSeasons":true,"EnableExternalContentInSuggestions":true,
     "EnableGroupingIntoCollections":false,"SaveMetadataHidden":false,"ImageSavingConvention":"Legacy","MetadataPath":"",
     "ChapterImageResolution":"MatchSource","DummyChapterDuration":0,
     "SortRemoveWords":["the","a","an"],"SortRemoveCharacters":[",","&","-","{","}","'"],"SortReplaceCharacters":[".","+","%"],"PathSubstitutions":[],
     "LibraryScanFanoutConcurrency":0,"LibraryMonitorDelay":60,"LibraryUpdateDuration":30,"ActivityLogRetentionDays":30,
     "InactiveSessionThreshold":0,"ParallelImageEncodingLimit":0,"ImageExtractionTimeoutMs":0,"SlowResponseThresholdMs":500,
     "EnableSlowResponseWarning":true,"EnableMetrics":false,"AllowClientLogUpload":true,"EnableLegacyAuthorization":false,
     "CorsHosts":["*"],"CastReceiverApplications":[{"Id":"F007D354","Name":"Stable"},{"Id":"6F511C87","Name":"Unstable"}],
     "TrickplayOptions":{"EnableHwAcceleration":false,"EnableHwEncoding":false,"EnableKeyFrameOnlyExtraction":false,"ScanBehavior":"NonBlocking",
       "ProcessPriority":"BelowNormal","Interval":10000,"WidthResolutions":[320],"TileWidth":10,"TileHeight":10,"Qscale":4,"JpegQuality":90,"ProcessThreads":0},
     "MetadataOptions":[{"ItemType":"Movie","DisabledMetadataFetchers":[],"MetadataFetcherOrder":["TheMovieDb"],"DisabledImageFetchers":[],"ImageFetcherOrder":["TheMovieDb"]},{"ItemType":"Series","DisabledMetadataFetchers":["TheTVDB"],"MetadataFetcherOrder":["TheMovieDb","TheTVDB"],"DisabledImageFetchers":[],"ImageFetcherOrder":["TheMovieDb","TheTVDB"]}]}
    """

private const val BRANDING = """{"LoginDisclaimer":"","CustomCss":"","SplashscreenEnabled":false}"""

private const val NETWORK =
    """
    {"BaseUrl":"","EnableHttps":false,"RequireHttps":false,"CertificatePath":"","InternalHttpPort":8096,"InternalHttpsPort":8920,
     "PublicHttpPort":8096,"PublicHttpsPort":8920,"AutoDiscovery":true,"EnableUPnP":false,"EnableRemoteAccess":true,
     "LocalNetworkSubnets":[],"LocalNetworkAddresses":[],"KnownProxies":[],"IgnoreVirtualInterfaces":true,"VirtualInterfaceNames":["veth"],
     "EnablePublishedServerUriByRequest":false,"PublishedServerUriBySubnet":[],"RemoteIPFilter":[],"IsRemoteIPFilterBlacklist":false}
    """

private const val ENCODING =
    """
    {"EncodingThreadCount":-1,"TranscodingTempPath":"/cache/transcodes","FallbackFontPath":"","EnableFallbackFont":false,"EnableAudioVbr":false,
     "DownMixAudioBoost":2,"DownMixStereoAlgorithm":"None","MaxMuxingQueueSize":2048,"EnableThrottling":false,"ThrottleDelaySeconds":180,
     "EnableSegmentDeletion":false,"SegmentKeepSeconds":720,"HardwareAccelerationType":"none","VaapiDevice":"/dev/dri/renderD128","QsvDevice":"",
     "EnableTonemapping":false,"EnableVppTonemapping":false,"EnableVideoToolboxTonemapping":false,"TonemappingAlgorithm":"bt2390",
     "TonemappingMode":"auto","TonemappingRange":"auto","TonemappingDesat":0,"TonemappingPeak":100,"TonemappingParam":0,
     "VppTonemappingBrightness":16,"VppTonemappingContrast":1,"H264Crf":23,"H265Crf":28,"EncoderPreset":"auto","DeinterlaceDoubleRate":false,
     "DeinterlaceMethod":"yadif","EnableSubtitleExtraction":true,"EnableHardwareEncoding":true,"AllowHevcEncoding":false,"AllowAv1Encoding":false,
     "EnableIntelLowPowerH264HwEncoder":false,"EnableIntelLowPowerHevcHwEncoder":false,"EnableDecodingColorDepth10Hevc":true,
     "EnableDecodingColorDepth10Vp9":true,"PreferSystemNativeHwDecoder":true,"EnableEnhancedNvdecDecoder":true,
     "HardwareDecodingCodecs":["h264","hevc","mpeg2video","vc1","vp9"],"RemoteClientBitrateLimit":0,
     "MinResumePct":5,"MaxResumePct":90,"MinResumeDurationSeconds":300,"MinAudiobookResume":5,"MaxAudiobookResume":5,"NightmodeDialogue":false}
    """

private const val LIVE_TV =
    """
    {"RecordingPath":"/recordings","MovieRecordingPath":"","SeriesRecordingPath":"","PrePaddingSeconds":60,"PostPaddingSeconds":180,
     "EnableRecordingSubfolders":true,"EnableOriginalAudioWithEncodedRecordings":false,"SaveRecordingNFO":true,"SaveRecordingImages":true,"GuideDays":7,
     "TunerHosts":[{"Id":"tuner1","Type":"hdhomerun","Url":"http://192.168.1.50","FriendlyName":"HDHomeRun Prime","TunerCount":3,"AllowHWTranscoding":true}],
     "ListingProviders":[{"Id":"guide1","Type":"xmltv","Path":"http://192.168.1.2:8080/guide.xml"}]}
    """

private const val GUIDE_DAYS = 6L
private const val DISCOVERED_PORT = 80

/** The server's configuration documents (the main one, branding, network, encoding and Live TV), kept as the server keeps them: whole. */
internal class DemoConfiguration : DemoState {
    private val documents: MutableMap<String, JsonObject> = seed()

    private fun seed(): MutableMap<String, JsonObject> =
        mutableMapOf(
            "System/Configuration" to jsonObject(SERVER),
            "System/Configuration/branding" to jsonObject(BRANDING),
            "System/Configuration/network" to jsonObject(NETWORK),
            "System/Configuration/encoding" to jsonObject(ENCODING),
            "System/Configuration/livetv" to jsonObject(LIVE_TV),
        )

    override fun reset() {
        documents.clear()
        documents.putAll(seed())
    }

    fun quickConnectAvailable(): Boolean = documents["System/Configuration"]?.bool("QuickConnectAvailable") != false

    override fun routes(): List<DemoRoute> =
        documents.keys.flatMap { path ->
            val segments = path.split('/')
            listOf(
                DemoRoute("GET", segments) { _, _ -> ok(documents.getValue(path)) },
                DemoRoute("POST", segments) { call, _ -> noContent.also { documents[path] = call.jsonBody() } },
            )
        } + liveTv() + splashscreen()

    private fun splashscreen() =
        listOf(
            route("POST", "Branding", "Splashscreen") { _, _ -> noContent },
            route("DELETE", "Branding", "Splashscreen") { _, _ -> noContent },
            route("POST", "System", "Restart") { _, _ -> noContent },
            route("POST", "System", "Shutdown") { _, _ -> noContent },
        )

    private fun liveTv() =
        listOf(
            route("POST", "LiveTv", "TunerHosts") { call, _ -> saveEntry("TunerHosts", call.jsonBody()) },
            route("DELETE", "LiveTv", "TunerHosts") { call, _ -> deleteEntry("TunerHosts", call.query("id")) },
            route("POST", "LiveTv", "ListingProviders") { call, _ -> saveEntry("ListingProviders", call.jsonBody()) },
            route("DELETE", "LiveTv", "ListingProviders") { call, _ -> deleteEntry("ListingProviders", call.query("id")) },
            route("GET", "LiveTv", "Tuners", "Discover") { _, _ -> ok(discovered()) },
            route("GET", "LiveTv", "GuideInfo") { _, _ -> ok(guideInfo()) },
            route("GET", "LiveTv", "Channels") { _, _ -> ok(channelList()) },
            route("GET", "LiveTv", "ChannelMappingOptions") { _, _ -> ok(mappingOptions()) },
            route("POST", "LiveTv", "ChannelMappings") { call, _ -> map(call.jsonBody()) },
            route("GET", "LiveTv", "ListingProviders", "Lineups") { _, _ -> ok(JsonArray(emptyList())) },
            route("GET", "LiveTv", "ListingProviders", "SchedulesDirect", "Countries") { _, _ -> ok(JsonArray(emptyList())) },
        )

    private fun entries(key: String): List<JsonObject> = (liveTvDocument()[key] as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }

    private fun liveTvDocument(): JsonObject = documents.getValue("System/Configuration/livetv")

    private fun saveEntry(
        key: String,
        body: JsonObject,
    ): DemoAnswer {
        val saved =
            if (body
                    .string(
                        "Id",
                    ).isNullOrBlank()
            ) {
                body.with("Id", JsonPrimitive(UUID.randomUUID().toString().replace("-", "")))
            } else {
                body
            }
        val others = entries(key).filter { it.string("Id") != saved.string("Id") }
        documents["System/Configuration/livetv"] = liveTvDocument().with(key, JsonArray(others + saved))
        return ok(saved)
    }

    private fun deleteEntry(
        key: String,
        id: String?,
    ): DemoAnswer {
        documents["System/Configuration/livetv"] = liveTvDocument().with(key, JsonArray(entries(key).filter { it.string("Id") != id }))
        return noContent
    }

    private fun discovered() =
        JsonArray(
            listOf(
                buildJsonObject {
                    put("Url", "http://192.168.1.51:$DISCOVERED_PORT")
                    put("Type", "hdhomerun")
                    put("FriendlyName", "HDHomeRun Connect")
                },
            ),
        )

    private fun guideInfo() =
        buildJsonObject {
            put("StartDate", Instant.now().toString())
            put("EndDate", Instant.now().plus(Duration.ofDays(GUIDE_DAYS)).toString())
        }

    private fun channelList() =
        buildJsonObject {
            put(
                "Items",
                JsonArray(
                    CHANNELS.map { (id, name) ->
                        buildJsonObject {
                            put("Id", id)
                            put("Name", name)
                        }
                    },
                ),
            )
            put("TotalRecordCount", CHANNELS.size)
        }

    private val mappings: MutableMap<String, String> = mutableMapOf("c1" to "p1", "c2" to "p2")

    private fun mappingOptions() =
        buildJsonObject {
            put("ProviderName", "XMLTV")
            put(
                "ProviderChannels",
                JsonArray(
                    CHANNELS.map { (id, name) ->
                        buildJsonObject {
                            put("Id", "p${id.drop(1)}")
                            put("Name", name)
                        }
                    },
                ),
            )
            put(
                "TunerChannels",
                JsonArray(
                    CHANNELS.map { (id, name) ->
                        buildJsonObject {
                            put("Id", id)
                            put("Name", name)
                            mappings[id]?.let { put("ProviderChannelId", it) }
                        }
                    },
                ),
            )
        }

    private fun map(body: JsonObject): DemoAnswer {
        val tuner = body.string("TunerChannelId") ?: return demoNotFound
        val provider = body.string("ProviderChannelId") ?: return demoNotFound
        mappings[tuner] = provider
        return ok(
            buildJsonObject {
                put("Id", tuner)
                put("Name", CHANNELS.firstOrNull { it.first == tuner }?.second ?: tuner)
                put("ProviderChannelId", provider)
            },
        )
    }

    private companion object {
        val CHANNELS = listOf("c1" to "BBC One", "c2" to "BBC Two", "c3" to "ITV1", "c4" to "Channel 4", "c5" to "Film4")
    }
}
