package com.yamada.notkayla.module.modules;

import com.sedmelluq.discord.lavaplayer.player.AudioLoadResultHandler;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayer;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.player.DefaultAudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.player.event.AudioEventAdapter;
import com.sedmelluq.discord.lavaplayer.source.AudioSourceManagers;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.track.AudioPlaylist;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackEndReason;
import com.sedmelluq.discord.lavaplayer.track.playback.AudioFrame;
import com.yamada.notkayla.Yamada;
import com.yamada.notkayla.utils.Timeout;
import net.dv8tion.jda.core.EmbedBuilder;
import net.dv8tion.jda.core.audio.AudioSendHandler;
import net.dv8tion.jda.core.entities.Guild;
import net.dv8tion.jda.core.entities.Member;
import net.dv8tion.jda.core.entities.TextChannel;
import net.dv8tion.jda.core.events.message.guild.GuildMessageReceivedEvent;
import net.dv8tion.jda.core.managers.AudioManager;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

public class MusicModule {
    private final AudioPlayerManager playerManager;
    private final Map<Long, GuildMusicManager> musicManagers;

    public MusicModule() {
        playerManager = new DefaultAudioPlayerManager();
        musicManagers = new HashMap<>();

        AudioSourceManagers.registerLocalSource(playerManager);
        AudioSourceManagers.registerRemoteSources(playerManager);
    }

    // From the 1.3.0 demo of Lavaplayer.
    public synchronized GuildMusicManager getGuildAudioPlayer(Guild guild, TextChannel channel) {
        long guildId = guild.getIdLong();
        GuildMusicManager musicManager = musicManagers.get(guildId);

        if (musicManager == null) {
            musicManager = new GuildMusicManager(playerManager,channel);
            musicManagers.put(guildId, musicManager);
        }

        guild.getAudioManager().setSendingHandler(musicManager.getSendHandler());

        return musicManager;
    }

    private boolean hasGuildAudioPlayerFor(Long id){
        if (musicManagers.containsKey(id)){
            return musicManagers.get(id).player.getPlayingTrack() != null;
        } else return false;
    }

    public void loadAndPlay(GuildMessageReceivedEvent event, String trackURL) {
        TextChannel textChannel = event.getChannel();
        GuildMusicManager musicManager = getGuildAudioPlayer(event.getGuild(),event.getChannel());

        playerManager.loadItemOrdered(musicManager, trackURL, new AudioLoadResultHandler() {
            @Override
            public void trackLoaded(AudioTrack track) {
                musicManager.channel.sendMessage("Added first track to queue").queue();
                play(event, musicManager, track);
            }

            @Override
            public void playlistLoaded(AudioPlaylist playlist) {
                AudioTrack firstTrack = playlist.getSelectedTrack();

                if (firstTrack == null) {
                    firstTrack = playlist.getTracks().get(0);
                }

                textChannel.sendMessage("Adding to queue " + firstTrack.getInfo().title + " (first track of playlist: " + playlist.getName() + ")").queue();

                play(event, musicManager, firstTrack);
            }

            @Override
            public void noMatches() {
                textChannel.sendMessage("Can't find any matches. :(").queue();
            }

            @Override
            public void loadFailed(FriendlyException exception) {
                textChannel.sendMessage("Not only did the loading fail, but what comes next will be a total disaster.").queue();
            }
        });
    }
    private void play(GuildMessageReceivedEvent event, GuildMusicManager musicManager, AudioTrack track) {
        AudioManager audioManager = event.getGuild().getAudioManager();
        if (!event.getMember().getVoiceState().inVoiceChannel()){
            event.getChannel().sendMessage("You must be in "+(event.getGuild().getSelfMember().getVoiceState().inVoiceChannel()?"my":"a")+" voice chat to use my music commands.").queue();
            return;
        }
        if (audioManager.isConnected() && audioManager.isAttemptingToConnect() && !event.getMember().getVoiceState().getChannel().getId().equals(audioManager.getConnectedChannel().getId())){
            event.getChannel().sendMessage("You have to be in **my** voice chat to use my music commands.").queue();
            return;
        }
        if (!audioManager.isConnected() && !audioManager.isAttemptingToConnect()){
            audioManager.openAudioConnection(event.getMember().getVoiceState().getChannel());
        }
        track.setUserData(event.getMember());
        musicManager.scheduler.queue(track);
    }

    @SuppressWarnings("unused")
    public void skipTrack(GuildMessageReceivedEvent event) {
        GuildMusicManager musicManager = getGuildAudioPlayer(event.getGuild(),event.getChannel());
        musicManager.scheduler.nextTrack();
        if (!musicManager.scheduler.queue.isEmpty())event.getChannel().sendMessage("Skipped to next track.").queue();
    }

    //used for QueueCommand
    public void getQueue(GuildMessageReceivedEvent event, String[] args){
        if (!hasGuildAudioPlayerFor(event.getGuild().getIdLong())){
            event.getChannel().sendMessage("Bot is not connected to a voice chat.").queue();
            return;
        }
        GuildMusicManager guildAudioPlayer = getGuildAudioPlayer(event.getGuild(), event.getChannel());
        List<AudioTrack> queue = new ArrayList<>(guildAudioPlayer.scheduler.queue);
        EmbedBuilder eb = new EmbedBuilder();
        eb.setTitle("Current queue: ");
        AudioTrack now = guildAudioPlayer.scheduler.player.getPlayingTrack();
        eb.addField("Now: " + now.getInfo().title, "Uploaded by: " + now.getInfo().author, false);
        for (int i=0;i<queue.size();i++) {
            AudioTrack audioTrack = queue.get(i);
            eb.addField((i+1) + ". " + audioTrack.getInfo().title,"Uploaded by: " + audioTrack.getInfo().author,false);
        }
        event.getChannel().sendMessage(eb.build()).queue();
    }

    /**
     * Get the queue silently. Only used for PPCommand
     * @param event
     * @return
     */
    public ArrayList<AudioTrack> getQueue(GuildMessageReceivedEvent event) {
        GuildMusicManager guildMusicManager = getGuildAudioPlayer(event.getGuild(), event.getChannel());
        return new ArrayList<>(guildMusicManager.scheduler.queue);
    }

    public void pause(GuildMessageReceivedEvent event) {
        GuildMusicManager musicManager = getGuildAudioPlayer(event.getGuild(),event.getChannel());
        musicManager.scheduler.pause();
    }

    public void stop(GuildMessageReceivedEvent event) {
        GuildMusicManager musicManager = getGuildAudioPlayer(event.getGuild(),event.getChannel());
        musicManager.scheduler.stop();
        event.getChannel().sendMessage("Cleared the queue and left the channel.").queue();
    }

    public class GuildMusicManager {
        /**
         * Audio player for the guild.
         */
        public final AudioPlayer player;
        /**
         * Track scheduler for the player.
         */
        final TrackScheduler scheduler;
        /**
         * Text channel in which the bot was told to join
         */
        final TextChannel channel;
        /**
         * Creates a player and a track scheduler.
         * @param manager Audio player manager to use for creating the player.
         * @param channel Text channel for sending messages informing the user of events.
         */
        GuildMusicManager(AudioPlayerManager manager, TextChannel channel) {
            player = manager.createPlayer();
            scheduler = new TrackScheduler(player,this);
            player.addListener(scheduler);
            this.channel = channel;
        }

        /**
         * @return Wrapper around AudioPlayer to use it as an AudioSendHandler.
         */
        AudioPlayerSendHandler getSendHandler() {
            return new AudioPlayerSendHandler(player);
        }
    }

    // TODO: Find a way to simplify this.
    public class TrackScheduler extends AudioEventAdapter {
        private final AudioPlayer player;
        public final BlockingQueue<AudioTrack> queue;
        private final GuildMusicManager gm;
        private Timeout stopping;
        /**
         * @param player The audio player this scheduler uses
         * @param guildMusicManager does the thing
         */
        TrackScheduler(AudioPlayer player, GuildMusicManager guildMusicManager) {
            this.player = player;
            this.queue = new LinkedBlockingQueue<>();
            gm = guildMusicManager;
        }

        /**
         * Add the next track to queue or play right away if nothing is in the queue.
         *
         * @param track The track to play or add to queue.
         */
        public void queue(AudioTrack track) {
            // Calling startTrack with the noInterrupt set to true will start the track only if nothing is currently playing. If
            // something is playing, it returns false and does nothing. In that case the player was already playing so this
            // track goes to the queue instead.
            if (!player.startTrack(track, true)) {
                queue.offer(track);
            }
        }

        void stop(){

            gm.channel.getGuild().getAudioManager().closeAudioConnection();
            player.destroy();
            queue.clear();
        }

        void pause() {
            if (player.isPaused()) player.setPaused(false);
            else player.setPaused(true);
        }

        /**
         * Start the next track, stopping the current one if it is playing.
         */
        void nextTrack() {
            // Start the next track, regardless of if something is already playing or not. In case queue was empty, we are
            // giving null to startTrack, which is a valid argument and will simply stop the player.
            player.startTrack(queue.poll(), false);
        }

        @Override
        public void onPlayerPause(AudioPlayer player) {
            gm.channel.sendMessage("Player paused.").queue();
        }

        @Override
        public void onPlayerResume(AudioPlayer player) {
            gm.channel.sendMessage("Player resumed.").queue();
        }

        @Override
        public void onTrackStart(AudioPlayer player, AudioTrack track) {
            if (stopping != null) {
                stopping.cancel();
                stopping = null;
            }
            Member m = (Member) track.getUserData();
            gm.channel.sendMessage(new EmbedBuilder().setTitle("Now playing")
                    .setDescription(track.getInfo().title)
                    .setAuthor(m.getEffectiveName(),null,m.getUser().getEffectiveAvatarUrl())
                    .build()).queue();
        }

        @Override
        public void onTrackEnd(AudioPlayer player, AudioTrack track, AudioTrackEndReason endReason) {
            // Only start the next track if the end reason is suitable for it (FINISHED or LOAD_FAILED)
            if (endReason.mayStartNext || endReason == AudioTrackEndReason.REPLACED) {
                if (endReason == AudioTrackEndReason.LOAD_FAILED){
                    gm.channel.sendMessage("Could not load " + track.getInfo().title + (queue.size() ==0? ". ":", skipping to next song in queue.")).queue();
                }
                if(queue.size() == 0 && endReason != AudioTrackEndReason.REPLACED){
                    gm.channel.sendMessage(new EmbedBuilder()
                            .setTitle("The queue is now empty.")
                            .setDescription("Add more songs!")
                            .setFooter("I'll disconnect in 10 minutes if you don't add any more.",
                                    gm.channel.getJDA().getSelfUser().getEffectiveAvatarUrl())
                            .build()).complete();
                    stopping = new Timeout(600000, this::stop);
                    stopping.start();
                    return;
                }
                nextTrack();
            }
        }
    }
    /**
     * This is a wrapper around AudioPlayer which makes it behave as an AudioSendHandler for JDA. As JDA calls canProvide
     * before every call to provide20MsAudio(), we pull the frame in canProvide() and use the frame we already pulled in
     * provide20MsAudio().
     */
    class AudioPlayerSendHandler implements AudioSendHandler {
        private final AudioPlayer audioPlayer;
        private AudioFrame lastFrame;

        /**
         * @param audioPlayer Audio player to wrap.
         */
        AudioPlayerSendHandler(AudioPlayer audioPlayer) {
            this.audioPlayer = audioPlayer;
        }

        @Override
        public boolean canProvide() {
            if (lastFrame == null) {
                lastFrame = audioPlayer.provide();
            }

            return lastFrame != null;
        }

        @Override
        public byte[] provide20MsAudio() {
            if (lastFrame == null) {
                lastFrame = audioPlayer.provide();
            }

            byte[] data = lastFrame != null ? lastFrame.getData() : null;
            lastFrame = null;

            return data;
        }

        @Override
        public boolean isOpus() {
            return true;
        }
    }
}