using HPLab.Scheduler;
using System;
using System.Linq;
using System.Threading;

namespace HPLab
{
    class Program
    {
        static void Main(string[] args)
        {
            int threadsTotal;
            
            if (args.Length == 0 || !int.TryParse(args[0], out threadsTotal))
            {
                do
                {
                    Console.WriteLine("Please, provide initial threads count.");
                } while (!int.TryParse(Console.ReadLine(), out threadsTotal));
            }

            SimpleThreadScheduler.Default.SetInitialThreadCount(threadsTotal);

            Console.WriteLine("Hit `Enter` to start next test.");

            Console.ReadLine();
            Console.Write("SimpleTaskTest");
            SimpleTaskTest();
            Console.WriteLine(" - Success");

            Console.ReadLine();
            Console.Write("ConsecutiveTaskSmallTest");
            /ConsecutiveTaskSmallTest();
            Console.WriteLine(" - Success");

            Console.ReadLine();
            Console.Write("ConsecutiveTaskTest");
            //ConsecutiveTaskTest();
            Console.WriteLine(" - Success");

            Console.ReadLine();
            Console.Write("ConsecutiveTaskWithChildrenTest");
            ConsecutiveTaskWithChildrenTest();
            Console.WriteLine(" - Success");

            Console.WriteLine("Press any key to continue...");
            Console.ReadLine();
            SimpleThreadScheduler.Default.Dispose();
        }

        private static void SimpleTaskTest()
        {
            var parent = SimpleThreadScheduler.Default.SubmitNewTask(10);
            if ((int)parent.Result != parent.TotalTime)
            {
                throw new ApplicationException("No luck here");
            }
        }

        private static void ConsecutiveTaskSmallTest()
        {
            var tasks = new Future[SimpleThreadScheduler.Default.TotalThreads * 2];
            foreach (var taskDuration in Enumerable.Range(1, tasks.Length))
            {
                tasks[taskDuration - 1] = SimpleThreadScheduler.Default.SubmitNewTask(taskDuration);
            }

            Thread.Sleep(TimeSpan.FromMinutes(1));
            foreach (var task in tasks)
            {
                if ((int)task.Result != task.TotalTime)
                {
                    throw new ApplicationException("No luck here");
                }
            }
        }

        private static void ConsecutiveTaskTest()
        {
            var tasks = new Future[1000];
            foreach (var taskDuration in Enumerable.Range(1, tasks.Length))
            {
                tasks[taskDuration - 1] = SimpleThreadScheduler.Default.SubmitNewTask(taskDuration);
            }

            Thread.Sleep(TimeSpan.FromMinutes(20));
            foreach (var task in tasks)
            {
                if ((int)task.Result != task.TotalTime)
                {
                    throw new ApplicationException("No luck here");
                }
            }
        }

        private static void ConsecutiveTaskWithChildrenTest()
        {
            var tasks = new Future[10];
            foreach (var taskDuration in Enumerable.Range(1, tasks.Length / 2))
            {
                tasks[2 * taskDuration - 1] = SimpleThreadScheduler.Default.SubmitNewTask(taskDuration);
                Thread.Sleep(TimeSpan.FromSeconds(7));
                tasks[2 * taskDuration] = SimpleThreadScheduler.Default.SubmitChildTask(taskDuration * 2, tasks[taskDuration].Id);
            }
        }
    }
}