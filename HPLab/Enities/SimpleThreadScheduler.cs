using System;
using System.Collections.Generic;
using System.Linq;
using System.Threading;

namespace HPLab.Enities
{
    public class SimpleThreadScheduler : IDisposable
    {
        private const int MAX_TASKS_PER_THREAD = 10;

        private bool disposed;
        private volatile int TasksInProgress;
        private volatile int TotalTasks;

        private List<Thread> Threads;
        private readonly Thread mainThread;
        public int TotalThreads { get; private set; }

        private ManualResetEventSlim newTaskAvailable;
        private int[] TaskQueue;
        private Dictionary<int, Future<object>> Tasks;
        private Dictionary<int, object> TaskResults;
        private Dictionary<int, CancellationTokenSource> Tokens;

        public SimpleThreadScheduler()
        {
            disposed = false;
            newTaskAvailable = new ManualResetEventSlim();
            Tokens = new Dictionary<int, CancellationTokenSource>();
            TaskResults = new Dictionary<int, object>();
            mainThread = new Thread(MainThreadStart);
        }

        static SimpleThreadScheduler()
        {
            Default = new SimpleThreadScheduler();
        }

        public static SimpleThreadScheduler Default;

        public void SetInitialThreadCount(int totalThreads)
        {
            if (Threads != null)
            {
                throw new InvalidOperationException("Scheduler is already initialized.");
            }

            TotalThreads = totalThreads;
            TaskQueue = new int[TotalThreads * MAX_TASKS_PER_THREAD + 1];
            Tasks = new Dictionary<int, Future<object>>(TaskQueue.Length);
            Threads = new List<Thread>(TotalThreads);
            for (var i = 0; i < TotalThreads; ++i)
            {
                Threads[i] = new Thread(ThreadStartPolling);
                Threads[i].Start();
            }
        }

        private void MainThreadStart()
        {
            while (!disposed)
            {
                var totalTasks = Volatile.Read(ref TotalTasks);
                var tasksInProgress = Volatile.Read(ref TasksInProgress);
                
                if (tasksInProgress < totalTasks)
                {
                    totalTasks = Volatile.Read(ref TotalTasks);
                    tasksInProgress = Volatile.Read(ref TasksInProgress);
         
                    while (tasksInProgress < totalTasks)
                    {
                        newTaskAvailable.Set();
                        ++tasksInProgress;
                    }
                }
            }
        }

        private void ThreadStartPolling()
        {
            while (!disposed)
            {
                int taskIdToRun = -1;
                do
                {
                    newTaskAvailable.Wait();;
                } while ((taskIdToRun = Interlocked.Exchange(ref TasksInProgress, TasksInProgress + 1)) != TasksInProgress);
                //run logic here
            }
        }

        internal object GetResult(int id)
        {
            if (id < TotalTasks || !Tasks.ContainsKey(id))
            {
                //throw ?
                return null;
            }

            var task = Tasks[id];
            while (task.State <= FutureState.InProgress)
            {
                Thread.Yield();
            }

            switch (task.State)
            {
                case FutureState.Success:
                    return TaskResults[id];

                case FutureState.Faulted:
                    throw GetAggregateException(task);

                case FutureState.Canceled:
                default:
                    return null;
            }
        }

        internal bool Complete(int id, object result)
        {
            if (id > TotalTasks || !Tasks.ContainsKey(id))
            {
                //throw ?
                return false;
            }
            var task = Tasks[id];

            if (task.State <= FutureState.InProgress)
            {
                lock (task)
                {
                    if (task.State <= FutureState.InProgress)
                    {
                        TaskResults[id] = result;
                        task.State = FutureState.Success;
                        return true;
                    }
                }
            }

            return task.State == FutureState.Success;
        }

        private static AggregateException GetAggregateException(Future<object> task)
        {
            var faults = task.ChildTasks == null
                ? new List<ApplicationException>()
                : task.ChildTasks.Where(t => t.State == FutureState.Faulted)
                    .Select(t => t.ExecutionException).ToList();
            if (task.ExecutionException != null)
            {
                faults.Add(task.ExecutionException);
            }

            if (faults.Count > 0)
            {
                return new AggregateException(faults);
            }

            return new AggregateException("Something bad happened during task execution.");
        }

        internal bool Cancel(int id)
        {
            if (id > TotalTasks || !Tasks.ContainsKey(id))
            {
                //throw ?
                return false;
            }
            var task = Tasks[id];

            if (task.State <= FutureState.InProgress)
            {
                lock (task)
                {
                    if (task.State <= FutureState.InProgress)
                    {
                        Tokens[id].Cancel();
                        task.State = FutureState.Canceled;
                        return true;
                    }
                }
            }

            return task.State == FutureState.Canceled;
        }

        internal bool Fail(int id, ApplicationException ex)
        {
            //check
            if (id > TotalTasks || !Tasks.ContainsKey(id))
            {
                //throw ?
                return false;
            }
            var task = Tasks[id];

            if (task.State <= FutureState.InProgress)
            {
                lock (task)
                {
                    if (task.State <= FutureState.InProgress)
                    {
                        task.ExecutionException = ex;
                        task.State = FutureState.Faulted;
                        return true;
                    }
                }
            }

            return task.State == FutureState.Faulted;
        }

        ~SimpleThreadScheduler()
        {
            Dispose(false);
        }

        public void Dispose()
        {
            Dispose(true);
            GC.SuppressFinalize(this);
        }

        private void Dispose(bool isDisposing)
        {
            if (disposed)
            {
                return;
            }
            if (isDisposing)
            {
                if (newTaskAvailable != null)
                {
                    newTaskAvailable.Dispose();
                }
                foreach (var thread in Threads)
                {
                    if (thread != null)
                    {
                        thread.Abort();
                    }
                }
                if (mainThread != null)
                {
                    mainThread.Abort();
                }
            }
            disposed = true;
        }
    }
}