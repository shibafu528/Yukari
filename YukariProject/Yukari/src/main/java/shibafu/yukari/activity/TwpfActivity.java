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

import java.io.IOException;
import java.util.Set;
import java.util.regex.Pattern;

import butterknife.ButterKnife;
import butterknife.InjectView;
import info.shibafu528.twpfparser.TwiProfile;
import info.shibafu528.twpfparser.TwiProfileFactory;
import shibafu.yukari.af2015.R;
import shibafu.yukari.activity.base.ActionBarYukariBase;
import shibafu.yukari.common.async.ParallelAsyncTask;
import shibafu.yukari.common.bitmapcache.ImageLoaderTask;

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

        @InjectView(R.id.ivProfileIcon)         ImageView ivProfileIcon;
        @InjectView(R.id.tvProfileName)         TextView  tvName;
        @InjectView(R.id.tvProfileScreenName)   TextView  tvScreenName;
        @InjectView(R.id.tvProfileBio)          TextView  tvDescription;
        @InjectView(R.id.tvProfileLongBio)      TextView  tvLongDescription;
        @InjectView(R.id.tvProfileLocation)     TextView  tvLocation;
        @InjectView(R.id.tvProfileWeb)          TextView  tvWeb;
        @InjectView(R.id.tvProfilePersonalTags) TextView  tvPersonalTags;
        @InjectView(R.id.tvProfileLike)         TextView  tvLikeTags;
        @InjectView(R.id.tvProfileDislike)      TextView  tvDislikeTags;
        @InjectView(R.id.tvProfileFreeTags)     TextView  tvFreeTags;

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
                            Toast.makeText(getActivity(), "通信エラー", Toast.LENGTH_SHORT).show();
                            getActivity().finish();
                        } else {
                            profile = twiProfile;
                            updateView();
                        }
                    }
                }.executeParallel(args.getString(ARG_SCREEN_NAME));
            }
        }

        @Override
        public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
            View v = inflater.inflate(R.layout.fragment_twpf, container, false);
            ButterKnife.inject(this, v);
            if (profile != null) {
                updateView();
            }
            return v;
        }

        @Override
        public void onDestroyView() {
            super.onDestroyView();
            ButterKnife.reset(this);
        }

        private void updateView() {
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
