package shibafu.yukari.fragment

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.support.v4.app.Fragment
import android.text.Editable
import android.text.Spanned
import android.text.TextWatcher
import android.text.style.CharacterStyle
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.ImageButton
import android.widget.Toast
import shibafu.yukari.R
import shibafu.yukari.activity.AccountChooserActivity
import shibafu.yukari.common.FontAsset
import shibafu.yukari.common.TweetDraft
import shibafu.yukari.common.bitmapcache.ImageLoaderTask
import shibafu.yukari.service.PostService
import shibafu.yukari.twitter.AuthUserRecord
import shibafu.yukari.util.showToast
import twitter4j.util.CharacterUtil

class QuickPostFragment : Fragment() {
    /**
     * ツイート後や入力欄が空白の時にツイートボタンを押した際にセットするデフォルト文。
     */
    var defaultText: String? = ""
        set(value) {
            field = value
            if (etTweet.text.isNullOrEmpty()) {
                etTweet.append(value)
            }
        }

    /**
     * ツイートに使用するアカウント。
     */
    var selectedAccount: AuthUserRecord? = null
        set(value) {
            field = value
            if (value != null) {
                ImageLoaderTask.loadProfileIcon(context.applicationContext, ibSelectAccount, value.ProfileImageUrl)
            }
        }

    private val ibCloseTweet by lazy { view?.findViewById(R.id.ibCloseTweet) as ImageButton }
    private val ibSelectAccount by lazy { view?.findViewById(R.id.ibAccount) as ImageButton }
    private val ibTweet by lazy { view?.findViewById(R.id.ibTweet) as ImageButton }
    private val etTweet by lazy { view?.findViewById(R.id.etTweetInput) as EditText }
    private val imm by lazy { context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        if (savedInstanceState != null) {
            defaultText = savedInstanceState.getString("defaultText")
            selectedAccount = savedInstanceState.getSerializable("selectedAccount") as AuthUserRecord
        }
    }

    override fun onCreateView(inflater: LayoutInflater?, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater?.inflate(R.layout.fragment_quickpost, container, false)
    }

    override fun onViewCreated(view: View?, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        ibCloseTweet.setOnClickListener { onClickClose() }
        ibSelectAccount.setOnClickListener { onClickSelectAccount() }
        ibTweet.setOnClickListener { onClickTweet() }
        etTweet.setOnKeyListener { v, keyCode, event ->
            if (event.action == KeyEvent.ACTION_DOWN && event.keyCode == KeyEvent.KEYCODE_ENTER) {
                imm.hideSoftInputFromWindow(v.windowToken, 0)
                postTweet()
            }
            false
        }
        etTweet.addTextChangedListener(object: TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                // 装飾の除去
                val spanned = s?.getSpans(0, s.length, Any::class.java)
                if (spanned != null) {
                    spanned.filter { it is CharacterStyle && (s.getSpanFlags(it) and Spanned.SPAN_COMPOSING) != Spanned.SPAN_COMPOSING }
                            .forEach { s.removeSpan(it) }
                }
            }

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })
        etTweet.typeface = FontAsset.getInstance(context).font
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (resultCode == Activity.RESULT_OK) {
            when (requestCode) {
                REQUEST_CHOOSE_ACCOUNT -> {
                    selectedAccount = data?.getSerializableExtra(AccountChooserActivity.EXTRA_SELECTED_RECORD) as AuthUserRecord
                }
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle?) {
        super.onSaveInstanceState(outState)
        outState?.let {
            outState.putString("defaultText", defaultText)
            outState.putSerializable("selectedAccount", selectedAccount)
        }
    }

    fun onClickClose() {
        if (etTweet.text.isNotEmpty()) {
            etTweet.setText("")
        } else {
            val activity = activity
            if (activity is OnCloseQuickPostListener) {
                activity.onCloseQuickPost()
            }
        }
    }

    fun onClickSelectAccount() {
        val intent = Intent(context.applicationContext, AccountChooserActivity::class.java)
        startActivityForResult(intent, REQUEST_CHOOSE_ACCOUNT)
    }

    fun onClickTweet() {
        postTweet()
    }

    private fun postTweet() {
        if (selectedAccount == null) {
            showToast("アカウントが選択されていません", Toast.LENGTH_LONG)
        } else if (etTweet.text.isEmpty()) {
            // StreamFilterが取れたらそれをセット
            if (defaultText.isNullOrEmpty()) {
                showToast("テキストが入力されていません", Toast.LENGTH_LONG)
            } else {
                etTweet.append(defaultText)
            }
        } else if (selectedAccount != null && CharacterUtil.count(etTweet.text.toString()) <= 140) {
            //ドラフト生成
            val draft = TweetDraft.Builder()
                    .addWriter(selectedAccount)
                    .setText(etTweet.text.toString())
                    .setDateTime(System.currentTimeMillis())
                    .build()

            //サービス起動
            activity?.startService(PostService.newIntent(context.applicationContext, draft))

            //投稿欄を掃除する
            etTweet.setText("")
            if (!defaultText.isNullOrEmpty()) {
                etTweet.append(defaultText)
            }
            etTweet.requestFocus()
            imm.showSoftInput(etTweet, InputMethodManager.SHOW_FORCED)
        }
    }

    interface OnCloseQuickPostListener {
        fun onCloseQuickPost()
    }

    companion object {
        private const val REQUEST_CHOOSE_ACCOUNT = 1
    }
}