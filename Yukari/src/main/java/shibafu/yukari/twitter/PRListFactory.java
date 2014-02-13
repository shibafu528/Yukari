package shibafu.yukari.twitter;

import java.util.ArrayList;

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
            list = new ArrayList<PreformedStatus>();
        }
        else {
            list = new ArrayList<PreformedStatus>(responseList.size());
            for (Status s : responseList) {
                list.add(new PreformedStatus(s, receivedUser));
            }
        }
        return new PreformedResponseList<PreformedStatus>(list, responseList);
    }

    public static PreformedResponseList<PreformedStatus> create(
            QueryResult queryResult, AuthUserRecord receivedUser) {
        ArrayList<PreformedStatus> list;
        if (queryResult == null) {
            list = new ArrayList<PreformedStatus>();
        }
        else {
            list = new ArrayList<PreformedStatus>(queryResult.getTweets().size());
            for (Status s : queryResult.getTweets()) {
                list.add(new PreformedStatus(s, receivedUser));
            }
        }
        return new PreformedResponseList<PreformedStatus>(list, queryResult);
    }
}
