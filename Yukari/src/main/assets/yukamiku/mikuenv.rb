# encoding: utf-8

# core/config.rb, core/environment.rb 互換モジュール

module CHIConfig
  NAME = "Yukari for Android"
  ACRO = "yukari4a"

  TWITTER_CONSUMER_KEY = "XXXXXXXXXXXXXXXXXXXXXX"
  TWITTER_CONSUMER_SECRET = "XXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX"
  TWITTER_AUTHENTICATE_REVISION = 1

  PIDFILE = "/sdcard/Android/data/shibafu.yukari/files/#{ACRO}.pid"

  CONFROOT = "/sdcard/Android/data/shibafu.yukari/files/"

  TMPDIR = File.join(CONFROOT, 'tmp')

  LOGDIR = File.join(CONFROOT, 'log')

  SETTINGDIR = File.join(CONFROOT, 'settings')

  CACHE = File.join(CONFROOT, 'cache')

  PLUGIN_PATH = File.join(CONFROOT, 'plugin')

  AutoTag = false

  NeverRetrieveOverlappedMumble = false

  REVISION = 9999

  VERSION = [3, 3, 9, REVISION]
end

module Environment
  NAME = CHIConfig::NAME

  ACRO = CHIConfig::ACRO

  TWITTER_CONSUMER_KEY = CHIConfig::TWITTER_CONSUMER_KEY
  TWITTER_CONSUMER_SECRET = CHIConfig::TWITTER_CONSUMER_SECRET
  TWITTER_AUTHENTICATE_REVISION = CHIConfig::TWITTER_AUTHENTICATE_REVISION

  PIDFILE = CHIConfig::PIDFILE

  CONFROOT = CHIConfig::CONFROOT

  TMPDIR = CHIConfig::TMPDIR

  LOGDIR = CHIConfig::LOGDIR

  SETTINGDIR = CHIConfig::SETTINGDIR

  CACHE = CHIConfig::CACHE

  PLUGIN_PATH = CHIConfig::PLUGIN_PATH

  AutoTag = CHIConfig::AutoTag

  NeverRetrieveOverlappedMumble = CHIConfig::NeverRetrieveOverlappedMumble

  class Version
    include Comparable

    attr_reader :mejor, :minor, :debug, :devel

    def initialize(mejor, minor, debug, devel=0)
      @mejor = mejor
      @minor = minor
      @debug = debug
      @devel = devel
    end

    def to_a
      [@mejor, @minor, @debug, @devel]
    end

    def to_s
      if 9999 == @devel
        [@mejor, @minor, @debug].join('.')
      else
        [@mejor, @minor, @debug, @devel].join('.')
      end
    end

    def to_i
      @mejor
    end

    def to_f
      @mejor + @minor/100
    end

    def inspect
      "#{Environment::NAME} ver.#{self.to_s}"
    end

    def size
      to_a.size
    end

    def <=>(other)
      self.to_a <=> other.to_a
    end

  end

  VERSION = Version.new(*CHIConfig::VERSION)

end

def notice(msg)
  log "notice", msg
end

def warn(msg)
  log "warning", msg
end

def error(msg)
  log "error", msg
end

def log(prefix, object)
  begin
    msg = "#{prefix}: #{object}"
    msg += "\nfrom " + object.backtrace.join("\nfrom ") if object.is_a? Exception

    if msg.is_a? Exception
      puts msg.to_s
      puts msg.backtrace.join("\n")
    else
      puts msg
    end
  rescue Exception => e
    puts "critical!: #{e.to_s}"
    puts e.backtrace.join("\n")
  end
end