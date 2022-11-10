package at.hugo.bukkit.plugin.tresuryprovider;

import cloud.commandframework.Command;
import cloud.commandframework.CommandTree;
import cloud.commandframework.execution.AsynchronousCommandExecutionCoordinator;
import cloud.commandframework.execution.CommandExecutionCoordinator;
import cloud.commandframework.extra.confirmation.CommandConfirmationManager;
import cloud.commandframework.minecraft.extras.MinecraftExceptionHandler;
import cloud.commandframework.paper.PaperCommandManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import static net.kyori.adventure.text.Component.text;

/**
 * This Class Manages Command creations for cloud commands
 */
public class CommandManager {

    private final PaperCommandManager<CommandSender> commandManager;
    private final CommandConfirmationManager<CommandSender> confirmationManager;

    /**
     * Creates a CommandManager instance
     *
     * @param plugin      The plugin that creates the instance
     * @param errorPrefix the prefix that error messages should have
     * @throws InstantiationException When cloud's CommandManager can't be created
     */
    public CommandManager(final JavaPlugin plugin, Component errorPrefix) throws InstantiationException {

        final Function<CommandTree<CommandSender>, CommandExecutionCoordinator<CommandSender>> executionCommandCoordinator
                = AsynchronousCommandExecutionCoordinator.<CommandSender>newBuilder().withAsynchronousParsing().build();

        try {
            commandManager = new PaperCommandManager<>(plugin,
                    executionCommandCoordinator,
                    Function.identity(),
                    Function.identity()
            );
        } catch (Exception e) {
            throw new InstantiationException("Could not create the Command Manager: " + e.getMessage());
        }
        commandManager.registerAsynchronousCompletions();
        commandManager.registerBrigadier();

        confirmationManager = new CommandConfirmationManager<>(
                30L,
                TimeUnit.SECONDS,
                context -> context.getCommandContext().getSender().sendMessage(
                        text("Confirmation required. Confirm using /example confirm.").color(NamedTextColor.RED)),
                sender -> sender.sendMessage(
                        text("You don't have any pending commands.").color(NamedTextColor.RED))
        );
        confirmationManager.registerConfirmationProcessor(commandManager);

        new MinecraftExceptionHandler<CommandSender>()
                .withInvalidSyntaxHandler()
                .withInvalidSenderHandler()
                .withNoPermissionHandler()
                .withArgumentParsingHandler()
                .withCommandExecutionHandler()
                .withDefaultHandlers()
                .withCommandExecutionHandler()
                .withDecorator(
                        errorPrefix::append
                ).apply(this.commandManager, sender -> sender);

    }

    /**
     * Registers a Command Builder as a new command
     *
     * @param builder the builder to register
     */
    public void command(Command.Builder<CommandSender> builder) {
        this.commandManager.command(builder);
    }

    /**
     * gets cloud's CommandManager in case this is needed
     *
     * @return cloud's CommandManager that this manager manages
     */
    public PaperCommandManager<CommandSender> manager() {
        return this.commandManager;
    }

}
