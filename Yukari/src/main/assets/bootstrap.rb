# encoding: utf-8
# Yukari; exVoice
# mrubyとPluggaloidを使用したモバイル向けプラグインサブシステム

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
    Android::Log.d "Require: #{path}"
    Android.require_assets path
  end
end

def tick
  begin
    $last_ticked ||= Time.now

    span = Time.now.to_f - $last_ticked.to_f
    Android::Log.d "yukari-exvoice: tick! +#{span} sec, #{Delayer.size} job(s)" if span >= 0.6 || Delayer.size > 0
    Delayer.run until Delayer.empty?

    $last_ticked = Time.now
  rescue Exception => e
    Android::Log.d e.inspect
    Android::Log.d e.backtrace.map{|v| "\t" + v}.join("\n")
  end
end

begin
  bootstrap
  Android::Log.d 'welcome to yukari-exvoice'
rescue Exception => e
  Android::Log.d e.inspect
  Android::Log.d e.backtrace.map{|v| "\t" + v}.join("\n")
end