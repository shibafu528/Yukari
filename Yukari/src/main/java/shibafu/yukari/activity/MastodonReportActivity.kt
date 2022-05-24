package shibafu.yukari.activity

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Parcel
import android.os.Parcelable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import androidx.activity.viewModels
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.commit
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.lifecycleScope
import com.google.gson.JsonParser
import com.sys1yagi.mastodon4j.MastodonClient
import com.sys1yagi.mastodon4j.api.exception.Mastodon4jRequestException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import shibafu.yukari.R
import shibafu.yukari.activity.base.ActionBarYukariBase
import shibafu.yukari.common.bitmapcache.ImageLoaderTask
import shibafu.yukari.database.AuthUserRecord
import shibafu.yukari.database.Provider
import shibafu.yukari.databinding.FragmentReportCategoryBinding
import shibafu.yukari.databinding.FragmentReportCommentBinding
import shibafu.yukari.databinding.FragmentReportSelectRuleBinding
import shibafu.yukari.databinding.RowCheckBinding
import shibafu.yukari.fragment.PostProgressDialogFragment
import shibafu.yukari.fragment.base.YukariBaseFragment
import shibafu.yukari.mastodon.MastodonApi
import shibafu.yukari.mastodon.entity.DonStatus
import shibafu.yukari.service.TwitterServiceDelegate
import shibafu.yukari.util.AttrUtil
import shibafu.yukari.util.getTwitterServiceAwait
import shibafu.yukari.util.showToast

class MastodonReportActivity : ActionBarYukariBase() {
    private val model: ReportViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_parent)
        if (savedInstanceState == null) {
            supportFragmentManager.commit {
                replace(R.id.frame, CategoryFragment())
            }

            model.currentUser.value = intent.getSerializableExtra(EXTRA_USER) as AuthUserRecord
            model.status.value = intent.getParcelableExtra(EXTRA_STATUS)
        }

        model.currentUser.observe(this) { user ->
            loadRules(user)
        }
    }

    override fun onStart() {
        super.onStart()
        model.currentUser.value?.let { user ->
            loadRules(user)
        }
    }

    override fun onServiceConnected() {}

    override fun onServiceDisconnected() {}

    private fun loadRules(userRecord: AuthUserRecord) {
        lifecycleScope.launch {
            if (model.loadingRules.value!!) {
                return@launch
            }

            model.loadingRules.value = true
            try {
                val service = getTwitterServiceAwait() ?: return@launch
                val client = service.getProviderApi(userRecord)!!.getApiClient(userRecord) as MastodonClient

                val rules = withContext(Dispatchers.IO) {
                    val response = client.get("instance")
                    if (response.isSuccessful) {
                        val body = response.body()!!.string()
                        val element = JsonParser.parseString(body)
                        val rules = element.asJsonObject["rules"]
                        if (rules != null && rules.isJsonArray) {
                            rules.asJsonArray.map {
                                val rule = it.asJsonObject
                                Rule(rule["id"].asString, rule["text"].asString)
                            }
                        } else {
                            emptyList()
                        }
                    } else {
                        throw Mastodon4jRequestException(response)
                    }
                }

                if (rules.isEmpty()) {
                    model.categories.value = Category.defaultsForNoRuleServer(this@MastodonReportActivity)
                } else {
                    model.categories.value = Category.defaults(this@MastodonReportActivity)
                }

                model.rules.value = rules
            } catch (e: Mastodon4jRequestException) {
                e.printStackTrace()
                showToast("サーバールールの確認中にエラーが発生しました")

                model.categories.value = Category.defaultsForNoRuleServer(this@MastodonReportActivity)
            } finally {
                model.loadingRules.value = false
            }
        }
    }

    data class Category(val id: String, val text: String) : Parcelable {
        constructor(parcel: Parcel) : this(parcel.readString()!!, parcel.readString()!!)

        override fun toString(): String = text

        companion object {
            @JvmField val CREATOR = object : Parcelable.Creator<Category> {
                override fun createFromParcel(parcel: Parcel): Category {
                    return Category(parcel)
                }

                override fun newArray(size: Int): Array<Category?> {
                    return arrayOfNulls(size)
                }
            }

            fun defaults(context: Context) = listOf(
                    Category("spam", context.getString(R.string.mastodon_report_category_spam)),
                    Category("violation", context.getString(R.string.mastodon_report_category_violation)),
                    Category("other", context.getString(R.string.mastodon_report_category_other)),
            )

            fun defaultsForNoRuleServer(context: Context) = listOf(
                    Category("spam", context.getString(R.string.mastodon_report_category_spam)),
                    Category("other", context.getString(R.string.mastodon_report_category_other)),
            )
        }

        override fun writeToParcel(parcel: Parcel, flags: Int) {
            parcel.writeString(id)
            parcel.writeString(text)
        }

        override fun describeContents(): Int {
            return 0
        }
    }

    data class Rule(val id: String, val text: String) : Parcelable {
        constructor(parcel: Parcel) : this(parcel.readString()!!, parcel.readString()!!)

        override fun writeToParcel(parcel: Parcel, flags: Int) {
            parcel.writeString(id)
            parcel.writeString(text)
        }

        override fun describeContents(): Int {
            return 0
        }

        companion object CREATOR : Parcelable.Creator<Rule> {
            override fun createFromParcel(parcel: Parcel): Rule {
                return Rule(parcel)
            }

            override fun newArray(size: Int): Array<Rule?> {
                return arrayOfNulls(size)
            }
        }
    }

    class ReportViewModel(private val state: SavedStateHandle) : ViewModel() {
        val currentUser = state.getLiveData<AuthUserRecord>("currentUser")
        val status = state.getLiveData<DonStatus>("status")
        val category = state.getLiveData<Category>("category")
        val selectedRules = state.getLiveData<ArrayList<Rule>>("selectedRules", arrayListOf())

        val loadingRules = MutableLiveData(false)
        val rules = MutableLiveData<List<Rule>>()
        val categories = MutableLiveData<List<Category>>()
    }

    class CategoryFragment : YukariBaseFragment(), AdapterView.OnItemClickListener {
        private var _binding: FragmentReportCategoryBinding? = null
        private val binding get() = _binding!!

        private val model: ReportViewModel by activityViewModels()

        override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
            _binding = FragmentReportCategoryBinding.inflate(inflater, container, false)

            binding.listView.onItemClickListener = this

            binding.userView.root.isClickable = true
            binding.userView.root.isFocusable = true
            binding.userView.root.setBackgroundResource(AttrUtil.resolveAttribute(requireActivity().theme, R.attr.selectableItemBackground))
            binding.userView.root.setOnClickListener {
                val intent = Intent(activity, AccountChooserActivity::class.java).apply {
                    putExtra(AccountChooserActivity.EXTRA_FILTER_PROVIDER_API_TYPE, Provider.API_MASTODON)
                }
                startActivityForResult(intent, REQUEST_CHANGE_ACCOUNT)
            }

            return binding.root
        }

        override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
            super.onViewCreated(view, savedInstanceState)
            model.loadingRules.observe(viewLifecycleOwner) { loading ->
                binding.listView.visibility = if (loading) { View.INVISIBLE } else { View.VISIBLE }
                binding.progressBar.visibility = if (loading) { View.VISIBLE } else { View.GONE }
                binding.progressText.visibility = if (loading) { View.VISIBLE } else { View.GONE }
            }
            model.categories.observe(viewLifecycleOwner) { categories ->
                binding.listView.adapter = ArrayAdapter(requireActivity(), android.R.layout.simple_list_item_1, categories)
            }
            model.currentUser.observe(viewLifecycleOwner) { user ->
                binding.userView.userName.text = user.Name
                binding.userView.userSn.text = "@${user.ScreenName}"
                ImageLoaderTask.loadProfileIcon(requireContext(), binding.userView.userIcon, user.ProfileImageUrl)
            }
        }

        override fun onDestroyView() {
            super.onDestroyView()
            _binding = null
        }

        override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
            super.onActivityResult(requestCode, resultCode, data)

            if (resultCode == Activity.RESULT_OK) {
                when (requestCode) {
                    REQUEST_CHANGE_ACCOUNT -> {
                        model.currentUser.value = data?.getSerializableExtra(AccountChooserActivity.EXTRA_SELECTED_RECORD) as? AuthUserRecord ?: return
                    }
                }
            }
        }

        override fun onItemClick(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
            val category = parent?.getItemAtPosition(position) as? Category ?: return
            model.category.value = category
            if (category.id == "violation") {
                parentFragmentManager.commit {
                    replace(R.id.frame, SelectRuleFragment())
                    addToBackStack(null)
                }
            } else {
                parentFragmentManager.commit {
                    replace(R.id.frame, CommentFragment())
                    addToBackStack(null)
                }
            }
        }

        override fun onServiceConnected() {}

        override fun onServiceDisconnected() {}

        companion object {
            private const val REQUEST_CHANGE_ACCOUNT = 1
        }
    }

    class SelectRuleFragment : YukariBaseFragment(), AdapterView.OnItemClickListener {
        private var _binding: FragmentReportSelectRuleBinding? = null
        private val binding get() = _binding!!

        private val model: ReportViewModel by activityViewModels()

        override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
            _binding = FragmentReportSelectRuleBinding.inflate(inflater, container, false)
            binding.listView.onItemClickListener = this
            binding.btnNext.setOnClickListener {
                parentFragmentManager.commit {
                    replace(R.id.frame, CommentFragment())
                    addToBackStack(null)
                }
            }
            return binding.root
        }

        override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
            super.onViewCreated(view, savedInstanceState)
            model.rules.observe(viewLifecycleOwner) { rules ->
                binding.listView.adapter = RuleCheckListAdapter(requireActivity(), rules)
            }
            model.selectedRules.observe(viewLifecycleOwner) { rules ->
                binding.btnNext.isEnabled = !rules.isEmpty()
                (binding.listView.adapter as? RuleCheckListAdapter)?.notifyDataSetChanged()
            }
        }

        override fun onDestroyView() {
            super.onDestroyView()
            _binding = null
        }

        override fun onItemClick(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
            val rule = parent?.getItemAtPosition(position) as? Rule ?: return
            val selectedRules = model.selectedRules.value ?: emptyList()
            model.selectedRules.value = if (selectedRules.contains(rule)) {
                ArrayList(selectedRules - rule)
            } else {
                ArrayList(selectedRules + rule)
            }
        }

        override fun onServiceConnected() {}

        override fun onServiceDisconnected() {}

        inner class RuleCheckListAdapter(context: Context, rules: List<Rule>) : ArrayAdapter<Rule>(context, 0, rules) {
            private val inflater = context.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater

            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val binding = if (convertView == null) {
                    RowCheckBinding.inflate(inflater).also {
                        it.root.tag = it
                    }
                } else {
                    convertView.tag as RowCheckBinding
                }

                val item = getItem(position)
                if (item != null) {
                    binding.checkBox.text = item.text
                    binding.checkBox.isChecked = model.selectedRules.value?.contains(item) ?: false
                }

                return binding.root
            }
        }
    }

    class CommentFragment : YukariBaseFragment(), View.OnClickListener {
        private var _binding: FragmentReportCommentBinding? = null
        private val binding get() = _binding!!

        private val model: ReportViewModel by activityViewModels()

        override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
            _binding = FragmentReportCommentBinding.inflate(inflater, container, false)

            val currentUser = model.currentUser.value
            val status = model.status.value
            if (currentUser != null && status != null) {
                if (status.user.host == currentUser.Provider.host) {
                    binding.tvForward.visibility = View.GONE
                    binding.cbForward.visibility = View.GONE
                } else {
                    binding.tvForward.visibility = View.VISIBLE
                    binding.cbForward.visibility = View.VISIBLE
                }
            }

            binding.btnSend.setOnClickListener(this)

            return binding.root
        }

        override fun onDestroyView() {
            super.onDestroyView()
            _binding = null
        }

        override fun onClick(v: View?) {
            if (v == binding.btnSend) {
                lifecycleScope.launchWhenStarted {
                    val currentUser = model.currentUser.value!!
                    val status = model.status.value!!
                    val category = model.category.value?.id
                    val ruleIds = if (category == "violation") {
                        model.selectedRules.value?.map { it.id }
                    } else {
                        null
                    }
                    val comment = binding.etComment.text.toString()
                    val forward = binding.cbForward.isChecked

                    val progressDialog = PostProgressDialogFragment.newInstance()
                    try {
                        progressDialog.show(parentFragmentManager, "progress")

                        val twitterService = (requireActivity() as TwitterServiceDelegate).getTwitterServiceAwait() ?: return@launchWhenStarted
                        val api = twitterService.getProviderApi(currentUser) as MastodonApi
                        withContext(Dispatchers.IO) {
                            api.reportStatus(currentUser, status, comment, forward, category = category, ruleIds = ruleIds)
                        }

                        showToast("通報しました")
                        activity?.finish()
                    } catch (e: Exception) {
                        e.printStackTrace()
                        showToast("通報中にエラーが発生しました")
                    } finally {
                        progressDialog.dismiss()
                    }
                }
            }
        }

        override fun onServiceConnected() {}
        override fun onServiceDisconnected() {}
    }

    companion object {
        private const val EXTRA_USER = "user"
        private const val EXTRA_STATUS = "status"

        fun newIntent(context: Context, userRecord: AuthUserRecord, status: DonStatus): Intent {
            return Intent(context, MastodonReportActivity::class.java).apply {
                putExtra(EXTRA_USER, userRecord)
                putExtra(EXTRA_STATUS, status as Parcelable)
            }
        }
    }
}