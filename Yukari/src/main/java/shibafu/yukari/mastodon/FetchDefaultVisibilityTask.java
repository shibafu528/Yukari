package shibafu.yukari.mastodon;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.sys1yagi.mastodon4j.MastodonClient;

import java.util.Map;

import okhttp3.Response;
import shibafu.yukari.common.async.SimpleAsyncTask;
import shibafu.yukari.database.AuthUserRecord;
import shibafu.yukari.database.Provider;
import shibafu.yukari.entity.StatusDraft;
import shibafu.yukari.linkage.ApiCollectionProvider;

public class FetchDefaultVisibilityTask extends SimpleAsyncTask {
    private final ApiCollectionProvider apiCollectionProvider;
    private final DefaultVisibilityCache defaultVisibilityCache;
    private final AuthUserRecord aur;

    public FetchDefaultVisibilityTask(ApiCollectionProvider apiCollectionProvider, DefaultVisibilityCache defaultVisibilityCache, AuthUserRecord aur) {
        this.apiCollectionProvider = apiCollectionProvider;
        this.defaultVisibilityCache = defaultVisibilityCache;
        this.aur = aur;
    }

    @Override
    protected Void doInBackground(Void... voids) {
        MastodonClient client = (MastodonClient) apiCollectionProvider.getProviderApi(Provider.API_MASTODON).getApiClient(aur);
        try {
            Response response = client.get("preferences", null, "v1");
            if (response.isSuccessful()) {
                String body = response.body().string();
                Map<String, Object> prefs = new Gson().fromJson(body, new TypeToken<Map<String, Object>>() {
                }.getType());
                Object maybeVisibility = prefs.get("posting:default:visibility");
                if (maybeVisibility instanceof String) {
                    switch ((String) maybeVisibility) {
                        case "public":
                            defaultVisibilityCache.set(aur.ScreenName, StatusDraft.Visibility.PUBLIC);
                            break;
                        case "unlisted":
                            defaultVisibilityCache.set(aur.ScreenName, StatusDraft.Visibility.UNLISTED);
                            break;
                        case "private":
                            defaultVisibilityCache.set(aur.ScreenName, StatusDraft.Visibility.PRIVATE);
                            break;
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }
}
