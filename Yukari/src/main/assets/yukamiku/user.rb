# encoding: utf-8
Android.require_assets 'yukamiku/compatmodel.rb'

# User互換クラス
class User < Retriever::Model

  # args format
  # key     | value
  # --------+-----------
  # id      | user id
  # idname  | account name
  # nickname| account usual name
  # location| location(not required)
  # detail  | detail
  # profile_image_url | icon

  self.keys = [[:id, :string],
               [:idname, :string],
               [:name, :string],
               [:location, :string],
               [:detail, :string],
               [:profile_image_url, :string],
               [:url, :string],
               [:protected, :bool],
               [:verified, :bool],
               [:followers_count, :int],
               [:statuses_count, :int],
               [:friends_count, :int],
  ]

  def self.system
    if not defined? @@system then
      @@system = User.new({:id => 0,
                           :idname => 'mikutter_bot',
                           :name => Environment::NAME,
                           :profile_image_url => nil})
    end
    @@system
  end

  def initialize(value)
    super(value)
  end

  def idname
    self[:idname]
  end

  alias to_s idname

  def protected?
    !!self[:protected]
  end

  def verified?
    !!self[:verified]
  end

  def profile_image_url_large
    url = self[:profile_image_url]
    if url
      url.gsub(/_normal(.[a-zA-Z0-9]+)\Z/, '\1')
    end
  end

  def inspect
    "User(@#{@value[:idname]})"
  end

  def ==(other)
    if other.is_a?(String) then
      @value[:idname] == other
    elsif other.is_a?(User) then
      other[:id] == self[:id]
    end
  end

  def me?(service)
    false
  end

  alias is_me? me?

  def user
    self
  end
  alias to_user user
end