# encoding: UTF-8

#
# twiccaインターフェースと互換性のあるプラグインを作成するためのDSLを提供します。
#
Plugin.create(:twicca) do

  defdsl :twicca_action do |intent_action, slug, options = {}, &exec|
    command = options.merge(intent_action: intent_action, slug: slug, exec: exec)
    add_event_filter(:"twicca_action_#{intent_action}") { |action|
      action[slug] = command
      [action]
    }
  end

end