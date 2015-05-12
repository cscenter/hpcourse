using System;
using System.Collections.Generic;
using System.Linq;
using System.Threading;

namespace HPLab.Scheduler
{
    public class SimpleThreadScheduler : IDisposable
    {
        private const int INITIAL_TASKS_PER_THREAD = 10;

        private bool disposed;
        private volatile int CurrentStartedTaskId;
        private volatile int TotalTasks;

        private List<Thread> Threads;
        private readonly Thread mainThread;
        public int TotalThreads { get; private set; }

        private ManualResetEventSlim newTaskAvailable;
        private int[] TaskQueue;
        private Dictionary<int, Future<object>> Tasks;
        private Dictionary<int, object> TaskResults;
        private Dictionary<int, CancellationTokenSource> Tokens;
        private Dictionary<int, int> TasksToThreadQueue;

        public SimpleThreadScheduler()
        {
            disposed = false;
            newTaskAvailable = new ManualResetEventSlim();
            Tokens = new Dictionary<int, CancellationTokenSource>();
            TaskResults = new Dictionary<int, object>();
            mainThread = new Thread(MainThreadStart);
            mainThread.Start();
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
            TaskQueue = new int[TotalThreads * INITIAL_TASKS_PER_THREAD + 1];
            Tasks = new Dictionary<int, Future<object>>(TaskQueue.Length);
            TasksToThreadQueue = new Dictionary<int, int>();
            Threads = new List<Thread>(TotalThreads);
            for (var i = 0; i < TotalThreads; ++i)
            {
                Threads[i] = new Thread(ThreadStartPolling);
                TasksToThreadQueue[Threads[i].ManagedThreadId] = 0;
                Threads[i].Start();
            }
        }

        private void MainThreadStart()
        {
            while (!disposed)
            {
                var totalTasks = Volatile.Read(ref TotalTasks);
                var tasksInProgress = Volatile.Read(ref CurrentStartedTaskId);
                
                if (tasksInProgress < totalTasks)
                {
                    totalTasks = Volatile.Read(ref TotalTasks);
                    tasksInProgress = Volatile.Read(ref CurrentStartedTaskId);
         
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
                newTaskAvailable.Wait();
                if (TasksToThreadQueue[Thread.CurrentThread.ManagedThreadId] >= TotalTasks)
                {
                    continue;
                }

                if (!StartTask(TaskQueue[TasksToThreadQueue[Thread.CurrentThread.ManagedThreadId]]))
                {
                    ++TasksToThreadQueue[Thread.CurrentThread.ManagedThreadId];
                    continue;
                }
                newTaskAvailable.Reset();
                var taskIdToRun = TasksToThreadQueue[Thread.CurrentThread.ManagedThreadId];
                Interlocked.Increment(ref CurrentStartedTaskId);
                RunTask(taskIdToRun);
            }
        }

        private void RunTask(int taskIdToRun)
        {
            var task = Tasks[taskIdToRun];
            var result = 0;

            while (task.CurrentTime < task.TotalTime)
            {
                if (Tokens[taskIdToRun].IsCancellationRequested)
                {
                    CancelTask(taskIdToRun);
                    if (task.State == FutureState.InProgress)
                    {
                        Tokens[taskIdToRun].Token.ThrowIfCancellationRequested();
                    }
                    break;
                }
                for (var i = 1; i <= 1000000; ++i)
                {
                    result += i;
                    result /= i;
                }
                ++task.CurrentTime;
            }
         
            CompleteTask(taskIdToRun, task.TotalTime + result - 1);
        }

        private bool StartTask(int taskId)
        {
            if (taskId > TotalTasks || !Tasks.ContainsKey(taskId))
            {
                //throw ?
                return false;
            }
            var task = Tasks[taskId];

            if (task.State == FutureState.Created)
            {
                lock (task)
                {
                    if (task.State == FutureState.Created)
                    {
                        task.State = FutureState.InProgress;
                        return true;
                    }
                }
            }

            return false;
        }

        internal object GetTaskResult(int taskId)
        {
            if (taskId < TotalTasks || !Tasks.ContainsKey(taskId))
            {
                //throw ?
                return null;
            }

            var task = Tasks[taskId];
            while (task.State <= FutureState.InProgress)
            {
                Thread.Yield();
            }

            switch (task.State)
            {
                case FutureState.Success:
                    return TaskResults[taskId];

                case FutureState.Faulted:
                    throw GetAggregateException(task);

                case FutureState.Canceled:
                default:
                    return null;
            }
        }

        internal bool CompleteTask(int taskId, object result)
        {
            if (taskId > TotalTasks || !Tasks.ContainsKey(taskId))
            {
                //throw ?
                return false;
            }
            var task = Tasks[taskId];

            if (task.State <= FutureState.InProgress)
            {
                lock (task)
                {
                    if (task.State <= FutureState.InProgress)
                    {
                        TaskResults[taskId] = result;
                        task.State = FutureState.Success;
                        return true;
                    }
                }
            }

            return false;
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

        internal bool CancelTask(int taskId)
        {
            if (taskId > TotalTasks || !Tasks.ContainsKey(taskId))
            {
                //throw ?
                return false;
            }
            var task = Tasks[taskId];

            if (task.State <= FutureState.InProgress)
            {
                lock (task)
                {
                    if (task.State <= FutureState.InProgress)
                    {
                        Tokens[taskId].Cancel();
                        task.State = FutureState.Canceled;
                        return true;
                    }
                }
            }

            return false;
        }

        internal bool FailTask(int taskId, ApplicationException ex)
        {
            if (taskId > TotalTasks || !Tasks.ContainsKey(taskId))
            {
                //throw ?
                return false;
            }
            var task = Tasks[taskId];

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

            return false;
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