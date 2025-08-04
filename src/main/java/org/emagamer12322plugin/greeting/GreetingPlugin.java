package org.emagamer12322plugin.greeting;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import java.util.Map;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.ArrayList;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import me.clip.placeholderapi.PlaceholderAPI;
import org.bukkit.configuration.ConfigurationSection;
import java.io.InputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import java.util.ArrayList;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.net.URISyntaxException;
import java.net.URL;
import java.io.File;



public final class GreetingPlugin extends JavaPlugin implements Listener, TabCompleter {
    private List<String> getResourceFolderFiles(String folder) {
        List<String> filenames = new ArrayList<>();
        try {
            URL url = getClass().getClassLoader().getResource(folder);
            if (url == null) {
                return filenames;  // No existe la carpeta
            }
            if (url.getProtocol().equals("jar")) {
                String path = url.getPath();
                String jarPath = path.substring(5, path.indexOf("!"));
                try (JarFile jar = new JarFile(jarPath)) {
                    jar.stream().forEach(jarEntry -> {
                        String name = jarEntry.getName();
                        if (name.startsWith(folder + "/") && !jarEntry.isDirectory()) {
                            filenames.add(name.substring((folder + "/").length()));
                        }
                    });
                }
            } else if (url.getProtocol().equals("file")) {
                File folderFile = new File(url.toURI());
                if (folderFile.isDirectory()) {
                    for (File file : folderFile.listFiles()) {
                        if (file.isFile()) {
                            filenames.add(file.getName());
                        }
                    }
                }
            }
        } catch (IOException | URISyntaxException e) {
            getLogger().warning("Error listando recursos: " + e.getMessage());
        }
        return filenames;
    }

    private File playerDataFile;
    private FileConfiguration playerDataConfig;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        copiarCarpetaEventos();
        saveResource("readme.txt", false);
        guardarArchivosIdioma();

        // Cargar playerdata.yml
        playerDataFile = new File(getDataFolder(), "playerdata.yml");
        if (!playerDataFile.exists()) {
            try {
                playerDataFile.createNewFile();
            } catch (IOException e) {
                getLogger().severe("Could not create playerdata.yml");
                e.printStackTrace();
            }
        }
        playerDataConfig = YamlConfiguration.loadConfiguration(playerDataFile);

        getServer().getPluginManager().registerEvents(this, this);
        this.getCommand("greeting").setTabCompleter(this);
        getLogger().info("Plugin activated");

    }
    private void copiarCarpetaEventos() {
        File carpetaDestino = new File(getDataFolder(), "events");

        if (!carpetaDestino.exists()) {
            carpetaDestino.mkdirs();

            // Obtener lista de archivos en "events/" del jar
            try {
                for (String nombreArchivo : getResourceFolderFiles("events")) {
                    InputStream input = getResource("events/" + nombreArchivo);
                    if (input != null) {
                        File archivoDestino = new File(carpetaDestino, nombreArchivo);
                        Files.copy(input, archivoDestino.toPath());
                        input.close();
                    }
                }
            } catch (IOException e) {
                getLogger().warning("No se pudo copiar la carpeta 'events': " + e.getMessage());
            }
        }
    }


    @Override
    public void onDisable() {
        try {
            playerDataConfig.save(playerDataFile);
        } catch (IOException e) {
            getLogger().severe("Error saving playerdata.yml when deactivating");
            e.printStackTrace();
        }
        getLogger().info("Plugin disabled");
    }

    private void guardarArchivosIdioma() {
        String[] idiomas = {"en", "es", "pt", "fr", "de", "it"};
        for (String idioma : idiomas) {
            String ruta = "language/" + idioma + ".yml";
            File archivo = new File(getDataFolder(), ruta);
            if (!archivo.exists()) {
                saveResource(ruta, false);
                getLogger().info("Created language: " + idioma);
            }
        }
    }

    // Devuelve el FileConfiguration del idioma del jugador, o idioma por defecto
    private FileConfiguration getLangConfigJugador(Player jugador) {
        String uuid = jugador.getUniqueId().toString();
        String idioma = playerDataConfig.getString(uuid, getConfig().getString("language", "en"));

        File langFile = new File(getDataFolder(), "language/" + idioma + ".yml");
        if (!langFile.exists()) {
            langFile = new File(getDataFolder(), "language/en.yml");
        }
        return YamlConfiguration.loadConfiguration(langFile);
    }

    // Reemplaza %server_name% en mensajes
    private String aplicarNombreServidor(String mensaje) {
        if (mensaje == null) return "";
        String nombreServidor = getConfig().getString("server_name", "Servidor");
        return mensaje.replace("%server_name%", nombreServidor);
    }

    // Aplica PlaceholderAPI si está disponible
    private String aplicarPlaceholders(Player jugador, String mensaje) {
        if (mensaje == null) return "";
        if (jugador != null && getServer().getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            return PlaceholderAPI.setPlaceholders(jugador, mensaje);
        }
        return mensaje;
    }

    // Obtener mensaje desde la configuración del idioma
    private String getMsg(FileConfiguration langConfig, String path) {
        if (langConfig.contains(path)) {
            return langConfig.getString(path);
        }
        return ChatColor.RED + "Mensaje no encontrado: " + path;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!command.getName().equalsIgnoreCase("greeting")) return false;

        if (args.length == 0) {
            sender.sendMessage(ChatColor.RED + "Use /greeting <help|reload|version|messagesoutput|setlang>");
            return true;
        }

        String sub = args[0].toLowerCase();

        // Para comandos que requieren idioma personalizado:
        FileConfiguration langConfig = null;
        Player jugador = null;
        if (sender instanceof Player) {
            jugador = (Player) sender;
            langConfig = getLangConfigJugador(jugador);
        } else {
            // Si consola, idioma por defecto:
            langConfig = YamlConfiguration.loadConfiguration(new File(getDataFolder(), "language/" + getConfig().getString("language", "en") + ".yml"));
        }

        switch (sub) {
            case "help":
                if (!sender.hasPermission("greeting.help")) {
                    sender.sendMessage(ChatColor.RED + getMsg(langConfig, "messages.no_permission"));
                    return true;
                }
                sender.sendMessage(ChatColor.GOLD + getMsg(langConfig, "messages.help_header"));
                sender.sendMessage(ChatColor.YELLOW + getMsg(langConfig, "messages.help_version"));
                sender.sendMessage(ChatColor.YELLOW + getMsg(langConfig, "messages.help_reload"));
                sender.sendMessage(ChatColor.YELLOW + getMsg(langConfig, "messages.help_messagesoutput"));
                sender.sendMessage(ChatColor.YELLOW + getMsg(langConfig, "messages.help_help"));
                sender.sendMessage(ChatColor.YELLOW + "/greeting setlang <codigo_idioma> - Cambiar idioma");
                break;

            case "version":
                if (!sender.hasPermission("greeting.version")) {
                    sender.sendMessage(ChatColor.RED + getMsg(langConfig, "messages.no_permission"));
                    return true;
                }
                sender.sendMessage(ChatColor.GREEN + getMsg(langConfig, "messages.version_message") + ChatColor.YELLOW + getDescription().getVersion());
                break;

            case "reload":
                if (!sender.hasPermission("greeting.reload")) {
                    sender.sendMessage(ChatColor.RED + getMsg(langConfig, "messages.no_permission"));
                    return true;
                }
                reloadConfig();
                guardarArchivosIdioma();
                playerDataConfig = YamlConfiguration.loadConfiguration(playerDataFile);
                sender.sendMessage(ChatColor.GREEN + getMsg(langConfig, "messages.config_reloaded"));
                break;

            case "messagesoutput":
                if (!sender.hasPermission("greeting.messagesoutput")) {
                    sender.sendMessage(ChatColor.RED + getMsg(langConfig, "messages.no_permission"));
                    return true;
                }
                if (!(sender instanceof Player)) {
                    sender.sendMessage(ChatColor.RED + getMsg(langConfig, "messages.only_in_game"));
                    return true;
                }
                // Mostrar el mensaje de salida al jugador
                String msgSalida = getMsg(langConfig, "goodbye");
                msgSalida = aplicarNombreServidor(msgSalida);
                msgSalida = aplicarPlaceholders(jugador, msgSalida);
                msgSalida = ChatColor.translateAlternateColorCodes('&', msgSalida);
                jugador.sendMessage(ChatColor.GREEN + getMsg(langConfig, "messages.exit_message_show") + ChatColor.RESET + msgSalida);
                break;

            case "setlang":
                if (!(sender instanceof Player)) {
                    sender.sendMessage(ChatColor.RED + getMsg(langConfig, "messages.only_in_game"));
                    return true;
                }
                if (!sender.hasPermission("greeting.setlang")) {
                    sender.sendMessage(ChatColor.RED + getMsg(langConfig, "messages.no_permission"));
                    return true;
                }
                if (args.length < 2) {
                    sender.sendMessage(ChatColor.RED + "Use: /greeting setlang <codigo_idioma>");
                    return true;
                }
                String nuevoIdioma = args[1].toLowerCase();
                File langFile = new File(getDataFolder(), "language/" + nuevoIdioma + ".yml");
                if (!langFile.exists()) {
                    sender.sendMessage(ChatColor.RED + "The " + nuevoIdioma + " language does not exist.");
                    return true;
                }
                // Guardar idioma en playerdata.yml
                playerDataConfig.set(jugador.getUniqueId().toString(), nuevoIdioma);
                try {
                    playerDataConfig.save(playerDataFile);
                } catch (IOException e) {
                    sender.sendMessage(ChatColor.RED + "Error saving language.");
                    e.printStackTrace();
                    return true;
                }
                sender.sendMessage(ChatColor.GREEN + "Language changed to  " + nuevoIdioma);
                break;

            default:
                sender.sendMessage(ChatColor.RED + getMsg(langConfig, "messages.unknown_subcommand"));
                break;
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!command.getName().equalsIgnoreCase("greeting")) return null;

        List<String> opciones = new ArrayList<>();
        opciones.add("help");
        opciones.add("reload");
        opciones.add("version");
        opciones.add("messagesoutput");
        opciones.add("setlang");

        if (args.length == 1) {
            String parcial = args[0].toLowerCase();
            List<String> resultado = new ArrayList<>();
            for (String s : opciones) {
                if (s.startsWith(parcial)) resultado.add(s);
            }
            return resultado;
        }
        return null;
    }

    @EventHandler
    public void alEntrar(PlayerJoinEvent e) {
        Player jugador = e.getPlayer();
        FileConfiguration lang = getLangConfigJugador(jugador);

        String msgUsuario = aplicarNombreServidor(lang.getString("welcome_user", ""));
        msgUsuario = aplicarPlaceholders(jugador, msgUsuario);
        msgUsuario = ChatColor.translateAlternateColorCodes('&', msgUsuario);
        jugador.sendMessage(msgUsuario);

        String msgTodos = aplicarNombreServidor(lang.getString("welcome", ""));
        msgTodos = aplicarPlaceholders(jugador, msgTodos);
        msgTodos = ChatColor.translateAlternateColorCodes('&', msgTodos);

        for (Player online : getServer().getOnlinePlayers()) {
            online.sendMessage(msgTodos);
        }

        File eventsFolder = new File(getDataFolder(), "events");
        if (eventsFolder.exists() && eventsFolder.isDirectory()) {
            for (File file : eventsFolder.listFiles()) {
                if (file.getName().endsWith(".yml")) {
                    FileConfiguration cfg = YamlConfiguration.loadConfiguration(file);

                    List<Map<?, ?>> eventos = (List<Map<?, ?>>) cfg.getList("events");
                    if (eventos == null) continue;

                    for (Map<?, ?> eventSection : eventos) {
                        // Obtener datos del evento
                        String type = (String) eventSection.get("type");
                        if (type == null) continue;

                        boolean once = false;
                        if (eventSection.containsKey("once")) {
                            once = (boolean) eventSection.get("once");
                        }

                        int delayTicks = 0;
                        if (eventSection.containsKey("delay")) {
                            Object delayObj = eventSection.get("delay");
                            if (delayObj instanceof Integer) {
                                delayTicks = (Integer) delayObj;
                            } else if (delayObj instanceof String) {
                                try {
                                    delayTicks = Integer.parseInt((String) delayObj);
                                } catch (NumberFormatException ex) {
                                    delayTicks = 0;
                                }
                            }
                        }

                        if (once && jugador.hasPlayedBefore()) {
                            // Este evento se ejecuta solo una vez, y el jugador ya jugó antes: saltar
                            continue;
                        }

                        final String tipoEvento = type.toLowerCase();
                        final int delay = delayTicks;

                        new BukkitRunnable() {
                            @Override
                            public void run() {
                                if (tipoEvento.equals("message")) {
                                    String msgjugador = (String) eventSection.get("message");
                                    if (msgjugador != null) {
                                        msgjugador = aplicarNombreServidor(msgjugador);
                                        msgjugador = aplicarPlaceholders(jugador, msgjugador);
                                        msgjugador = ChatColor.translateAlternateColorCodes('&', msgjugador);
                                        jugador.sendMessage(msgjugador);
                                    }
                                } else if (tipoEvento.equals("command")) {
                                    String comando = (String) eventSection.get("command");
                                    if (comando != null) {
                                        comando = comando.replace("%player%", jugador.getName());
                                        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), comando);
                                    }
                                } else if (tipoEvento.equals("give")) {
                                    String itemStr = (String) eventSection.get("item");
                                    int amount = 1;
                                    if (eventSection.get("amount") != null) {
                                        amount = (int) eventSection.get("amount");
                                    }
                                    try {
                                        Material material = Material.valueOf(itemStr.toUpperCase());
                                        ItemStack item = new ItemStack(material, amount);
                                        jugador.getInventory().addItem(item);
                                    } catch (IllegalArgumentException ex) {
                                        Bukkit.getLogger().warning("Ítem inválido en archivo de evento: " + itemStr);
                                    }
                                }
                            }
                        }.runTaskLater(this, delay);
                    }
                }
            }
        }
    }



    @EventHandler
    public void alSalir(PlayerQuitEvent e) {
        Player jugador = e.getPlayer();
        FileConfiguration lang = getLangConfigJugador(jugador);

        String msg = aplicarNombreServidor(lang.getString("goodbye", ""));
        msg = aplicarPlaceholders(jugador, msg);
        msg = ChatColor.translateAlternateColorCodes('&', msg);
        e.setQuitMessage(msg);
    }
}
