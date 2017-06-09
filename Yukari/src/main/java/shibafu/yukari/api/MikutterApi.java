package shibafu.yukari.api;

import com.google.gson.annotations.SerializedName;
import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Path;

import java.util.List;

/**
 * mikutter.hachune.net API
 *
 * @see <a href="http://mikutter.hachune.net/api">mikutter API</a>
 */
public interface MikutterApi {

    @GET("/download/{query}.json")
    Call<List<VersionInfo>> download(@Path("query") String query);

    class VersionInfo {
        static class Version {
            public int major, minor, teeny, build;
            public String meta;
        }
        @SerializedName("version_string") public String versionString;
        public Version version;
        public String created;
        public String url;
    }
}
