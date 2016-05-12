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
    validate
  end

  def fetch(key)
    @value[key.to_sym]
  end

  alias [] fetch

  def []=(key, value)
    @value[key.to_sym] = value
    value
  end

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

  def validate
    raise RuntimeError, "argument is #{@value}, not Hash" if not @value.is_a?(Hash)
    self.class.keys.each { |column|
      key, type, required = *column
      begin
        Model.cast(self.fetch(key), type, required)
      rescue InvalidTypeError => e
        estr = e.to_s + "\nin #{self.fetch(key).inspect} of #{key}"
        warn estr
        warn @value.inspect
        raise InvalidTypeError, estr
      end
    }
  end
end