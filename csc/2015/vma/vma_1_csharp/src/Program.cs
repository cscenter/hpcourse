using HPLab.Scheduler;
using System;
using System.Collections.Generic;
using System.Linq;
using System.Text;

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
        }
    }
}
