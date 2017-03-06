package shibafu.yukari.activity;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.text.Html;
import android.text.method.LinkMovementMethod;
import android.text.util.Linkify;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.Unbinder;
import info.shibafu528.twpfparser.TwiProfile;
import info.shibafu528.twpfparser.TwiProfileFactory;
import shibafu.yukari.R;
import shibafu.yukari.activity.base.ActionBarYukariBase;
import shibafu.yukari.common.async.ParallelAsyncTask;
import shibafu.yukari.common.bitmapcache.ImageLoaderTask;

import java.io.IOException;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Created by shibafu on 14/12/18.
 */
public class TwpfActivity extends ActionBarYukariBase {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_parent);
        if (savedInstanceState == null) {
            Intent intent = getIntent();
            if (intent.getData().getLastPathSegment() == null) {
                Toast.makeText(getApplicationContext(), "対応URLではありません.", Toast.LENGTH_SHORT).show();
                startActivity(new Intent(Intent.ACTION_VIEW, intent.getData()));
                finish();
                return;
            } else if (intent.getData().getPathSegments().size() > 1) {
                startActivity(new Intent(Intent.ACTION_VIEW, intent.getData()).addCategory(Intent.CATEGORY_BROWSABLE));
                finish();
                return;
            }

            TwpfFragment fragment = new TwpfFragment();
            Bundle args = new Bundle();
            args.putString(TwpfFragment.ARG_SCREEN_NAME, intent.getData().getLastPathSegment());
            fragment.setArguments(args);

            getSupportFragmentManager().beginTransaction()
                    .replace(R.id.frame, fragment)
                    .commit();
        }
    }

    @Override
    public void onServiceConnected() {}

    @Override
    public void onServiceDisconnected() {}

    public static class TwpfFragment extends Fragment {
        public static final String ARG_SCREEN_NAME = "screen_name";

        private static final String TAG_TARGET_PERSONAL = "personal_tag";
        private static final String TAG_TARGET_LIKE = "like_tag";
        private static final String TAG_TARGET_DISLIKE = "dislike_tag";
        private static final String TAG_TARGET_FREE = "freedom_tag";

        private TwiProfile profile;

        @BindView(R.id.ivProfileIcon)         ImageView ivProfileIcon;
        @BindView(R.id.tvProfileName)         TextView  tvName;
        @BindView(R.id.tvProfileScreenName)   TextView  tvScreenName;
        @BindView(R.id.tvProfileBio)          TextView  tvDescription;
        @BindView(R.id.tvProfileLongBio)      TextView  tvLongDescription;
        @BindView(R.id.tvProfileLocation)     TextView  tvLocation;
        @BindView(R.id.tvProfileWeb)          TextView  tvWeb;
        @BindView(R.id.tvProfilePersonalTags) TextView  tvPersonalTags;
        @BindView(R.id.tvProfileLike)         TextView  tvLikeTags;
        @BindView(R.id.tvProfileDislike)      TextView  tvDislikeTags;
        @BindView(R.id.tvProfileFreeTags)     TextView  tvFreeTags;
        private Unbinder unbinder;

        public TwpfFragment() {
            setRetainInstance(true);
        }

        @Override
        public void onAttach(Activity activity) {
            super.onAttach(activity);
            if (profile == null) {
                Bundle args = getArguments();
                new ParallelAsyncTask<String, Void, TwiProfile>() {

                    @Override
                    protected TwiProfile doInBackground(String... params) {
                        try {
                            return TwiProfileFactory.getTwiProfile(params[0]);
                        } catch (IOException e) {
                            e.printStackTrace();
                            return null;
                        }
                    }

                    @Override
                    protected void onPostExecute(TwiProfile twiProfile) {
                        super.onPostExecute(twiProfile);
                        if (twiProfile == null) {
                            if (getActivity() != null) {
                                Toast.makeText(getActivity(), "通信エラー", Toast.LENGTH_SHORT).show();
                                getActivity().finish();
                            }
                            return;
                        }
                        profile = twiProfile;

                        if (isDetached()) {
                            return;
                        }
                        updateView();
                    }
                }.executeParallel(args.getString(ARG_SCREEN_NAME));
            }
        }

        @Override
        public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
            View v = inflater.inflate(R.layout.fragment_twpf, container, false);
            unbinder = ButterKnife.bind(this, v);
            if (profile != null) {
                updateView();
            }
            return v;
        }

        @Override
        public void onDestroyView() {
            super.onDestroyView();
            unbinder.unbind();
        }

        private boolean isNull(Object... obj) {
            for (Object o : obj) {
                if (o == null) return true;
            }
            return false;
        }

        private void updateView() {
            if (isNull(ivProfileIcon,
                    tvName,
                    tvScreenName,
                    tvDescription,
                    tvLongDescription,
                    tvLocation,
                    tvWeb,
                    tvPersonalTags,
                    tvLikeTags,
                    tvDislikeTags,
                    tvFreeTags)) return;

            ImageLoaderTask.loadProfileIcon(getActivity(), ivProfileIcon, profile.getProfileImageUrl());
            tvName.setText(profile.getName());
            tvScreenName.setText(profile.getScreenName());
            tvDescription.setMovementMethod(LinkMovementMethod.getInstance());
            tvDescription.setText(Html.fromHtml(profile.getBiographyHtml()));
            tvLongDescription.setMovementMethod(LinkMovementMethod.getInstance());
            tvLongDescription.setText(Html.fromHtml(profile.getMoreBiographyHtml()));
            tvLocation.setText(profile.getLocation());
            tvWeb.setText(profile.getWeb());
            setTags(tvPersonalTags, profile.getPersonalTags(), TAG_TARGET_PERSONAL);
            setTags(tvLikeTags, profile.getLikeTags(), TAG_TARGET_LIKE);
            setTags(tvDislikeTags, profile.getDislikeTags(), TAG_TARGET_DISLIKE);
            setTags(tvFreeTags, profile.getFreeTags(), TAG_TARGET_FREE);
        }

        private void setTags(TextView textView, Set<String> tags, String target) {
            StringBuffer sb = new StringBuffer();
            for (String tag : tags) {
                if (sb.length() > 0) {
                    sb.append(", ");
                }
                sb.append(tag);
            }
            textView.setText(sb);
            for (String tag : tags) {
                Linkify.addLinks(textView, Pattern.compile(Pattern.quote(tag)), "http://twpf.jp/search/profile?target=" + target + "&keyword=");
            }
        }
    }
}
