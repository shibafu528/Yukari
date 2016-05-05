# encoding: UTF-8

module Plugin::PostCommand
  class Finish; end
end

#
# いわゆる隠しコマンドを追加するためのDSLを提供します。
#
Plugin.create(:post_command) do

  defdsl :post_command do |command, &exec|
    add_event_filter(:post_command) do |action|
      action[command] = exec
      [action]
    end
  end

  # commands = Plugin.filtering(:post_command, {})
  # replaced_text = commands[:find_command].call(text) unless commands[:find_command].nil?
  # if replaced_text.nil?
  #   # noting to do
  # elsif replaced_text.is_a? Plugin::PostCommand::Finish
  #   # finish activity
  #   finish()
  # else
  #   # tweet!
  #   tweet(replaced_text)
  # end

end