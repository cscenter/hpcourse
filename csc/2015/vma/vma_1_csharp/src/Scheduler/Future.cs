using System;
using System.Collections.Generic;
using System.Linq;
using System.Text;
using System.Threading;
using System.Threading.Tasks;

namespace HPLab.Scheduler
{
    public enum FutureState
    {
        None,
        Created,
        InProgress,
        Success,
        Faulted,
        Canceled
    }

    public class Future
    {
        private readonly int _id;
        private readonly SimpleThreadScheduler _scheduler;

        public Future(int id, SimpleThreadScheduler scheduler)
        {
            _id = id;
            _scheduler = scheduler;
        }

        public Future(int id)
            : this(id, SimpleThreadScheduler.Default)
        {

        }

        internal List<Future> ChildTasks { get; set; }

        public FutureState State { get; internal set; }
        
        public int CurrentTime { get; internal set; }
        
        public int TotalTime { get; internal set; }

        public int Id { get { return _id; } }

        public int? ParentId { get; internal set; }

        public ApplicationException ExecutionException { get; internal set; }

        public object Result
        {
            get
            {
                return _scheduler.GetTaskResult(_id);
            }
        }

        public bool Cancel()
        {
            return _scheduler.CancelTask(_id);
        }

        public bool Fail(ApplicationException ex)
        {
            return _scheduler.FailTask(_id, ex);
        }
    }
}