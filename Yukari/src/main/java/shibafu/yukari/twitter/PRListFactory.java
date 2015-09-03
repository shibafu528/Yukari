package shibafu.yukari.twitter;

import java.util.ArrayList;

import shibafu.yukari.twitter.statusimpl.PreformedStatus;
import twitter4j.QueryResult;
import twitter4j.ResponseList;
import twitter4j.Status;

/**
 * Created by Shibafu on 14/01/16.
 */
public class PRListFactory {

    public static PreformedResponseList<PreformedStatus> create(
            ResponseList<Status> responseList, AuthUserRecord receivedUser) {
        ArrayList<PreformedStatus> list;
        if (responseList == null) {
            list = new ArrayList<>();
        }
        else {
            list = new ArrayList<>(responseList.size());
            for (Status s : responseList) {
                list.add(new PreformedStatus(s, receivedUser));
            }
        }
        return new PreformedResponseList<>(list, responseList);
    }

    public static PreformedResponseList<PreformedStatus> create(
            QueryResult queryResult, AuthUserRecord receivedUser) {
        ArrayList<PreformedStatus> list;
        if (queryResult == null) {
            list = new ArrayList<>();
        }
        else {
            list = new ArrayList<>(queryResult.getTweets().size());
            for (Status s : queryResult.getTweets()) {
                list.add(new PreformedStatus(s, receivedUser));
            }
        }
        return new PreformedResponseList<>(list, queryResult);
    }

    public static PreformedResponseList<PreformedStatus> create(
            Status response, AuthUserRecord receivedUser) {
        ArrayList<PreformedStatus> list = new ArrayList<>(1);
        list.add(new PreformedStatus(response, receivedUser));
        return new PreformedResponseList<>(list, (QueryResult)null);
    }
}
