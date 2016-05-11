# encoding: utf-8
Android.require_assets 'yukamiku/message.rb'
Android.require_assets 'yukamiku/gui.rb'

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
        puts "mikutter_command #{slug}(role:#{miku_command}) => twicca_action :show_tweet"

        twicca_action(:show_tweet, slug, miku_command) do |extra|
          opt = Plugin::GUI::Event.new(:contextmenu, nil, [extra])
          exec.call(opt)
        end

      when :postbox
        puts "mikutter_command #{slug}(role:#{miku_command}) => twicca_action :edit_tweet"

        twicca_action(:edit_tweet, slug, miku_command) do |extra|
          opt = Plugin::GUI::Event.new(:contextmenu, nil, [extra])
          exec.call(opt)
        end

      else
        puts "mikutter_command #{slug}(role:#{miku_command[:role]}) is not compatible."
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