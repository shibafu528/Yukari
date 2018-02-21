package shibafu.yukari.activity

import android.app.Activity
import android.app.Dialog
import android.app.ProgressDialog
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.AsyncTask
import android.os.Bundle
import android.support.design.widget.TextInputLayout
import android.support.v4.app.DialogFragment
import android.support.v4.app.Fragment
import android.support.v4.app.ListFragment
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ListView
import android.widget.Toast
import com.sys1yagi.mastodon4j.api.Scope
import com.sys1yagi.mastodon4j.api.entity.Account
import com.sys1yagi.mastodon4j.api.entity.auth.AppRegistration
import com.sys1yagi.mastodon4j.api.exception.Mastodon4jRequestException
import com.sys1yagi.mastodon4j.api.method.Accounts
import com.sys1yagi.mastodon4j.api.method.Apps
import shibafu.yukari.R
import shibafu.yukari.activity.base.ActionBarYukariBase
import shibafu.yukari.common.async.ParallelAsyncTask
import shibafu.yukari.database.Provider
import shibafu.yukari.twitter.AuthUserRecord
import shibafu.yukari.twitter.TwitterUtil
import twitter4j.TwitterException
import twitter4j.auth.AccessToken
import twitter4j.auth.RequestToken
import java.util.concurrent.CountDownLatch
import com.sys1yagi.mastodon4j.api.entity.auth.AccessToken as MastodonAccessToken

/**
 * Created by Shibafu on 13/08/01.
 */
class OAuthActivity : ActionBarYukariBase() {
    private var serviceLatch: CountDownLatch? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_parent)

        if (savedInstanceState == null) {
            val fragment = ProviderChooserFragment()
            supportFragmentManager.beginTransaction()
                    .replace(R.id.frame, fragment)
                    .commit()
        }
    }

    override fun onStart() {
        super.onStart()
        serviceLatch = CountDownLatch(1)
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)

        val currentFragment = supportFragmentManager.findFragmentById(R.id.frame)
        when (currentFragment) {
            is TwitterOAuthFragment -> {
                //コールバック以外のintentが流れ込んで来たらエラー
                if (intent == null || intent.data == null || !intent.data.toString().startsWith(TWITTER_CALLBACK_URL))
                    return

                currentFragment.verifier = intent.data.getQueryParameter("oauth_verifier")
            }
            is MastodonOAuthFragment -> {
                //コールバック以外のintentが流れ込んで来たらエラー
                if (intent == null || intent.data == null || !intent.data.toString().startsWith(MASTODON_CALLBACK_URL))
                    return

                currentFragment.authorizeCode = intent.data.getQueryParameter("code")
            }
        }
    }

    override fun onBackPressed() {
        if (supportFragmentManager.backStackEntryCount == 0) {
            setResult(Activity.RESULT_CANCELED)
            finish()
            return
        }

        super.onBackPressed()
    }

    private fun saveAccessToken(accessToken: AccessToken) {
        val task = object : AsyncTask<AccessToken, Void, Boolean>() {
            var dialog: LoadDialogFragment? = null

            override fun doInBackground(vararg params: AccessToken): Boolean {
                try {
                    try {
                        serviceLatch?.await()
                    } catch (e: InterruptedException) {
                        e.printStackTrace()
                    }

                    val twitter = twitterService.getTwitterOrThrow(null)
                    twitter.oAuthAccessToken = accessToken
                    val existsUsers = twitterService.users

                    val userRecord = AuthUserRecord(accessToken)
                    userRecord.isActive = true
                    if (existsUsers != null) {
                        var foundPrimary = false
                        for (user in existsUsers) {
                            if (user.isPrimary && user.NumericId != accessToken.userId) {
                                foundPrimary = true
                            }
                        }
                        if (!foundPrimary) {
                            userRecord.isPrimary = true
                        }
                    }

                    val database = twitterService.database
                    database.addAccount(userRecord)
                    val user = twitter.showUser(accessToken.userId)
                    database.updateAccountProfile(Provider.API_TWITTER.toLong(), accessToken.userId, user.screenName,
                            user.name, user.profileImageURLHttps)

                    twitterService.reloadUsers()
                    return true
                } catch (e: TwitterException) {
                    e.printStackTrace()
                }

                return false
            }

            override fun onPreExecute() {
                dialog = LoadDialogFragment.newInstance()
                dialog?.show(supportFragmentManager, "")
            }

            override fun onPostExecute(aBoolean: Boolean) {
                dialog?.dismiss()
                if (aBoolean) {
                    Toast.makeText(this@OAuthActivity, "認証成功", Toast.LENGTH_SHORT).show()
                    setResult(Activity.RESULT_OK)
                    if (intent.getBooleanExtra(EXTRA_REBOOT, false)) {
                        startActivity(Intent(this@OAuthActivity, MainActivity::class.java))
                    }
                    finish()
                } else {
                    Toast.makeText(this@OAuthActivity, "認証に失敗しました", Toast.LENGTH_SHORT).show()
                    setResult(Activity.RESULT_CANCELED)
                    finish()
                }
            }
        }
        task.execute(accessToken)
    }

    override fun onServiceConnected() {
        serviceLatch?.countDown()
        Log.d("OAuthActivity", "Bound Service.")
    }

    override fun onServiceDisconnected() {}

    class ProviderChooserFragment : ListFragment() {
        private lateinit var adapter: ArrayAdapter<Option>

        override fun onCreateView(inflater: LayoutInflater?, container: ViewGroup?, savedInstanceState: Bundle?): View? {
            return inflater?.inflate(R.layout.fragment_oauth_provider, container, false)
        }

        override fun onViewCreated(view: View?, savedInstanceState: Bundle?) {
            super.onViewCreated(view, savedInstanceState)

            adapter = ArrayAdapter(activity, android.R.layout.simple_list_item_1)

            adapter.add(Option.TWITTER)

            if (existsTwitterApp()) {
                adapter.add(Option.TWITTER_APP)
            }

            adapter.add(Option.MASTODON)

            listAdapter = adapter
        }

        override fun onListItemClick(l: ListView?, v: View?, position: Int, id: Long) {
            val option = adapter.getItem(position)
            val activity = activity as OAuthActivity
            when (option) {
                Option.TWITTER -> {
                    activity.supportFragmentManager.beginTransaction()
                            .replace(R.id.frame, TwitterOAuthFragment())
                            .addToBackStack(null)
                            .commit()
                }
                Option.TWITTER_APP -> {
                    activity.supportFragmentManager.beginTransaction()
                            .replace(R.id.frame, TwitterAppAuthFragment())
                            .addToBackStack(null)
                            .commit()
                }
                Option.MASTODON -> {
                    activity.supportFragmentManager.beginTransaction()
                            .replace(R.id.frame, MastodonOAuthFragment())
                            .addToBackStack(null)
                            .commit()
                }
            }
        }

        private fun existsTwitterApp(): Boolean {
            try {
                activity.packageManager.getActivityInfo(TWITTER_AUTH_ACTIVITY, PackageManager.GET_ACTIVITIES)
                return true
            } catch (ignored: PackageManager.NameNotFoundException) {}

            return false
        }

        private enum class Option(val label: String) {
            TWITTER("Twitter"),
            TWITTER_APP("Twitter (公式アプリを起動)"),
            MASTODON("Mastodon");

            override fun toString(): String = label
        }
    }

    class TwitterOAuthFragment : Fragment() {
        var requestToken: RequestToken? = null
        var verifier: String? = null

        override fun onCreateView(inflater: LayoutInflater?, container: ViewGroup?, savedInstanceState: Bundle?): View? {
            return inflater?.inflate(R.layout.activity_parent, container, false)
        }

        override fun onViewCreated(view: View?, savedInstanceState: Bundle?) {
            super.onViewCreated(view, savedInstanceState)

            if (savedInstanceState == null) {
                val task = object : AsyncTask<Void, Void, String>() {
                    override fun doInBackground(vararg params: Void): String? {
                        try {
                            val twitter = TwitterUtil.getTwitterFactory(activity).instance
                            val token = twitter.getOAuthRequestToken(TWITTER_CALLBACK_URL)
                            requestToken = token
                            return token.authorizationURL
                        } catch (e: TwitterException) {
                            e.printStackTrace()
                        }

                        return null
                    }

                    override fun onPostExecute(s: String?) {
                        if (s != null) {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(s))
                            startActivity(intent)
                        } else {
                            Toast.makeText(activity, "認証の準備プロセスでエラーが発生しました", Toast.LENGTH_SHORT).show()
                            activity.supportFragmentManager.beginTransaction()
                                    .replace(R.id.frame, ProviderChooserFragment())
                                    .commit()
                        }
                    }
                }
                task.execute()
            } else {
                requestToken = savedInstanceState.getSerializable("requestToken") as? RequestToken
            }
        }

        override fun onSaveInstanceState(outState: Bundle?) {
            super.onSaveInstanceState(outState)
            outState?.putSerializable("requestToken", requestToken)
        }

        override fun onResume() {
            super.onResume()

            if (requestToken != null) {
                if (verifier == null) {
                    Toast.makeText(activity, "認証が中断されました", Toast.LENGTH_SHORT).show()
                    activity.supportFragmentManager.beginTransaction()
                            .replace(R.id.frame, ProviderChooserFragment())
                            .commit()
                } else {
                    val task = object : AsyncTask<Void, Void, AccessToken>() {
                        var dialog: LoadDialogFragment? = null

                        override fun doInBackground(vararg params: Void): AccessToken? {
                            try {
                                val twitter = TwitterUtil.getTwitterFactory(activity).instance
                                return twitter.getOAuthAccessToken(requestToken, verifier)
                            } catch (e: TwitterException) {
                                e.printStackTrace()
                            }

                            return null
                        }

                        override fun onPreExecute() {
                            super.onPreExecute()
                            dialog = LoadDialogFragment.newInstance()
                            dialog?.show(fragmentManager, "")
                        }

                        override fun onPostExecute(accessToken: AccessToken?) {
                            dialog?.dismiss()

                            val activity = activity as OAuthActivity
                            if (accessToken != null) {
                                activity.saveAccessToken(accessToken)
                            } else {
                                Toast.makeText(activity, "認証に失敗しました", Toast.LENGTH_SHORT).show()
                                activity.supportFragmentManager.beginTransaction()
                                        .replace(R.id.frame, ProviderChooserFragment())
                                        .commit()
                            }
                        }
                    }
                    task.execute()
                }
            }
        }
    }

    class TwitterAppAuthFragment : Fragment() {
        var waitingActivityResult: Boolean = false

        override fun onCreateView(inflater: LayoutInflater?, container: ViewGroup?, savedInstanceState: Bundle?): View? {
            return inflater?.inflate(R.layout.activity_parent, container, false)
        }

        override fun onViewCreated(view: View?, savedInstanceState: Bundle?) {
            super.onViewCreated(view, savedInstanceState)

            if (savedInstanceState == null) {
                val intent = Intent().setComponent(TWITTER_AUTH_ACTIVITY)
                intent.putExtra("ck", getString(R.string.twitter_consumer_key))
                intent.putExtra("cs", getString(R.string.twitter_consumer_secret))
                try {
                    startActivityForResult(intent, REQUEST_TWITTER)
                } catch (e: SecurityException) {
                    e.printStackTrace()
                    Toast.makeText(activity.applicationContext, "実行権限が不足しています。ブラウザでの認証に切り替えます。", Toast.LENGTH_LONG).show()

                    activity.supportFragmentManager.beginTransaction()
                            .replace(R.id.frame, TwitterOAuthFragment())
                            .commit()
                }
            } else {
                waitingActivityResult = savedInstanceState.getBoolean("waitingActivityResult")
            }
        }

        override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
            super.onActivityResult(requestCode, resultCode, data)

            if (requestCode == REQUEST_TWITTER && data != null) {
                val extras = data.extras
                val accessToken = AccessToken(
                        extras.getString("tk"),
                        extras.getString("ts"),
                        extras.getLong("user_id"))
                val activity = activity as OAuthActivity

                try {
                    activity.saveAccessToken(accessToken)
                    waitingActivityResult = false
                } catch (e: IllegalArgumentException) {
                    Toast.makeText(activity, "認証が中断されました", Toast.LENGTH_SHORT).show()
                    activity.supportFragmentManager.beginTransaction()
                            .replace(R.id.frame, ProviderChooserFragment())
                            .commit()
                }
            }
        }

        override fun onResume() {
            super.onResume()

            if (waitingActivityResult) {
                Toast.makeText(activity, "認証が中断されました", Toast.LENGTH_SHORT).show()
                activity.supportFragmentManager.beginTransaction()
                        .replace(R.id.frame, ProviderChooserFragment())
                        .commit()
            } else {
                waitingActivityResult = true
            }
        }

        override fun onSaveInstanceState(outState: Bundle?) {
            super.onSaveInstanceState(outState)
            outState?.putBoolean("waitingActivityResult", waitingActivityResult)
        }
    }

    class MastodonOAuthFragment : Fragment() {
        var currentProvider: Provider? = null
        var authorizeCode: String? = null

        private lateinit var tilInstanceHostName: TextInputLayout

        override fun onCreateView(inflater: LayoutInflater?, container: ViewGroup?, savedInstanceState: Bundle?): View? {
            val v = inflater!!.inflate(R.layout.fragment_oauth_mastodon, container, false)
            tilInstanceHostName = v.findViewById(R.id.tilInstanceHostName) as TextInputLayout
            v.findViewById(R.id.btnLogin).setOnClickListener listener@ {
                val instanceHostName = tilInstanceHostName.editText?.text.toString()
                if (instanceHostName.isEmpty()) {
                    tilInstanceHostName.error = "入力してください。"
                    return@listener
                }
                if (instanceHostName.contains("@")) {
                    tilInstanceHostName.error = "IDなどを含めていませんか？消してください。\n× @username@***.jp , username@***.jp\n○ ***.jp"
                    return@listener
                }
                tilInstanceHostName.error = ""

                // 登録済Providerか？
                val activity = activity as OAuthActivity
                val provider = activity.twitterService.database.getRecords(Provider::class.java)
                        .find { it.apiType == Provider.API_MASTODON && it.host == instanceHostName }
                if (provider == null) {
                    // 未登録Provider -> アプリ登録から
                    startRegisterNewProvider(instanceHostName)
                } else {
                    // 登録済Provider -> 認証から
                    startAuthorize(provider)
                }
            }

            if (savedInstanceState != null) {
                currentProvider = savedInstanceState.getSerializable("currentProvider") as Provider?
            }

            return v
        }

        override fun onSaveInstanceState(outState: Bundle?) {
            super.onSaveInstanceState(outState)
            outState?.putSerializable("currentProvider", currentProvider)
        }

        override fun onResume() {
            super.onResume()

            val currentProvider = currentProvider
            if (currentProvider != null) {
                val authorizeCode = authorizeCode
                if (authorizeCode == null) {
                    Toast.makeText(activity, "認証が中断されました", Toast.LENGTH_SHORT).show()
                    activity.supportFragmentManager.beginTransaction()
                            .replace(R.id.frame, ProviderChooserFragment())
                            .commit()
                } else {
                    finishAuthorize(currentProvider, authorizeCode)
                }
            }
        }

        private fun startRegisterNewProvider(instanceHostName: String) {
            val activity = activity as OAuthActivity
            val client = activity.twitterService.getMastodonClient(instanceHostName, null)
            object : ParallelAsyncTask<Void?, Void?, AppRegistration?>() {
                var dialog: LoadDialogFragment? = null

                override fun doInBackground(vararg params: Void?): AppRegistration? {
                    val apps = Apps(client)
                    try {
                        return apps.createApp(getString(R.string.app_name),
                                MASTODON_CALLBACK_URL,
                                Scope(Scope.Name.ALL),
                                getString(R.string.mastodon_website_url)).execute()
                    } catch (e: Mastodon4jRequestException) {
                        e.printStackTrace()
                    }
                    return null
                }

                override fun onPreExecute() {
                    super.onPreExecute()
                    dialog = LoadDialogFragment.newInstance()
                    dialog?.show(fragmentManager, "")
                }

                override fun onPostExecute(result: AppRegistration?) {
                    super.onPostExecute(result)
                    dialog?.dismiss()

                    if (result != null) {
                        val provider = Provider(instanceHostName, instanceHostName, Provider.API_MASTODON, result.clientId, result.clientSecret)
                        activity.twitterService.database.updateRecord(provider)
                        startAuthorize(provider)
                    } else {
                        Toast.makeText(activity, "アプリの登録中にエラーが発生しました\nインスタンス名が正確であること、サーバがダウンしていないことを確認してください", Toast.LENGTH_LONG).show()
                        activity.supportFragmentManager.beginTransaction()
                                .replace(R.id.frame, ProviderChooserFragment())
                                .commit()
                    }
                }
            }.executeParallel()
        }

        private fun startAuthorize(provider: Provider) {
            val activity = activity as OAuthActivity
            val client = activity.twitterService.getMastodonClient(provider.host, null)
            currentProvider = provider
            object : ParallelAsyncTask<Void?, Void?, String?>() {
                var dialog: LoadDialogFragment? = null

                override fun doInBackground(vararg params: Void?): String? {
                    val apps = Apps(client)
                    try {
                        return apps.getOAuthUrl(provider.consumerKey, Scope(Scope.Name.ALL), MASTODON_CALLBACK_URL)
                    } catch (e: Mastodon4jRequestException) {
                        e.printStackTrace()
                    }
                    return null
                }

                override fun onPreExecute() {
                    super.onPreExecute()
                    dialog = LoadDialogFragment.newInstance()
                    dialog?.show(fragmentManager, "")
                }

                override fun onPostExecute(result: String?) {
                    super.onPostExecute(result)
                    dialog?.dismiss()

                    if (result != null) {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(result))
                        startActivity(intent)
                    } else {
                        Toast.makeText(activity, "認証の準備プロセスでエラーが発生しました", Toast.LENGTH_SHORT).show()
                        activity.supportFragmentManager.beginTransaction()
                                .replace(R.id.frame, ProviderChooserFragment())
                                .commit()
                    }
                }
            }.executeParallel()
        }

        private fun finishAuthorize(provider: Provider, authorizeCode: String) {
            val activity = activity as OAuthActivity
            val client = activity.twitterService.getMastodonClient(provider.host, null)

            object : ParallelAsyncTask<Void?, Void?, Pair<MastodonAccessToken, Account>?>() {
                var dialog: LoadDialogFragment? = null

                override fun doInBackground(vararg params: Void?): Pair<MastodonAccessToken, Account>? {
                    val apps = Apps(client)
                    try {
                        val accessToken = apps.getAccessToken(provider.consumerKey,
                                provider.consumerSecret,
                                MASTODON_CALLBACK_URL,
                                authorizeCode).execute()

                        val newClient = activity.twitterService.getMastodonClient(provider.host, accessToken.accessToken)
                        val credentials = Accounts(newClient).getVerifyCredentials().execute()

                        return accessToken to credentials
                    } catch (e: Mastodon4jRequestException) {
                        e.printStackTrace()
                    }
                    return null
                }

                override fun onPreExecute() {
                    super.onPreExecute()
                    dialog = LoadDialogFragment.newInstance()
                    dialog?.show(fragmentManager, "")
                }

                override fun onPostExecute(pair: Pair<MastodonAccessToken, Account>?) {
                    dialog?.dismiss()

                    if (pair != null) {
                        val (accessToken, account) = pair
                        val service = activity.twitterService

                        // ユーザ情報を保存
                        val userRecord = AuthUserRecord(accessToken, account, provider)
                        val existsPrimary = service.users.any {
                            it.isPrimary && it.Provider != userRecord.Provider || it.NumericId != userRecord.NumericId
                        }
                        userRecord.isActive = true
                        userRecord.isPrimary = !existsPrimary
                        userRecord.Name = account.displayName
                        userRecord.ProfileImageUrl = account.avatar
                        service.database.addAccount(userRecord)
                        service.reloadUsers()

                        Toast.makeText(activity, "認証成功", Toast.LENGTH_SHORT).show()
                        activity.setResult(Activity.RESULT_OK)
                        if (activity.intent.getBooleanExtra(EXTRA_REBOOT, false)) {
                            startActivity(Intent(activity, MainActivity::class.java))
                        }
                        activity.finish()
                    } else {
                        Toast.makeText(activity, "認証に失敗しました", Toast.LENGTH_SHORT).show()
                        activity.supportFragmentManager.beginTransaction()
                                .replace(R.id.frame, ProviderChooserFragment())
                                .commit()
                    }
                }
            }.executeParallel()
        }
    }

    class LoadDialogFragment : DialogFragment() {
        override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
            val pd = ProgressDialog(activity)
            pd.setMessage("認証中...")
            pd.setProgressStyle(ProgressDialog.STYLE_SPINNER)
            pd.isIndeterminate = true
            return pd
        }

        companion object {
            fun newInstance(): LoadDialogFragment {
                val fragment = LoadDialogFragment()
                fragment.isCancelable = false
                return fragment
            }
        }
    }

    companion object {
        const val EXTRA_REBOOT = "reboot"

        private val REQUEST_TWITTER = 1
        private val TWITTER_AUTH_ACTIVITY = ComponentName("com.twitter.android", "com.twitter.android.AuthorizeAppActivity")

        private val TWITTER_CALLBACK_URL = "yukari://twitter"
        private val MASTODON_CALLBACK_URL = "yukari://mastodon"
    }
}
