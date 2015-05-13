using HPLab.Scheduler;
using System;
using System.Collections.Generic;
using System.Linq;
using System.Text;
using System.Threading;

namespace HPLab
{
    class Program
    {
        static void Main(string[] args)
        {
            var threadsTotal = 0;
            
            if (args.Length == 0 || !int.TryParse(args[0], out threadsTotal))
            {
                do
                {
                    Console.WriteLine("Please, provide initial threads count.");
                } while (!int.TryParse(Console.ReadLine(), out threadsTotal));
            }

            SimpleThreadScheduler.Default.SetInitialThreadCount(threadsTotal);

            SimpleTaskTest();
            //ConsecutiveTaskTest();
            //ConsecutiveTaskWithChildrenTest();
            Console.ReadKey();
        }

        private static void SimpleTaskTest()
        {
            var parent = SimpleThreadScheduler.Default.SubmitNewTask(20);
        }

        private static void ConsecutiveTaskTest()
        {
            Future[] tasks = new Future[1000];
            foreach (var taskDuration in Enumerable.Range(0, 1000))
            {
                tasks[taskDuration] = SimpleThreadScheduler.Default.SubmitNewTask(taskDuration);
            }
        }

        private static void ConsecutiveTaskWithChildrenTest()
        {
            Future[] tasks = new Future[2000];
            foreach (var taskDuration in Enumerable.Range(0, 1000))
            {
                tasks[taskDuration] = SimpleThreadScheduler.Default.SubmitNewTask(taskDuration);
                Thread.Sleep(7000);
                tasks[taskDuration + 1] = SimpleThreadScheduler.Default.SubmitChildTask(taskDuration * 2, tasks[taskDuration].Id);
            }
        }
    }
}