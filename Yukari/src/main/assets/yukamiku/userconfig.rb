# coding: utf-8

# UserConfig互換クラス
class UserConfig
  # 今はなにもまともに保持しない

  @@store = {}

  def self.[](key)
    @@store[key]
  end

  def self.[]=(key, value)
    @@store[key] = value
  end
end