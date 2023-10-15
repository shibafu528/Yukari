package shibafu.yukari.activity

import android.app.Application
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Parcelable
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.PopupMenu
import android.widget.Toast
import androidx.activity.viewModels
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.sys1yagi.mastodon4j.MastodonClient
import com.sys1yagi.mastodon4j.api.entity.Relationship
import com.sys1yagi.mastodon4j.api.exception.Mastodon4jRequestException
import com.sys1yagi.mastodon4j.api.method.Accounts
import com.sys1yagi.mastodon4j.api.method.Public
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import shibafu.yukari.R
import shibafu.yukari.activity.base.ActionBarYukariBase
import shibafu.yukari.common.bitmapcache.ImageLoaderTask
import shibafu.yukari.core.App
import shibafu.yukari.database.AuthUserRecord
import shibafu.yukari.database.Provider
import shibafu.yukari.databinding.ActivityMastodonFollowBinding
import shibafu.yukari.databinding.RowFollowBinding
import shibafu.yukari.mastodon.api.AccountsEx
import shibafu.yukari.mastodon.entity.DonUser

class MastodonFollowActivity : ActionBarYukariBase() {
    private lateinit var binding: ActivityMastodonFollowBinding

    private val model: FollowViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMastodonFollowBinding.inflate(layoutInflater)
        setContentView(binding.root)

        supportActionBar?.apply {
            subtitle = "@${model.targetUser.screenName}"
            setDisplayHomeAsUpEnabled(true)
        }

        val adapter = object : RelationStatusAdapter(model.targetUser) {
            override fun onRelationClaim(claim: RelationState.Updating) {
                model.postClaim(claim)
            }
        }
        binding.recyclerView.adapter = adapter

        adapter.submitList(model.relations.value)
        model.relations.observe(this) {
            adapter.submitList(it)
        }

        model.loadRelations()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    sealed class RelationState {
        data class Loading(override val userRecord: AuthUserRecord) : RelationState() {
            fun success(relationship: Relationship) = Loaded(userRecord, relationship)
            fun failed() = LoadFailed(userRecord)
        }
        data class LoadFailed(override val userRecord: AuthUserRecord) : RelationState() {
            fun loading() = Loading(userRecord)
        }
        data class Loaded(override val userRecord: AuthUserRecord, val relationship: Relationship) : RelationState() {
            fun claim(claim: RelationClaim) = Updating(userRecord, relationship, claim)
            fun update(newRelationship: Relationship) = Loaded(userRecord, newRelationship)
        }
        data class Updating(override val userRecord: AuthUserRecord, val relationship: Relationship, val claim: RelationClaim) : RelationState() {
            fun success(newRelationship: Relationship) = Loaded(userRecord, newRelationship)
            fun failed() = Loaded(userRecord, relationship)
        }

        abstract val userRecord: AuthUserRecord
    }

    enum class RelationClaim {
        /** フォロー解除 */
        UNFOLLOW,
        /** フォローする */
        FOLLOW,
        /** ブロックする */
        BLOCK,
        /** ブロック解除 */
        UNBLOCK,
        /** 相手にフォローを解除させる */
        REMOVE,
    }

    class FollowViewModel(application: Application, state: SavedStateHandle) : AndroidViewModel(application) {
        private val app get() = getApplication() as App

        val targetUser = state.get<DonUser>(EXTRA_TARGET_USER)!!
        val relations = MutableLiveData<List<RelationState>>(
            App.getInstance(application).accountManager.users
                .filter { it.Provider.apiType == Provider.API_MASTODON }
                .map { RelationState.Loading(it) }
        )

        fun loadRelations() {
            val api = app.getProviderApi(Provider.API_MASTODON)
            relations.value!!.forEach { current ->
                val current = when (current) {
                    is RelationState.Loading -> current
                    is RelationState.LoadFailed -> current.loading()
                    else -> return@forEach
                }

                viewModelScope.launch(Dispatchers.IO) {
                    val client = api.getApiClient(current.userRecord) as MastodonClient
                    val newRelation = runCatching {
                        // FIXME: えっなんでDonUser#urlがnullableなの
                        val searchResult = Public(client).getSearch(targetUser.url!!, true).execute()
                        val account = searchResult.accounts.firstOrNull() ?: return@runCatching current.failed()

                        val relationship = Accounts(client).getRelationships(listOf(account.id)).execute().first()
                        current.success(relationship)
                    }.getOrElse {
                        it.printStackTrace()
                        current.failed()
                    }

                    launch(Dispatchers.Main) {
                        relations.value = relations.value!!.map { if (it.userRecord == newRelation.userRecord) newRelation else it }
                    }
                }
            }
        }

        fun postClaim(claimState: RelationState.Updating) = viewModelScope.launch {
            relations.value = relations.value!!.map { if (it.userRecord == claimState.userRecord) claimState else it }

            val api = app.getProviderApi(Provider.API_MASTODON)
            val client = api.getApiClient(claimState.userRecord) as MastodonClient
            val targetAccountId = claimState.relationship.id

            val result = async(Dispatchers.IO) {
                runCatching {
                    when (claimState.claim) {
                        RelationClaim.UNFOLLOW -> Accounts(client).postUnFollow(targetAccountId).execute()
                        RelationClaim.FOLLOW -> Accounts(client).postFollow(targetAccountId).execute()
                        RelationClaim.BLOCK -> Accounts(client).postBlock(targetAccountId).execute()
                        RelationClaim.UNBLOCK -> Accounts(client).postUnblock(targetAccountId).execute()
                        RelationClaim.REMOVE -> AccountsEx(client).postRemoveFromFollowers(targetAccountId).execute()
                    }
                }
            }.await()

            val newStatus = result.fold({
                val message = when (claimState.claim) {
                    RelationClaim.UNFOLLOW -> app.getString(R.string.follow_control_unfollow_succeed, claimState.userRecord.ScreenName)
                    RelationClaim.FOLLOW -> app.getString(R.string.follow_control_follow_succeed, claimState.userRecord.ScreenName)
                    RelationClaim.BLOCK -> app.getString(R.string.follow_control_block_succeed, claimState.userRecord.ScreenName)
                    RelationClaim.UNBLOCK -> app.getString(R.string.follow_control_unblock_succeed, claimState.userRecord.ScreenName)
                    RelationClaim.REMOVE -> app.getString(R.string.follow_control_remove_from_followers_succeed, claimState.userRecord.ScreenName)
                }
                Toast.makeText(app, message, Toast.LENGTH_SHORT).show()

                claimState.success(it)
            }, {
                it.printStackTrace()

                val message = when (claimState.claim) {
                    RelationClaim.UNFOLLOW -> app.getString(R.string.follow_control_unfollow_failed, claimState.userRecord.ScreenName)
                    RelationClaim.FOLLOW -> app.getString(R.string.follow_control_follow_failed, claimState.userRecord.ScreenName)
                    RelationClaim.BLOCK -> app.getString(R.string.follow_control_block_failed, claimState.userRecord.ScreenName)
                    RelationClaim.UNBLOCK -> app.getString(R.string.follow_control_unblock_failed, claimState.userRecord.ScreenName)
                    RelationClaim.REMOVE -> app.getString(R.string.follow_control_remove_from_followers_failed, claimState.userRecord.ScreenName)
                }
                Toast.makeText(app, message, Toast.LENGTH_SHORT).show()

                claimState.failed()
            })

            relations.value = relations.value!!.map { if (it.userRecord == newStatus.userRecord) newStatus else it }

            // フォローリクエスト中になった場合、自動承認機能ですぐにフォロー中に更新されるかもしれないので、しばらくの間ポーリングする
            if (newStatus.relationship.isRequested) {
                launch(Dispatchers.IO) polling@{
                    var waitMilliSeconds = 1000L
                    while (waitMilliSeconds <= 32000L) {
                        delay(waitMilliSeconds)

                        try {
                            val newRelationship = Accounts(client).getRelationships(listOf(targetAccountId)).execute().first()
                            if (!newRelationship.isRequested) {
                                val refreshedStatus = newStatus.update(newRelationship)
                                launch(Dispatchers.Main) {
                                    relations.value = relations.value!!.map { if (it.userRecord == refreshedStatus.userRecord) refreshedStatus else it }
                                }
                                return@polling
                            }
                        } catch (e: Mastodon4jRequestException) {
                            e.printStackTrace()
                        }

                        waitMilliSeconds *= 2
                    }
                }
            }
        }
    }

    open class RelationStatusAdapter(private val targetUser: DonUser) : ListAdapter<RelationState, RelationStatusAdapter.ViewHolder>(DIFF_CALLBACK) {
        class ViewHolder(private val binding: RowFollowBinding) : RecyclerView.ViewHolder(binding.root) {
            fun bindTo(adapter: RelationStatusAdapter, targetUser: DonUser, relation: RelationState) {
                binding.ivFoOwn.tag = relation.userRecord.ProfileImageUrl
                ImageLoaderTask.loadProfileIcon(binding.root.context, binding.ivFoOwn, relation.userRecord.ProfileImageUrl)

                binding.ivFoTarget.tag = targetUser.biggerProfileImageUrl
                ImageLoaderTask.loadProfileIcon(binding.root.context, binding.ivFoTarget, targetUser.biggerProfileImageUrl)

                if (targetUser.identicalUrl!! == relation.userRecord.IdenticalUrl) {
                    binding.btnFollow.visibility = View.GONE
                    binding.ibMenu.visibility = View.GONE
                    binding.tvFoYou.visibility = View.VISIBLE
                    return
                }
                binding.btnFollow.visibility = View.VISIBLE
                binding.ibMenu.visibility = View.VISIBLE
                binding.tvFoYou.visibility = View.GONE

                when (relation) {
                    is RelationState.Loading -> {
                        binding.btnFollow.setText(R.string.follow_control_loading)
                        binding.btnFollow.isEnabled = false
                        binding.ibMenu.isEnabled = false
                        binding.ivFollowStatus.setImageResource(R.drawable.ic_f_not)
                    }
                    is RelationState.LoadFailed -> {
                        binding.btnFollow.setText(R.string.follow_control_load_failed)
                        binding.btnFollow.isEnabled = false
                        binding.ibMenu.isEnabled = false
                        binding.ivFollowStatus.setImageResource(R.drawable.ic_f_not)
                    }
                    is RelationState.Loaded -> {
                        binding.btnFollow.setText(
                            when {
                                relation.relationship.isBlocking -> R.string.follow_control_unblock
                                relation.relationship.isFollowing -> R.string.follow_control_unfollow
                                relation.relationship.isRequested -> R.string.follow_control_requested
                                else -> R.string.follow_control_follow
                            }
                        )
                        binding.btnFollow.isEnabled = true
                        binding.ibMenu.isEnabled = true
                        binding.ivFollowStatus.setImageResource(
                            if ((relation.relationship.isFollowing || relation.relationship.isRequested) && relation.relationship.isFollowedBy) {
                                R.drawable.ic_f_friend
                            } else if (relation.relationship.isFollowing || relation.relationship.isRequested) {
                                R.drawable.ic_f_follow
                            } else if (relation.relationship.isFollowedBy) {
                                R.drawable.ic_f_follower
                            } else {
                                R.drawable.ic_f_not
                            }
                        )
                    }
                    is RelationState.Updating -> {
                        binding.btnFollow.setText(
                            when (relation.claim) {
                                RelationClaim.UNFOLLOW -> R.string.follow_control_unfollow_processing
                                RelationClaim.FOLLOW -> R.string.follow_control_follow_processing
                                RelationClaim.BLOCK -> R.string.follow_control_block_processing
                                RelationClaim.UNBLOCK -> R.string.follow_control_unblock_processing
                                RelationClaim.REMOVE -> R.string.follow_control_remove_from_followers_processing
                            }
                        )
                        binding.btnFollow.isEnabled = false
                        binding.ibMenu.isEnabled = false
                        binding.ivFollowStatus.setImageResource(
                            when (relation.claim) {
                                RelationClaim.UNFOLLOW -> if (relation.relationship.isFollowedBy) {
                                    R.drawable.ic_f_follower
                                } else {
                                    R.drawable.ic_f_not
                                }
                                RelationClaim.FOLLOW -> if (relation.relationship.isFollowedBy) {
                                    R.drawable.ic_f_friend
                                } else {
                                    R.drawable.ic_f_follow
                                }
                                RelationClaim.BLOCK -> R.drawable.ic_f_not
                                RelationClaim.UNBLOCK -> R.drawable.ic_f_not
                                RelationClaim.REMOVE -> R.drawable.ic_f_not
                            }
                        )
                    }
                }

                binding.btnFollow.setOnClickListener {
                    if (relation !is RelationState.Loaded) {
                        return@setOnClickListener
                    }

                    val claim = when {
                        relation.relationship.isBlocking -> relation.claim(RelationClaim.UNBLOCK)
                        relation.relationship.isFollowing -> relation.claim(RelationClaim.UNFOLLOW)
                        else -> relation.claim(RelationClaim.FOLLOW)
                    }
                    adapter.onRelationClaim(claim)
                }
                binding.ibMenu.setOnClickListener {
                    if (relation !is RelationState.Loaded) {
                        return@setOnClickListener
                    }

                    val menu = PopupMenu(it.context, it)
                    menu.inflate(R.menu.follow)

                    val blockItem = menu.menu.findItem(R.id.action_block)
                    blockItem.isEnabled = !relation.relationship.isBlocking

                    val removeItem = menu.menu.findItem(R.id.action_remove)
                    removeItem.isVisible = true
                    removeItem.isEnabled = !relation.relationship.isBlocking && relation.relationship.isFollowedBy

                    menu.setOnMenuItemClickListener { item ->
                        when (item.itemId) {
                            R.id.action_block -> {
                                adapter.onRelationClaim(relation.claim(RelationClaim.BLOCK))
                                true
                            }
                            R.id.action_remove -> {
                                adapter.onRelationClaim(relation.claim(RelationClaim.REMOVE))
                                true
                            }
                            else -> false
                        }
                    }
                    menu.show()
                }
            }
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            return ViewHolder(RowFollowBinding.inflate(LayoutInflater.from(parent.context), parent, false))
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            holder.bindTo(this, targetUser, getItem(position))
        }

        open fun onRelationClaim(claim: RelationState.Updating) {}

        companion object {
            private val DIFF_CALLBACK = object : DiffUtil.ItemCallback<RelationState>() {
                override fun areItemsTheSame(oldItem: RelationState, newItem: RelationState): Boolean {
                    return oldItem.userRecord.InternalId == newItem.userRecord.InternalId
                }

                override fun areContentsTheSame(oldItem: RelationState, newItem: RelationState): Boolean {
                    return oldItem == newItem
                }
            }
        }
    }

    companion object {
        private const val EXTRA_TARGET_USER = "targetUser"

        fun newIntent(context: Context, targetUser: DonUser): Intent {
            return Intent(context, MastodonFollowActivity::class.java).apply {
                putExtra(EXTRA_TARGET_USER, targetUser as Parcelable)
            }
        }
    }
}
