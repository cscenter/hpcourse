import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Callable;

/**
 * Created by olgaoskina
 * 02 May 2015
 */
public class CommandFactory {

    public enum CommandType {
        EXIT("exit"),
        ADD("add"),
        STATUS("status"),
        INTERRUPT("interrupt");

        private String value;

        CommandType(String value) {
            this.value = value;
        }

        public static CommandType resolveCommand(String value) {
            for (CommandType command : CommandType.values()) {
                if (command.value.equals(value)) {
                    return command;
                }
            }
            return null;
        }
    }

    public interface Command {
        CommandType getType();
    }

    public static class AddCommand implements Command {
//        private final Runnable runnable;
        private final Callable<Optional> callable;

        public AddCommand(long duration) {
//            runnable = () -> {
//                try {
//                    Thread.sleep(duration);
//                } catch (InterruptedException e) {
////                    LogWrapper.i("");
//                }
//            };
//
            callable = () -> {
                Thread.sleep(duration);
                return Optional.empty();
            };
        }

        public Callable<Optional> getCallable() {
            return callable;
        }

        //        public Runnable getRunnable() {
//            return runnable;
//        }

        @Override
        public CommandType getType() {
            return CommandType.ADD;
        }
    }

    public static class StatusCommand implements Command {
        private final UUID id;

        public StatusCommand(UUID id) {
            this.id = id;
        }

        public UUID getId() {
            return id;
        }

        @Override
        public CommandType getType() {
            return CommandType.STATUS;
        }
    }

    public static class InterruptCommand implements Command {
        private final UUID id;

        public InterruptCommand(UUID id) {
            this.id = id;
        }

        public UUID getId() {
            return id;
        }

        @Override
        public CommandType getType() {
            return CommandType.INTERRUPT;
        }
    }

    public static class ExitCommand implements Command {
        @Override
        public CommandType getType() {
            return CommandType.EXIT;
        }
    }

    public static Command parseCommand(String input) {
        String[] args = input.split(" ");
        CommandType type = CommandType.resolveCommand(args[0]);
        switch (type) {
            case ADD:
                return new AddCommand(Long.parseLong(args[1]));
            case EXIT:
                return new ExitCommand();
            case STATUS:
                return new StatusCommand(UUID.fromString(args[1]));
            case INTERRUPT:
                return new InterruptCommand(UUID.fromString(args[1]));
            default: {
                return null;
                //                FIXME: replace null with Optional
            }
        }
    }
}
