import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import org.apache.log4j.PropertyConfigurator;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.Optional;

/**
 * Created by olgaoskina
 * 02 May 2015
 */
public class RunScheduler {
    @Parameter(
            names = {"-c"},
            description = "Count of threads",
            required = false
    )
    private int threadCount = 4;

    @Parameter(
            names = {"-h", "-help"},
            help = true
    )
    private boolean help = false;


    public static void main(String[] args) {
        PropertyConfigurator.configure("log4j.properties");
        RunScheduler scheduler = new RunScheduler();
        JCommander jCommander = new JCommander(scheduler, args);
        if (scheduler.help) {
            jCommander.usage();
        } else {
            scheduler.work();
        }
    }

    public void work() {
        Scheduler scheduler = new Scheduler(threadCount);
        BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
        boolean exit = false;

        while (!exit) {
            try {
                CommandFactory.Command command = CommandFactory.parseCommand(reader.readLine());
                if (command == null) {
                    LogWrapper.w("There is no such command");
                } else {
                    switch (command.getType()) {
                        case ADD: {
                            CommandFactory.AddCommand addCommand = (CommandFactory.AddCommand) command;
                            TaskFuture future = scheduler.submit(addCommand.getCallable());
                            LogWrapper.i("ADDED TASK: " + future.getId());
                            break;
                        }
                        case ADD_SUBTASK: {
                            CommandFactory.AddSubtaskCommand addSubtaskCommand = (CommandFactory.AddSubtaskCommand) command;
                            Optional<TaskFuture> future = scheduler.submitSubtask(addSubtaskCommand.getId(), addSubtaskCommand.getCallable());
                            if (future.isPresent()) {
                                LogWrapper.i("ADDED TASK: " + future.get().getId());
                            } else {
                                LogWrapper.w("There is no task with id: " + addSubtaskCommand.getId());
                                LogWrapper.w("Or task with id: " + addSubtaskCommand.getId() + " in status INTERRUPT or COMPLETED");
                            }
                            break;
                        }
                        case STATUS: {
                            CommandFactory.StatusCommand statusCommand = (CommandFactory.StatusCommand) command;
                            Optional<TaskFuture.Status> status = scheduler.getStatus(statusCommand.getId());
                            if (status.isPresent()) {
                                LogWrapper.i("TASK: " + statusCommand.getId() + " STATUS: " + status.get());
                            } else {
                                LogWrapper.w("There is no task with id: " + statusCommand.getId());
                            }
                            break;
                        }
                        case INTERRUPT: {
                            CommandFactory.InterruptCommand interruptCommand = (CommandFactory.InterruptCommand) command;
                            if (scheduler.interrupt(interruptCommand.getId())) {
                                LogWrapper.i("TASK: " + interruptCommand.getId() + " INTERRUPTED");
                            } else {
                                LogWrapper.w("There is no task with id: " + interruptCommand.getId());
                                LogWrapper.w("Or task with id: " + interruptCommand.getId() + " is not int RUNNING status");
                            }
                            break;
                        }
                        case RESULT: {
                            CommandFactory.ResultCommand resultCommand = (CommandFactory.ResultCommand) command;
                            Optional<Long> mayBeResult = scheduler.getResult(resultCommand.getId());
                            if (mayBeResult.isPresent()) {
                                LogWrapper.i("RESULT OF TASK: " + resultCommand.getId() + " IS " + mayBeResult.get());
                            } else {
                                LogWrapper.w("There is no task with id: " + resultCommand.getId());
                            }
                            break;
                        }
                        case RUN_SUBTASK: {
                            CommandFactory.RunSubtaskCommand runSubtaskCommand = (CommandFactory.RunSubtaskCommand) command;
                            boolean result = scheduler.runSubtask(runSubtaskCommand.getParentId(), runSubtaskCommand.getSubtaskId());
                            if (result) {
                                LogWrapper.i("SUBTASK WITH ID: " + runSubtaskCommand.getSubtaskId() + " WILL BE RUN");
                            } else {
                                LogWrapper.w("There is no subtask with id: " + runSubtaskCommand.getSubtaskId() + " in task with id: " + runSubtaskCommand.getParentId());
                            }
                            break;
                        }
                        case EXCEPTION: {
                            CommandFactory.ExceptionCommand exceptionCommand = (CommandFactory.ExceptionCommand) command;
                            Optional<Exception> mayBeResult = scheduler.getInternalException(exceptionCommand.getId());
                            if (mayBeResult.isPresent()) {
                                LogWrapper.i("EXCEPTION OF TASK: " + exceptionCommand.getId() + " IS " + mayBeResult.get().toString());
                            } else {
                                LogWrapper.w("There is no task with id: " + exceptionCommand.getId());
                            }
                            break;
                        }
                        case EXIT: {
                            scheduler.exit();
                            exit = true;
                            LogWrapper.i("Goodbye :)");
                            break;
                        }
                    }
                }
            } catch (Exception e) {
                LogWrapper.e("", e);
            }
        }
    }
}
