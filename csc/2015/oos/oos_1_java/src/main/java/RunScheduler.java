import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import org.apache.log4j.PropertyConfigurator;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Optional;
import java.util.UUID;

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


        // FIXME: replace true
        while (true) {
            try {
                CommandFactory.Command command = CommandFactory.parseCommand(reader.readLine());
                switch (command.getType()) {
                    case ADD: {
                        CommandFactory.AddCommand addCommand = (CommandFactory.AddCommand) command;
                        TaskFuture<UUID> future = scheduler.submit(addCommand.getRunnable());
                        LogWrapper.i("ADDED TASK: " + future.getId());
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
                        }
                        break;
                    }
                    case EXIT: {
                        //                        scheduler.interruptAll();
                        break;
                    }
                }
            } catch (IOException e) {
                LogWrapper.e("", e);
            }
        }
    }
}
