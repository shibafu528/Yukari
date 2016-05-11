# encoding: utf-8

# Retriever::Modelとの最低限の互換性を持たせようとしたクラス
class CompatModel
  class << self
    def keys=(keys)
      @keys = keys
    end

    def keys
      @keys
    end
  end

  def initialize(args)
    @value = args.dup
  end

  def fetch(key)
    @value[key.to_sym]
  end

  alias [] fetch

  def id
    @value[:id]
  end

  def hash
    self.id.to_i
  end

  def <=>(other)
    if other.is_a?(Retriever)
      id - other.id
    elsif other.respond_to?(:[]) and other[:id]
      id - other[:id]
    else
      id - other
    end
  end

  def ==(other)
    if other.is_a?(Retriever)
      id == other.id
    elsif other.respond_to?(:[]) and other[:id]
      id == other[:id]
    else
      id == other
    end
  end

  def to_hash
    @value.dup
  end
end