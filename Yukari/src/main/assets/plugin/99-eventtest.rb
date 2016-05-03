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

  filter_type_test do |bool, str, sym, int, float, array, hash|
    puts "filter arguments: #{p [bool, str, sym, int, float, array, hash]}"
    [false, 'Filtered', :symbol, 1222, 12.22, [:arrayVal, :arrayVal2, [:innerVal1, :innerVal2], {innerKey1: :innerVal1}], {key1: 'value1', 'key2' => 'value2', key3: [:arr1, :arr2], key4: {innerHash: :innerHashVal}}].tap { |myself|
      puts "filter returns: #{myself}"
    }
  end
end