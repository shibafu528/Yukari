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
      @widget_post = Widget.new(Buffer.new(''))
    end

    def self.instance
      self[:singleton]
    end

    attr_reader :widget_post
  end
end

module Plugin::Gtk
  class GtkError < StandardError; end
end

Plugin.create(:gtk) do
  # 互換クラスのインスタンスを保持する
  @pseudo_instances = {
      postbox: Plugin::GUI::Postbox.instance
  }

  def widgetof(slug)
    @pseudo_instances[slug]
  end
end