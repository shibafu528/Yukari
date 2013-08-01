package shibafu.yukari.twitter;

import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.StreamCorruptedException;
import java.util.ArrayList;
import java.util.List;
import android.content.Context;

import shibafu.yukari.R;
import twitter4j.Twitter;
import twitter4j.TwitterFactory;

public class TwitterUtil {

	public static final String USER_RECORD = "user_records";

	public static Twitter getTwitterInstance(Context context) {
		String consumer_key = context.getString(R.string.twitter_consumer_key);
		String consumer_secret = context.getString(R.string.twitter_consumer_secret);

		TwitterFactory factory = new TwitterFactory();
		Twitter twitter = factory.getInstance();
		twitter.setOAuthConsumer(consumer_key, consumer_secret);

		AuthUserRecord[] users = loadUserRecords(context);
		if (users != null && users.length > 0) {
			twitter.setOAuthAccessToken(users[0].getAccessToken());
		}

		return twitter;
	}

	public static void storeUserRecord(Context context, AuthUserRecord record) {
		AuthUserRecord[] stored = loadUserRecords(context);
		List<AuthUserRecord> modify = new ArrayList<AuthUserRecord>();
		if (stored == null || stored.length < 1) {
			modify.add(record);
		}
		else {
			boolean addedNewRecord = false;
			for (AuthUserRecord data : stored) {
				if (data.NumericId == record.NumericId) {
					//NumericIdが重複していたら新規データを書き込む
					modify.add(record);
					addedNewRecord = true;
				}
				else
					modify.add(data);
			}
			if (!addedNewRecord)
				modify.add(record);
		}
		//保存する
		try {
			FileOutputStream fos = context.openFileOutput(USER_RECORD, Context.MODE_PRIVATE);
			ObjectOutputStream oos = new ObjectOutputStream(fos);
			for (AuthUserRecord data : modify) {
				oos.writeObject(data);
			}
			oos.close();
			fos.close();
		} catch (FileNotFoundException e) {
			// TODO 自動生成された catch ブロック
			e.printStackTrace();
		} catch (IOException e) {
			// TODO 自動生成された catch ブロック
			e.printStackTrace();
		}
	}

	public static AuthUserRecord[] loadUserRecords(Context context) {
		File f = context.getFileStreamPath(USER_RECORD);
		if (!f.exists())
			return new AuthUserRecord[0];
		else {
			List<AuthUserRecord> list = new ArrayList<AuthUserRecord>();
			try {
				FileInputStream fis = new FileInputStream(f);
				ObjectInputStream ois = new ObjectInputStream(fis);

				AuthUserRecord aur;
				while ((aur = (AuthUserRecord) ois.readObject()) != null) {
					list.add(aur);
				}
				ois.close();
				fis.close();

			} catch (EOFException e) {
			} catch (FileNotFoundException e) {
				// TODO 自動生成された catch ブロック
				e.printStackTrace();
			} catch (StreamCorruptedException e) {
				// TODO 自動生成された catch ブロック
				e.printStackTrace();
			} catch (IOException e) {
				// TODO 自動生成された catch ブロック
				e.printStackTrace();
			} catch (ClassNotFoundException e) {
				// TODO 自動生成された catch ブロック
				e.printStackTrace();
			}
			return list.toArray(new AuthUserRecord[0]);
		}
	}

}
