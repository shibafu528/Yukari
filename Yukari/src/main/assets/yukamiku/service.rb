# encoding: utf-8

# Serviceをエミュレートする
class Service
  include InstanceStorage
  extend Enumerable

  class << self
    alias services instances

    def each(*args, &proc)
      instances.each(*args, &proc)
    end

    def primary
      if @primary
        @primary
      elsif services.empty?
        nil
      else
        set_primary(services.first)
        @primary
      end
    end

    alias primary_service primary

    def primary!
      result = primary
      raise Service::NotExistError, 'Services does not exists.' unless result
      result
    end

    def set_primary(service)
      before_primary = @primary
      return self if before_primary != @primary || @primary == service
      @primary = service
      Plugin.call(:primary_service_changed, service)
      self
    end
  end

  def user_obj
    @user_obj
  end

  alias to_user user_obj

  def user
    @user_obj[:idname]
  end

  alias :idname :user

  def user_by_cache
    @user_idname
  end

  def service
    self
  end

  # emulate
  def update(options = {})
    raise ':message is nil' if options[:message].nil?

    unless options[:replyto].nil?
      Plugin.call(:intent,
                  activity: :TweetActivity,
                  mode: :reply,
                  in_reply_to: options[:replyto].id,
                  text: options[:message])
    else
      Plugin.call(:intent,
                  activity: :TweetActivity,
                  mode: :tweet,
                  text: options[:message])
    end

  end

  alias post update

  def inspect
    "#<Service #{idname}>"
  end
end

Service.set_primary(Service.new('system'))