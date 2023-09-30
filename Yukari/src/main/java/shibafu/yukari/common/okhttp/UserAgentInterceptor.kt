package shibafu.yukari.common.okhttp

import android.content.Context
import okhttp3.Interceptor
import okhttp3.Response
import shibafu.yukari.util.StringUtil

class UserAgentInterceptor(private val context: Context) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        return chain.proceed(
            chain.request()
                .newBuilder()
                .header("User-Agent", StringUtil.getShortVersionInfo(context))
                .build()
        )
    }
}