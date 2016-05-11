# encoding: utf-8

module Plugin::GUI
  Event = Struct.new(:event, :widget, :messages)
end