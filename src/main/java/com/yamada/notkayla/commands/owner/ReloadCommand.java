package com.yamada.notkayla.commands.owner;

import com.yamada.notkayla.Events;
import com.yamada.notkayla.Yamada;
import com.yamada.notkayla.commands.Command;
import net.dv8tion.jda.core.JDA;
import net.dv8tion.jda.core.events.message.guild.GuildMessageReceivedEvent;

import java.lang.reflect.InvocationTargetException;
import java.util.Arrays;
import java.util.logging.Level;

@Command(name="reload",group = "owner")
public class ReloadCommand {
    public void run (JDA bot, GuildMessageReceivedEvent event,String[] args) throws IllegalAccessException, InstantiationException, NoSuchMethodException, InvocationTargetException, ClassNotFoundException {
        if(bot.getGuildById("481210197453438996").getMemberById(event.getAuthor().getId()) == null) return;
        Yamada.log.log(Level.INFO, Arrays.toString(args));
        Events.registry.reload(args[1]);
    }
}
