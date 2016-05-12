# encoding: utf-8

module Retriever
  # Retriever::Modelとの最低限の互換性を持たせようとしたクラス
  class Model
    class << self
      def keys=(keys)
        @keys = keys
      end

      def keys
        @keys
      end

      def cast(value, type, required=false)
        if value.nil?
          raise InvalidTypeError, 'it is required value'+[value, type, required].inspect if required
          nil
        elsif type.is_a?(Symbol)
          begin
            result = (value and Retriever::cast_func(type).call(value))
            if required and not result
              raise InvalidTypeError, 'it is required value, but returned nil from cast function'
            end
            result
          rescue InvalidTypeError
            raise InvalidTypeError, "#{value.inspect} is not #{type}"
          end
        elsif type.is_a?(Array)
          if value.respond_to?(:map)
            value.map { |v| cast(v, type.first, required) }
          elsif not value
            nil
          else
            raise InvalidTypeError, 'invalid type'
          end
        elsif value.is_a?(type)
          raise InvalidTypeError, 'invalid type' if required and not value.id
          value.id
        elsif self.cast(value, type.keys.assoc(:id)[1], true)
          value
        end
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

  @@cast = {
      :int => lambda { |v|
        begin
          v.to_i;
        rescue NoMethodError then
          raise InvalidTypeError
        end },
      :bool => lambda { |v| !!(v and not v == 'false') },
      :string => lambda { |v|
        begin
          v.to_s;
        rescue NoMethodError then
          raise InvalidTypeError
        end },
      :time => lambda { |v|
        if not v then
          nil
        elsif v.is_a? String then
          Time.parse(v)
        else
          Time.at(v)
        end
      }
  }

  def self.cast_func(type)
    @@cast[type]
  end

  class RetrieverError < StandardError
  end

  class InvalidTypeError < RetrieverError
  end
end