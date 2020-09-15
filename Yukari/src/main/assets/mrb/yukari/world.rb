# encoding: UTF-8

module Yukari
  class World < Diva::Model
    include Plugin::World::TraditionalBehavior::World
    register :yukari_world, name: 'Yukari virtual world'
  end
end

Plugin.create :yukari do
  vworld = Yukari::World.new({})

  filter_world_current do |result|
    if result
      [result]
    else
      [vworld]
    end
  end
end