# encoding: utf-8
# Yukari; exVoice
# mrubyとPluggaloidを使用したモバイル向けプラグインサブシステム

def bootstrap
  # Initialize Pluggaloid
  Delayer.default = Delayer.generate_class(priority: %i<high, normal, low>, default: :normal)
  include Pluggaloid

  # Load bundle plugins
  Android::AssetDir.open('plugin').each do |path|
    if path.end_with? '.rb'
      Android::Log.d "Require: #{path}"
      Android.require_assets path
    end
  end
end

begin
  bootstrap
  Android::Log.d 'welcome to yukari-exvoice'
rescue Exception => e
  Android::Log.d e.inspect
  Android::Log.d e.backtrace.map{|v| "\t" + v}.join("\n")
end