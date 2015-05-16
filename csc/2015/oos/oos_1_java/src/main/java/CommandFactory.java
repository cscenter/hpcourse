import java.util.Optional;
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
        INTERRUPT("interrupt"),
        RESULT("result"),
        EXCEPTION("exception"),
        ADD_SUBTASK("add_subtask"),
        RUN_SUBTASK("run_subtask");

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
        private final Callable<Optional> callable;

        public AddCommand(long duration) {
            callable = () -> {
                Thread.sleep(duration);
                return Optional.empty();
            };
        }

        public Callable<Optional> getCallable() {
            return callable;
        }

        @Override
        public CommandType getType() {
            return CommandType.ADD;
        }
    }

    public static class AddSubtaskCommand implements Command {
        private final long id;
        private final Callable<Optional> callable;

        public AddSubtaskCommand(long id, long duration) {
            this.id = id;
            callable = () -> {
                Thread.sleep(duration);
                return Optional.empty();
            };
        }

        public long getId() {
            return id;
        }

        public Callable<Optional> getCallable() {
            return callable;
        }

        @Override
        public CommandType getType() {
            return CommandType.ADD_SUBTASK;
        }
    }

    public static class StatusCommand implements Command {
        private final long id;

        public StatusCommand(long id) {
            this.id = id;
        }

        public long getId() {
            return id;
        }

        @Override
        public CommandType getType() {
            return CommandType.STATUS;
        }
    }

    public static class InterruptCommand implements Command {
        private final long id;

        public InterruptCommand(long id) {
            this.id = id;
        }

        public long getId() {
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

    public static class ResultCommand implements Command {
        private final long id;

        public ResultCommand(long id) {
            this.id = id;
        }

        public long getId() {
            return id;
        }

        @Override
        public CommandType getType() {
            return CommandType.RESULT;
        }
    }

    public static class RunSubtaskCommand implements Command {
        private final long parentId;
        private final long subtaskId;

        public RunSubtaskCommand(long parentId, long subtaskId) {
            this.parentId = parentId;
            this.subtaskId = subtaskId;
        }

        public long getParentId() {
            return parentId;
        }

        public long getSubtaskId() {
            return subtaskId;
        }

        @Override
        public CommandType getType() {
            return CommandType.RUN_SUBTASK;
        }
    }

    public static class ExceptionCommand implements Command {
        private final long id;

        public ExceptionCommand(long id) {
            this.id = id;
        }

        public long getId() {
            return id;
        }

        @Override
        public CommandType getType() {
            return CommandType.EXCEPTION;
        }
    }

    public static Command parseCommand(String input) {
        String[] args = input.split(" ");
        CommandType type = CommandType.resolveCommand(args[0]);
        if (type != null) {
            switch (type) {
                case ADD:
                    return new AddCommand(Long.parseLong(args[1]));
                case EXIT:
                    return new ExitCommand();
                case STATUS:
                    return new StatusCommand(Long.parseLong(args[1]));
                case INTERRUPT:
                    return new InterruptCommand(Long.parseLong(args[1]));
                case RESULT:
                    return new ResultCommand(Long.parseLong(args[1]));
                case EXCEPTION:
                    return new ExceptionCommand(Long.parseLong(args[1]));
                case ADD_SUBTASK:
                    return new AddSubtaskCommand(Long.parseLong(args[1]), Long.parseLong(args[2]));
                case RUN_SUBTASK:
                    return new RunSubtaskCommand(Long.parseLong(args[1]), Long.parseLong(args[2]));
            }
        }
        return null;
    }
}
