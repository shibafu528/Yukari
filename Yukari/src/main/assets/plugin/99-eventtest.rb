# encoding: utf-8

#
# イベント動作テスト
#
Plugin.create(:event_test) do

  on_event_test do
    puts "Call :event_test at #{Time.now}"
  end

  filter_filter_test do |v|
    v = "Filter :event_test at #{Time.now}"
    [v]
  end
end