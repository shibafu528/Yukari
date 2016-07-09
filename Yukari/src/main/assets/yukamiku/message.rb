# encoding: utf-8
Android.require_assets 'yukamiku/compatmodel.rb'

# Message互換クラス
class Message < Retriever::Model

  # args format
  # key     | value(class)
  #---------+--------------
  # id      | id of status(mixed)
  # entity  | entity(mixed)
  # message | posted text(String)
  # tags    | kind of message(Array)
  # user    | user who post this message(User or Hash or mixed(User IDNumber))
  # reciver | recive user(User)
  # replyto | source message(Message or mixed(Status ID))
  # retweet | retweet to this message(Message or StatusID)
  # post    | post object(Service)
  # image   | image(URL or Image object)

  self.keys = [
      [:id, :string, true], # ID
      [:message, :string, true], # Message description
      [:user, User, true], # Send by user
      [:receiver, User], # Send to user
      [:replyto, Message], # Reply to this message
      [:retweet, Message], # ReTweet to this message
      [:source, :string], # using client
      [:geo, :string], # geotag
      [:exact, :bool], # true if complete data
      [:created, :time], # posted time
      [:modified, :time], # updated time
  ]

  def initialize(value)
    super(value)
  end

  def idname
    user[:idname]
  end

  def system?
    self[:system]
  end

  def protected?
    if retweet?
      retweet_ancestor.protected?
    else
      user.protected?
    end
  end

  def verified?
    user.verified?
  end

  def user
    self[:user]
  end

  def has_receive_message?
    !!self[:replyto]
  end

  alias reply? has_receive_message?

  def retweet?
    !!self[:retweet]
  end

  def post(other, &proc)
    other[:replyto] = self
    other[:receiver] = self[:user]

    # TODO: mikutterではプライマリサービスが必ず優先されるけど、y4aでは代表ユーザ[PreformedStatus#getRepresentUser()]を使ったほうがいいんじゃないかな
    service = Service.primary
    if service.is_a? Service
      service.post(other)
    end
  end

  def message
    self
  end
end