# encoding: utf-8
# Yukari; exVoice
# mrubyとPluggaloidを使用したモバイル向けプラグインサブシステム

class String
  def ascii_only?
    !(self =~ /[^\x00-\x7f]/)
  end unless method_defined? :ascii_only?
end

def bootstrap
  # Initialize Pluggaloid
  Delayer.default = Delayer.generate_class(priority: [:high, :normal, :low], default: :normal)
  include Pluggaloid

  # Find bundle plugins
  plugins = []
  Android::AssetDir.open('plugin').each do |path|
    plugins << path if path.end_with? '.rb'
  end

  # Load bundle plugins
  plugins.sort.each do |path|
    puts "Require: #{path}"
    Android.require_assets path
  end

  # Initialize global variables
  $tick_counter = 0

  # Call :boot event
  Plugin.call :boot
end

def tick
  begin
    $last_ticked ||= Time.now
    $tick_counter = $tick_counter + 1

    span = Time.now.to_f - $last_ticked.to_f
    Android::Log.d "yukari-exvoice: tick! +#{span} sec, #{Delayer.size} job(s)" if span >= 0.6 || Delayer.size > 0

    Plugin.call :tick
    if $tick_counter >= 120
      Plugin.call :period
      $tick_counter = 0
    end
    Delayer.run until Delayer.empty?

    $last_ticked = Time.now
  rescue Exception => e
    puts e.inspect
    puts e.backtrace.map{|v| "\t" + v}.join("\n")
  end
end

begin
  bootstrap
  puts 'welcome to yukari-exvoice'
rescue Exception => e
  puts e.inspect
  puts e.backtrace.map{|v| "\t" + v}.join("\n")
end