package shibafu.yukari.fragment;

import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.app.DialogFragment;
import android.support.v7.app.AlertDialog;
import android.support.v7.widget.PopupMenu;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;
import shibafu.yukari.R;
import shibafu.yukari.common.bitmapcache.ImageLoaderTask;
import shibafu.yukari.entity.User;
import shibafu.yukari.database.AuthUserRecord;
import twitter4j.Relationship;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by shibafu on 14/01/25.
 */
public class FollowDialogFragment extends DialogFragment {

    public static final String ARGUMENT_TARGET = "target";
    public static final String ARGUMENT_KNOWN_RELATIONS = "known_relations";
    public static final String ARGUMENT_ALL_R4S = "r4s";
    public static final String ARGUMENT_ALLOW_R4S = "allow_r4s";

    public static final int RELATION_NONE = 0;
    public static final int RELATION_FOLLOW = 1;
    public static final int RELATION_BLOCK = 2;
    public static final int RELATION_PRE_R4S = 3;
    public static final int RELATION_UNBLOCK = 4;
    public static final int RELATION_CUTOFF = 5;

    private AlertDialog dialog;

    private List<ListEntry> entryList = new ArrayList<>();
    private User targetUser;
    private ListView listView;
    private boolean allowR4s;

    public static FollowDialogFragment newInstance(User targetUser, ArrayList<KnownRelationship> knownRelations) {
        return newInstance(targetUser, knownRelations, true, false);
    }

    public static FollowDialogFragment newInstance(User targetUser, ArrayList<KnownRelationship> knownRelations, boolean allowR4s) {
        return newInstance(targetUser, knownRelations, allowR4s, false);
    }

    public static FollowDialogFragment newInstance(User targetUser, ArrayList<KnownRelationship> knownRelations, boolean allowR4s, boolean allR4s) {
        FollowDialogFragment fragment = new FollowDialogFragment();
        Bundle args = new Bundle();

        args.putSerializable(ARGUMENT_TARGET, targetUser);
        args.putSerializable(ARGUMENT_KNOWN_RELATIONS, knownRelations);
        args.putBoolean(ARGUMENT_ALLOW_R4S, allowR4s);
        args.putBoolean(ARGUMENT_ALL_R4S, allR4s);

        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        Bundle args = getArguments();
        targetUser = (User) args.getSerializable(ARGUMENT_TARGET);
        allowR4s = args.getBoolean(ARGUMENT_ALLOW_R4S);

        listView = new ListView(getActivity());
        listView.setChoiceMode(AbsListView.CHOICE_MODE_NONE);

        List<KnownRelationship> relationships = (List<KnownRelationship>) args.getSerializable(ARGUMENT_KNOWN_RELATIONS);
        for (KnownRelationship entry : relationships) {
            entryList.add(new ListEntry(entry));
        }

        if (args.getBoolean(ARGUMENT_ALL_R4S, false)) {
            if ("toshi_a".equals(targetUser.getScreenName())) {
                Toast.makeText(getActivity(), "ｱｱｱｯwwwwミクッター作者に全垢r4sかまそうとしてるｩwwwwwwwwwwwww\n\n(toshi_a proof を発動しました)", Toast.LENGTH_LONG).show();
            } else {
                for (ListEntry e : entryList) {
                    e.afterRelation = RELATION_PRE_R4S;
                }
            }
        }

        Adapter adapter = new Adapter(getActivity(), entryList);
        listView.setAdapter(adapter);

        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        builder.setTitle("フォロー状態 @" + targetUser.getScreenName());
        builder.setView(listView);
        builder.setPositiveButton("OK", (dialogInterface, i) -> {
            FollowDialogCallback callback = (FollowDialogCallback) getTargetFragment();
            if (callback != null) {
                List<RelationClaim> claims = new ArrayList<>();
                for (ListEntry entry : entryList) {
                    if (entry.beforeRelation != entry.afterRelation) {
                        claims.add(new RelationClaim(entry.getUserRecord(), targetUser.getId(), entry.afterRelation));
                    }
                }
                callback.onChangedRelationships(claims);
            }
        });
        builder.setNegativeButton("キャンセル", (dialogInterface, i) -> {
        });

        dialog = builder.create();
        return dialog;
    }

    /**
     * 操作結果のコールバック用インターフェース
     */
    public interface FollowDialogCallback {
        void onChangedRelationships(List<RelationClaim> claims);
    }

    /**
     * 現在のフォロー関係
     */
    public static class KnownRelationship implements Serializable {
        private AuthUserRecord userRecord;
        private int relation;
        private boolean isMyself;
        private boolean isFollower;

        public KnownRelationship(AuthUserRecord userRecord, int relation, boolean isMyself, boolean isFollower) {
            this.userRecord = userRecord;
            this.relation = relation;
            this.isMyself = isMyself;
            this.isFollower = isFollower;
        }

        public KnownRelationship(AuthUserRecord userRecord, Relationship relationship) {
            this.userRecord = userRecord;

            if (relationship.isSourceBlockingTarget()) {
                relation = RELATION_BLOCK;
            } else if (relationship.isSourceFollowingTarget()) {
                relation = RELATION_FOLLOW;
            } else {
                relation = RELATION_NONE;
            }

            isMyself = userRecord.NumericId == relationship.getTargetUserId();
            isFollower = relationship.isTargetFollowingSource();
        }
    }

    /**
     * フォロー関係の変更要求
     */
    public static class RelationClaim {
        private AuthUserRecord sourceAccount;
        private long targetUser;
        private int newRelation;

        public RelationClaim(AuthUserRecord sourceAccount, long targetUser, int newRelation) {
            this.sourceAccount = sourceAccount;
            this.targetUser = targetUser;
            this.newRelation = newRelation;
        }

        public AuthUserRecord getSourceAccount() {
            return sourceAccount;
        }

        public long getTargetUser() {
            return targetUser;
        }

        public int getNewRelation() {
            return newRelation;
        }
    }

    private static class ListEntry {
        private AuthUserRecord userRecord;
        private int beforeRelation;
        private int afterRelation;
        private boolean isMyself;
        private boolean isFollower;

        private ListEntry(KnownRelationship known) {
            this.userRecord = known.userRecord;
            this.beforeRelation = known.relation;
            this.afterRelation = beforeRelation;
            this.isMyself = known.isMyself;
            this.isFollower = known.isFollower;
        }

        public AuthUserRecord getUserRecord() {
            return userRecord;
        }

        public int getBeforeRelation() {
            return beforeRelation;
        }

        public int getAfterRelation() {
            return afterRelation;
        }

        public boolean isMyself() {
            return isMyself;
        }

        public boolean isFollower() {
            return isFollower;
        }
    }

    private class Adapter extends ArrayAdapter<ListEntry> {

        private LayoutInflater inflater;
        private boolean visibleCutoff = false;

        public Adapter(Context context, List<ListEntry> objects) {
            super(context, 0, objects);
            inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            visibleCutoff = PreferenceManager.getDefaultSharedPreferences(context).getBoolean("allow_cutoff", false);
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            View v = convertView;
            if (v == null) {
                v = inflater.inflate(R.layout.row_follow, null);
            }

            ListEntry e = getItem(position);
            if (e != null) {
                ImageView ivOwn = (ImageView) v.findViewById(R.id.ivFoOwn);
                ivOwn.setTag(e.getUserRecord().ProfileImageUrl);
                ImageLoaderTask.loadProfileIcon(getContext(), ivOwn, e.getUserRecord().ProfileImageUrl);

                ImageView ivTarget = (ImageView) v.findViewById(R.id.ivFoTarget);
                ivTarget.setTag(targetUser.getBiggerProfileImageUrl());
                ImageLoaderTask.loadProfileIcon(getContext(), ivTarget, targetUser.getBiggerProfileImageUrl());

                final ImageView ivRelation = (ImageView) v.findViewById(R.id.ivFollowStatus);
                final Button btnFollow = (Button) v.findViewById(R.id.btnFollow);
                btnFollow.setTag(e);
                btnFollow.setOnClickListener(view -> {
                    final ListEntry e1 = (ListEntry) view.getTag();

                    switch (e1.afterRelation) {
                        case RELATION_NONE:
                        case RELATION_UNBLOCK:
                            e1.afterRelation = RELATION_FOLLOW;
                            break;
                        case RELATION_BLOCK:
                            e1.afterRelation = RELATION_UNBLOCK;
                            break;
                        case RELATION_FOLLOW:
                        case RELATION_PRE_R4S:
                            if (e1.beforeRelation == RELATION_BLOCK) {
                                e1.afterRelation = RELATION_UNBLOCK;
                            }
                            else {
                                e1.afterRelation = RELATION_NONE;
                            }
                            break;
                        default:
                            e1.afterRelation = e1.beforeRelation;
                            break;
                    }

                    setStatus(e1, btnFollow, ivRelation);
                });
                setStatus(e, btnFollow, ivRelation);

                ImageButton ibMenu = (ImageButton) v.findViewById(R.id.ibMenu);
                ibMenu.setTag(e);
                ibMenu.setOnClickListener(view -> {
                    final ListEntry e1 = (ListEntry) view.getTag();

                    PopupMenu popupMenu = new PopupMenu(getContext(), view);
                    popupMenu.inflate(R.menu.follow);
                    popupMenu.getMenu().findItem(R.id.action_report).setVisible(allowR4s);
                    popupMenu.getMenu().findItem(R.id.action_cutoff).setVisible(visibleCutoff);
                    popupMenu.setOnMenuItemClickListener(menuItem -> {
                        switch (menuItem.getItemId()) {
                            case R.id.action_block:
                                e1.afterRelation = RELATION_BLOCK;
                                setStatus(e1, btnFollow, ivRelation);
                                return true;
                            case R.id.action_report:
                                e1.afterRelation = RELATION_PRE_R4S;
                                setStatus(e1, btnFollow, ivRelation);
                                return true;
                            case R.id.action_cutoff:
                                e1.afterRelation = RELATION_CUTOFF;
                                setStatus(e1, btnFollow, ivRelation);
                                return true;
                        }
                        return true;
                    });
                    popupMenu.show();
                });

                TextView tvFoYou = (TextView) v.findViewById(R.id.tvFoYou);
                if (e.isMyself()) {
                    btnFollow.setVisibility(View.GONE);
                    ibMenu.setVisibility(View.GONE);
                    tvFoYou.setVisibility(View.VISIBLE);
                } else {
                    btnFollow.setVisibility(View.VISIBLE);
                    ibMenu.setVisibility(View.VISIBLE);
                    tvFoYou.setVisibility(View.GONE);
                }
            }

            return v;
        }

        private void setStatus(ListEntry e, Button btnFollow, ImageView ivRelation) {
            if (e.getAfterRelation() == RELATION_BLOCK) {
                btnFollow.setText("ブロック中");
                ivRelation.setImageResource(R.drawable.ic_f_not);
            }
            else if (e.getAfterRelation() == RELATION_PRE_R4S) {
                btnFollow.setText("報告取りやめ");
                ivRelation.setImageResource(R.drawable.ic_f_not);
            }
            else if (e.getAfterRelation() == RELATION_CUTOFF) {
                btnFollow.setText("ブロ解中断");
                ivRelation.setImageResource(R.drawable.ic_f_not);
            }
            else if (e.getAfterRelation() == RELATION_FOLLOW) {
                btnFollow.setText("フォロー解除");
                if (e.isFollower) {
                    ivRelation.setImageResource(R.drawable.ic_f_friend);
                }
                else {
                    ivRelation.setImageResource(R.drawable.ic_f_follow);
                }
            }
            else if (e.isFollower) {
                btnFollow.setText("フォロー");
                ivRelation.setImageResource(R.drawable.ic_f_follower);
            }
            else {
                btnFollow.setText("フォロー");
                ivRelation.setImageResource(R.drawable.ic_f_not);
            }
        }
    }
}
