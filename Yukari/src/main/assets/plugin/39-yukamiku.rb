# encoding: utf-8
Android.require_assets 'yukamiku/mikuenv.rb'
Android.require_assets 'yukamiku/user.rb'
Android.require_assets 'yukamiku/message.rb'
Android.require_assets 'yukamiku/gui.rb'
Android.require_assets 'yukamiku/service.rb'
Android.require_assets 'yukamiku/userconfig.rb'

module Plugin::YukaMiku
  class << self

    # twicca extrasをmikutter messageに変換する。
    # @param [Hash] extra twicca extras
    # @return [Message] mikutter message
    def to_message(extra)
      value = {
          id: extra['id'].to_i,
          message: extra['text'],
          user: to_user(extra),
          receiver: nil,
          replyto: extra['in_reply_to_status_id'].to_i,
          retweet: nil,
          source: extra['source'],
          geo: "#{extra['latitude']}, #{extra['longitude']}",
          exact: false,
          created: nil, #extra['created_at'],
          modified: nil #extra['created_at']
      }
      Message.new(value)
    end

    # twicca extrasをmikutter userに変換する
    # @param [Hash] extra twicca extras
    # @return [User] mikutter user
    def to_user(extra)
      value = {
          id: extra['user_id'],
          idname: extra['user_screen_name'],
          name: extra['user_name'],
          profile_image_url: extra['user_profile_image_url_bigger']
      }
      User.new(value)
    end

  end
end

#
# ゆかミクは尊い (mikutter互換のDSLを提供するプラグインです)
#
Plugin.create :yukamiku do

  # mikutterコマンドを定義
  # ==== Args
  # [slug] コマンドスラッグ
  # [options] コマンドオプション
  # [&exec] コマンドの実行内容
  defdsl :command do |slug, options, &exec|
    miku_command = options.merge(slug: slug, exec: exec)

    # パラメータの互換性対応
    miku_command[:label] = miku_command[:name]

    # 近い機能を持つDSLに振り分ける
    case miku_command[:role]
      when :timeline
        puts "mikutter_command #{slug}(role: :timeline) => twicca_action :show_tweet"

        twicca_action(:show_tweet, slug, miku_command) do |extra|
          opt = Plugin::GUI::Event.new(:contextmenu, Plugin[:gtk].widgetof(:timeline), [Plugin::YukaMiku.to_message(extra)])

          # Postboxの変化を監視するために現在値を保持
          postbox = Plugin[:gtk].widgetof(:postbox)
          postbox_before_text = postbox.widget_post.buffer.text
          postbox_before_options = postbox.options

          exec.call(opt)

          # Postboxの内容が変化していたらツイート画面を出す
          if postbox.widget_post.buffer.text != postbox_before_text
            Plugin.call(:intent, activity: :TweetActivity, mode: :tweet, text: postbox.widget_post.buffer.text)
          elsif postbox.options != postbox_before_options
            call_opt = {activity: :TweetActivity, text: [postbox.options[:header], postbox.options[:footer]].join(' ').strip}

            if postbox.options.has_key? :to
              call_opt[:mode] = :reply
              call_opt[:in_reply_to] = postbox.options[:to][:id]
            else
              call_opt[:mode] = :tweet
            end

            Plugin.call(:intent, call_opt)
          end
        end

      when :postbox
        puts "mikutter_command #{slug}(role: :postbox) => twicca_action :edit_tweet"

        twicca_action(:edit_tweet, slug, miku_command) do |extra|
          opt = Plugin::GUI::Event.new(:contextmenu, Plugin[:gtk].widgetof(:postbox), [])

          # Postboxに現在の入力内容を投入する
          postbox = Plugin[:gtk].widgetof(:postbox)
          postbox.widget_post.buffer.text = extra['text']

          exec.call(opt)

          # Postboxの内容を結果として返却する
          {result_code: :ok, intent: {text: postbox.widget_post.buffer.text}}
        end

      else
        puts "mikutter_command #{slug}(role: #{miku_command[:role]}) is not compatible."
    end
  end

  # 設定画面を作る
  # ==== Args
  # - String name タイトル
  # - Proc &place 設定画面を作る無名関数
  defdsl :settings do |name, &place|
    puts "mikutter_settings #{name} is not compatible."
  end

  # ユーザに向けて通知を発生させる。
  # 通知は、activityプラグインなど、通知の表示に対応するプラグインが
  # 入っていればユーザがそれを確認することができるが、そのようなプラグインがない場合は
  # 通知は単に無視される。
  # ==== Args
  # [kind] Symbol 通知の種類
  # [title] String 通知のタイトル
  # [args] Hash その他オプション。主に以下の値
  #   icon :: String|Gdk::Pixbuf アイコン
  #   date :: Time イベントの発生した時刻
  #   service :: Service 関係するServiceオブジェクト
  #   related :: 自分に関係するかどうかのフラグ
  defdsl :activity do |kind, title, args = {}|
    Plugin.call(:modify_activity,
                { plugin: self,
                  kind: kind,
                  title: title,
                  date: Time.new,
                  description: title }.merge(args))
  end
end