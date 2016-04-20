# encoding: utf-8
# Yukari; exVoice
# mrubyとPluggaloidを使用したモバイル向けプラグインサブシステム

# Initialize Pluggaloid
Delayer.default = Delayer.generate_class(priority: %i<high, normal, low>, default: :normal)
include Pluggaloid

# Load bundle plugins

class Object
  def define_c(name)
    c_method_name = "c_#{name}".to_sym
    define_method(name) do |*argv|
      __send__(c_method_name, *argv)
    end
  end
end

# TODO: なんか頑張ってネイティブ実装にしておくれ
module Assets
  class Dir
    include Enumerable

    class << self
      define_c :open
    end

    def each(&block)
      e = Enumerator.new do |y|
        while (path = self.next) do
          y << path
        end
      end

      e.each(&block) if block
    end

    define_c :close

    private
    define_c :next
  end
end

# Load bundle plugins
Assets::Dir.open('plugin').each do |path|
  Assets.require path if path.end_with? '.rb'
end