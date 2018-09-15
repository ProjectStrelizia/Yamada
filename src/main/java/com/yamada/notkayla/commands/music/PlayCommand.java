package com.yamada.notkayla.commands.music;


import com.yamada.notkayla.Kayla;
import com.yamada.notkayla.commands.Command;
import net.dv8tion.jda.core.JDA;
import net.dv8tion.jda.core.events.message.guild.GuildMessageReceivedEvent;

@Command(name="play", description = "Play something.", group = "music")
public class PlayCommand {
    //TODO: Add search capabilities.
    public void run(JDA bot, GuildMessageReceivedEvent event, String[] args) {
        //MusicAdapter music = (MusicAdapter) Kayla.modules.get("music");
        Kayla.music.loadAndPlay(event.getChannel(), String.join("", args));
    }
}
