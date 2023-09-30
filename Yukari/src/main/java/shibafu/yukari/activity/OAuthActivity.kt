package shibafu.yukari.activity

import android.app.Activity
import android.app.Dialog
import android.app.ProgressDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.ListView
import android.widget.Toast
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.Fragment
import androidx.fragment.app.ListFragment
import androidx.fragment.app.commit
import com.google.android.material.textfield.TextInputLayout
import com.sys1yagi.mastodon4j.api.Scope
import com.sys1yagi.mastodon4j.api.entity.Account
import com.sys1yagi.mastodon4j.api.entity.auth.AppRegistration
import com.sys1yagi.mastodon4j.api.exception.Mastodon4jRequestException
import com.sys1yagi.mastodon4j.api.method.Accounts
import com.sys1yagi.mastodon4j.api.method.Apps
import shibafu.yukari.R
import shibafu.yukari.activity.base.ActionBarYukariBase
import shibafu.yukari.common.async.ParallelAsyncTask
import shibafu.yukari.database.AuthUserRecord
import shibafu.yukari.database.Provider
import shibafu.yukari.databinding.FragmentOauthSuccessBinding
import shibafu.yukari.mastodon.MastodonApi
import com.sys1yagi.mastodon4j.api.entity.auth.AccessToken as MastodonAccessToken

/**
 * Created by Shibafu on 13/08/01.
 */
class OAuthActivity : ActionBarYukariBase() {

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

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)

        val currentFragment = supportFragmentManager.findFragmentById(R.id.frame)
        val data = intent?.data ?: return
        when (currentFragment) {
            is MastodonOAuthFragment -> {
                //コールバック以外のintentが流れ込んで来たらエラー
                if (!data.toString().startsWith(MASTODON_CALLBACK_URL))
                    return

                currentFragment.authorizeCode = data.getQueryParameter("code")
            }
        }
    }

    override fun onBackPressed() {
        if (supportFragmentManager.findFragmentById(R.id.frame) is FinishFragment) {
            setResult(Activity.RESULT_OK)
            if (intent.getBooleanExtra(EXTRA_REBOOT, false)) {
                startActivity(Intent(this, MainActivity::class.java))
            }
            finish()
            return
        } else if (supportFragmentManager.backStackEntryCount == 0) {
            setResult(Activity.RESULT_CANCELED)
            finish()
            return
        }

        super.onBackPressed()
    }

    override fun onServiceConnected() {
        Log.d("OAuthActivity", "Bound Service.")
    }

    override fun onServiceDisconnected() {}

    class ProviderChooserFragment : ListFragment() {
        private lateinit var adapter: ArrayAdapter<Option>

        override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
            return inflater.inflate(R.layout.fragment_oauth_provider, container, false)
        }

        override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
            super.onViewCreated(view, savedInstanceState)

            adapter = ArrayAdapter(requireActivity(), android.R.layout.simple_list_item_1)

            adapter.add(Option.MASTODON)

            listAdapter = adapter
        }

        override fun onListItemClick(l: ListView, v: View, position: Int, id: Long) {
            val option = adapter.getItem(position)
            val activity = activity as OAuthActivity
            when (option) {
                Option.MASTODON -> {
                    activity.supportFragmentManager.beginTransaction()
                            .replace(R.id.frame, MastodonOAuthFragment())
                            .addToBackStack(null)
                            .commit()
                }
                null -> {}
            }
        }

        private enum class Option(val label: String) {
            MASTODON("Mastodon");

            override fun toString(): String = label
        }
    }

    class MastodonOAuthFragment : Fragment() {
        var currentProvider: Provider? = null
        var authorizeCode: String? = null

        private lateinit var tilInstanceHostName: TextInputLayout

        override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
            val v = inflater.inflate(R.layout.fragment_oauth_mastodon, container, false)
            tilInstanceHostName = v.findViewById(R.id.tilInstanceHostName) as TextInputLayout
            v.findViewById<Button>(R.id.btnLogin).setOnClickListener listener@ {
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

        override fun onSaveInstanceState(outState: Bundle) {
            super.onSaveInstanceState(outState)
            outState.putSerializable("currentProvider", currentProvider)
        }

        override fun onResume() {
            super.onResume()

            val currentProvider = currentProvider
            if (currentProvider != null) {
                val authorizeCode = authorizeCode
                if (authorizeCode == null) {
                    Toast.makeText(activity, "認証が中断されました", Toast.LENGTH_SHORT).show()
                    requireActivity().supportFragmentManager.popBackStack()
                } else {
                    finishAuthorize(currentProvider, authorizeCode)
                }
            }
        }

        private fun startRegisterNewProvider(instanceHostName: String) {
            val activity = activity as OAuthActivity

            object : ParallelAsyncTask<Void?, Void?, AppRegistration?>() {
                var dialog: LoadDialogFragment? = null

                override fun doInBackground(vararg params: Void?): AppRegistration? {
                    while (!activity.isTwitterServiceBound) {
                        try {
                            Thread.sleep(50)
                        } catch (e: InterruptedException) {}
                    }

                    val client = (activity.twitterService.getProviderApi(Provider.API_MASTODON) as MastodonApi).getApiClient(instanceHostName, null)
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
                    dialog?.show(parentFragmentManager, "")
                }

                override fun onPostExecute(result: AppRegistration?) {
                    super.onPostExecute(result)
                    dialog?.dismiss()

                    if (result != null) {
                        val provider = Provider(instanceHostName, instanceHostName, Provider.API_MASTODON, result.clientId, result.clientSecret)
                        activity.twitterService.database.updateRecord(provider)
                        val registeredProvider = activity.twitterService.database.getRecords(Provider::class.java).first { it.apiType == Provider.API_MASTODON && it.host == instanceHostName}
                        startAuthorize(registeredProvider)
                    } else {
                        tilInstanceHostName.error = "アプリの登録中にエラーが発生しました\nインスタンス名が正確であること、サーバがダウンしていないことを確認してください"
                    }
                }
            }.executeParallel()
        }

        private fun startAuthorize(provider: Provider) {
            val activity = activity as OAuthActivity
            currentProvider = provider

            object : ParallelAsyncTask<Void?, Void?, String?>() {
                var dialog: LoadDialogFragment? = null

                override fun doInBackground(vararg params: Void?): String? {
                    while (!activity.isTwitterServiceBound) {
                        try {
                            Thread.sleep(50)
                        } catch (e: InterruptedException) {}
                    }

                    val client = (activity.twitterService.getProviderApi(Provider.API_MASTODON) as MastodonApi).getApiClient(provider.host, null)
                    return Apps(client).getOAuthUrl(provider.consumerKey, Scope(Scope.Name.ALL), MASTODON_CALLBACK_URL)
                }

                override fun onPreExecute() {
                    super.onPreExecute()
                    dialog = LoadDialogFragment.newInstance()
                    dialog?.show(parentFragmentManager, "")
                }

                override fun onPostExecute(result: String?) {
                    super.onPostExecute(result)
                    dialog?.dismiss()

                    assert(result != null)
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(result))
                    startActivity(intent)
                }
            }.executeParallel()
        }

        private fun finishAuthorize(provider: Provider, authorizeCode: String) {
            val activity = activity as OAuthActivity

            object : ParallelAsyncTask<Void?, Void?, Pair<MastodonAccessToken, Account>?>() {
                var dialog: LoadDialogFragment? = null

                override fun doInBackground(vararg params: Void?): Pair<MastodonAccessToken, Account>? {
                    while (!activity.isTwitterServiceBound) {
                        try {
                            Thread.sleep(50)
                        } catch (e: InterruptedException) {}
                    }

                    val client = (activity.twitterService.getProviderApi(Provider.API_MASTODON) as MastodonApi).getApiClient(provider.host, null)
                    val apps = Apps(client)
                    try {
                        val accessToken = apps.getAccessToken(provider.consumerKey,
                                provider.consumerSecret,
                                MASTODON_CALLBACK_URL,
                                authorizeCode).execute()

                        val newClient = (activity.twitterService.getProviderApi(Provider.API_MASTODON) as MastodonApi).getApiClient(provider.host, accessToken.accessToken)
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
                    dialog?.show(parentFragmentManager, "")
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
                        userRecord.isPrimary = !existsPrimary
                        userRecord.Name = account.displayName
                        userRecord.ProfileImageUrl = account.avatar
                        service.database.addAccount(userRecord)
                        service.reloadUsers()

                        activity.supportFragmentManager.popBackStackImmediate()
                        activity.supportFragmentManager.commit {
                            replace(R.id.frame, FinishFragment())
                        }
                    } else {
                        Toast.makeText(activity, "認証に失敗しました", Toast.LENGTH_SHORT).show()
                        activity.supportFragmentManager.popBackStack()
                    }
                }
            }.executeParallel()
        }
    }

    class FinishFragment : Fragment() {
        private var _binding: FragmentOauthSuccessBinding? = null
        private val binding get() = _binding!!

        override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
            _binding = FragmentOauthSuccessBinding.inflate(inflater, container, false)

            if (requireActivity().intent.getBooleanExtra(EXTRA_REBOOT, false)) {
                binding.btnDone.setText(R.string.oauth_success_fragment_finish_first)
            }

            binding.btnDone.setOnClickListener {
                val activity = requireActivity()
                activity.setResult(Activity.RESULT_OK)
                if (activity.intent.getBooleanExtra(EXTRA_REBOOT, false)) {
                    startActivity(Intent(activity, MainActivity::class.java))
                }
                activity.finish()
            }

            binding.btnChannelManage.setOnClickListener {
                startActivity(Intent(requireContext(), ChannelManageActivity::class.java))
            }

            return binding.root
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
        /**
         * (Boolean) 認証成功後、MainActivityに遷移させる
         */
        const val EXTRA_REBOOT = "reboot"

        private const val MASTODON_CALLBACK_URL = "yukari://mastodon"
    }
}
