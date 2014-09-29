package shibafu.yukari.activity;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.support.v4.app.Fragment;
import android.support.v4.util.LruCache;
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
import android.widget.Toast;

import java.io.File;
import java.lang.ref.WeakReference;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import butterknife.ButterKnife;
import butterknife.InjectView;
import shibafu.yukari.R;
import shibafu.yukari.activity.base.ActionBarYukariBase;
import shibafu.yukari.common.async.ParallelAsyncTask;

/**
 * Created by shibafu on 14/09/27.
 */
public class PictureChooserActivity extends ActionBarYukariBase {
    private static final int REQUEST_GALLERY = 0;
    private static final int REQUEST_CAMERA = 1;

    public static final String EXTRA_CHOOSE_LIMIT = "max";
    public static final String EXTRA_URIS = "uris";

    //撮影用の一時変数
    private Uri cameraTemp;

    //選択上限
    private int limit = 1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_parent);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setDisplayShowHomeEnabled(false);
        if (savedInstanceState == null) {
            InnerFragment fragment = new InnerFragment();
            getSupportFragmentManager().beginTransaction()
                    .replace(R.id.frame, fragment, "grid")
                    .commit();
        }
        limit = getIntent().getIntExtra(EXTRA_CHOOSE_LIMIT, 1);
        updateLimitCount();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.picture_choose, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        super.onOptionsItemSelected(item);
        switch (item.getItemId()) {
            case android.R.id.home:
                setResult(RESULT_CANCELED);
                finish();
                return true;
            case R.id.action_accept: {
                InnerFragment fragment = (InnerFragment) getSupportFragmentManager().findFragmentByTag("grid");
                if (fragment != null) {
                    accept(fragment.getSelectedUris());
                }
                return true;
            }
            case R.id.action_gallery: {
                Intent intent = new Intent(Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT ? Intent.ACTION_PICK : Intent.ACTION_GET_CONTENT);
                intent.setType("image/*");
                startActivityForResult(intent, REQUEST_GALLERY);
                return true;
            }
            case R.id.action_take_shot: {
                //SDカード使用可否のチェックを行う
                boolean existExternal = Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED);
                if (!existExternal) {
                    Toast.makeText(PictureChooserActivity.this, "ストレージが使用できないため、カメラを起動できません", Toast.LENGTH_SHORT).show();
                    return true;
                }
                //保存先パスを作成する
                String extStorage = Environment.getExternalStorageDirectory().getPath();
                File extDestDir = new File(extStorage + "/DCIM/" + getPackageName());
                if (!extDestDir.exists()) {
                    extDestDir.mkdirs();
                }
                SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd_HHmmss");
                String fileName = sdf.format(new Date(System.currentTimeMillis()));
                File destFile = new File(extDestDir.getPath() + "/" + fileName + ".jpg");
                //コンテントプロバイダに登録
                ContentValues values = new ContentValues();
                values.put(MediaStore.Images.Media.TITLE, fileName);
                values.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg");
                values.put(MediaStore.Images.Media.DATA, destFile.getPath());
                cameraTemp = getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
                //カメラを呼び出す
                Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
                intent.putExtra(MediaStore.EXTRA_OUTPUT, cameraTemp);
                startActivityForResult(intent, REQUEST_CAMERA);
                return true;
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
                        Toast.makeText(PictureChooserActivity.this, "ギャラリーからの取得に失敗しました", Toast.LENGTH_SHORT).show();
                    }
                }
                break;
            case REQUEST_CAMERA: {
                if (resultCode == RESULT_OK) {
                    if (data != null && data.getData() != null) {
                        //getDataでUriが返ってくる端末用
                        //フィールドは手に入ったUriで上書き
                        cameraTemp = data.getData();
                    }
                    if (cameraTemp == null) {
                        Toast.makeText(PictureChooserActivity.this, "カメラとの連携に失敗しました。\n使用したカメラアプリとの相性かもしれません。", Toast.LENGTH_LONG).show();
                    } else {
                        accept(cameraTemp);
                    }
                } else if (resultCode == RESULT_CANCELED) {
                    Cursor c = getContentResolver().query(cameraTemp, new String[]{MediaStore.Images.Media.DATA}, null, null, null);
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
        overridePendingTransition(R.anim.activity_tweet_close_enter, R.anim.activity_tweet_close_exit);
    }

    private void accept(Uri... uri) {
        Intent result = new Intent();
        result.putExtra(EXTRA_URIS, uri);
        setResult(RESULT_OK, result);
        finish();
    }

    public void updateLimitCount() {
        InnerFragment fragment = (InnerFragment) getSupportFragmentManager().findFragmentByTag("grid");
        getSupportActionBar().setSubtitle(String.format("あと %d 枚選択可能",
                limit - (fragment != null ? fragment.getSelectedIds().size() : 0)));
    }

    public int getLimit() {
        return limit;
    }

    @Override
    public void onServiceConnected() {}

    @Override
    public void onServiceDisconnected() {}

    public static class InnerFragment extends Fragment {
        @InjectView(R.id.gridView)
        GridView gridView;

        private ContentAdapter adapter;
        private List<Long> selected = new ArrayList<>(4);

        public InnerFragment() {
            setRetainInstance(true);
        }

        @Override
        public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
            View v = inflater.inflate(R.layout.fragment_grid, container, false);
            ButterKnife.inject(this, v);
            return v;
        }

        @Override
        public void onActivityCreated(Bundle savedInstanceState) {
            super.onActivityCreated(savedInstanceState);
            ContentResolver resolver = getActivity().getContentResolver();
            adapter = new ContentAdapter(getActivity(),
                    resolver.query(
                            MediaStore.Images.Media.EXTERNAL_CONTENT_URI, null, null, null,
                            MediaStore.Images.Media.DATE_MODIFIED + " DESC"
                    ),
                    resolver);
            gridView.setAdapter(adapter);
            gridView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
                @Override
                public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                    toggleSelect(id);
                    ContentAdapter.ViewHolder vh = (ContentAdapter.ViewHolder) view.getTag();
                    if (vh != null) {
                        vh.maskView.setVisibility(getSelectedIds().contains(id) ? View.VISIBLE : View.INVISIBLE);
                    }
                    ((PictureChooserActivity) getActivity()).updateLimitCount();
                }
            });
        }

        @Override
        public void onDestroyView() {
            super.onDestroyView();
            ButterKnife.reset(this);
        }

        public void toggleSelect(long id) {
            if (selected.contains(id)) {
                selected.remove(id);
            } else if (((PictureChooserActivity)getActivity()).getLimit() > selected.size()){
                selected.add(id);
            }
        }

        public List<Long> getSelectedIds() {
            return selected;
        }

        public Uri[] getSelectedUris() {
            Uri[] uris = new Uri[selected.size()];
            for (int i = 0; i < selected.size(); i++) {
                uris[i] = Uri.withAppendedPath(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, String.valueOf(selected.get(i)));
            }
            return uris;
        }

        class ContentAdapter extends CursorAdapter {
            private ContentResolver resolver;
            private LayoutInflater inflater;

            public ContentAdapter(Context context, Cursor c, ContentResolver cr) {
                super(context, c, false);
                this.resolver = cr;
                this.inflater = (LayoutInflater) context.getSystemService(LAYOUT_INFLATER_SERVICE);
            }

            @Override
            public View newView(Context context, Cursor cursor, ViewGroup parent) {
                View v = inflater.inflate(R.layout.row_picturechoose, null);
                ViewHolder vh = new ViewHolder(v);
                {
                    int columns = context.getResources().getInteger(R.integer.grid_columns_num);
                    WindowManager wm = (WindowManager) context.getSystemService(WINDOW_SERVICE);
                    Display display = wm.getDefaultDisplay();
                    FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(display.getWidth() / columns, display.getWidth() / columns);
                    vh.imageView.setLayoutParams(params);
                    vh.maskView.setLayoutParams(params);
                    // この手法でいい感じにスペースが空いてくれると思った！？
                    // すべて正方形じゃないと見栄えがアレなのでコメントアウト
                    //int padding = (int) (context.getResources().getDisplayMetrics().density * 2 + 0.5f);
                    //imageView.setPadding(padding, padding, padding, padding);
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
                vh.imageView.setImageResource(R.drawable.yukatterload);
                new ThumbnailLoader(vh.imageView).executeParallel(new Pair<>(resolver, id));
                vh.maskView.setVisibility(selected.contains(id) ? View.VISIBLE : View.INVISIBLE);
            }

            class ViewHolder {
                @InjectView(R.id.imageView) ImageView imageView;
                @InjectView(R.id.mask) ImageView maskView;

                public ViewHolder(View v) {
                    ButterKnife.inject(this, v);
                    v.setTag(this);
                }
            }
        }

        private static class ThumbnailLoader extends ParallelAsyncTask<Pair<ContentResolver, Long>, Void, Bitmap> {
            private static LruCache<Long, Bitmap> cache = new LruCache<Long, Bitmap>(16*1024*1024) {
                @Override
                protected int sizeOf(Long key, Bitmap value) {
                    return value.getRowBytes() * value.getHeight();
                }
            };
            private static BitmapFactory.Options options = new BitmapFactory.Options();

            private WeakReference<ImageView> imageView;
            private String tag;

            static {
                options.inPreferredConfig = Bitmap.Config.RGB_565;
                options.inPurgeable = true;
            }

            private ThumbnailLoader(ImageView imageView) {
                this.imageView = new WeakReference<>(imageView);
                this.tag = (String) imageView.getTag();
            }

            @Override
            protected Bitmap doInBackground(Pair<ContentResolver, Long>... params) {
                Bitmap bitmap = cache.get(params[0].second);
                if (bitmap == null) {
                    bitmap = MediaStore.Images.Thumbnails.getThumbnail(params[0].first, params[0].second, MediaStore.Images.Thumbnails.MINI_KIND, options);
                    if (bitmap != null) {
                        cache.put(params[0].second, bitmap);
                    }
                }
                return bitmap;
            }

            @Override
            protected void onPostExecute(Bitmap bitmap) {
                ImageView imageView = this.imageView != null ? this.imageView.get() : null;
                if (imageView != null && tag.equals(imageView.getTag())) {
                    imageView.setImageBitmap(bitmap);
                }
            }
        }
    }
}
