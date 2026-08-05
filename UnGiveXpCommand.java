package fr.timeo.lumidiscord.discord;

import fr.timeo.lumidiscord.LumiDiscord;
import fr.timeo.lumidiscord.config.PluginConfig;
import fr.timeo.lumidiscord.database.DatabaseService;
import fr.timeo.lumidiscord.discord.commands.ProfileCommand;
import fr.timeo.lumidiscord.discord.commands.DailyXpCommand;
import fr.timeo.lumidiscord.discord.commands.XpTopDiscordCommand;
import fr.timeo.lumidiscord.minecraft.managers.LevelManager;
import fr.timeo.lumidiscord.minecraft.managers.XpBufferManager;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.channel.ChannelType;
import net.dv8tion.jda.api.entities.GuildVoiceState;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.utils.MemberCachePolicy;
import org.jetbrains.annotations.NotNull;

import java.awt.Color;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class DiscordBotService extends ListenerAdapter {

    private final LumiDiscord plugin;
    private final PluginConfig pluginConfig;
    private final DatabaseService databaseService;
    private final XpBufferManager xpBufferManager;
    private final LevelManager levelManager;
    private final ProfileCommand profileCommand;
    private final DailyXpCommand dailyXpCommand;
    private final XpTopDiscordCommand xpTopDiscordCommand;
    private final Map<String, Long> lastMessageByUser = new ConcurrentHashMap<>();
    // Cache Discord ID -> UUID Minecraft pour éviter 1 SQL/membre vocal/seconde
    private final Map<String, UUID> discordToMcUuidCache = new ConcurrentHashMap<>();
    private JDA jda;

    public JDA getJda() {
        return jda;
    }

    public DiscordBotService(LumiDiscord plugin, PluginConfig pluginConfig, DatabaseService databaseService, XpBufferManager xpBufferManager, LevelManager levelManager) {
        this.plugin = plugin;
        this.pluginConfig = pluginConfig;
        this.databaseService = databaseService;
        this.xpBufferManager = xpBufferManager;
        this.levelManager = levelManager;
        this.profileCommand = new ProfileCommand(plugin, databaseService, levelManager);
        this.dailyXpCommand = new DailyXpCommand(plugin, databaseService, pluginConfig);
        this.xpTopDiscordCommand = new XpTopDiscordCommand(plugin, databaseService);
    }

    public void start() {
        try {
            EnumSet<GatewayIntent> intents = EnumSet.of(GatewayIntent.GUILD_MESSAGES, GatewayIntent.MESSAGE_CONTENT, GatewayIntent.GUILD_VOICE_STATES, GatewayIntent.GUILD_MEMBERS);
            jda = JDABuilder.createDefault(pluginConfig.getString("discord.token"))
                    .enableIntents(intents)
                    .setMemberCachePolicy(MemberCachePolicy.ALL)
                    .addEventListeners(this, profileCommand, dailyXpCommand, xpTopDiscordCommand)
                    .build();
            jda.awaitReady();
            clearStaleGlobalCommands();
            registerSlashCommands();
            plugin.getServer().getScheduler().runTaskTimerAsynchronously(plugin, this::tickVoiceActivity, 20L, 20L);
            if (pluginConfig.getBoolean("discord.online-players-status")) {
                plugin.getServer().getScheduler().runTaskTimerAsynchronously(plugin, this::tickPresence, 20L, 1200L); // every minute
            }
            plugin.getLogger().info("Discord bot started.");
        } catch (Exception exception) {
            plugin.getLogger().severe("Unable to start Discord bot: " + exception.getMessage());
        }
    }

    public void stop() {
        if (jda != null) {
            jda.shutdown();
        }
    }

    private void clearStaleGlobalCommands() {
        if (jda == null) {
            return;
        }
        jda.retrieveCommands().queue(
                commands -> {
                    if (commands.isEmpty()) {
                        plugin.getLogger().info("Aucune ancienne commande globale Discord à supprimer.");
                        return;
                    }
                    for (var command : commands) {
                        command.delete().queue(
                                success -> plugin.getLogger().info("Commande globale Discord supprimée : /" + command.getName()),
                                error -> plugin.getLogger().warning("Échec de suppression de la commande globale Discord /" + command.getName() + " : " + error.getMessage())
                        );
                    }
                },
                error -> plugin.getLogger().warning("Impossible de lister les commandes globales Discord à nettoyer : " + error.getMessage())
        );
    }

    private void registerSlashCommands() {
        String guildId = pluginConfig.getString("discord.guild-id");
        if (guildId == null || guildId.isBlank()) {
            plugin.getLogger().severe("discord.guild-id est vide ou manquant : les slash commands Discord ne peuvent pas être enregistrées dans la guilde.");
            return;
        }
        Guild guild = jda.getGuildById(guildId);
        if (guild == null) {
            plugin.getLogger().severe("Guild Discord introuvable pour l'enregistrement des slash commands : " + guildId + " (vérifiez discord.guild-id dans config.yml)");
            return;
        }
        guild.updateCommands()
                .addCommands(
                        Commands.slash("link", "Lie votre compte Discord à votre compte Minecraft")
                                .addOption(OptionType.STRING, "code", "Code de liaison généré dans Minecraft", true),
                        Commands.slash("profil", "Affiche votre profil LumiDiscord / Discord"),
                        Commands.slash("givexp", "Donner de l'XP à un joueur Minecraft")
                                .addOption(OptionType.STRING, "joueur", "Pseudo Minecraft", true)
                                .addOption(OptionType.INTEGER, "montant", "Montant d'XP", true)
                                .setDefaultPermissions(net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions.enabledFor(net.dv8tion.jda.api.Permission.ADMINISTRATOR)),
                        Commands.slash("ungivexp", "Retirer de l'XP à un joueur Minecraft")
                                .addOption(OptionType.STRING, "joueur", "Pseudo Minecraft", true)
                                .addOption(OptionType.INTEGER, "montant", "Montant d'XP", true)
                                .setDefaultPermissions(net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions.enabledFor(net.dv8tion.jda.api.Permission.ADMINISTRATOR)),
                        Commands.slash("dailyxp", "Récupère ton bonus journalier d'XP"),
                        Commands.slash("xptop", "Affiche le classement des 10 meilleurs joueurs")
                )
                .queue(
                        commands -> plugin.getLogger().info("Slash commands mises à jour dans la guilde : " + commands.stream().map(command -> "/" + command.getName()).toList()),
                        error -> plugin.getLogger().warning("Échec de la mise à jour des slash commands dans la guilde : " + error.getMessage())
                );
    }

    @Override
    public void onSlashCommandInteraction(@NotNull SlashCommandInteractionEvent event) {
        if (event.getName().equals("givexp") || event.getName().equals("ungivexp")) {
            event.deferReply(true).queue();
            String playerName = event.getOption("joueur") != null ? event.getOption("joueur").getAsString() : "";
            long amount = event.getOption("montant") != null ? event.getOption("montant").getAsLong() : 0;
            if (amount <= 0 || playerName.isBlank()) {
                replyEmbed(event, "❌ Erreur", "Le nom du joueur et un montant supérieur à 0 sont requis.", Color.RED);
                return;
            }
            boolean isGive = event.getName().equals("givexp");

            plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
                org.bukkit.OfflinePlayer offlinePlayer = org.bukkit.Bukkit.getOfflinePlayer(playerName);
                if (offlinePlayer == null || offlinePlayer.getUniqueId() == null) {
                    replyEmbed(event, "❌ Erreur", "Le joueur spécifié n'existe pas sur le serveur.", Color.RED);
                    return;
                }
                UUID uuid = offlinePlayer.getUniqueId();
                try (Connection connection = databaseService.getConnection()) {
                    long oldXp;
                    try (PreparedStatement select = connection.prepareStatement("SELECT xp FROM players WHERE uuid = ? FOR UPDATE")) {
                        select.setString(1, uuid.toString());
                        try (ResultSet resultSet = select.executeQuery()) {
                            if (!resultSet.next()) {
                                replyEmbed(event, "❌ Erreur", "Le joueur spécifié n'existe pas dans la base de données.", Color.RED);
                                return;
                            }
                            oldXp = resultSet.getLong("xp");
                        }
                    }

                    long actualAmount = amount;
                    if (!isGive && oldXp < amount) {
                        actualAmount = oldXp;
                    }

                    String query = isGive ? "UPDATE players SET xp = xp + ? WHERE uuid = ?" : "UPDATE players SET xp = xp - ? WHERE uuid = ?";
                    try (PreparedStatement update = connection.prepareStatement(query)) {
                        update.setLong(1, actualAmount);
                        update.setString(2, uuid.toString());
                        update.executeUpdate();
                    }

                    long newXp;
                    try (PreparedStatement select = connection.prepareStatement("SELECT xp FROM players WHERE uuid = ?")) {
                        select.setString(1, uuid.toString());
                        try (ResultSet resultSet = select.executeQuery()) {
                            resultSet.next();
                            newXp = resultSet.getLong("xp");
                        }
                    }

                    int oldLevel = levelManager.calculateLevel(oldXp);
                    int newLevel = levelManager.calculateLevel(newXp);
                    if (isGive) {
                        plugin.getRewardManager().checkAndGrantForLevel(uuid, oldLevel, newLevel);
                    } else if (newLevel < oldLevel) {
                        plugin.getRewardManager().removeClaimsForLevelsAbove(uuid, newLevel);
                    }

                    long finalAmount = actualAmount;
                    long finalNewXp = newXp;
                    int finalNewLevel = newLevel;

                    plugin.getServer().getScheduler().runTask(plugin, () -> {
                        org.bukkit.entity.Player online = org.bukkit.Bukkit.getPlayer(uuid);
                        if (online != null && online.isOnline()) {
                            if (isGive) {
                                online.sendMessage("§6[LumiDiscord] §aVous avez reçu " + finalAmount + " XP de la part d'un administrateur (via Discord).");
                            } else {
                                online.sendMessage("§6[LumiDiscord] §cUn administrateur vous a retiré " + finalAmount + " XP (via Discord).");
                            }
                        }
                    });

                    if (isGive) {
                        replyEmbed(event, "✅ Succès", "Vous avez ajouté **" + finalAmount + "** XP à **" + playerName + "**. (Total : " + finalNewXp + " XP, Niveau " + finalNewLevel + ")", Color.GREEN);
                    } else {
                        replyEmbed(event, "✅ Succès", "Vous avez retiré **" + finalAmount + "** XP à **" + playerName + "**. (Total : " + finalNewXp + " XP, Niveau " + finalNewLevel + ")", Color.GREEN);
                    }
                } catch (SQLException exception) {
                    plugin.getLogger().warning("Unable to " + (isGive ? "give" : "remove") + " XP via Discord for " + playerName + ": " + exception.getMessage());
                    replyEmbed(event, "❌ Erreur", "Une erreur est survenue lors de la modification de l'XP.", Color.RED);
                }
            });
            return;
        }

        if (!event.getName().equals("link")) {
            return;
        }
        event.deferReply(true).queue();
        String code = event.getOption("code") != null ? event.getOption("code").getAsString() : "";
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try (Connection connection = databaseService.getConnection()) {
                connection.setAutoCommit(false);
                try (PreparedStatement select = connection.prepareStatement("SELECT code, uuid, expires_at, used FROM link_codes WHERE code = ? FOR UPDATE")) {
                    select.setString(1, code);
                    try (ResultSet resultSet = select.executeQuery()) {
                        if (!resultSet.next()) {
                            replyEmbed(event, "❌ Liaison impossible", "Code de liaison invalide.", Color.RED);
                            connection.rollback();
                            return;
                        }
                        Timestamp expiresAt = resultSet.getTimestamp("expires_at");
                        boolean used = resultSet.getBoolean("used");
                        String playerUuid = resultSet.getString("uuid");
                        if (used) {
                            replyEmbed(event, "⚠️ Code déjà utilisé", "Ce code a déjà été utilisé.", Color.ORANGE);
                            connection.rollback();
                            return;
                        }
                        if (expiresAt == null || expiresAt.toInstant().isBefore(java.time.Instant.now())) {
                            replyEmbed(event, "⏰ Code expiré", "Ce code a expiré. Demandez un nouveau code avec /linkdiscord.", Color.ORANGE);
                            connection.rollback();
                            return;
                        }
                        try (PreparedStatement existing = connection.prepareStatement("SELECT uuid FROM players WHERE discord_id = ? AND uuid <> ?")) {
                            existing.setString(1, event.getUser().getId());
                            existing.setString(2, playerUuid);
                            try (ResultSet existingResult = existing.executeQuery()) {
                                if (existingResult.next()) {
                                    replyEmbed(event, "⚠️ Compte déjà lié", "Ce compte Discord est déjà lié à un autre joueur.", Color.ORANGE);
                                    connection.rollback();
                                    return;
                                }
                            }
                        }
                        try (PreparedStatement updatePlayer = connection.prepareStatement("UPDATE players SET discord_id = ?, discord_username = ? WHERE uuid = ?")) {
                            updatePlayer.setString(1, event.getUser().getId());
                            updatePlayer.setString(2, event.getUser().getEffectiveName());
                            updatePlayer.setString(3, playerUuid);
                            updatePlayer.executeUpdate();
                        }
                        try (PreparedStatement markUsed = connection.prepareStatement("UPDATE link_codes SET used = TRUE WHERE code = ?")) {
                            markUsed.setString(1, code);
                            markUsed.executeUpdate();
                        }
                        connection.commit();
                        // Mettre à jour le cache XP immédiatement pour que le joueur commence à gagner de l'XP
                        UUID linkedUuid = UUID.fromString(playerUuid);
                        plugin.getXpBufferManager().markAsLinked(linkedUuid);
                        plugin.getStreakManager().loadStreak(linkedUuid);
                        // Mettre à jour le cache vocal
                        discordToMcUuidCache.put(event.getUser().getId(), linkedUuid);
                        if (!event.isAcknowledged()) {
                            event.deferReply(true).queue();
                        }
                        EmbedBuilder embed = new EmbedBuilder()
                                .setTitle("✅ Liaison réussie")
                                .setDescription("Votre compte Discord a bien été lié à votre profil Minecraft.")
                                .setColor(Color.GREEN)
                                .setThumbnail("https://crafatar.skyblock.net/avatars/" + playerUuid)
                                .setTimestamp(java.time.Instant.now());
                        event.getHook().sendMessageEmbeds(embed.build()).queue();
                    }
                }
            } catch (SQLException exception) {
                plugin.getLogger().warning("Unable to link Discord account: " + exception.getMessage());
                replyEmbed(event, "❌ Erreur de liaison", "Une erreur est survenue pendant la liaison.", Color.RED);
            }
        });
    }

    @Override
    public void onMessageReceived(@NotNull MessageReceivedEvent event) {
        if (event.getAuthor().isBot() || event.isWebhookMessage() || !event.isFromType(ChannelType.TEXT)) {
            return;
        }
        String channelName = event.getChannel().getName();
        if (!isAllowedChannel(channelName)) {
            return;
        }
        long now = System.currentTimeMillis();
        String userId = event.getAuthor().getId();
        Long lastSeen = lastMessageByUser.get(userId);
        int cooldown = pluginConfig.getInt("discord.message-spam-cooldown-seconds");
        if (lastSeen != null && now - lastSeen < cooldown * 1000L) {
            return;
        }
        lastMessageByUser.put(userId, now);
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            String discordId = event.getAuthor().getId();
            try (Connection connection = databaseService.getConnection();
                 PreparedStatement select = connection.prepareStatement("SELECT uuid FROM players WHERE discord_id = ?")) {
                select.setString(1, discordId);
                try (ResultSet resultSet = select.executeQuery()) {
                    if (resultSet.next()) {
                        UUID uuid = UUID.fromString(resultSet.getString("uuid"));
                        xpBufferManager.addDiscordMessage(uuid);
                    }
                }
            } catch (SQLException exception) {
                plugin.getLogger().warning("Unable to process Discord message XP: " + exception.getMessage());
            }
        });
    }

    private void tickVoiceActivity() {
        String guildId = pluginConfig.getString("discord.guild-id");
        if (guildId.isBlank() || jda == null) {
            return;
        }

        Guild guild = jda.getGuildById(guildId);
        if (guild == null) {
            plugin.getLogger().warning("Guild Discord introuvable pour le suivi vocal : " + guildId);
            return;
        }

        for (var voiceChannel : guild.getVoiceChannels()) {
            long humanCount = voiceChannel.getMembers().stream()
                    .filter(member -> !member.getUser().isBot())
                    .count();
            if (humanCount < 2) {
                continue;
            }

            voiceChannel.getMembers().stream()
                    .filter(member -> !member.getUser().isBot())
                    .filter(member -> {
                        var voiceState = member.getVoiceState();
                        return voiceState != null && voiceState.inAudioChannel()
                                && !voiceState.isMuted() && !voiceState.isDeafened();
                    })
                    .forEach(member -> {
                        UUID minecraftUuid = resolveMinecraftUuid(member.getId());
                        if (minecraftUuid != null) {
                            xpBufferManager.addDiscordVoice(minecraftUuid);
                        }
                    });
        }
    }

    private void tickPresence() {
        if (jda == null) return;
        int onlineCount = plugin.getServer().getOnlinePlayers().size();
        int maxPlayers = plugin.getServer().getMaxPlayers();
        jda.getPresence().setActivity(Activity.playing("🟢 Joueurs : " + onlineCount + "/" + maxPlayers));
    }

    private UUID resolveMinecraftUuid(String discordId) {
        // Vérifier le cache d'abord
        UUID cached = discordToMcUuidCache.get(discordId);
        if (cached != null) return cached;

        try (Connection connection = databaseService.getConnection();
             PreparedStatement statement = connection.prepareStatement("SELECT uuid FROM players WHERE discord_id = ?")) {
            statement.setString(1, discordId);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    UUID uuid = UUID.fromString(resultSet.getString("uuid"));
                    discordToMcUuidCache.put(discordId, uuid);
                    return uuid;
                } else {
                    // Pas lié, on ne cache pas null pour permettre une future liaison
                    return null;
                }
            }
        } catch (SQLException exception) {
            plugin.getLogger().warning("Unable to resolve Discord link for voice XP: " + exception.getMessage());
        }
        return null;
    }

    public void announceLevelUp(String discordId, String username, int level, String rewardName, UUID playerUuid) {
        String channelIdOrName = pluginConfig.getString("discord.level-up-channel");
        if (channelIdOrName == null || channelIdOrName.isBlank() || jda == null) {
            return;
        }
        Guild guild = jda.getGuildById(pluginConfig.getString("discord.guild-id"));
        if (guild == null) {
            return;
        }

        TextChannel channel = guild.getTextChannelsByName(channelIdOrName, true).stream().findFirst().orElse(null);
        if (channel == null) {
            try {
                channel = guild.getTextChannelById(channelIdOrName);
            } catch (IllegalArgumentException ignored) {
                channel = null;
            }
        }
        if (channel == null) {
            return;
        }

        String mention = (discordId != null && !discordId.isBlank()) ? "<@" + discordId + ">" : username;
        String description;
        if (rewardName != null && !rewardName.isBlank()) {
            description = "**" + username + "** vient d'atteindre le niveau **" + level + "** et a reçu : **" + rewardName + "** !";
        } else {
            description = "**" + username + "** vient d'atteindre le niveau **" + level + "** !";
        }
        EmbedBuilder embed = new EmbedBuilder()
                .setTitle("🎉 Niveau atteint")
                .setDescription(description)
                .setColor(Color.ORANGE)
                .setTimestamp(Instant.now());
        
        if (playerUuid != null) {
            embed.setThumbnail("https://crafatar.skyblock.net/avatars/" + playerUuid.toString());
        }

        channel.sendMessage(mention).addEmbeds(embed.build()).queue();

        if (discordId != null && !discordId.isBlank()) {
            String roleIdStr = pluginConfig.getString("roles." + level);
            if (roleIdStr != null && !roleIdStr.isBlank() && !roleIdStr.startsWith("ID_ROLE_")) {
                try {
                    net.dv8tion.jda.api.entities.Role role = guild.getRoleById(roleIdStr);
                    if (role != null) {
                        guild.retrieveMemberById(discordId).queue(member -> {
                            if (member != null) {
                                guild.addRoleToMember(member, role).queue(
                                        success -> plugin.getLogger().info("Rôle " + role.getName() + " attribué à " + username + " pour le niveau " + level),
                                        error -> plugin.getLogger().warning("Impossible d'attribuer le rôle " + role.getName() + " à " + username + " : " + error.getMessage())
                                );
                            }
                        }, error -> plugin.getLogger().warning("Membre Discord introuvable pour attribuer le rôle (" + discordId + ")"));
                    } else {
                        plugin.getLogger().warning("Rôle Discord introuvable avec l'ID : " + roleIdStr);
                    }
                } catch (IllegalArgumentException e) {
                    plugin.getLogger().warning("L'ID du rôle pour le niveau " + level + " est invalide : " + roleIdStr);
                }
            }
        }
    }

    private void replyEmbed(SlashCommandInteractionEvent event, String title, String description, Color color) {
        EmbedBuilder embed = new EmbedBuilder()
                .setTitle(title)
                .setDescription(description)
                .setColor(color)
                .setTimestamp(Instant.now());
        event.getHook().sendMessageEmbeds(embed.build()).setEphemeral(true).queue();
    }

    private boolean isAllowedChannel(String channelName) {
        List<String> allowed = pluginConfig.getStringList("discord.allowed-text-channels");
        return allowed.isEmpty() || allowed.contains(channelName);
    }
}
