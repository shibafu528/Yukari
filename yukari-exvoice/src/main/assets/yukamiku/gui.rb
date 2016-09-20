# encoding: utf-8
# GUI, Gtk回りの互換性

module Plugin::GUI
  Event = Struct.new(:event, :widget, :messages)

  class Postbox
    include InstanceStorage

    Widget = Struct.new(:buffer)
    Buffer = Struct.new(:text)

    def initialize(*args)
      super(*args)
      @options = {}
      @widget_post = Widget.new(Buffer.new(''))
    end

    def self.instance
      self[:singleton]
    end

    attr_reader :widget_post
    attr_accessor :options
  end

  class Timeline
    include InstanceStorage

    def self.instance
      self[:singleton]
    end

    def create_reply_postbox(in_reply_to_message, options = {})
      create_postbox(options.merge(to: [in_reply_to_message],
                                   header: "@#{in_reply_to_message.user.idname} "))
    end

    def create_postbox(options = {})
      Plugin::GUI::Postbox.instance.options = options
    end
  end
end

module Plugin::Gtk
  class GtkError < StandardError; end
end

Plugin.create(:gtk) do
  # 互換クラスのインスタンスを保持する
  @pseudo_instances = {
      postbox: Plugin::GUI::Postbox.instance,
      timeline: Plugin::GUI::Timeline.instance
  }

  def widgetof(slug_or_instance)
    if slug_or_instance.is_a? Symbol
      @pseudo_instances[slug_or_instance]
    else
      @pseudo_instances[slug_or_instance.class.split('::').last.downcase]
    end
  end
end