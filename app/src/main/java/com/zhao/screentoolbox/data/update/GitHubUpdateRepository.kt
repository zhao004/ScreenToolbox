package com.zhao.screentoolbox.data.update

import com.zhao.screentoolbox.core.model.AvailableUpdate
import com.zhao.screentoolbox.core.model.ScreenToolboxReleaseSource
import com.zhao.screentoolbox.domain.repository.UpdateCheckResult
import com.zhao.screentoolbox.domain.repository.UpdateRepository
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.semver4j.Semver

/** 更新服务器返回了可识别但不可用于升级的响应。 */
class UpdateCheckException(message: String) : IOException(message)

/**
 * 使用 Release 自带的校验清单作为轻量版本清单。
 *
 * `latest/download` 会直接指向最新稳定发布，不消耗 GitHub API 匿名额度。
 */
@Singleton
class GitHubUpdateRepository @Inject constructor() : UpdateRepository {
    override suspend fun checkForUpdate(
        currentVersionName: String,
        supportedAbis: List<String>,
    ): UpdateCheckResult = withContext(Dispatchers.IO) {
        val checksumText = fetchChecksumManifest()
        ReleaseChecksumParser.parse(
            checksumText = checksumText,
            currentVersionName = currentVersionName,
            supportedAbis = supportedAbis,
        )
    }

    private fun fetchChecksumManifest(): String {
        val connection = URL(
            ScreenToolboxReleaseSource.CHECKSUM_URL,
        ).openConnection() as HttpURLConnection
        return try {
            connection.requestMethod = "GET"
            connection.connectTimeout = CONNECT_TIMEOUT_MS
            connection.readTimeout = READ_TIMEOUT_MS
            connection.instanceFollowRedirects = true
            connection.setRequestProperty("Accept", "text/plain")
            connection.setRequestProperty(
                "User-Agent",
                ScreenToolboxReleaseSource.USER_AGENT,
            )
            val responseCode = connection.responseCode
            if (responseCode !in 200..299) {
                throw UpdateCheckException(
                    if (responseCode == HttpURLConnection.HTTP_NOT_FOUND) {
                        "更新服务器尚无可用的发布信息"
                    } else {
                        "更新服务器暂时不可用，请稍后重试"
                    },
                )
            }
            connection.inputStream.use { input ->
                readLimitedUtf8(input, MAX_MANIFEST_BYTES)
            }
        } catch (exception: UpdateCheckException) {
            throw exception
        } catch (exception: IOException) {
            throw UpdateCheckException("无法连接更新服务器，请检查网络后重试")
        } finally {
            connection.disconnect()
        }
    }

    private fun readLimitedUtf8(input: InputStream, maxBytes: Int): String {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var total = 0
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            total += count
            if (total > maxBytes) {
                throw UpdateCheckException("更新信息格式无效")
            }
            output.write(buffer, 0, count)
        }
        return output.toString(StandardCharsets.UTF_8.name())
    }

    private companion object {
        const val CONNECT_TIMEOUT_MS = 15_000
        const val READ_TIMEOUT_MS = 15_000
        const val MAX_MANIFEST_BYTES = 64 * 1024
    }
}

/** 严格解析由发布流水线生成的 `sha256sum` 文本。 */
internal object ReleaseChecksumParser {
    private val linePattern = Regex(
        """^([0-9A-Fa-f]{64})\s+\./ScreenToolbox-""" +
            """(v[0-9]+\.[0-9]+\.[0-9]+(?:[-+][0-9A-Za-z.-]+)?)-""" +
            """(arm64-v8a|armeabi-v7a)\.apk$""",
    )

    fun parse(
        checksumText: String,
        currentVersionName: String,
        supportedAbis: List<String>,
    ): UpdateCheckResult {
        val currentVersion = Semver.parse(currentVersionName)
            ?: throw UpdateCheckException("当前版本号格式无效")
        val assets = checksumText
            .lineSequence()
            .map(String::trim)
            .filter(String::isNotEmpty)
            .map { line ->
                val match = linePattern.matchEntire(line)
                    ?: throw UpdateCheckException("更新信息格式无效")
                ParsedAsset(
                    sha256 = match.groupValues[1].lowercase(),
                    tag = match.groupValues[2],
                    abi = match.groupValues[3],
                    fileName = match.groupValues[0].substringAfter("./"),
                )
            }
            .toList()

        if (assets.isEmpty() || assets.map(ParsedAsset::tag).distinct().size != 1) {
            throw UpdateCheckException("更新信息格式无效")
        }
        if (assets.map(ParsedAsset::abi).distinct().size != assets.size) {
            throw UpdateCheckException("更新信息包含重复安装包")
        }

        val tag = assets.first().tag
        val versionName = tag.removePrefix("v")
        val candidateVersion = Semver.parse(versionName)
            ?: throw UpdateCheckException("更新版本号格式无效")
        if (!candidateVersion.isGreaterThan(currentVersion)) {
            return UpdateCheckResult.UpToDate
        }

        val assetByAbi = assets.associateBy(ParsedAsset::abi)
        val asset = ABI_PREFERENCE
            .asSequence()
            .filter(supportedAbis::contains)
            .mapNotNull(assetByAbi::get)
            .firstOrNull()
            ?: return UpdateCheckResult.UnsupportedDevice(
                "当前版本未提供适用于此设备的安装包",
            )
        return UpdateCheckResult.Available(
            AvailableUpdate(
                tag = tag,
                versionName = versionName,
                abi = asset.abi,
                fileName = asset.fileName,
                downloadUrl = ScreenToolboxReleaseSource.apkDownloadUrl(
                    tag = tag,
                    fileName = asset.fileName,
                ),
                sha256 = asset.sha256,
            ),
        )
    }

    private data class ParsedAsset(
        val sha256: String,
        val tag: String,
        val abi: String,
        val fileName: String,
    )

    private val ABI_PREFERENCE = listOf("arm64-v8a", "armeabi-v7a")
}
