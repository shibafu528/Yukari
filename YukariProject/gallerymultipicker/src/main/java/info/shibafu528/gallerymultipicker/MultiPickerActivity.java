package info.shibafu528.gallerymultipicker;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentTransaction;
import android.support.v4.app.ListFragment;
import android.support.v7.app.ActionBar;
import android.support.v7.app.ActionBarActivity;
import android.text.TextUtils;
import android.util.Pair;
import android.view.Display;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.CursorAdapter;
import android.widget.FrameLayout;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Created by shibafu on 14/11/08.
 */
public class MultiPickerActivity extends ActionBarActivity{
    private static final int REQUEST_GALLERY = 0;
    private static final int REQUEST_CAMERA = 1;

    public static final String EXTRA_PICK_LIMIT = "max";
    public static final String EXTRA_URIS = "uris";
    public static final String EXTRA_THEME = "theme";
    public static final String EXTRA_ICON_THEME = "icon_theme";
    public static final String EXTRA_CLOSE_ENTER_ANIMATION = "close_enter_anim";
    public static final String EXTRA_CLOSE_EXIT_ANIMATION = "close_exit_anim";

    private String mMenuIconTheme = null;
    private int mCloseEnterAnimation;
    private int mCloseExitAnimation;

    private int mPickLimit = 1;
    private Uri mCameraTemp;

    private LongArray mSelectedIds = new LongArray();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        {
            Intent intent = getIntent();
            int themeResId = intent.getIntExtra(EXTRA_THEME, -1);
            if (themeResId > -1) {
                setTheme(themeResId);
            }
            mMenuIconTheme = intent.getStringExtra(EXTRA_ICON_THEME);
            mCloseEnterAnimation = intent.getIntExtra(EXTRA_CLOSE_ENTER_ANIMATION, 0);
            mCloseExitAnimation = intent.getIntExtra(EXTRA_CLOSE_EXIT_ANIMATION, 0);
            if (mMenuIconTheme == null) try {
                ActivityInfo info = getPackageManager().getActivityInfo(getComponentName(), PackageManager.GET_META_DATA);
                mMenuIconTheme = info.metaData.getString("info.shibafu528.gallerymultipicker.ICON_THEME");
            } catch (PackageManager.NameNotFoundException ignored) {}
        }
        super.onCreate(savedInstanceState);
        setContentView(R.layout.info_shibafu528_gallerymultipicker_container);
        ActionBar actionBar = getSupportActionBar();
        actionBar.setDisplayHomeAsUpEnabled(true);
        actionBar.setDisplayShowHomeEnabled(false);
        actionBar.setTitle(R.string.info_shibafu528_gallerymultipicker_title);
        if (savedInstanceState == null) {
            getSupportFragmentManager().beginTransaction()
                    .replace(android.R.id.content, new AlbumFragment())
                    .commit();
        }
        mPickLimit = getIntent().getIntExtra(EXTRA_PICK_LIMIT, 1);
        updateLimitCount();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putLongArray(EXTRA_URIS, mSelectedIds.toPrimitive());
    }

    @Override
    protected void onRestoreInstanceState(Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        mSelectedIds.addAll(savedInstanceState.getLongArray(EXTRA_URIS));
        updateLimitCount();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.info_shibafu528_gallerymultipicker_menu, menu);

        MenuItem gallery = menu.findItem(R.id.info_shibafu528_gallerymultipicker_action_gallery);
        MenuItem shot = menu.findItem(R.id.info_shibafu528_gallerymultipicker_action_take_shot);

        if (mMenuIconTheme != null) switch (mMenuIconTheme.toLowerCase()) {
            case "light":
                gallery.setIcon(R.drawable.info_shibafu528_gallerymultipicker_ic_action_gallery_light);
                shot.setIcon(R.drawable.info_shibafu528_gallerymultipicker_ic_action_take_shot_light);
                break;
            case "dark":
                gallery.setIcon(R.drawable.info_shibafu528_gallerymultipicker_ic_action_gallery_dark);
                shot.setIcon(R.drawable.info_shibafu528_gallerymultipicker_ic_action_take_shot_dark);
                break;
        }
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        super.onOptionsItemSelected(item);
        int id = item.getItemId();
        if (id == android.R.id.home) {
            if (getSupportFragmentManager().getBackStackEntryCount() > 0) {
                getSupportFragmentManager().popBackStack();
            } else {
                setResult(RESULT_CANCELED);
                finish();
            }
        } else if (id == R.id.info_shibafu528_gallerymultipicker_action_accept) {
            accept(getSelectedUris());
        } else if (id == R.id.info_shibafu528_gallerymultipicker_action_gallery) {
            Intent intent = new Intent(Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT ? Intent.ACTION_PICK : Intent.ACTION_GET_CONTENT);
            intent.setType("image/*");
            startActivityForResult(intent, REQUEST_GALLERY);
        } else if (id == R.id.info_shibafu528_gallerymultipicker_action_take_shot) {
            boolean existExternal = Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED);
            if (!existExternal) {
                Toast.makeText(this, R.string.info_shibafu528_gallerymultipicker_storage_error, Toast.LENGTH_SHORT).show();
                return true;
            } else {
                File extDestDir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM), "Camera");
                if (!extDestDir.exists()) {
                    extDestDir.mkdirs();
                }
                SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd_HHmmss");
                String fileName = sdf.format(new Date(System.currentTimeMillis()));
                File destFile = new File(extDestDir.getPath(), fileName + ".jpg");
                ContentValues values = new ContentValues();
                values.put(MediaStore.Images.Media.TITLE, fileName);
                values.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg");
                values.put(MediaStore.Images.Media.DATA, destFile.getPath());
                mCameraTemp = getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
                Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
                intent.putExtra(MediaStore.EXTRA_OUTPUT, mCameraTemp);
                startActivityForResult(intent, REQUEST_CAMERA);
            }
        }
        return true;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        switch (requestCode) {
            case REQUEST_GALLERY:
                if (resultCode == RESULT_OK) {
                    if (data.getData() != null) {
                        accept(data.getData());
                    } else {
                        Toast.makeText(this, R.string.info_shibafu528_gallerymultipicker_gallery_error, Toast.LENGTH_SHORT).show();
                    }
                }
                break;
            case REQUEST_CAMERA: {
                if (resultCode == RESULT_OK) {
                    if (data != null && data.getData() != null) {
                        //getDataでUriが返ってくる端末用, フィールドは手に入ったUriで上書き
                        mCameraTemp = data.getData();
                    }
                    if (mCameraTemp == null) {
                        Toast.makeText(this, R.string.info_shibafu528_gallerymultipicker_camera_error, Toast.LENGTH_LONG).show();
                    } else {
                        accept(mCameraTemp);
                    }
                } else if (resultCode == RESULT_CANCELED) {
                    Cursor c = getContentResolver().query(mCameraTemp,
                            new String[]{MediaStore.Images.Media.DATA}, null, null, null);
                    c.moveToFirst();
                    getContentResolver().delete(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                            MediaStore.Images.Media.DATA + "=?",
                            new String[]{c.getString(0)});
                }
                break;
            }
        }
    }

    @Override
    public void finish() {
        super.finish();
        overridePendingTransition(mCloseEnterAnimation, mCloseExitAnimation);
    }

    private void accept(Uri... uri) {
        Intent result = new Intent();
        result.putExtra(EXTRA_URIS, uri);
        setResult(RESULT_OK, result);
        finish();
    }

    public void updateLimitCount() {
        getSupportActionBar().setSubtitle(getString(R.string.info_shibafu528_gallerymultipicker_subtitle,
                mPickLimit - getSelectedIds().size()));
    }

    public int getPickLimit() {
        return mPickLimit;
    }

    public List<Long> getSelectedIds() {
        return mSelectedIds;
    }

    public Uri[] getSelectedUris() {
        Uri[] uris = new Uri[mSelectedIds.size()];
        for (int i = 0; i < mSelectedIds.size(); i++) {
            uris[i] = Uri.withAppendedPath(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, String.valueOf(mSelectedIds.get(i)));
        }
        return uris;
    }

    public void toggleSelect(long id) {
        if (mSelectedIds.contains(id)) {
            mSelectedIds.remove(id);
        } else if (getPickLimit() > mSelectedIds.size()){
            mSelectedIds.add(id);
        }
    }

    public static class AlbumFragment extends ListFragment {
        private static final String[] SELECT_BUCKET = {
                MediaStore.Images.ImageColumns._ID,
                MediaStore.Images.ImageColumns.BUCKET_ID,
                MediaStore.Images.ImageColumns.BUCKET_DISPLAY_NAME,
                MediaStore.Images.ImageColumns.DATA,
                "COUNT(*)"
        };

        @Override
        public void onActivityCreated(Bundle savedInstanceState) {
            super.onActivityCreated(savedInstanceState);
            ContentResolver resolver = getActivity().getContentResolver();

            Cursor cursor = resolver.query(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI, null, null, null,
                    MediaStore.Images.Media.DATE_MODIFIED + " DESC"
            );
            View view = getActivity().getLayoutInflater().inflate(R.layout.info_shibafu528_gallerymultipicker_row_album, null);
            ViewHolder vh = new ViewHolder(view);
            if (cursor.moveToFirst()) {
                long id = cursor.getLong(cursor.getColumnIndex(MediaStore.Images.Media._ID));
                vh.imageView.setTag(String.valueOf(id));
                vh.imageView.setImageResource(android.R.drawable.ic_popup_sync);
                new ThumbnailLoader(vh.imageView).executeParallel(new Pair<>(resolver, id));
            } else {
                vh.imageView.setImageResource(android.R.drawable.gallery_thumb);
            }
            vh.title.setText(R.string.info_shibafu528_gallerymultipicker_all_picture);
            vh.count.setText(String.valueOf(cursor.getCount()));
            cursor.close();
            getListView().addHeaderView(view);

            setListAdapter(new AlbumAdapter(getActivity(),
                    resolver.query(
                            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                            SELECT_BUCKET,
                            "1) GROUP BY (2",
                            null,
                            "MAX(datetaken) DESC"),
                    resolver));
            getListView().setFastScrollEnabled(true);
        }

        @Override
        public void onDestroyView() {
            setListAdapter(null);
            super.onDestroyView();
        }

        @Override
        public void onListItemClick(ListView l, View v, int position, long id) {
            String bucketId = null;
            if (position-- > 0) {
                Cursor c = (Cursor) getListAdapter().getItem(position);
                bucketId = c.getString(c.getColumnIndex(MediaStore.Images.ImageColumns.BUCKET_ID));
            }
            FragmentTransaction transaction = getActivity().getSupportFragmentManager().beginTransaction();
            transaction
                    .setCustomAnimations(
                            R.anim.info_shibafu528_gallerymultipicker_open_enter,
                            R.anim.info_shibafu528_gallerymultipicker_open_exit,
                            R.anim.info_shibafu528_gallerymultipicker_close_enter,
                            R.anim.info_shibafu528_gallerymultipicker_close_exit)
                    .replace(android.R.id.content, GridFragment.newInstance(bucketId))
                    .addToBackStack(null)
                    .commit();
        }

        private class AlbumAdapter extends CursorAdapter {
            private ContentResolver resolver;
            private LayoutInflater inflater;

            public AlbumAdapter(Context context, Cursor c, ContentResolver cr) {
                super(context, c, false);
                this.resolver = cr;
                this.inflater = (LayoutInflater) context.getSystemService(LAYOUT_INFLATER_SERVICE);
            }

            @Override
            public View newView(Context context, Cursor cursor, ViewGroup parent) {
                View v = inflater.inflate(R.layout.info_shibafu528_gallerymultipicker_row_album, null);
                ViewHolder vh = new ViewHolder(v);
                bindExistView(vh, context, cursor);
                return v;
            }

            @Override
            public void bindView(View view, Context context, Cursor cursor) {
                bindExistView((ViewHolder) view.getTag(), context, cursor);
            }

            public void bindExistView(ViewHolder vh, Context context, Cursor cursor) {
                long id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Images.ImageColumns._ID));
                vh.imageView.setTag(String.valueOf(id));
                vh.imageView.setImageResource(android.R.drawable.ic_popup_sync);
                new ThumbnailLoader(vh.imageView).executeParallel(new Pair<>(resolver, id));
                vh.title.setText(cursor.getString(cursor.getColumnIndex(MediaStore.Images.ImageColumns.BUCKET_DISPLAY_NAME)));
                vh.count.setText(cursor.getString(cursor.getColumnIndex("COUNT(*)")));
            }
        }

        private class ViewHolder {
            ImageView imageView;
            TextView title;
            TextView count;

            public ViewHolder(View v) {
                imageView = (ImageView) v.findViewById(android.R.id.icon);
                title = (TextView) v.findViewById(android.R.id.text1);
                count = (TextView) v.findViewById(android.R.id.text2);
                v.setTag(this);
            }
        }
    }

    public static class GridFragment extends Fragment {
        private static final String ARGV_BUCKET_ID = "bucket_id";

        private GridView gridView;

        private ContentAdapter adapter;

        public static GridFragment newInstance(String bucketId) {
            GridFragment fragment = new GridFragment();
            Bundle args = new Bundle();
            args.putString(ARGV_BUCKET_ID, bucketId);
            fragment.setArguments(args);
            return fragment;
        }

        public GridFragment() {
            setRetainInstance(true);
        }

        @Override
        public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
            View v = inflater.inflate(R.layout.info_shibafu528_gallerymultipicker_fragment_grid, container, false);
            gridView = (GridView) v.findViewById(android.R.id.list);
            return v;
        }

        @Override
        public void onActivityCreated(Bundle savedInstanceState) {
            super.onActivityCreated(savedInstanceState);
            ContentResolver resolver = getActivity().getContentResolver();
            String bucketId = getArguments().getString(ARGV_BUCKET_ID);
            Cursor cursor;
            if (TextUtils.isEmpty(bucketId)) {
                cursor = resolver.query(
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI, null, null, null,
                        MediaStore.Images.Media.DATE_MODIFIED + " DESC"
                );
            } else {
                cursor = resolver.query(
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI, null,
                        MediaStore.Images.Media.BUCKET_ID + "=?",
                        new String[]{bucketId},
                        MediaStore.Images.Media.DATE_MODIFIED + " DESC"
                );
            }
            adapter = new ContentAdapter(getActivity(), cursor, resolver);
            gridView.setAdapter(adapter);
            gridView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
                @Override
                public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                    ((MultiPickerActivity) getActivity()).toggleSelect(id);
                    ContentAdapter.ViewHolder vh = (ContentAdapter.ViewHolder) view.getTag();
                    if (vh != null) {
                        vh.maskView.setVisibility(((MultiPickerActivity) getActivity()).getSelectedIds().contains(id) ? View.VISIBLE : View.INVISIBLE);
                    }
                    ((MultiPickerActivity) getActivity()).updateLimitCount();
                }
            });
        }

        public void notifyDataSetChanged() {
            adapter.notifyDataSetChanged();
        }

        private class ContentAdapter extends CursorAdapter {
            private ContentResolver resolver;
            private LayoutInflater inflater;

            public ContentAdapter(Context context, Cursor c, ContentResolver cr) {
                super(context, c, false);
                this.resolver = cr;
                this.inflater = (LayoutInflater) context.getSystemService(LAYOUT_INFLATER_SERVICE);
            }

            @Override
            public View newView(Context context, Cursor cursor, ViewGroup parent) {
                View v = inflater.inflate(R.layout.info_shibafu528_gallerymultipicker_row_picture, null);
                ViewHolder vh = new ViewHolder(v);
                {
                    int columns = context.getResources().getInteger(R.integer.info_shibafu528_gallerymultipicker_grid_columns_num);
                    WindowManager wm = (WindowManager) context.getSystemService(WINDOW_SERVICE);
                    Display display = wm.getDefaultDisplay();
                    FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(display.getWidth() / columns, display.getWidth() / columns);
                    vh.imageView.setLayoutParams(params);
                    vh.maskView.setLayoutParams(params);
                }
                bindExistView(vh, context, cursor);
                return v;
            }

            @Override
            public void bindView(View view, Context context, Cursor cursor) {
                bindExistView((ViewHolder) view.getTag(), context, cursor);
            }

            public void bindExistView(ViewHolder vh, Context context, Cursor cursor) {
                long id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID));
                vh.imageView.setTag(String.valueOf(id));
                vh.imageView.setImageResource(android.R.drawable.ic_popup_sync);
                new ThumbnailLoader(vh.imageView).executeParallel(new Pair<>(resolver, id));
                vh.maskView.setVisibility(((MultiPickerActivity) getActivity()).getSelectedIds().contains(id) ? View.VISIBLE : View.INVISIBLE);
            }

            private class ViewHolder {
                ImageView imageView;
                ImageView maskView;

                public ViewHolder(View v) {
                    imageView = (ImageView) v.findViewById(android.R.id.icon1);
                    maskView = (ImageView) v.findViewById(android.R.id.icon2);
                    v.setTag(this);
                }
            }
        }
    }

    private static class LongArray extends ArrayList<Long> {
        public long[] toPrimitive() {
            final long[] result = new long[size()];
            for (int i = 0; i < result.length; i++) {
                result[i] = get(i);
            }
            return result;
        }

        public void addAll(long[] values) {
            if (values != null) for (long value : values) {
                add(value);
            }
        }
    }
}
