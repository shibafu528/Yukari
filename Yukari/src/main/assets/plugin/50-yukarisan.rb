# encoding: utf-8

#
# ゆかりさんゆかりさん！！
#
Plugin.create(:yukarisan) do

  # show_tweet => jp.r246.twicca.ACTION_SHOW_TWEET
  twicca_action(:show_tweet, :yukarisan_reply, label: 'ゆかりさんする') do |extra|
    Plugin.call(:intent,
                activity: :TweetActivity,
                mode: :reply,
                text: "@#{extra[:user_screen_name]} ゆかりさんゆかりさん！！")
  end

  # edit_tweet => jp.r246.twicca.ACTION_EDIT_TWEET
  twicca_action(:edit_tweet, :yukarisan, label: 'ゆかりさんする') do |extra|
    # Like Activity#setResult(resultCode, intent)
    {result_code: :ok, intent: {text: 'ゆかりさんゆかりさん！！'}}
  end
end